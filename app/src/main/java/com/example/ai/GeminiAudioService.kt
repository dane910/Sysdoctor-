package com.example.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.util.Log
import com.example.model.AdvisorUiState
import com.example.model.TtsPlaybackState
import com.example.model.TtsVoiceOption
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

private const val TAG = "GeminiAudioService"

class GeminiAudioService(private val context: Context) {

    private val _ttsState = MutableStateFlow<TtsPlaybackState>(TtsPlaybackState.Idle)
    val ttsState: StateFlow<TtsPlaybackState> = _ttsState.asStateFlow()

    private val _selectedVoice = MutableStateFlow(TtsVoiceOption.KORE)
    val selectedVoice: StateFlow<TtsVoiceOption> = _selectedVoice.asStateFlow()

    private val _advisorState = MutableStateFlow<AdvisorUiState>(AdvisorUiState.Idle)
    val advisorState: StateFlow<AdvisorUiState> = _advisorState.asStateFlow()

    private var activePlaybackJob: Job? = null
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var tempAudioFile: File? = null

    private val advisorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are SysDoctor's mobile systems engineer and defensive security architect. " +
                        "Provide clear, accurate, ethical guidance on Android storage optimization, " +
                        "Wi-Fi/DNS security hardening, Android Verified Boot, A/B partition recovery, " +
                        "USB OTG vs ConfigFS gadget architecture, and non-root diagnostics. " +
                        "Never provide exploit payloads, malware instructions, or unauthorized access techniques. " +
                        "Keep answers structured, concise, and practical."
                )
            }
        )
    }

    fun selectVoice(voice: TtsVoiceOption) {
        _selectedVoice.value = voice
    }

    @OptIn(PublicPreviewAPI::class)
    fun speakText(scope: CoroutineScope, label: String, script: String) {
        stopPlayback()
        val voice = _selectedVoice.value
        activePlaybackJob = scope.launch {
            _ttsState.value = TtsPlaybackState.Generating(label, voice)
            try {
                val ttsConfig = generationConfig {
                    responseModalities = listOf(ResponseModality.AUDIO)
                    speechConfig = SpeechConfig(voice = Voice(voice.voiceId))
                }
                val ttsModel = Firebase.ai.generativeModel(
                    modelName = "gemini-3.1-flash-tts-preview",
                    generationConfig = ttsConfig
                )

                val response = withTimeout(60_000L) {
                    ttsModel.generateContent(
                        "Read the following technical briefing clearly and naturally at a steady pace:\n\n$script"
                    )
                }

                val audioPart = response.candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.filterIsInstance<InlineDataPart>()
                    ?.firstOrNull { it.mimeType.startsWith("audio/", ignoreCase = true) }

                if (audioPart == null || audioPart.inlineData.isEmpty()) {
                    _ttsState.value = TtsPlaybackState.Error("TTS model returned no audio data.")
                    return@launch
                }

                playAudioBytes(label, voice, audioPart.inlineData, audioPart.mimeType)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini TTS timed out", e)
                _ttsState.value = TtsPlaybackState.Error("Voice synthesis timed out after 60s. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini TTS failed", e)
                _ttsState.value = TtsPlaybackState.Error(
                    e.localizedMessage ?: "Failed to generate Gemini TTS briefing."
                )
            }
        }
    }

    private suspend fun playAudioBytes(
        label: String,
        voice: TtsVoiceOption,
        bytes: ByteArray,
        mimeType: String
    ) = withContext(Dispatchers.IO) {
        val lowerMime = mimeType.lowercase()
        val isRawPcm = lowerMime.contains("pcm") || lowerMime.contains("l16")

        if (isRawPcm) {
            val sampleRate = parseSampleRate(lowerMime) ?: 24000
            playPcmStream(label, voice, bytes, sampleRate)
        } else {
            playEncodedFile(label, voice, bytes, lowerMime)
        }
    }

    private fun parseSampleRate(mimeType: String): Int? {
        val regex = Regex("rate=(\\d+)")
        return regex.find(mimeType)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private suspend fun playPcmStream(
        label: String,
        voice: TtsVoiceOption,
        pcmBytes: ByteArray,
        sampleRateHz: Int
    ) {
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRateHz)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        _ttsState.value = TtsPlaybackState.Playing(label, voice)
        track.play()

        try {
            var offset = 0
            val chunkSize = minBuf
            while (offset < pcmBytes.size) {
                if (_ttsState.value is TtsPlaybackState.Paused) {
                    kotlinx.coroutines.delay(120)
                    continue
                }
                val toWrite = minOf(chunkSize, pcmBytes.size - offset)
                val written = track.write(pcmBytes, offset, toWrite)
                if (written < 0) break
                offset += written
            }
            if (_ttsState.value is TtsPlaybackState.Playing) {
                track.stop()
                _ttsState.value = TtsPlaybackState.Idle
            }
        } finally {
            runCatching { track.release() }
            if (audioTrack === track) audioTrack = null
        }
    }

    private suspend fun playEncodedFile(
        label: String,
        voice: TtsVoiceOption,
        bytes: ByteArray,
        mimeType: String
    ) {
        val ext = when {
            mimeType.contains("wav") -> "wav"
            mimeType.contains("ogg") -> "ogg"
            else -> "mp3"
        }
        val outFile = File(context.cacheDir, "tts_briefing_${System.currentTimeMillis()}.$ext")
        outFile.writeBytes(bytes)
        tempAudioFile = outFile

        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            setDataSource(outFile.absolutePath)
            prepare()
        }
        mediaPlayer = player
        _ttsState.value = TtsPlaybackState.Playing(label, voice)
        player.start()

        try {
            while (player.isPlaying || _ttsState.value is TtsPlaybackState.Paused) {
                kotlinx.coroutines.delay(150)
            }
            _ttsState.value = TtsPlaybackState.Idle
        } finally {
            runCatching { player.release() }
            if (mediaPlayer === player) mediaPlayer = null
            runCatching { outFile.delete() }
        }
    }

    fun togglePauseResume() {
        val current = _ttsState.value
        when (current) {
            is TtsPlaybackState.Playing -> {
                runCatching { audioTrack?.pause() }
                runCatching { mediaPlayer?.pause() }
                _ttsState.value = TtsPlaybackState.Paused(current.label, current.voice)
            }
            is TtsPlaybackState.Paused -> {
                runCatching { audioTrack?.play() }
                runCatching { mediaPlayer?.start() }
                _ttsState.value = TtsPlaybackState.Playing(current.label, current.voice)
            }
            else -> Unit
        }
    }

    fun stopPlayback() {
        activePlaybackJob?.cancel()
        activePlaybackJob = null
        runCatching {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.release()
        }
        audioTrack = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        runCatching { tempAudioFile?.delete() }
        tempAudioFile = null
        _ttsState.value = TtsPlaybackState.Idle
    }

    fun askSecurityAdvisor(scope: CoroutineScope, question: String, contextTelemetry: String) {
        scope.launch {
            _advisorState.value = AdvisorUiState.Loading
            val accumulated = StringBuilder()
            try {
                val prompt = buildString {
                    appendLine("Current Device Telemetry Snapshot:")
                    appendLine(contextTelemetry)
                    appendLine()
                    appendLine("User Question:")
                    appendLine(question)
                }
                withTimeout(60_000L) {
                    advisorModel.generateContentStream(prompt).collect { chunk ->
                        accumulated.append(chunk.text.orEmpty())
                        _advisorState.value = AdvisorUiState.Streaming(accumulated.toString())
                    }
                }
                if (accumulated.isNotBlank()) {
                    _advisorState.value = AdvisorUiState.Success(accumulated.toString())
                } else {
                    _advisorState.value = AdvisorUiState.Error("Advisor returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Advisor request timed out", e)
                _advisorState.value = AdvisorUiState.Error("Request timed out after 60s.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Advisor generation failed", e)
                _advisorState.value = AdvisorUiState.Error(e.localizedMessage ?: "Advisor request failed.")
            }
        }
    }

    fun clearAdvisorState() {
        _advisorState.value = AdvisorUiState.Idle
    }
}
