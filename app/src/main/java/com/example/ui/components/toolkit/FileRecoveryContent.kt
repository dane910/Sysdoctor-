package com.example.ui.components.toolkit

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diagnostics.formatBytes
import com.example.model.QuarantinedFile
import com.example.model.RecoverableFileItem
import com.example.model.RecoveryScanState
import com.example.model.SeverityLevel
import com.example.ui.components.SeverityBadge

fun LazyListScope.fileRecoveryItems(
    recoveryScanState: RecoveryScanState,
    quarantineFiles: List<QuarantinedFile>,
    onPickRecoveryFolder: () -> Unit,
    onResetRecoveryScan: () -> Unit,
    onExportRecoverableFile: (Uri, String) -> Unit,
    onRestoreQuarantinedFile: (QuarantinedFile) -> Unit,
    onPurgeQuarantinedFile: (String) -> Unit
) {
    item(key = "recovery_scanner") {
        RecoveryScannerCard(
            scanState = recoveryScanState,
            onPickFolder = onPickRecoveryFolder,
            onReset = onResetRecoveryScan
        )
    }

    if (recoveryScanState is RecoveryScanState.Results && recoveryScanState.candidates.isNotEmpty()) {
        items(recoveryScanState.candidates, key = { "rec_${it.uriString}" }) { item ->
            RecoverableFileCard(
                item = item,
                onExport = {
                    val cleanName = item.name.removePrefix(".trashed-").substringAfter('-')
                        .ifBlank { item.name }
                    onExportRecoverableFile(Uri.parse(item.uriString), cleanName)
                }
            )
        }
    }

    item(key = "recovery_quarantine_vault") {
        QuarantineVaultCard(
            files = quarantineFiles,
            onRestore = onRestoreQuarantinedFile,
            onPurge = onPurgeQuarantinedFile
        )
    }
}

@Composable
private fun RecoveryScannerCard(
    scanState: RecoveryScanState,
    onPickFolder: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.testTag("toolkit_recovery_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "File Recovery & Signature Scanner",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pick any shared folder (DCIM, Downloads, Documents, or USB OTG) to locate soft-deleted .trashed files, .Trash folders, and verify JPEG/PNG/PDF/MP4 magic-byte headers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (scanState) {
                RecoveryScanState.Idle -> {
                    Button(
                        onClick = onPickFolder,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("pick_recovery_folder_button")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pick Folder to Scan for Recoverable Files")
                    }
                }
                is RecoveryScanState.Scanning -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Scanned ${scanState.filesScanned} files (${scanState.currentFileName})",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onReset) { Text("Cancel") }
                    }
                }
                is RecoveryScanState.Results -> {
                    Text(
                        text = "Folder: ${scanState.folderName} • ${scanState.candidates.size} recoverable candidate(s) found (${scanState.totalScanned} scanned)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onPickFolder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Scan Another Folder")
                    }
                }
                is RecoveryScanState.Error -> {
                    Text(
                        text = scanState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onPickFolder) { Text("Retry Folder Scan") }
                }
            }
        }
    }
}

@Composable
private fun RecoverableFileCard(
    item: RecoverableFileItem,
    onExport: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.detectedSignature} • ${formatBytes(item.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.recoveryReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onExport,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Recover")
            }
        }
    }
}

@Composable
private fun QuarantineVaultCard(
    files: List<QuarantinedFile>,
    onRestore: (QuarantinedFile) -> Unit,
    onPurge: (String) -> Unit
) {
    Card(
        modifier = Modifier.testTag("quarantine_vault_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.RestoreFromTrash, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SysDoctor Quarantine Vault", style = MaterialTheme.typography.titleMedium)
                }
                SeverityBadge(severity = SeverityLevel.INFO, customLabel = "${files.size} staged")
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (files.isEmpty()) {
                Text(
                    text = "No quarantined files yet. When 'Quarantine Backup Before Delete' is active in the Cleaner, removed logs and residual files appear here for safe restoration.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                files.forEach { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.originalName,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${file.category} • ${formatBytes(file.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { onRestore(file) }) {
                            Text("Restore")
                        }
                        TextButton(onClick = { onPurge(file.id) }) {
                            Icon(Icons.Filled.DeleteForever, contentDescription = "Purge", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
