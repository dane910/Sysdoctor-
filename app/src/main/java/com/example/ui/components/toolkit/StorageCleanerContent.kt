package com.example.ui.components.toolkit

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diagnostics.formatBytes
import com.example.model.CleanerCategoryType
import com.example.model.CleanerDeepScanResult
import com.example.model.CleanupReport
import com.example.model.DuplicateGroup
import com.example.model.DuplicateScanState
import com.example.model.SeverityLevel
import com.example.model.StorageSummary
import com.example.ui.components.AdvancedCleanerCard
import com.example.ui.components.CleanupReportCard
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.JetBrainsMonoFontFamily

fun LazyListScope.storageCleanerItems(
    storage: StorageSummary,
    duplicateState: DuplicateScanState,
    cleanupFeedback: String?,
    cleanerScanResult: CleanerDeepScanResult?,
    isScanningCleaner: Boolean,
    cleanupReport: CleanupReport?,
    quarantineBeforeDelete: Boolean,
    onToggleCleanerCategory: (CleanerCategoryType) -> Unit,
    onToggleQuarantineBeforeDelete: () -> Unit,
    onRescanAppCleaner: () -> Unit,
    onPickSafFolderForCleaner: () -> Unit,
    onExecuteAdvancedClean: () -> Unit,
    onDismissCleanupReport: () -> Unit,
    onDismissFeedback: () -> Unit,
    onClearAppCache: () -> Unit,
    onOpenSystemStorage: () -> Unit,
    onPickDuplicateFolder: () -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onRequestDeleteDuplicates: () -> Unit,
    onResetDuplicateScan: () -> Unit,
    onSpeakCleanupSummary: () -> Unit
) {
    item(key = "cleaner_feedback") {
        AnimatedVisibility(visible = cleanupFeedback != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = cleanupFeedback.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissFeedback) {
                        Text("OK")
                    }
                }
            }
        }
    }

    item(key = "cleaner_report") {
        CleanupReportCard(
            report = cleanupReport,
            onDismiss = onDismissCleanupReport
        )
    }

    item(key = "cleaner_advanced") {
        AdvancedCleanerCard(
            scanResult = cleanerScanResult,
            isScanning = isScanningCleaner,
            quarantineBeforeDelete = quarantineBeforeDelete,
            onToggleCategory = onToggleCleanerCategory,
            onToggleQuarantine = onToggleQuarantineBeforeDelete,
            onRescanApp = onRescanAppCleaner,
            onPickSafFolderForCleaner = onPickSafFolderForCleaner,
            onExecuteClean = onExecuteAdvancedClean
        )
    }

    item(key = "cleaner_app_cache") {
        Card(
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
                    Text(
                        text = "App Cache & Device Storage",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                SeverityBadge(
                    severity = if (storage.usedFraction > 0.85f) SeverityLevel.CAUTION else SeverityLevel.SAFE,
                    customLabel = "${formatBytes(storage.availableBytes)} Free"
                )
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { storage.usedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CacheStatColumn("Internal Cache", formatBytes(storage.appCacheBytes))
                    CacheStatColumn("External Cache", formatBytes(storage.externalCacheBytes))
                    CacheStatColumn("App Data", formatBytes(storage.appFilesBytes))
                }
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onClearAppCache,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("clear_cache_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Cache")
                    }
                    OutlinedButton(
                        onClick = onOpenSystemStorage,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("system_storage_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OS Storage")
                    }
                }
            }
        }
    }

    item(key = "cleaner_duplicates") {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "SHA-256 Duplicate File Scanner",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Select any folder (Downloads, Documents, or USB OTG) to match byte-for-byte SHA-256 hashes via Storage Access Framework.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                when (duplicateState) {
                    DuplicateScanState.Idle -> {
                        Button(
                            onClick = onPickDuplicateFolder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("pick_folder_button")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pick Folder to Scan for Duplicates")
                        }
                    }

                    is DuplicateScanState.Scanning -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Scanning: ${duplicateState.currentFileName}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${duplicateState.filesEnumerated} indexed • ${duplicateState.filesHashed} hashed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = onResetDuplicateScan,
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }

                    is DuplicateScanState.Error -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = duplicateState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Button(
                                onClick = onPickDuplicateFolder,
                                modifier = Modifier.testTag("retry_pick_folder_button")
                            ) {
                                Text("Pick Another Folder")
                            }
                        }
                    }

                    is DuplicateScanState.Results -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Folder: ${duplicateState.folderName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${duplicateState.totalFilesScanned} scanned • ${duplicateState.groups.size} group(s) (${formatBytes(duplicateState.totalWastedBytes)} reclaimable)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onPickDuplicateFolder,
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("rescan_folder_button")
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan Another")
                                }
                                if (duplicateState.selectedDeleteCount > 0) {
                                    Button(
                                        onClick = onRequestDeleteDuplicates,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                        modifier = Modifier
                                            .weight(1f)
                                            .minimumInteractiveComponentSize()
                                            .testTag("delete_duplicates_button")
                                    ) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Delete (${duplicateState.selectedDeleteCount})")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (duplicateState is DuplicateScanState.Results) {
        if (duplicateState.groups.isEmpty()) {
            item(key = "cleaner_no_dupes") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "No Exact Duplicates Found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "All ${duplicateState.totalFilesScanned} files in ${duplicateState.folderName} have unique SHA-256 checksums.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(duplicateState.groups, key = { "dupe_${it.hash}" }) { group ->
                DuplicateGroupCard(
                    group = group,
                    onToggleFile = { uri -> onToggleDuplicate(group.hash, uri) }
                )
            }
        }
    }
}

@Composable
private fun CacheStatColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    onToggleFile: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SHA-256: ${group.hash.take(12)}…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${group.files.size} copies • ${formatBytes(group.fileSizeBytes)} each",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            group.files.forEach { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = file.isSelectedForDelete,
                        onCheckedChange = { onToggleFile(file.uriString) },
                        modifier = Modifier.testTag("duplicate_checkbox_${file.name}")
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = file.mimeType,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (file.isSuggestedKeep) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Keep",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
