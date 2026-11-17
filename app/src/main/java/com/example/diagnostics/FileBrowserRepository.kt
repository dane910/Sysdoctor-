package com.example.diagnostics

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.ui.graphics.asImageBitmap
import com.example.model.BrowserEntry
import com.example.model.DeepFileScanState
import com.example.model.DeepFindingCategory
import com.example.model.DeepScanFinding
import com.example.model.FilePreviewContent
import com.example.model.FilePreviewState
import com.example.model.FileSortOption
import com.example.model.SeverityLevel
import com.example.model.StorageBreadcrumb
import com.example.model.StorageRootKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.URLConnection
import java.security.MessageDigest
import kotlin.math.max

data class IntegrityAnalysis(
    val detectedFormat: String,
    val findingCategory: DeepFindingCategory?,
    val severity: SeverityLevel,
    val verdictTitle: String,
    val reasonDetail: String
)

class FileBrowserRepository(private val context: Context) {

    fun resolveLocalRoot(kind: StorageRootKind): File? {
        return when (kind) {
            StorageRootKind.APP_FILES -> context.filesDir
            StorageRootKind.INTERNAL_CACHE -> context.cacheDir
            StorageRootKind.EXTERNAL_CACHE -> context.externalCacheDir
            StorageRootKind.QUARANTINE -> File(context.filesDir, "sysdoctor_quarantine").apply { mkdirs() }
            StorageRootKind.SAF_FOLDER -> null
        }
    }

