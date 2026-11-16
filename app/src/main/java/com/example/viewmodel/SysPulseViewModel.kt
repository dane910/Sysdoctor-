package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiAudioService
import com.example.data.BatteryHistoryRepository
import com.example.data.SysDoctorDatabase
import com.example.diagnostics.AdvancedCleanerRepository
import com.example.diagnostics.ArchitectureGuides
import com.example.diagnostics.DeviceDiagnosticsRepository
import com.example.diagnostics.HardwareToolkitRepository
import com.example.diagnostics.formatBytes
import com.example.model.AdvisorUiState
import com.example.model.ArchitectureTopic
import com.example.model.BatteryHardwareSnapshot
import com.example.model.BatteryTrendMetric
import com.example.model.BatteryTrendPoint
import com.example.model.BootImageInspectionState
import com.example.model.BootableUsbCapability
import com.example.model.CleanerCategoryType
import com.example.model.CleanerDeepScanResult
import com.example.model.CleanupReport
import com.example.model.DisplayMetricsSnapshot
import com.example.model.DuplicateScanState
import com.example.model.HardwareTestStatus
import com.example.model.LiveSensorReading
import com.example.model.QuarantinedFile
import com.example.model.RecoveryScanState
import com.example.model.SensorDescriptor
import com.example.model.StorageSummary
import com.example.model.StorageVolumeDetail
import com.example.model.SystemIntegrityInfo
import com.example.model.TtsPlaybackState
import com.example.model.TtsVoiceOption
import com.example.model.WifiSecurityAudit
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SysPulseViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceDiagnosticsRepository(application.applicationContext)
    private val cleanerRepository = AdvancedCleanerRepository(application.applicationContext)
    private val toolkitRepository = HardwareToolkitRepository(application.applicationContext)
    private val batteryHistoryRepository = BatteryHistoryRepository(
        SysDoctorDatabase.getInstance(application.applicationContext).batteryTelemetryDao()
    )
    private val audioService = GeminiAudioService(application.applicationContext)

    private val _storageSummary = MutableStateFlow(StorageSummary())
    val storageSummary: StateFlow<StorageSummary> = _storageSummary.asStateFlow()

    private val _wifiAudit = MutableStateFlow(WifiSecurityAudit())
    val wifiAudit: StateFlow<WifiSecurityAudit> = _wifiAudit.asStateFlow()

    private val _systemInfo = MutableStateFlow(SystemIntegrityInfo())
    val systemInfo: StateFlow<SystemIntegrityInfo> = _systemInfo.asStateFlow()

    private val _duplicateState = MutableStateFlow<DuplicateScanState>(DuplicateScanState.Idle)
    val duplicateState: StateFlow<DuplicateScanState> = _duplicateState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _auditBanner = MutableStateFlow<String?>(null)
    val auditBanner: StateFlow<String?> = _auditBanner.asStateFlow()

    private val _cleanupFeedback = MutableStateFlow<String?>(null)
    val cleanupFeedback: StateFlow<String?> = _cleanupFeedback.asStateFlow()

    private val _darkTheme = MutableStateFlow(true)
    val darkTheme: StateFlow<Boolean> = _darkTheme.asStateFlow()

    // 7-day Battery Trend (Room + Vico)
    val batteryTrend7Days: StateFlow<List<BatteryTrendPoint>> = batteryHistoryRepository
        .observeLast7Days()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedBatteryMetric = MutableStateFlow(BatteryTrendMetric.HEALTH_SCORE)
    val selectedBatteryMetric: StateFlow<BatteryTrendMetric> = _selectedBatteryMetric.asStateFlow()

    // Advanced System Cleaner State
    private val _cleanerScanResult = MutableStateFlow<CleanerDeepScanResult?>(null)
    val cleanerScanResult: StateFlow<CleanerDeepScanResult?> = _cleanerScanResult.asStateFlow()

    private val _isScanningCleaner = MutableStateFlow(false)
    val isScanningCleaner: StateFlow<Boolean> = _isScanningCleaner.asStateFlow()

    private val _cleanupReport = MutableStateFlow<CleanupReport?>(null)
    val cleanupReport: StateFlow<CleanupReport?> = _cleanupReport.asStateFlow()

    private val _quarantineFiles = MutableStateFlow<List<QuarantinedFile>>(emptyList())
    val quarantineFiles: StateFlow<List<QuarantinedFile>> = _quarantineFiles.asStateFlow()

    private val _quarantineBeforeDelete = MutableStateFlow(true)
    val quarantineBeforeDelete: StateFlow<Boolean> = _quarantineBeforeDelete.asStateFlow()

    // Hardware & Partition Toolkit State
    private val _batteryHardware = MutableStateFlow(BatteryHardwareSnapshot())
    val batteryHardware: StateFlow<BatteryHardwareSnapshot> = _batteryHardware.asStateFlow()

    private val _displayMetrics = MutableStateFlow(DisplayMetricsSnapshot())
    val displayMetrics: StateFlow<DisplayMetricsSnapshot> = _displayMetrics.asStateFlow()

    private val _sensors = MutableStateFlow<List<SensorDescriptor>>(emptyList())
    val sensors: StateFlow<List<SensorDescriptor>> = _sensors.asStateFlow()

    private val _activeSensorType = MutableStateFlow<Int?>(null)
    val activeSensorType: StateFlow<Int?> = _activeSensorType.asStateFlow()

    private val _activeSensorReading = MutableStateFlow<LiveSensorReading?>(null)
    val activeSensorReading: StateFlow<LiveSensorReading?> = _activeSensorReading.asStateFlow()

    private val _storageVolumes = MutableStateFlow<List<StorageVolumeDetail>>(emptyList())
    val storageVolumes: StateFlow<List<StorageVolumeDetail>> = _storageVolumes.asStateFlow()

    private val _recoveryScanState = MutableStateFlow<RecoveryScanState>(RecoveryScanState.Idle)
    val recoveryScanState: StateFlow<RecoveryScanState> = _recoveryScanState.asStateFlow()

    private val _bootableUsbCapability = MutableStateFlow(BootableUsbCapability())
    val bootableUsbCapability: StateFlow<BootableUsbCapability> = _bootableUsbCapability.asStateFlow()

    private val _bootImageInspectionState = MutableStateFlow<BootImageInspectionState>(BootImageInspectionState.Idle)
    val bootImageInspectionState: StateFlow<BootImageInspectionState> = _bootImageInspectionState.asStateFlow()

    private val _toolkitFeedback = MutableStateFlow<String?>(null)
    val toolkitFeedback: StateFlow<String?> = _toolkitFeedback.asStateFlow()

    val ttsState: StateFlow<TtsPlaybackState> = audioService.ttsState
    val selectedVoice: StateFlow<TtsVoiceOption> = audioService.selectedVoice
    val advisorState: StateFlow<AdvisorUiState> = audioService.advisorState
    val architectureTopics: List<ArchitectureTopic> = ArchitectureGuides.topics

    private var scanJob: Job? = null
    private var sensorJob: Job? = null
    private var recoveryJob: Job? = null
    private var bootImageJob: Job? = null

    init {
        refreshAllDiagnostics()
        runCleanerDeepScan(null)
        refreshToolkitData()
    }

    fun toggleTheme() {
        _darkTheme.value = !_darkTheme.value
    }

    fun selectBatteryMetric(metric: BatteryTrendMetric) {
        _selectedBatteryMetric.value = metric
    }

    fun runManualAudit() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _auditBanner.value = "Scanning system files, app volumes & cache directories..."
            kotlinx.coroutines.delay(800)
            refreshAllDiagnostics(showBanner = true)
        }
    }

    fun dismissAuditBanner() {
        _auditBanner.value = null
    }

    fun refreshAllDiagnostics(showBanner: Boolean = false) {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                coroutineScope {
                    val storageDeferred = async { runCatching { repository.getStorageSummary() }.getOrNull() }
                    val wifiDeferred = async { runCatching { repository.auditWifiAndNetwork() }.getOrNull() }
                    val sysDeferred = async { runCatching { repository.getSystemIntegrityInfo() }.getOrNull() }
                    val volumesDeferred = async { runCatching { toolkitRepository.readStorageVolumes() }.getOrNull() }

                    val storageRes = storageDeferred.await()
                    val wifiRes = wifiDeferred.await()
                    val sysRes = sysDeferred.await()
                    val volumesRes = volumesDeferred.await()

                    if (storageRes != null) _storageSummary.value = storageRes
                    if (wifiRes != null) _wifiAudit.value = wifiRes
                    if (sysRes != null) {
                        _systemInfo.value = sysRes
                        runCatching { batteryHistoryRepository.recordSnapshot(sysRes) }
                    }
                    if (volumesRes != null) _storageVolumes.value = volumesRes
                }

                _batteryHardware.value = toolkitRepository.readBatteryHardwareSnapshot(
                    evaluate = _batteryHardware.value.testStatus != HardwareTestStatus.NOT_RUN
                )

                if (showBanner) {
                    val timeLabel = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    val storage = _storageSummary.value
                    val wifi = _wifiAudit.value
                    val sys = _systemInfo.value
                    val usedPct = (storage.usedFraction * 100).toInt()
                    val netSummary = if (wifi.isConnected) "Network ${wifi.auditScore}/100" else "Offline"
                    _auditBanner.value =
                        "System Scan complete ($timeLabel) • Storage $usedPct% used • Battery ${sys.batteryLevelPct}% • $netSummary"
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun refreshWifiAuditOnly() {
        viewModelScope.launch {
            _wifiAudit.value = repository.auditWifiAndNetwork()
        }
    }

    // --- Advanced System Cleaner ---
    fun runCleanerDeepScan(safTreeUri: Uri? = null) {
        viewModelScope.launch {
            _isScanningCleaner.value = true
            _cleanerScanResult.value = cleanerRepository.scanForCleanableItems(safTreeUri)
            _quarantineFiles.value = cleanerRepository.listQuarantinedFiles()
            _storageSummary.value = repository.getStorageSummary()
            _isScanningCleaner.value = false
        }
    }

    fun toggleCleanerCategory(categoryType: CleanerCategoryType) {
        val current = _cleanerScanResult.value ?: return
        _cleanerScanResult.value = current.copy(
            categories = current.categories.map { cat ->
                if (cat.category == categoryType) cat.copy(isSelected = !cat.isSelected) else cat
            }
        )
    }

    fun toggleQuarantineBeforeDelete() {
        _quarantineBeforeDelete.value = !_quarantineBeforeDelete.value
    }

    fun executeAdvancedClean() {
        val current = _cleanerScanResult.value ?: return
        if (current.selectedItemCount == 0) return
        viewModelScope.launch {
            _isScanningCleaner.value = true
            val report = cleanerRepository.cleanSelectedCategories(
                scanResult = current,
                quarantineBeforeDelete = _quarantineBeforeDelete.value
            )
            _cleanupReport.value = report
            _cleanerScanResult.value = cleanerRepository.scanForCleanableItems(null)
            _quarantineFiles.value = cleanerRepository.listQuarantinedFiles()
            _storageSummary.value = repository.getStorageSummary()
            _cleanupFeedback.value = "Cleaned ${report.totalItemsRemoved} item(s), freeing ${formatBytes(report.totalBytesFreed)}."
            _isScanningCleaner.value = false
        }
    }

    fun dismissCleanupReport() {
        _cleanupReport.value = null
    }

    fun clearAppCache() {
        viewModelScope.launch {
            val freed = repository.clearAppCache()
            _storageSummary.value = repository.getStorageSummary()
            _cleanerScanResult.value = cleanerRepository.scanForCleanableItems(null)
            _cleanupFeedback.value = if (freed > 0L) {
                "Cleared ${formatBytes(freed)} from app cache directories."
            } else {
                "App cache is already clean (0 B to reclaim)."
            }
        }
    }

    fun dismissCleanupFeedback() {
        _cleanupFeedback.value = null
    }

    fun restoreQuarantinedFile(id: String, destUri: Uri) {
        viewModelScope.launch {
            val ok = cleanerRepository.restoreQuarantinedFile(id, destUri)
            _quarantineFiles.value = cleanerRepository.listQuarantinedFiles()
            val msg = if (ok) "Restored file from Quarantine Vault." else "Could not restore file to chosen destination."
            _cleanupFeedback.value = msg
            _toolkitFeedback.value = msg
        }
    }

    fun purgeQuarantinedFile(id: String) {
        viewModelScope.launch {
            cleanerRepository.purgeQuarantinedFile(id)
            _quarantineFiles.value = cleanerRepository.listQuarantinedFiles()
            _toolkitFeedback.value = "Permanently purged quarantined backup."
        }
    }

    // --- Duplicate Scanner ---
    fun startDuplicateScan(treeUri: Uri) {
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _duplicateState.value = DuplicateScanState.Scanning(0, 0, 0, "Opening folder…")
            repository.scanFolderForDuplicates(treeUri)
                .catch { e ->
                    _duplicateState.value = DuplicateScanState.Error(
                        e.localizedMessage ?: "Failed to scan folder."
                    )
                }
                .collect { state ->
                    _duplicateState.value = state
                }
        }
    }

    fun toggleDuplicateSelection(groupHash: String, uriString: String) {
        val current = _duplicateState.value as? DuplicateScanState.Results ?: return
        val updatedGroups = current.groups.map { group ->
            if (group.hash != groupHash) group
            else {
                group.copy(
                    files = group.files.map { item ->
                        if (item.uriString == uriString) {
                            item.copy(isSelectedForDelete = !item.isSelectedForDelete)
                        } else {
                            item
                        }
                    }
                )
            }
        }
        _duplicateState.value = current.copy(groups = updatedGroups)
    }

    fun deleteSelectedDuplicates() {
        val current = _duplicateState.value as? DuplicateScanState.Results ?: return
        val toDelete = current.groups.flatMap { g ->
            g.files.filter { it.isSelectedForDelete }.map { it.uriString }
        }.toSet()
        if (toDelete.isEmpty()) return

        viewModelScope.launch {
            val (deletedCount, freedBytes) = repository.deleteDuplicateUris(toDelete.toList())
            val remainingGroups = current.groups.mapNotNull { group ->
                val kept = group.files.filterNot { it.uriString in toDelete }
                if (kept.size <= 1) null else group.copy(files = kept)
            }
            _storageSummary.value = repository.getStorageSummary()
            _duplicateState.value = current.copy(
                groups = remainingGroups,
                lastFreedBytes = freedBytes,
                lastDeletedCount = deletedCount
            )
            _cleanupFeedback.value = "Removed $deletedCount duplicate file(s), reclaiming ${formatBytes(freedBytes)}."
        }
    }

    fun resetDuplicateScan() {
        scanJob?.cancel()
        _duplicateState.value = DuplicateScanState.Idle
    }

    // --- Hardware, File Recovery & Partition Toolkit ---
    fun refreshToolkitData() {
        viewModelScope.launch {
            _batteryHardware.value = toolkitRepository.readBatteryHardwareSnapshot(
                evaluate = _batteryHardware.value.testStatus != HardwareTestStatus.NOT_RUN
            )
            _displayMetrics.value = toolkitRepository.readDisplayMetricsSnapshot(_displayMetrics.value.testStatus)
            _sensors.value = toolkitRepository.listHardwareSensors()
            _storageVolumes.value = toolkitRepository.readStorageVolumes()
            _quarantineFiles.value = cleanerRepository.listQuarantinedFiles()
            _bootableUsbCapability.value = toolkitRepository.inspectBootableUsbCapability()
        }
    }

    fun runBatteryDiagnosticTest() {
        viewModelScope.launch {
            _batteryHardware.value = toolkitRepository.readBatteryHardwareSnapshot(evaluate = true)
        }
    }

    fun updateDisplayTestStatus(status: HardwareTestStatus) {
        _displayMetrics.value = _displayMetrics.value.copy(testStatus = status)
    }

    fun startSensorTest(sensorType: Int) {
        sensorJob?.cancel()
        _activeSensorType.value = sensorType
        _activeSensorReading.value = null
        sensorJob = viewModelScope.launch {
            toolkitRepository.observeSensor(sensorType).collect { reading ->
                _activeSensorReading.value = reading
            }
        }
    }

    fun stopSensorTest() {
        sensorJob?.cancel()
        sensorJob = null
        _activeSensorType.value = null
        _activeSensorReading.value = null
    }

    fun startRecoveryScan(treeUri: Uri) {
        recoveryJob?.cancel()
        recoveryJob = viewModelScope.launch {
            _recoveryScanState.value = RecoveryScanState.Scanning(0, "Opening folder…")
            toolkitRepository.scanFolderForRecoverableFiles(treeUri)
                .catch { e ->
                    _recoveryScanState.value = RecoveryScanState.Error(
                        e.localizedMessage ?: "Failed to scan folder for recoverable files."
                    )
                }
                .collect { state ->
                    _recoveryScanState.value = state
                }
        }
    }

    fun recoverFileToUri(sourceUri: Uri, destUri: Uri) {
        viewModelScope.launch {
            val ok = toolkitRepository.recoverDocumentToDestination(sourceUri, destUri)
            _toolkitFeedback.value = if (ok) {
                "Recovered file exported successfully."
            } else {
                "Failed to export recovered file."
            }
        }
    }

    fun resetRecoveryScan() {
        recoveryJob?.cancel()
        _recoveryScanState.value = RecoveryScanState.Idle
    }

    fun inspectBootImage(uri: Uri) {
        bootImageJob?.cancel()
        bootImageJob = viewModelScope.launch {
            toolkitRepository.inspectBootableImage(uri)
                .catch { e ->
                    _bootImageInspectionState.value = BootImageInspectionState.Error(
                        e.localizedMessage ?: "Failed to inspect ISO/IMG image."
                    )
                }
                .collect { state ->
                    _bootImageInspectionState.value = state
                }
        }
    }

    fun resetBootImageInspection() {
        bootImageJob?.cancel()
        _bootImageInspectionState.value = BootImageInspectionState.Idle
    }

    fun dismissToolkitFeedback() {
        _toolkitFeedback.value = null
    }

    // --- Gemini TTS & Advisor ---
    fun selectTtsVoice(voice: TtsVoiceOption) {
        audioService.selectVoice(voice)
    }

    fun toggleTtsPauseResume() {
        audioService.togglePauseResume()
    }

    fun stopTtsPlayback() {
        audioService.stopPlayback()
    }

    fun speakOverviewBriefing() {
        val storage = _storageSummary.value
        val wifi = _wifiAudit.value
        val sys = _systemInfo.value
        val usedPct = (storage.usedFraction * 100).toInt()
        val script = buildString {
            append("SysDoctor diagnostic briefing for ${sys.manufacturer} ${sys.model}. ")
            append("Android ${sys.androidVersion} with security patch ${sys.securityPatch}. ")
            append("Internal storage is $usedPct percent used, with ${formatBytes(storage.availableBytes)} available. ")
            append("Available memory is ${formatBytes(sys.availableRamBytes)} out of ${formatBytes(sys.totalRamBytes)}. ")
            append("Battery is at ${sys.batteryLevelPct} percent and ${sys.batteryTempC} degrees Celsius. ")
            if (wifi.isConnected) {
                append("Network audit score is ${wifi.auditScore} out of 100 over ${wifi.transportLabel}. ")
                append("Security protocol is ${wifi.securityProtocol}. ")
                append(if (wifi.isPrivateDnsActive) "Private DNS is active. " else "Private DNS is off; enabling DNS over TLS is recommended. ")
            } else {
                append("Device is currently offline. ")
            }
        }
        audioService.speakText(viewModelScope, "System Pulse Briefing", script)
    }

    fun speakWifiAudit() {
        val wifi = _wifiAudit.value
        val script = buildString {
            if (!wifi.isConnected) {
                append("No active network connection detected. Connect to Wi-Fi or cellular data to run a live security audit.")
            } else {
                append("Network security audit score: ${wifi.auditScore} out of 100. ")
                append("Connected via ${wifi.transportLabel}. ")
                if (wifi.isWifi) {
                    append("Wi-Fi protocol: ${wifi.securityProtocol}, frequency band ${wifi.bandLabel}. ")
                }
                append(if (wifi.isPrivateDnsActive) "Private DNS over TLS is enabled. " else "Private DNS is not active. ")
                append(if (wifi.isVpnActive) "An encrypted VPN tunnel is active. " else "No VPN tunnel detected. ")
                wifi.findings.forEach { finding ->
                    append("${finding.title}. ${finding.detail} ")
                }
            }
        }
        audioService.speakText(viewModelScope, "Wi-Fi Security Audit", script)
    }

    fun speakCleanupSummary() {
        val report = _cleanupReport.value
        val storage = _storageSummary.value
        val dup = _duplicateState.value
        val script = buildString {
            append("Storage and system cleanup report. ")
            if (report != null) {
                append("Last deep clean removed ${report.totalItemsRemoved} items and freed ${formatBytes(report.totalBytesFreed)}. ")
            }
            append("Total space available is ${formatBytes(storage.availableBytes)}. ")
            append("App cache footprint is ${formatBytes(storage.totalAppCacheBytes)}. ")
            when (dup) {
                is DuplicateScanState.Results -> {
                    if (dup.groups.isEmpty()) {
                        append("Scanned ${dup.totalFilesScanned} files in ${dup.folderName} and found zero duplicate files.")
                    } else {
                        append("Scanned ${dup.totalFilesScanned} files in ${dup.folderName}. Found ${dup.groups.size} duplicate groups wasting ${formatBytes(dup.totalWastedBytes)}.")
                    }
                }
                else -> append("Pick any folder such as Downloads to scan for exact SHA-256 duplicate files or stale logs.")
            }
        }
        audioService.speakText(viewModelScope, "Storage & Cleanup Report", script)
    }

    fun speakArchitectureTopic(topic: ArchitectureTopic) {
        audioService.speakText(viewModelScope, topic.title, topic.speechScript)
    }

    fun speakCustomText(label: String, text: String) {
        audioService.speakText(viewModelScope, label, text)
    }

    fun askAdvisor(question: String) {
        if (question.isBlank()) return
        val sys = _systemInfo.value
        val wifi = _wifiAudit.value
        val storage = _storageSummary.value
        val telemetrySnapshot = buildString {
            appendLine("Device: ${sys.manufacturer} ${sys.model}, Android ${sys.androidVersion} (API ${sys.apiLevel}), Patch ${sys.securityPatch}")
            appendLine("Build tags: ${sys.buildTags}, USB devices attached: ${sys.usbDevices.size}")
            appendLine("Storage available: ${formatBytes(storage.availableBytes)} / ${formatBytes(storage.totalBytes)}")
            appendLine("Network: ${wifi.transportLabel}, Protocol: ${wifi.securityProtocol}, Private DNS: ${wifi.isPrivateDnsActive}, VPN: ${wifi.isVpnActive}, Audit score: ${wifi.auditScore}/100")
        }
        audioService.askSecurityAdvisor(viewModelScope, question.trim(), telemetrySnapshot)
    }

    fun clearAdvisor() {
        audioService.clearAdvisorState()
    }

    override fun onCleared() {
        super.onCleared()
        sensorJob?.cancel()
        audioService.stopPlayback()
    }
}
