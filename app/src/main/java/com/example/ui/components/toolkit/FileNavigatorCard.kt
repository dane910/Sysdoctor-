package com.example.ui.components.toolkit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diagnostics.formatBytes
import com.example.model.BrowserEntry
import com.example.model.FileBrowserUiState
import com.example.model.FileSortOption
import com.example.model.MountedSafRoot
import com.example.model.StorageRootKind
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileNavigatorCard(
    state: FileBrowserUiState,
    onSelectRoot: (StorageRootKind) -> Unit,
    onSelectMountedSafRoot: (MountedSafRoot) -> Unit,
    onPickSafFolder: () -> Unit,
    onNavigateBreadcrumb: (Int) -> Unit,
    onOpenFolder: (BrowserEntry) -> Unit,
    onPreviewFile: (BrowserEntry) -> Unit,
    onDeleteEntry: (BrowserEntry) -> Unit,
    onChangeSort: (FileSortOption) -> Unit,
    onRefreshDirectory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDeleteEntry by remember { mutableStateOf<BrowserEntry?>(null) }

    pendingDeleteEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDeleteEntry = null },
            title = { Text("Delete ${if (entry.isDirectory) "Folder" else "File"}?") },
            text = {
                Text(
                    if (entry.isDirectory) {
                        "Delete folder '${entry.name}' and its contents permanently?"
                    } else {
                        "Delete '${entry.name}' (${formatBytes(entry.sizeBytes)}) permanently?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDeleteEntry = null
                        onDeleteEntry(entry)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteEntry = null }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("file_navigator_card"),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "File System Navigator",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap folders to browse or tap files to inspect & clean",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onRefreshDirectory,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("file_nav_refresh_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh directory")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scoped Storage + Mounted SAF Roots Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    StorageRootKind.APP_FILES,
                    StorageRootKind.INTERNAL_CACHE,
                    StorageRootKind.EXTERNAL_CACHE,
                    StorageRootKind.QUARANTINE
                ).forEach { kind ->
                    FilterChip(
                        selected = state.rootKind == kind,
                        onClick = { onSelectRoot(kind) },
                        label = { Text(kind.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("browser_root_${kind.name.lowercase()}")
                    )
                }

                state.mountedSafRoots.forEach { mounted ->
                    val isSelected = state.rootKind == StorageRootKind.SAF_FOLDER &&
                        state.activeSafTreeUri == mounted.treeUriString
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectMountedSafRoot(mounted) },
                        label = { Text(mounted.displayName, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.secondary
                        )
                    )
                }

                FilterChip(
                    selected = false,
                    onClick = onPickSafFolder,
                    leadingIcon = {
                        Icon(
                            Icons.Filled.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Open Folder", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("browser_pick_saf_folder_chip")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Breadcrumbs + Sort Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.breadcrumbs.forEachIndexed { idx, crumb ->
                        if (idx > 0) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        val isLast = idx == state.breadcrumbs.lastIndex
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isLast) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateBreadcrumb(idx) }
                                .testTag("breadcrumb_$idx")
                        ) {
                            Text(
                                text = crumb.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                                color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FileSortOption.entries.forEach { opt ->
                        val active = state.sortOption == opt
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onChangeSort(opt) }
                        ) {
                            Text(
                                text = opt.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(8.dp))

            when {
                state.isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Reading folder entries…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                state.errorMessage != null -> {
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }

                state.entries.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp)
                            .testTag("file_nav_empty_state"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "This folder is empty",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.entries.take(60).forEach { entry ->
                            BrowserEntryRow(
                                entry = entry,
                                onClick = {
                                    if (entry.isDirectory) onOpenFolder(entry)
                                    else onPreviewFile(entry)
                                },
                                onDeleteClick = { pendingDeleteEntry = entry }
                            )
                        }
                        if (state.entries.size > 60) {
                            Text(
                                text = "Showing first 60 of ${state.entries.size} entries",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserEntryRow(
    entry: BrowserEntry,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val icon: ImageVector
    val tint: Color
    if (entry.isDirectory) {
        icon = Icons.Filled.Folder
        tint = MaterialTheme.colorScheme.primary
    } else {
        val lower = entry.name.lowercase()
        when {
            entry.mimeType.startsWith("image/") || lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> {
                icon = Icons.Filled.Image
                tint = MaterialTheme.colorScheme.secondary
            }
            lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".sh") || lower.endsWith(".kt") -> {
                icon = Icons.Filled.Code
                tint = MaterialTheme.colorScheme.primary
            }
            lower.endsWith(".zip") || lower.endsWith(".apk") || lower.endsWith(".iso") -> {
                icon = Icons.Filled.Archive
                tint = MaterialTheme.colorScheme.tertiary
            }
            lower.endsWith(".txt") || lower.endsWith(".log") || lower.endsWith(".md") -> {
                icon = Icons.Filled.Description
                tint = MaterialTheme.colorScheme.onSurface
            }
            else -> {
                icon = Icons.AutoMirrored.Filled.InsertDriveFile
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("browser_entry_${entry.name}"),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (entry.isDirectory) "Folder" else "File",
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = if (entry.isDirectory) {
                    "${entry.sizeBytes} item(s)"
                } else {
                    val dateStr = if (entry.lastModifiedMillis > 0L) {
                        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(entry.lastModifiedMillis))
                    } else ""
                    "${formatBytes(entry.sizeBytes)} • $dateStr"
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (entry.isDirectory) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Open folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("browser_delete_${entry.name}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete ${entry.name}",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