    fun getSafDisplayName(treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull().orEmpty()
        return docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "Selected Folder" }
    }

    suspend fun listLocalDirectory(
        dir: File,
        sortOption: FileSortOption
    ): List<BrowserEntry> = withContext(Dispatchers.IO) {
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        val children = dir.listFiles() ?: return@withContext emptyList()
        val mapped = children.map { file ->
            val isDir = file.isDirectory
            val mime = if (isDir) {
                DocumentsContract.Document.MIME_TYPE_DIR
            } else {
                guessMimeType(file.name)
            }
            BrowserEntry(
                id = file.absolutePath,
                name = file.name,
                pathOrUri = file.absolutePath,
                isDirectory = isDir,
                sizeBytes = if (isDir) (file.listFiles()?.size?.toLong() ?: 0L) else file.length(),
                lastModifiedMillis = file.lastModified(),
                mimeType = mime,
                isSafDocument = false
            )
        }
        sortEntries(mapped, sortOption)
    }

    suspend fun listSafDirectory(
        treeUri: Uri,
        parentDocumentId: String,
        sortOption: FileSortOption
    ): List<BrowserEntry> = withContext(Dispatchers.IO) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        val list = mutableListOf<BrowserEntry>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val docId = cursor.getString(idCol) ?: continue
                val name = cursor.getString(nameCol) ?: "Unnamed"
                val mime = cursor.getString(mimeCol) ?: guessMimeType(name)
                val size = cursor.getLong(sizeCol)
                val mod = cursor.getLong(modCol)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                list.add(
                    BrowserEntry(
                        id = docUri.toString(),
                        name = name,
                        pathOrUri = docUri.toString(),
                        isDirectory = isDir,
                        sizeBytes = size,
                        lastModifiedMillis = mod,
                        mimeType = mime,
                        isSafDocument = true
                    )
                )
            }
        }
        sortEntries(list, sortOption)
    }

    fun initialBreadcrumbsForLocal(kind: StorageRootKind): List<StorageBreadcrumb> {
        val root = resolveLocalRoot(kind) ?: return emptyList()
        return listOf(
            StorageBreadcrumb(
                id = root.absolutePath,
                label = kind.label,
                pathOrUri = root.absolutePath,
                isSaf = false
            )
        )
    }

    fun initialBreadcrumbsForSaf(treeUri: Uri): List<StorageBreadcrumb> {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val label = getSafDisplayName(treeUri)
        return listOf(
            StorageBreadcrumb(
                id = docId,
                label = label,
                pathOrUri = treeUri.toString(),
                isSaf = true
            )
        )
    }

    fun sortEntries(entries: List<BrowserEntry>, sortOption: FileSortOption): List<BrowserEntry> {
        return when (sortOption) {
            FileSortOption.FOLDERS_FIRST_NAME -> entries.sortedWith(
                compareByDescending<BrowserEntry> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
            FileSortOption.SIZE_DESC -> entries.sortedWith(
                compareByDescending<BrowserEntry> { it.isDirectory }
                    .thenByDescending { it.sizeBytes }
            )
            FileSortOption.MODIFIED_DESC -> entries.sortedWith(
                compareByDescending<BrowserEntry> { it.isDirectory }
                    .thenByDescending { it.lastModifiedMillis }
            )
        }
    }

    private data class ScannedRawFile(
        val id: String,
        val name: String,
        val pathOrUri: String,
        val sizeBytes: Long,
        val lastModifiedMillis: Long,
        val mimeType: String,
        val isSafDocument: Boolean
    )

    fun runDeepFileScan(safTreeUri: Uri?): Flow<DeepFileScanState> = flow {
        val scopeLabel = if (safTreeUri != null) {
            "Folder: ${getSafDisplayName(safTreeUri)}"
        } else {
            "App Scoped Storage (Files & Caches)"
        }

        emit(DeepFileScanState.Scanning("Indexing files…", 0, scopeLabel))

        val collected = mutableListOf<ScannedRawFile>()
        if (safTreeUri != null) {
            val rootDocId = DocumentsContract.getTreeDocumentId(safTreeUri)
            val queue = ArrayDeque<String>()
            queue.add(rootDocId)
            while (queue.isNotEmpty() && collected.size < MAX_SCAN_FILES) {
                val parentId = queue.removeFirst()
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(safTreeUri, parentId)
                val projection = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )
                runCatching {
                    context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                        val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                        val modCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                        while (cursor.moveToNext() && collected.size < MAX_SCAN_FILES) {
                            val docId = cursor.getString(idCol) ?: continue
                            val name = cursor.getString(nameCol) ?: "Unnamed"
                            val mime = cursor.getString(mimeCol) ?: guessMimeType(name)
                            val size = cursor.getLong(sizeCol)
                            val mod = cursor.getLong(modCol)
                            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                                queue.add(docId)
                            } else {
                                val docUri = DocumentsContract.buildDocumentUriUsingTree(safTreeUri, docId)
                                collected.add(
                                    ScannedRawFile(
                                        id = docUri.toString(),
                                        name = name,
                                        pathOrUri = docUri.toString(),
                                        sizeBytes = size,
                                        lastModifiedMillis = mod,
                                        mimeType = mime,
                                        isSafDocument = true
                                    )
                                )
                                if (collected.size % 20 == 0) {
                                    emit(DeepFileScanState.Scanning("Indexing SAF files…", collected.size, name))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val quarantineRoot = resolveLocalRoot(StorageRootKind.QUARANTINE)?.absolutePath
            val localRoots = listOfNotNull(
                context.filesDir,
                context.cacheDir,
                context.externalCacheDir,
                context.codeCacheDir
            )
            for (root in localRoots) {
                if (!root.exists()) continue
                val stack = ArrayDeque<File>()
                stack.add(root)
                while (stack.isNotEmpty() && collected.size < MAX_SCAN_FILES) {
                    val current = stack.removeFirst()
                    val children = current.listFiles() ?: continue
                    for (child in children) {
                        if (collected.size >= MAX_SCAN_FILES) break
                        if (child.isDirectory) {
                            if (quarantineRoot == null || !child.absolutePath.startsWith(quarantineRoot)) {
                                stack.add(child)
                            }
                        } else {
                            collected.add(
                                ScannedRawFile(
                                    id = child.absolutePath,
                                    name = child.name,
                                    pathOrUri = child.absolutePath,
                                    sizeBytes = child.length(),
                                    lastModifiedMillis = child.lastModified(),
                                    mimeType = guessMimeType(child.name),
                                    isSafDocument = false
                                )
                            )
                            if (collected.size % 20 == 0) {
                                emit(DeepFileScanState.Scanning("Indexing app storage…", collected.size, child.name))
                            }
                        }
                    }
                }
            }
        }

        val findings = mutableListOf<DeepScanFinding>()

        // Pass 1: Magic-byte header, threat/malware heuristic, and corruption check
        collected.forEachIndexed { idx, file ->
            if (idx % 25 == 0) {
                emit(DeepFileScanState.Scanning("Inspecting headers & integrity…", idx + 1, file.name))
            }
            val (header, readErr) = readHeaderBytes(file.pathOrUri, file.isSafDocument, 64)
            val analysis = analyzeHeaderAndName(
                fileName = file.name,
                sizeBytes = file.sizeBytes,
                header = header,
                isReadError = readErr
            )
            if (analysis.findingCategory != null) {
                findings.add(
                    DeepScanFinding(
                        id = "${analysis.findingCategory.name}_${file.id}",
                        name = file.name,
                        pathOrUri = file.pathOrUri,
                        sizeBytes = file.sizeBytes,
                        lastModifiedMillis = file.lastModifiedMillis,
                        mimeType = file.mimeType,
                        isSafDocument = file.isSafDocument,
                        category = analysis.findingCategory,
                        severity = analysis.severity,
                        verdictTitle = analysis.verdictTitle,
                        reasonDetail = analysis.reasonDetail,
                        isSelectedForDelete = analysis.findingCategory == DeepFindingCategory.THREAT_SUSPICIOUS ||
                            analysis.findingCategory == DeepFindingCategory.CORRUPTED
                    )
                )
            }
        }

        // Pass 2: SHA-256 Duplicate Detection (bucket by non-zero size)
        val sizeGroups = collected.filter { it.sizeBytes > 0L }
            .groupBy { it.sizeBytes }
            .filterValues { it.size > 1 }
        val hashBuckets = mutableMapOf<String, MutableList<ScannedRawFile>>()
        var hashed = 0
        for ((_, candidates) in sizeGroups) {
            for (candidate in candidates) {
                hashed++
                if (hashed % 5 == 0 || hashed == 1) {
                    emit(DeepFileScanState.Scanning("Computing SHA-256 digests…", collected.size, candidate.name))
                }
                val digest = computeSha256(candidate.pathOrUri, candidate.isSafDocument)
                if (digest != null) {
                    hashBuckets.getOrPut(digest) { mutableListOf() }.add(candidate)
                }
            }
        }

        hashBuckets.filterValues { it.size > 1 }
            .entries
            .sortedByDescending { (_, list) -> list.first().sizeBytes * (list.size - 1) }
            .forEach { (hash, list) ->
                val sorted = list.sortedBy { it.lastModifiedMillis }
                val shortHash = hash.take(12)
                sorted.forEachIndexed { idx, f ->
                    val keep = idx == 0
                    findings.add(
                        DeepScanFinding(
                            id = "DUP_${shortHash}_${f.id}",
                            name = f.name,
                            pathOrUri = f.pathOrUri,
                            sizeBytes = f.sizeBytes,
                            lastModifiedMillis = f.lastModifiedMillis,
                            mimeType = f.mimeType,
                            isSafDocument = f.isSafDocument,
                            category = DeepFindingCategory.DUPLICATE,
                            severity = if (keep) SeverityLevel.INFO else SeverityLevel.CAUTION,
                            verdictTitle = if (keep) "Original Copy (Keep)" else "Exact Duplicate (${sorted.size} copies)",
                            reasonDetail = "SHA-256 $shortHash… matches ${sorted.size - 1} other file(s).",
                            sha256Short = shortHash,
                            groupHash = hash,
                            isSuggestedKeep = keep,
                            isSelectedForDelete = !keep
                        )
                    )
                }
            }

        val orderedFindings = findings.sortedWith(
            compareBy<DeepScanFinding> {
                when (it.category) {
                    DeepFindingCategory.THREAT_SUSPICIOUS -> 0
                    DeepFindingCategory.CORRUPTED -> 1
                    DeepFindingCategory.DUPLICATE -> 2
                    DeepFindingCategory.ALL -> 3
                }
            }.thenBy { it.isSuggestedKeep }
        )

        emit(
            DeepFileScanState.Results(
                scopeLabel = scopeLabel,
                totalFilesScanned = collected.size,
                findings = orderedFindings
            )
        )
    }.flowOn(Dispatchers.IO)

    suspend fun inspectFileForPreview(
        id: String,
        name: String,
        pathOrUri: String,
        sizeBytes: Long,
        lastModifiedMillis: Long,
        mimeType: String,
        isSafDocument: Boolean
    ): FilePreviewState = withContext(Dispatchers.IO) {
        runCatching {
            val (header, readErr) = readHeaderBytes(pathOrUri, isSafDocument, 64)
            val analysis = analyzeHeaderAndName(name, sizeBytes, header, readErr)
            val sha256 = if (sizeBytes in 1L..MAX_HASH_PREVIEW_BYTES) {
                computeSha256(pathOrUri, isSafDocument) ?: "Unavailable"
            } else if (sizeBytes == 0L) {
                "Empty file (0 bytes)"
            } else {
                "Skipped (>32 MB for fast preview)"
            }

            val lower = name.lowercase()
            val ext = lower.substringAfterLast('.', "")
            val isImage = mimeType.startsWith("image/") || ext in IMAGE_EXTENSIONS
            val isTextLike = mimeType.startsWith("text/") ||
                mimeType == "application/json" ||
                mimeType == "application/xml" ||
                ext in TEXT_EXTENSIONS

            val content: FilePreviewContent = when {
                readErr -> FilePreviewContent.Empty("Unable to read file stream. It may be locked or damaged.")
                sizeBytes == 0L -> FilePreviewContent.Empty("File is 0 bytes (empty or truncated).")
                isImage -> {
                    decodeSampledImage(pathOrUri, isSafDocument)
                        ?: FilePreviewContent.HexBinary(formatHexPreview(header))
                }
                isTextLike -> {
                    readBoundedText(pathOrUri, isSafDocument)
                        ?: FilePreviewContent.HexBinary(formatHexPreview(header))
                }
                else -> FilePreviewContent.HexBinary(formatHexPreview(header))
            }

            FilePreviewState.Ready(
                id = id,
                name = name,
                pathOrUri = pathOrUri,
                sizeBytes = sizeBytes,
                lastModifiedMillis = lastModifiedMillis,
                mimeType = mimeType,
                isSafDocument = isSafDocument,
                detectedFormat = analysis.detectedFormat,
                integritySeverity = analysis.severity,
                integrityVerdict = analysis.verdictTitle,
                integrityDetail = analysis.reasonDetail,
                sha256Hex = sha256,
                content = content
            )
        }.getOrElse { err ->
            FilePreviewState.Error(name, err.localizedMessage ?: "Failed to inspect file")
        }
    }

    suspend fun deleteTarget(pathOrUri: String, isSafDocument: Boolean): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (isSafDocument) {
                DocumentsContract.deleteDocument(context.contentResolver, Uri.parse(pathOrUri))
            } else {
                val target = File(pathOrUri)
                if (!target.exists()) false
                else if (target.isDirectory) target.deleteRecursively()
                else target.delete()
            }
        }.getOrDefault(false)
    }

    private fun readHeaderBytes(pathOrUri: String, isSafDocument: Boolean, maxBytes: Int): Pair<ByteArray, Boolean> {
        return try {
            openStream(pathOrUri, isSafDocument)?.use { input ->
                val buf = ByteArray(maxBytes)
                val read = input.read(buf)
                if (read <= 0) ByteArray(0) to false else buf.copyOf(read) to false
            } ?: (ByteArray(0) to true)
        } catch (_: Exception) {
            ByteArray(0) to true
        }
    }

    private fun computeSha256(pathOrUri: String, isSafDocument: Boolean): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(32 * 1024)
            openStream(pathOrUri, isSafDocument)?.use { input ->
                var read = input.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            } ?: return null
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeSampledImage(pathOrUri: String, isSafDocument: Boolean): FilePreviewContent.Image? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            openStream(pathOrUri, isSafDocument)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            } ?: return null
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            val maxDim = max(bounds.outWidth, bounds.outHeight)
            while (maxDim / sample > 1200) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = openStream(pathOrUri, isSafDocument)?.use { input ->
                BitmapFactory.decodeStream(input, null, opts)
            } ?: return null

            FilePreviewContent.Image(
                bitmap = bmp.asImageBitmap(),
                width = bounds.outWidth,
                height = bounds.outHeight
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun readBoundedText(pathOrUri: String, isSafDocument: Boolean): FilePreviewContent.Text? {
        return try {
            openStream(pathOrUri, isSafDocument)?.use { input ->
                val limit = 24 * 1024
                val buffer = ByteArray(limit + 1)
                var total = 0
                while (total < buffer.size) {
                    val read = input.read(buffer, total, buffer.size - total)
                    if (read <= 0) break
                    total += read
                }
                val truncated = total > limit
                val slice = buffer.copyOf(minOf(total, limit))
                val nullCount = slice.count { it == 0.toByte() }
                if (slice.isNotEmpty() && nullCount > slice.size / 10) {
                    return null
                }
                FilePreviewContent.Text(
                    text = slice.toString(Charsets.UTF_8),
                    truncated = truncated
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openStream(pathOrUri: String, isSafDocument: Boolean): InputStream? {
        return if (isSafDocument) {
            context.contentResolver.openInputStream(Uri.parse(pathOrUri))
        } else {
            val f = File(pathOrUri)
            if (f.exists() && f.isFile) f.inputStream() else null
        }
    }

    private fun guessMimeType(fileName: String): String {
        val ext = fileName.lowercase().substringAfterLast('.', "")
        return when (ext) {
            "log", "txt", "md", "cfg", "ini", "properties" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            "apk" -> "application/vnd.android.package-archive"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> URLConnection.guessContentTypeFromName(fileName) ?: "application/octet-stream"
        }
    }

    private fun formatHexPreview(header: ByteArray): String {
        if (header.isEmpty()) return "(Empty — 0 bytes)"
        return header.toList().chunked(8).joinToString("\n") { row ->
            val hex = row.joinToString(" ") { "%02X".format(it) }.padEnd(23, ' ')
            val ascii = row.joinToString("") { b ->
                val c = b.toInt() and 0xFF
                if (c in 32..126) c.toChar().toString() else "."
            }
            "$hex  |$ascii|"
        }
    }

    companion object {
        private const val MAX_SCAN_FILES = 2500
        private const val MAX_HASH_PREVIEW_BYTES = 32L * 1024L * 1024L

        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
        private val TEXT_EXTENSIONS = setOf(
            "txt", "log", "json", "xml", "csv", "md", "kt", "java", "sh", "bat",
            "html", "htm", "ini", "cfg", "conf", "properties", "yaml", "yml", "sql"
        )
        private val EXECUTABLE_OR_SCRIPT_EXTENSIONS = setOf(
            "apk", "dex", "jar", "so", "exe", "dll", "bat", "cmd", "sh", "vbs", "ps1", "msi"
        )

        fun analyzeHeaderAndName(
            fileName: String,
            sizeBytes: Long,
            header: ByteArray,
            isReadError: Boolean = false
        ): IntegrityAnalysis {
            val lower = fileName.lowercase()
            val ext = lower.substringAfterLast('.', "")
            val detected = detectMagicFormat(header)

            if (isReadError) {
                return IntegrityAnalysis(
                    detectedFormat = "Unreadable Stream",
                    findingCategory = DeepFindingCategory.CORRUPTED,
                    severity = SeverityLevel.ALERT,
                    verdictTitle = "Unreadable / I/O Failure",
                    reasonDetail = "Stream could not be opened or read cleanly; file handle may be corrupted."
                )
            }

            if (sizeBytes == 0L) {
                return IntegrityAnalysis(
                    detectedFormat = "0-Byte Empty",
                    findingCategory = DeepFindingCategory.CORRUPTED,
                    severity = SeverityLevel.CAUTION,
                    verdictTitle = "Corrupted / Empty (0 B)",
                    reasonDetail = "File contains 0 bytes and has no valid header payload."
                )
            }

            // Double extension spoofing (e.g., invoice.pdf.apk, photo.jpg.exe)
            val segments = lower.split('.')
            if (segments.size >= 3 && segments.last() in EXECUTABLE_OR_SCRIPT_EXTENSIONS) {
                val decoy = segments[segments.size - 2]
                if (decoy in setOf("pdf", "jpg", "jpeg", "png", "txt", "doc", "docx", "xls", "mp3", "mp4")) {
                    return IntegrityAnalysis(
                        detectedFormat = detected,
                        findingCategory = DeepFindingCategory.THREAT_SUSPICIOUS,
                        severity = SeverityLevel.ALERT,
                        verdictTitle = "Double-Extension Payload",
                        reasonDetail = "Suspicious spoofed filename hiding '.${segments.last()}' behind '.$decoy'."
                    )
                }
            }

            // Disguised executable or archive header inside non-executable extension
            val isBinaryExecutableHeader = detected.startsWith("ELF") ||
                detected.startsWith("PE/MZ") ||
                detected.startsWith("DEX") ||
                detected.startsWith("Mach-O")
            if (isBinaryExecutableHeader && ext !in setOf("so", "dex", "exe", "dll", "bin")) {
                return IntegrityAnalysis(
                    detectedFormat = detected,
                    findingCategory = DeepFindingCategory.THREAT_SUSPICIOUS,
                    severity = SeverityLevel.ALERT,
                    verdictTitle = "Disguised Executable Signature",
                    reasonDetail = "Header identifies '$detected' inside a '.$ext' file — potential malware masquerade."
                )
            }

            if (detected.startsWith("ZIP/APK") && ext in (IMAGE_EXTENSIONS + setOf("pdf", "txt", "mp3", "mp4"))) {
                return IntegrityAnalysis(
                    detectedFormat = detected,
                    findingCategory = DeepFindingCategory.THREAT_SUSPICIOUS,
                    severity = SeverityLevel.ALERT,
                    verdictTitle = "Disguised Archive / APK",
                    reasonDetail = "ZIP/APK archive header found inside '.$ext' media/document file."
                )
            }

            // Standalone risky scripts/installers in user/cache folders
            if (ext in EXECUTABLE_OR_SCRIPT_EXTENSIONS) {
                return IntegrityAnalysis(
                    detectedFormat = detected,
                    findingCategory = DeepFindingCategory.THREAT_SUSPICIOUS,
                    severity = SeverityLevel.CAUTION,
                    verdictTitle = "Suspicious Executable / Script",
                    reasonDetail = "Standalone '.$ext' binary/installer artifact found; verify source before trusting."
                )
            }

            // Magic-byte corruption checks for standard media/archives/documents
            val expectedMismatch: String? = when (ext) {
                "jpg", "jpeg" -> if (detected != "JPEG Image") "Expected JPEG (FF D8 FF) header, found $detected" else null
                "png" -> if (detected != "PNG Image") "Expected PNG (89 50 4E 47) header, found $detected" else null
                "gif" -> if (detected != "GIF Image") "Expected GIF8 header, found $detected" else null
                "pdf" -> if (detected != "PDF Document") "Expected %PDF header, found $detected" else null
                "zip" -> if (!detected.startsWith("ZIP")) "Expected PK zip header, found $detected" else null
                "db", "sqlite" -> if (detected != "SQLite DB") "Expected SQLite 3 header, found $detected" else null
                else -> null
            }

            if (expectedMismatch != null) {
                return IntegrityAnalysis(
                    detectedFormat = detected,
                    findingCategory = DeepFindingCategory.CORRUPTED,
                    severity = SeverityLevel.CAUTION,
                    verdictTitle = "Malformed / Corrupted Header",
                    reasonDetail = "$expectedMismatch."
                )
            }

            return IntegrityAnalysis(
                detectedFormat = detected,
                findingCategory = null,
                severity = SeverityLevel.SAFE,
                verdictTitle = "Verified File Integrity",
                reasonDetail = "Header ($detected) matches expected structure with no threat or corruption indicators."
            )
        }

        private fun detectMagicFormat(header: ByteArray): String {
            if (header.isEmpty()) return "Empty"
            fun u(idx: Int) = if (idx < header.size) (header[idx].toInt() and 0xFF) else -1

            return when {
                u(0) == 0x7F && u(1) == 0x45 && u(2) == 0x4C && u(3) == 0x46 -> "ELF Native Binary"
                u(0) == 0x4D && u(1) == 0x5A -> "PE/MZ Windows Binary"
                u(0) == 0x64 && u(1) == 0x65 && u(2) == 0x78 && u(3) == 0x0A -> "DEX Dalvik Bytecode"
                (u(0) == 0xFE && u(1) == 0xED && u(2) == 0xFA) ||
                    (u(0) == 0xCF && u(1) == 0xFA && u(2) == 0xED && u(3) == 0xFE) -> "Mach-O Binary"
                u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47 -> "PNG Image"
                u(0) == 0xFF && u(1) == 0xD8 && u(2) == 0xFF -> "JPEG Image"
                u(0) == 0x47 && u(1) == 0x49 && u(2) == 0x46 && u(3) == 0x38 -> "GIF Image"
                u(0) == 0x52 && u(1) == 0x49 && u(2) == 0x46 && u(3) == 0x46 -> "RIFF / WebP Container"
                u(0) == 0x25 && u(1) == 0x50 && u(2) == 0x44 && u(3) == 0x46 -> "PDF Document"
                u(0) == 0x50 && u(1) == 0x4B && (u(2) == 0x03 || u(2) == 0x05) -> "ZIP/APK Archive"
                header.size >= 15 && header.copyOf(15).toString(Charsets.US_ASCII) == "SQLite format 3" -> "SQLite DB"
                else -> {
                    val sample = header.take(32)
                    val printable = sample.count { b ->
                        val c = b.toInt() and 0xFF
                        c == 9 || c == 10 || c == 13 || c in 32..126
                    }
                    if (sample.isNotEmpty() && printable >= (sample.size * 0.85f)) "UTF-8 / Text" else "Binary Stream"
                }
            }
        }
    }
}
