package com.example.ui.components.toolkit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diagnostics.formatBytes
import com.example.model.DeepFileScanState
import com.example.model.DeepFindingCategory
import com.example.model.DeepScanFinding
import com.example.model.SeverityLevel
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun UnifiedDeepScanCard(
    scanState: DeepFileScanState,
    quarantineBeforeDelete: Boolean,
    onToggleQuarantine: () -> Unit,
    onStartAppDeepScan: () -> Unit,
    onPickSafFolderForDeepScan: () -> Unit,
    onSelectFilter: (DeepFindingCategory) -> Unit,
    onToggleFindingSelection: (String) -> Unit,
    onSelectAllFiltered: (Boolean) -> Unit,
    onInspectFinding: (DeepScanFinding) -> Unit,
    onDeleteSelected: () -> Unit,
    onResetScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDeleteDialog by remember { mutableStateOf(false) }

    if (confirmDeleteDialog && scanState is DeepFileScanState.Results) {
        AlertDialog(
            onDismissRequest = { confirmDeleteDialog = false },
            title = { Text("Delete Reviewed Files?") },
            text = {
                Text(
                    "Remove ${scanState.selectedDeleteCount} selected file(s) (${formatBytes(scanState.selectedDeleteBytes)})? " +
                        if (quarantineBeforeDelete) {
                            "Eligible items will be archived into Quarantine Vault first."
                        } else {
                            "Permanent deletion is immediate."
                        }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeleteDialog = false
                        onDeleteSelected()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_deep_delete_button")
                ) { Text("Delete Selected") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("unified_deep_scan_card"),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Deep File Threat & Integrity Scan",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Malware/disguised payloads, corrupted headers & SHA-256 duplicates",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (scanState is DeepFileScanState.Results) {
                    SeverityBadge(
                        severity = when {
                            scanState.threatCount > 0 -> SeverityLevel.ALERT
                            scanState.corruptedCount > 0 || scanState.duplicateCount > 0 -> SeverityLevel.CAUTION
                            else -> SeverityLevel.SAFE
                        },
                        customLabel = "${scanState.findings.size} Found"
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (scanState) {
                DeepFileScanState.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStartAppDeepScan,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("deep_scan_app_button")
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Deep Scan App")
                        }
                        OutlinedButton(
                            onClick = onPickSafFolderForDeepScan,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("deep_scan_folder_button")
                        ) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan Folder")
                        }
                    }
                }

                is DeepFileScanState.Scanning -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = scanState.phase,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${scanState.filesScanned} file(s) • ${scanState.currentFileName}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TextButton(onClick = onResetScan) { Text("Cancel") }
                        }
                    }
                }

                is DeepFileScanState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = scanState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onStartAppDeepScan) { Text("Retry App Scan") }
                            OutlinedButton(onClick = onPickSafFolderForDeepScan) { Text("Pick Folder") }
                        }
                    }
                }

                is DeepFileScanState.Results -> {
                    Text(
                        text = "${scanState.scopeLabel} • ${scanState.totalFilesScanned} file(s) audited",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Unified Filter Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DeepFindingCategory.entries.forEach { category ->
                            val count = when (category) {
                                DeepFindingCategory.ALL -> scanState.findings.size
                                DeepFindingCategory.THREAT_SUSPICIOUS -> scanState.threatCount
                                DeepFindingCategory.CORRUPTED -> scanState.corruptedCount
                                DeepFindingCategory.DUPLICATE -> scanState.duplicateCount
                            }
                            FilterChip(
                                selected = scanState.selectedFilter == category,
                                onClick = { onSelectFilter(category) },
                                label = {
                                    Text("${category.label} ($count)", style = MaterialTheme.typography.labelSmall)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.testTag("deep_filter_${category.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val visible = scanState.filteredFindings
                    if (visible.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (scanState.findings.isEmpty()) {
                                        "No malware signatures, corrupted files, or duplicates found."
                                    } else {
                                        "No findings match the '${scanState.selectedFilter.label}' filter."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tap item to preview & inspect before removal",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                TextButton(onClick = { onSelectAllFiltered(true) }) { Text("Select All") }
                                TextButton(onClick = { onSelectAllFiltered(false) }) { Text("Clear") }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            visible.take(50).forEach { finding ->
                                DeepFindingRow(
                                    finding = finding,
                                    onToggleSelection = { onToggleFindingSelection(finding.id) },
                                    onInspect = { onInspectFinding(finding) }
                                )
                            }
                            if (visible.size > 50) {
                                Text(
                                    text = "Showing first 50 of ${visible.size} reviewed findings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Quarantine Backup Before Delete",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Preserve copy in Recovery Vault when deleting",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = quarantineBeforeDelete,
                                onCheckedChange = { onToggleQuarantine() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onStartAppDeepScan,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) { Text("Rescan App") }
                        OutlinedButton(
                            onClick = onPickSafFolderForDeepScan,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                        ) { Text("Pick Folder") }
                    }

                    if (scanState.selectedDeleteCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { confirmDeleteDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("deep_delete_selected_button")
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Delete ${scanState.selectedDeleteCount} Reviewed (${formatBytes(scanState.selectedDeleteBytes)})"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeepFindingRow(
    finding: DeepScanFinding,
    onToggleSelection: () -> Unit,
    onInspect: () -> Unit
) {
    val catIcon = when (finding.category) {
        DeepFindingCategory.THREAT_SUSPICIOUS -> Icons.Filled.GppBad
        DeepFindingCategory.CORRUPTED -> Icons.Filled.BrokenImage
        DeepFindingCategory.DUPLICATE, DeepFindingCategory.ALL -> Icons.Filled.ContentCopy
    }
    val tint = when (finding.severity) {
        SeverityLevel.ALERT -> MaterialTheme.colorScheme.error
        SeverityLevel.CAUTION -> MaterialTheme.colorScheme.tertiary
        SeverityLevel.SAFE -> MaterialTheme.colorScheme.secondary
        SeverityLevel.INFO -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onInspect)
            .testTag("deep_finding_row_${finding.name}"),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = finding.isSelectedForDelete,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.testTag("deep_finding_check_${finding.name}")
            )
            Icon(
                imageVector = catIcon,
                contentDescription = finding.category.label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = finding.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (finding.isSuggestedKeep) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = "Keep",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${finding.verdictTitle} • ${formatBytes(finding.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = finding.reasonDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onInspect,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Visibility,
                    contentDescription = "Inspect ${finding.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
