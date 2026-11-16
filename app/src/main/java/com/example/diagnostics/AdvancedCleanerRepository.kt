package com.example.diagnostics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.model.CleanerCandidateItem
import com.example.model.CleanerCategoryScan
import com.example.model.CleanerCategoryType
import com.example.model.CleanerDeepScanResult
import com.example.model.CleanupCategoryReport
import com.example.model.CleanupReport
import com.example.model.QuarantinedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class AdvancedCleanerRepository(private val context: Context) {

    private val quarantineDir: File
        get() = File(context.filesDir, "sysdoctor_quarantine").apply { mkdirs() }

    private val quarantineManifestFile: File
        get() = File(quarantineDir, "quarantine_index.json")

    suspend fun scanForCleanableItems(safTreeUri: Uri? = null): CleanerDeepScanResult =
        withContext(Dispatchers.IO) {
            val cacheItems = mutableListOf<CleanerCandidateItem>()
            val logItems = mutableListOf<CleanerCandidateItem>()
            val residualItems = mutableListOf<CleanerCandidateItem>()

            // 1. App internal, external, and code caches
            val cacheRoots = listOfNotNull(
                context.cacheDir,
                context.externalCacheDir,
                context.codeCacheDir
            )
            cacheRoots.forEach { root ->
                collectFilesRecursively(root) { file ->
                    val nameLower = file.name.lowercase()
                    when {
                        isLogFileName(nameLower) -> {
                            logItems.add(
                                CleanerCandidateItem(
                                    id = file.absolutePath,
                                    name = file.name,
                                    pathOrUri = file.absolutePath,
                                    sizeBytes = file.length(),
                                    category = CleanerCategoryType.OLD_LOG_FILES
                                )
                            )
                        }
                        isResidualFileName(nameLower) -> {
                            residualItems.add(
                                CleanerCandidateItem(
                                    id = file.absolutePath,
                                    name = file.name,
                                    pathOrUri = file.absolutePath,
                                    sizeBytes = file.length(),
                                    category = CleanerCategoryType.RESIDUAL_APP_DATA
                                )
                            )
                        }
                        else -> {
                            cacheItems.add(
                                CleanerCandidateItem(
                                    id = file.absolutePath,
                                    name = file.name,
                                    pathOrUri = file.absolutePath,
                                    sizeBytes = file.length(),
                                    category = CleanerCategoryType.SYSTEM_AND_APP_CACHE
                                )
                            )
                        }
                    }
                }
            }

            // 2. App-private filesDir logs & residual temp artifacts (excluding Room DB & Quarantine)
            collectFilesRecursively(context.filesDir) { file ->
                val abs = file.absolutePath
                if (abs.startsWith(quarantineDir.absolutePath)) return@collectFilesRecursively
                val nameLower = file.name.lowercase()
                when {
                    isLogFileName(nameLower) -> {
                        logItems.add(
                            CleanerCandidateItem(
                                id = abs,
                                name = file.name,
                                pathOrUri = abs,
                                sizeBytes = file.length(),
                                category = CleanerCategoryType.OLD_LOG_FILES
                            )
                        )
                    }
                    isResidualFileName(nameLower) -> {
                        residualItems.add(
                            CleanerCandidateItem(
                                id = abs,
                                name = file.name,
                                pathOrUri = abs,
                                sizeBytes = file.length(),
                                category = CleanerCategoryType.RESIDUAL_APP_DATA
                            )
                        )
                    }
                }
            }

            // 3. Optional user-granted SAF folder (Downloads, Android/data, Documents, USB OTG)
            var folderName: String? = null
            if (safTreeUri != null) {
                val rootDocId = DocumentsContract.getTreeDocumentId(safTreeUri)
                folderName = rootDocId.substringAfterLast(':').ifBlank { "Selected Folder" }
                scanSafFolderForJunk(safTreeUri, rootDocId, logItems, residualItems)
            }

            val categories = listOf(
                CleanerCategoryScan(CleanerCategoryType.SYSTEM_AND_APP_CACHE, cacheItems, isSelected = true),
                CleanerCategoryScan(CleanerCategoryType.OLD_LOG_FILES, logItems, isSelected = true),
                CleanerCategoryScan(CleanerCategoryType.RESIDUAL_APP_DATA, residualItems, isSelected = true)
            )

            CleanerDeepScanResult(
                scannedFolderName = folderName,
                categories = categories,
                scannedAtMillis = System.currentTimeMillis()
            )
        }

    suspend fun cleanSelectedCategories(
        scanResult: CleanerDeepScanResult,
        quarantineBeforeDelete: Boolean
    ): CleanupReport = withContext(Dispatchers.IO) {
        val categoryReports = mutableListOf<CleanupCategoryReport>()
        var totalRemoved = 0
        var totalFreed = 0L
        var totalQuarantined = 0

        for (catScan in scanResult.categories) {
            if (!catScan.isSelected || catScan.items.isEmpty()) continue
            var catRemoved = 0
            var catFreed = 0L
            val cleanedNames = mutableListOf<String>()

            for (item in catScan.items) {
                val deleted = if (item.isSafDocument) {
                    val uri = Uri.parse(item.pathOrUri)
                    if (quarantineBeforeDelete && item.sizeBytes in 1L..MAX_QUARANTINE_BYTES) {
                        if (copyUriToQuarantine(uri, item.name, catScan.category.title, item.sizeBytes)) {
                            totalQuarantined++
                        }
                    }
                    runCatching {
                        DocumentsContract.deleteDocument(context.contentResolver, uri)
                    }.getOrDefault(false)
                } else {
                    val file = File(item.pathOrUri)
                    if (quarantineBeforeDelete && file.exists() && item.sizeBytes in 1L..MAX_QUARANTINE_BYTES) {
                        if (copyFileToQuarantine(file, item.name, catScan.category.title)) {
                            totalQuarantined++
                        }
                    }
                    runCatching { file.exists() && file.delete() }.getOrDefault(false)
                }

                if (deleted) {
                    catRemoved++
                    catFreed += item.sizeBytes
                    if (cleanedNames.size < 8) {
                        cleanedNames.add(item.name)
                    }
                }
            }

            if (catScan.category == CleanerCategoryType.SYSTEM_AND_APP_CACHE) {
                pruneEmptyDirs(context.cacheDir)
                context.externalCacheDir?.let { pruneEmptyDirs(it) }
            }

            totalRemoved += catRemoved
            totalFreed += catFreed
            categoryReports.add(
                CleanupCategoryReport(
                    category = catScan.category,
                    itemsRemoved = catRemoved,
                    bytesFreed = catFreed,
                    cleanedNames = cleanedNames
                )
            )
        }

        val note = if (totalQuarantined > 0) {
            "Quarantined $totalQuarantined file(s) in SysDoctor Recovery Vault for safe restoration. " +
                "Other apps' private /data/data caches remain isolated by Android SELinux and can be cleared via System Storage."
        } else {
            "Cleaned all selected in-app and granted-folder artifacts. " +
                "Other apps' private /data/data caches remain isolated by Android SELinux and can be cleared via System Storage."
        }

        CleanupReport(
            timestampMillis = System.currentTimeMillis(),
            totalItemsRemoved = totalRemoved,
            totalBytesFreed = totalFreed,
            quarantinedCount = totalQuarantined,
            categories = categoryReports,
            systemNote = note
        )
    }

    suspend fun listQuarantinedFiles(): List<QuarantinedFile> = withContext(Dispatchers.IO) {
        readQuarantineManifest().filter { File(it.filePath).exists() }
            .sortedByDescending { it.quarantinedAtMillis }
    }

    suspend fun restoreQuarantinedFile(id: String, destUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val entries = readQuarantineManifest().toMutableList()
        val target = entries.firstOrNull { it.id == id } ?: return@withContext false
        val srcFile = File(target.filePath)
        if (!srcFile.exists()) return@withContext false

        val copied = runCatching {
            context.contentResolver.openOutputStream(destUri)?.use { out ->
                srcFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            } != null
        }.getOrDefault(false)

        if (copied) {
            srcFile.delete()
            entries.removeAll { it.id == id }
            writeQuarantineManifest(entries)
        }
        copied
    }

    suspend fun purgeQuarantinedFile(id: String) = withContext(Dispatchers.IO) {
        val entries = readQuarantineManifest().toMutableList()
        val target = entries.firstOrNull { it.id == id }
        if (target != null) {
            runCatching { File(target.filePath).delete() }
            entries.removeAll { it.id == id }
            writeQuarantineManifest(entries)
        }
    }

    private fun scanSafFolderForJunk(
        treeUri: Uri,
        rootDocId: String,
        logItems: MutableList<CleanerCandidateItem>,
        residualItems: MutableList<CleanerCandidateItem>
    ) {
        val queue = ArrayDeque<String>()
        queue.add(rootDocId)
        var visited = 0

        while (queue.isNotEmpty() && visited < 1500) {
            val parentId = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE
            )
            runCatching {
                context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                    val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                    val sizeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
                    while (cursor.moveToNext()) {
                        visited++
                        val docId = cursor.getString(idCol)
                        val name = cursor.getString(nameCol) ?: continue
                        val mime = cursor.getString(mimeCol) ?: ""
                        val size = cursor.getLong(sizeCol)

                        if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                            queue.add(docId)
                        } else {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            val lower = name.lowercase()
                            when {
                                isLogFileName(lower) -> {
                                    logItems.add(
                                        CleanerCandidateItem(
                                            id = docUri.toString(),
                                            name = name,
                                            pathOrUri = docUri.toString(),
                                            sizeBytes = size,
                                            category = CleanerCategoryType.OLD_LOG_FILES,
                                            isSafDocument = true
                                        )
                                    )
                                }
                                isResidualFileName(lower) -> {
                                    residualItems.add(
                                        CleanerCandidateItem(
                                            id = docUri.toString(),
                                            name = name,
                                            pathOrUri = docUri.toString(),
                                            sizeBytes = size,
                                            category = CleanerCategoryType.RESIDUAL_APP_DATA,
                                            isSafDocument = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun copyFileToQuarantine(file: File, originalName: String, category: String): Boolean {
        return runCatching {
            val id = UUID.randomUUID().toString()
            val qFile = File(quarantineDir, "${id}_${originalName.take(40)}")
            file.inputStream().use { input ->
                qFile.outputStream().use { output -> input.copyTo(output) }
            }
            appendQuarantineEntry(
                QuarantinedFile(
                    id = id,
                    originalName = originalName,
                    category = category,
                    sizeBytes = qFile.length(),
                    quarantinedAtMillis = System.currentTimeMillis(),
                    filePath = qFile.absolutePath
                )
            )
            true
        }.getOrDefault(false)
    }

    private fun copyUriToQuarantine(
        uri: Uri,
        originalName: String,
        category: String,
        sizeBytes: Long
    ): Boolean {
        return runCatching {
            val id = UUID.randomUUID().toString()
            val qFile = File(quarantineDir, "${id}_${originalName.take(40)}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                qFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
            appendQuarantineEntry(
                QuarantinedFile(
                    id = id,
                    originalName = originalName,
                    category = category,
                    sizeBytes = sizeBytes.coerceAtLeast(qFile.length()),
                    quarantinedAtMillis = System.currentTimeMillis(),
                    filePath = qFile.absolutePath
                )
            )
            true
        }.getOrDefault(false)
    }

    private fun appendQuarantineEntry(entry: QuarantinedFile) {
        val current = readQuarantineManifest().toMutableList()
        current.add(entry)
        writeQuarantineManifest(current)
    }

    private fun readQuarantineManifest(): List<QuarantinedFile> {
        val file = quarantineManifestFile
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                QuarantinedFile(
                    id = obj.getString("id"),
                    originalName = obj.getString("originalName"),
                    category = obj.getString("category"),
                    sizeBytes = obj.getLong("sizeBytes"),
                    quarantinedAtMillis = obj.getLong("quarantinedAtMillis"),
                    filePath = obj.getString("filePath")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeQuarantineManifest(items: List<QuarantinedFile>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("originalName", item.originalName)
                    put("category", item.category)
                    put("sizeBytes", item.sizeBytes)
                    put("quarantinedAtMillis", item.quarantinedAtMillis)
                    put("filePath", item.filePath)
                }
            )
        }
        quarantineManifestFile.writeText(arr.toString())
    }

    private fun collectFilesRecursively(dir: File?, onFile: (File) -> Unit) {
        if (dir == null || !dir.exists()) return
        val stack = ArrayDeque<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val current = stack.removeFirst()
            current.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    stack.add(child)
                } else {
                    onFile(child)
                }
            }
        }
    }

    private fun pruneEmptyDirs(dir: File?) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                pruneEmptyDirs(child)
                if (child.listFiles()?.isEmpty() == true) {
                    child.delete()
                }
            }
        }
    }

    private fun isLogFileName(lowerName: String): Boolean {
        return lowerName.endsWith(".log") ||
            lowerName.endsWith(".anr") ||
            lowerName.endsWith(".crash") ||
            lowerName.endsWith(".trace") ||
            lowerName.endsWith(".dmp") ||
            (lowerName.contains("log") && lowerName.endsWith(".txt"))
    }

    private fun isResidualFileName(lowerName: String): Boolean {
        return lowerName.endsWith(".tmp") ||
            lowerName.endsWith(".temp") ||
            lowerName.endsWith(".bak") ||
            lowerName.endsWith(".part") ||
            lowerName.endsWith(".crdownload") ||
            lowerName == "thumbs.db" ||
            lowerName == ".ds_store"
    }

    companion object {
        private const val MAX_QUARANTINE_BYTES = 10L * 1024L * 1024L
    }
}
