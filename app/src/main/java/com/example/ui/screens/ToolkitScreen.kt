package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diagnostics.formatBytes
import com.example.model.BatteryHardwareSnapshot
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
import com.example.model.SeverityLevel
import com.example.model.StorageSummary
import com.example.model.StorageVolumeDetail
import com.example.model.SystemIntegrityInfo
import com.example.model.ToolkitCategory
import com.example.model.WifiSecurityAudit
import com.example.ui.components.DisplayDiagnosticDialog
import com.example.ui.components.SeverityBadge
import com.example.ui.components.toolkit.fileRecoveryItems
import com.example.ui.components.toolkit.hardwareDiagnosticsItems
import com.example.ui.components.toolkit.networkAndSystemAuditItems
import com.example.ui.components.toolkit.storageCleanerItems
import com.example.ui.components.toolkit.usbToolsItems

@Composable
fun ToolkitScreen(
    selectedCategory: ToolkitCategory?,
    onSelectCategory: (ToolkitCategory?) -> Unit,
    storage: StorageSummary,
    wifi: WifiSecurityAudit,
    systemInfo: SystemIntegrityInfo,
    batteryHardware: BatteryHardwareSnapshot,
    displayMetrics: DisplayMetricsSnapshot,
    sensors: List<SensorDescriptor>,
    activeSensorType: Int?,
    activeSensorReading: LiveSensorReading?,
    storageVolumes: List<StorageVolumeDetail>,
    recoveryScanState: RecoveryScanState,
    quarantineFiles: List<QuarantinedFile>,
    duplicateState: DuplicateScanState,
    cleanupFeedback: String?,
    cleanerScanResult: CleanerDeepScanResult?,
    isScanningCleaner: Boolean,
    cleanupReport: CleanupReport?,
    quarantineBeforeDelete: Boolean,
    toolkitFeedback: String?,
    onRunBatteryTest: () -> Unit,
    onUpdateDisplayTestStatus: (HardwareTestStatus) -> Unit,
    onStartSensorTest: (Int) -> Unit,
    onStopSensorTest: () -> Unit,
    onStartRecoveryScan: (Uri) -> Unit,
    onRecoverFileToDestination: (Uri, Uri) -> Unit,
    onResetRecoveryScan: () -> Unit,
    onRestoreQuarantinedFile: (String, Uri) -> Unit,
    onPurgeQuarantinedFile: (String) -> Unit,
    onToggleCleanerCategory: (CleanerCategoryType) -> Unit,
    onToggleQuarantineBeforeDelete: () -> Unit,
    onRescanCleaner: (Uri?) -> Unit,
    onExecuteAdvancedClean: () -> Unit,
    onDismissCleanupReport: () -> Unit,
    onDismissCleanupFeedback: () -> Unit,
    onClearAppCache: () -> Unit,
    onFolderPickedForDuplicates: (Uri) -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onDeleteSelectedDuplicates: () -> Unit,
    onResetDuplicateScan: () -> Unit,
    onSpeakCleanupSummary: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onRefreshToolkit: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    bootableUsbCapability: BootableUsbCapability = BootableUsbCapability(),
    bootImageInspectionState: BootImageInspectionState = BootImageInspectionState.Idle,
    onInspectBootImage: (Uri) -> Unit = {},
    onResetBootImageInspection: () -> Unit = {}
) {
    val context = LocalContext.current
    var showDisplayDialog by remember { mutableStateOf(false) }
    var showConfirmDeleteDialog by remember { mutableStateOf(false) }
    var locationDeniedNote by remember { mutableStateOf(false) }
    var pendingRecoverySourceUri by remember { mutableStateOf<Uri?>(null) }
    var pendingQuarantineRestoreId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { onStopSensorTest() }
    }

    val bootImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onInspectBootImage(uri) }

    val recoveryFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) onStartRecoveryScan(uri) }

    val browseVolumeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { _ -> }

    val duplicateFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) onFolderPickedForDuplicates(uri) }

    val cleanerSafPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) onRescanCleaner(uri) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationDeniedNote = !granted
        onRefreshNetwork()
    }

    val exportRecoveredFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val src = pendingRecoverySourceUri
        pendingRecoverySourceUri = null
        if (destUri != null && src != null) {
            onRecoverFileToDestination(src, destUri)
        }
    }

    val restoreQuarantineLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri ->
        val id = pendingQuarantineRestoreId
        pendingQuarantineRestoreId = null
        if (destUri != null && id != null) {
            onRestoreQuarantinedFile(id, destUri)
        }
    }

    if (showDisplayDialog) {
        DisplayDiagnosticDialog(
            onDismiss = { showDisplayDialog = false },
            onResult = { status ->
                onUpdateDisplayTestStatus(status)
                showDisplayDialog = false
            }
        )
    }

    if (showConfirmDeleteDialog && duplicateState is DuplicateScanState.Results) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteDialog = false },
            title = { Text("Delete Duplicate Files?") },
            text = {
                Text(
                    "Permanently delete ${duplicateState.selectedDeleteCount} selected duplicate file(s) " +
                        "to reclaim ${formatBytes(duplicateState.selectedDeleteBytes)}? Original copies marked 'Keep' remain untouched."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteDialog = false
                        onDeleteSelectedDuplicates()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_duplicates_button")
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("toolkit_screen")
    ) {
        val isTwoPane = maxWidth >= 600.dp

        if (!isTwoPane && selectedCategory != null) {
            BackHandler { onSelectCategory(null) }
        }

        if (isTwoPane) {
            val activeCategory = selectedCategory ?: ToolkitCategory.DIAGNOSTICS
            Row(modifier = Modifier.fillMaxSize()) {
                CategoryListPane(
                    selectedCategory = activeCategory,
                    storage = storage,
                    wifi = wifi,
                    systemInfo = systemInfo,
                    batteryHardware = batteryHardware,
                    bootableUsbCapability = bootableUsbCapability,
                    quarantineCount = quarantineFiles.size,
                    onSelectCategory = onSelectCategory,
                    onRefreshToolkit = onRefreshToolkit,
                    onRunBatteryTest = onRunBatteryTest,
                    onLaunchDisplayTest = { showDisplayDialog = true },
                    onRefreshNetwork = onRefreshNetwork,
                    onOpenStorageSettings = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
                    },
                    onOpenWirelessSettings = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                    },
                    onPickDuplicateFolder = { duplicateFolderLauncher.launch(null) },
                    onPickRecoveryFolder = { recoveryFolderLauncher.launch(null) },
                    modifier = Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                CategoryDetailPane(
                    category = activeCategory,
                    showBackButton = false,
                    onBack = {},
                    toolkitFeedback = toolkitFeedback,
                    onDismissFeedback = onDismissFeedback,
                    onRefreshToolkit = onRefreshToolkit,
                    storage = storage,
                    wifi = wifi,
                    systemInfo = systemInfo,
                    batteryHardware = batteryHardware,
                    displayMetrics = displayMetrics,
                    sensors = sensors,
                    activeSensorType = activeSensorType,
                    activeSensorReading = activeSensorReading,
                    storageVolumes = storageVolumes,
                    recoveryScanState = recoveryScanState,
                    quarantineFiles = quarantineFiles,
                    duplicateState = duplicateState,
                    cleanupFeedback = cleanupFeedback,
                    cleanerScanResult = cleanerScanResult,
                    isScanningCleaner = isScanningCleaner,
                    cleanupReport = cleanupReport,
                    quarantineBeforeDelete = quarantineBeforeDelete,
                    bootableUsbCapability = bootableUsbCapability,
                    bootImageInspectionState = bootImageInspectionState,
                    locationDeniedNote = locationDeniedNote,
                    onRunBatteryTest = onRunBatteryTest,
                    onLaunchDisplayTest = { showDisplayDialog = true },
                    onStartSensorTest = onStartSensorTest,
                    onStopSensorTest = onStopSensorTest,
                    onPickBootImage = { bootImagePickerLauncher.launch(arrayOf("*/*")) },
                    onResetBootImageInspection = onResetBootImageInspection,
                    onToggleCleanerCategory = onToggleCleanerCategory,
                    onToggleQuarantineBeforeDelete = onToggleQuarantineBeforeDelete,
                    onRescanAppCleaner = { onRescanCleaner(null) },
                    onPickSafFolderForCleaner = { cleanerSafPickerLauncher.launch(null) },
                    onExecuteAdvancedClean = onExecuteAdvancedClean,
                    onDismissCleanupReport = onDismissCleanupReport,
                    onDismissCleanupFeedback = onDismissCleanupFeedback,
                    onClearAppCache = onClearAppCache,
                    onOpenSystemStorage = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
                    },
                    onPickDuplicateFolder = { duplicateFolderLauncher.launch(null) },
                    onToggleDuplicate = onToggleDuplicate,
                    onRequestDeleteDuplicates = { showConfirmDeleteDialog = true },
                    onResetDuplicateScan = onResetDuplicateScan,
                    onSpeakCleanupSummary = onSpeakCleanupSummary,
                    onRefreshNetwork = onRefreshNetwork,
                    onOpenWirelessSettings = {
                        runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                    },
                    onRequestLocationForSsid = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    onBrowseVolume = { browseVolumeLauncher.launch(null) },
                    onPickRecoveryFolder = { recoveryFolderLauncher.launch(null) },
                    onResetRecoveryScan = onResetRecoveryScan,
                    onExportRecoverableFile = { srcUri, suggestedName ->
                        pendingRecoverySourceUri = srcUri
                        exportRecoveredFileLauncher.launch(suggestedName)
                    },
                    onRestoreQuarantinedFile = { file ->
                        pendingQuarantineRestoreId = file.id
                        restoreQuarantineLauncher.launch(file.originalName)
                    },
                    onPurgeQuarantinedFile = onPurgeQuarantinedFile,
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                )
            }
        } else if (selectedCategory == null) {
            CategoryListPane(
                selectedCategory = null,
                storage = storage,
                wifi = wifi,
                systemInfo = systemInfo,
                batteryHardware = batteryHardware,
                bootableUsbCapability = bootableUsbCapability,
                quarantineCount = quarantineFiles.size,
                onSelectCategory = onSelectCategory,
                onRefreshToolkit = onRefreshToolkit,
                onRunBatteryTest = onRunBatteryTest,
                onLaunchDisplayTest = { showDisplayDialog = true },
                onRefreshNetwork = onRefreshNetwork,
                onOpenStorageSettings = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
                },
                onOpenWirelessSettings = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                },
                onPickDuplicateFolder = { duplicateFolderLauncher.launch(null) },
                onPickRecoveryFolder = { recoveryFolderLauncher.launch(null) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CategoryDetailPane(
                category = selectedCategory,
                showBackButton = true,
                onBack = { onSelectCategory(null) },
                toolkitFeedback = toolkitFeedback,
                onDismissFeedback = onDismissFeedback,
                onRefreshToolkit = onRefreshToolkit,
                storage = storage,
                wifi = wifi,
                systemInfo = systemInfo,
                batteryHardware = batteryHardware,
                displayMetrics = displayMetrics,
                sensors = sensors,
                activeSensorType = activeSensorType,
                activeSensorReading = activeSensorReading,
                storageVolumes = storageVolumes,
                recoveryScanState = recoveryScanState,
                quarantineFiles = quarantineFiles,
                duplicateState = duplicateState,
                cleanupFeedback = cleanupFeedback,
                cleanerScanResult = cleanerScanResult,
                isScanningCleaner = isScanningCleaner,
                cleanupReport = cleanupReport,
                quarantineBeforeDelete = quarantineBeforeDelete,
                bootableUsbCapability = bootableUsbCapability,
                bootImageInspectionState = bootImageInspectionState,
                locationDeniedNote = locationDeniedNote,
                onRunBatteryTest = onRunBatteryTest,
                onLaunchDisplayTest = { showDisplayDialog = true },
                onStartSensorTest = onStartSensorTest,
                onStopSensorTest = onStopSensorTest,
                onPickBootImage = { bootImagePickerLauncher.launch(arrayOf("*/*")) },
                onResetBootImageInspection = onResetBootImageInspection,
                onToggleCleanerCategory = onToggleCleanerCategory,
                onToggleQuarantineBeforeDelete = onToggleQuarantineBeforeDelete,
                onRescanAppCleaner = { onRescanCleaner(null) },
                onPickSafFolderForCleaner = { cleanerSafPickerLauncher.launch(null) },
                onExecuteAdvancedClean = onExecuteAdvancedClean,
                onDismissCleanupReport = onDismissCleanupReport,
                onDismissCleanupFeedback = onDismissCleanupFeedback,
                onClearAppCache = onClearAppCache,
                onOpenSystemStorage = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }
                },
                onPickDuplicateFolder = { duplicateFolderLauncher.launch(null) },
                onToggleDuplicate = onToggleDuplicate,
                onRequestDeleteDuplicates = { showConfirmDeleteDialog = true },
                onResetDuplicateScan = onResetDuplicateScan,
                onSpeakCleanupSummary = onSpeakCleanupSummary,
                onRefreshNetwork = onRefreshNetwork,
                onOpenWirelessSettings = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                },
                onRequestLocationForSsid = {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onBrowseVolume = { browseVolumeLauncher.launch(null) },
                onPickRecoveryFolder = { recoveryFolderLauncher.launch(null) },
                onResetRecoveryScan = onResetRecoveryScan,
                onExportRecoverableFile = { srcUri, suggestedName ->
                    pendingRecoverySourceUri = srcUri
                    exportRecoveredFileLauncher.launch(suggestedName)
                },
                onRestoreQuarantinedFile = { file ->
                    pendingQuarantineRestoreId = file.id
                    restoreQuarantineLauncher.launch(file.originalName)
                },
                onPurgeQuarantinedFile = onPurgeQuarantinedFile,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CategoryListPane(
    selectedCategory: ToolkitCategory?,
    storage: StorageSummary,
    wifi: WifiSecurityAudit,
    systemInfo: SystemIntegrityInfo,
    batteryHardware: BatteryHardwareSnapshot,
    bootableUsbCapability: BootableUsbCapability,
    quarantineCount: Int,
    onSelectCategory: (ToolkitCategory) -> Unit,
    onRefreshToolkit: () -> Unit,
    onRunBatteryTest: () -> Unit,
    onLaunchDisplayTest: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onOpenStorageSettings: () -> Unit,
    onOpenWirelessSettings: () -> Unit,
    onPickDuplicateFolder: () -> Unit,
    onPickRecoveryFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedCategoryId by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.testTag("toolkit_category_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "toolkit_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SYSDOCTOR TOOLKIT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Expandable Categories",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                OutlinedButton(
                    onClick = onRefreshToolkit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toolkit_refresh_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh toolkit", modifier = Modifier.size(18.dp))
                }
            }
        }

        items(ToolkitCategory.entries, key = { it.name }) { category ->
            val (icon, accent, statusText, statusSeverity) = categoryMeta(
                category = category,
                storage = storage,
                wifi = wifi,
                systemInfo = systemInfo,
                batteryHardware = batteryHardware,
                bootableUsbCapability = bootableUsbCapability,
                quarantineCount = quarantineCount
            )
            val isExpanded = expandedCategoryId == category.name
            val isSelected = selectedCategory == category

            ExpandableToolkitCategoryCard(
                category = category,
                icon = icon,
                accent = accent,
                statusText = statusText,
                statusSeverity = statusSeverity,
                isExpanded = isExpanded,
                isSelected = isSelected,
                onToggleExpand = {
                    expandedCategoryId = if (isExpanded) null else category.name
                },
                onOpenFullDetail = { onSelectCategory(category) },
                onActionScan = {
                    when (category) {
                        ToolkitCategory.DIAGNOSTICS -> onRunBatteryTest()
                        ToolkitCategory.USB_TOOLS -> onRefreshToolkit()
                        ToolkitCategory.CLEANER -> onPickDuplicateFolder()
                        ToolkitCategory.NETWORK_AUDIT -> onRefreshNetwork()
                        ToolkitCategory.RECOVERY -> onPickRecoveryFolder()
                    }
                },
                onActionRepairOrOpen = {
                    when (category) {
                        ToolkitCategory.DIAGNOSTICS -> onLaunchDisplayTest()
                        ToolkitCategory.USB_TOOLS -> onRefreshToolkit()
                        ToolkitCategory.CLEANER -> onOpenStorageSettings()
                        ToolkitCategory.NETWORK_AUDIT -> onOpenWirelessSettings()
                        ToolkitCategory.RECOVERY -> onPickRecoveryFolder()
                    }
                }
            )
        }
    }
}

@Composable
private fun ExpandableToolkitCategoryCard(
    category: ToolkitCategory,
    icon: ImageVector,
    accent: Color,
    statusText: String,
    statusSeverity: SeverityLevel,
    isExpanded: Boolean,
    isSelected: Boolean,
    onToggleExpand: () -> Unit,
    onOpenFullDetail: () -> Unit,
    onActionScan: () -> Unit,
    onActionRepairOrOpen: () -> Unit
) {
    val (scanLabel, repairLabel) = when (category) {
        ToolkitCategory.DIAGNOSTICS -> "Run Test" to "Display Test"
        ToolkitCategory.USB_TOOLS -> "Scan USB" to "Probe OTG"
        ToolkitCategory.CLEANER -> "Scan Dupes" to "OS Storage"
        ToolkitCategory.NETWORK_AUDIT -> "Re-Audit" to "Net Settings"
        ToolkitCategory.RECOVERY -> "Scan Folder" to "Vault"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .testTag("toolkit_category_${category.name.lowercase()}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected || isExpanded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(
            width = if (isSelected || isExpanded) 1.5.dp else 1.dp,
            color = if (isSelected || isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = category.title,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        SeverityBadge(severity = statusSeverity, customLabel = statusText)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = category.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse actions" else "Expand actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = spring()) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring()) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onActionScan,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("expand_action_scan_${category.name.lowercase()}")
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(scanLabel)
                        }
                        OutlinedButton(
                            onClick = onActionRepairOrOpen,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("expand_action_repair_${category.name.lowercase()}")
                        ) {
                            Text(repairLabel)
                        }
                    }
                    TextButton(
                        onClick = onOpenFullDetail,
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("expand_action_open_${category.name.lowercase()}")
                    ) {
                        Text("Open Full Module →")
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryDetailPane(
    category: ToolkitCategory,
    showBackButton: Boolean,
    onBack: () -> Unit,
    toolkitFeedback: String?,
    onDismissFeedback: () -> Unit,
    onRefreshToolkit: () -> Unit,
    storage: StorageSummary,
    wifi: WifiSecurityAudit,
    systemInfo: SystemIntegrityInfo,
    batteryHardware: BatteryHardwareSnapshot,
    displayMetrics: DisplayMetricsSnapshot,
    sensors: List<SensorDescriptor>,
    activeSensorType: Int?,
    activeSensorReading: LiveSensorReading?,
    storageVolumes: List<StorageVolumeDetail>,
    recoveryScanState: RecoveryScanState,
    quarantineFiles: List<QuarantinedFile>,
    duplicateState: DuplicateScanState,
    cleanupFeedback: String?,
    cleanerScanResult: CleanerDeepScanResult?,
    isScanningCleaner: Boolean,
    cleanupReport: CleanupReport?,
    quarantineBeforeDelete: Boolean,
    bootableUsbCapability: BootableUsbCapability,
    bootImageInspectionState: BootImageInspectionState,
    locationDeniedNote: Boolean,
    onRunBatteryTest: () -> Unit,
    onLaunchDisplayTest: () -> Unit,
    onStartSensorTest: (Int) -> Unit,
    onStopSensorTest: () -> Unit,
    onPickBootImage: () -> Unit,
    onResetBootImageInspection: () -> Unit,
    onToggleCleanerCategory: (CleanerCategoryType) -> Unit,
    onToggleQuarantineBeforeDelete: () -> Unit,
    onRescanAppCleaner: () -> Unit,
    onPickSafFolderForCleaner: () -> Unit,
    onExecuteAdvancedClean: () -> Unit,
    onDismissCleanupReport: () -> Unit,
    onDismissCleanupFeedback: () -> Unit,
    onClearAppCache: () -> Unit,
    onOpenSystemStorage: () -> Unit,
    onPickDuplicateFolder: () -> Unit,
    onToggleDuplicate: (String, String) -> Unit,
    onRequestDeleteDuplicates: () -> Unit,
    onResetDuplicateScan: () -> Unit,
    onSpeakCleanupSummary: () -> Unit,
    onRefreshNetwork: () -> Unit,
    onOpenWirelessSettings: () -> Unit,
    onRequestLocationForSsid: () -> Unit,
    onBrowseVolume: () -> Unit,
    onPickRecoveryFolder: () -> Unit,
    onResetRecoveryScan: () -> Unit,
    onExportRecoverableFile: (Uri, String) -> Unit,
    onRestoreQuarantinedFile: (QuarantinedFile) -> Unit,
    onPurgeQuarantinedFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.testTag("toolkit_detail_pane"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "detail_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("toolkit_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to categories"
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = category.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                OutlinedButton(
                    onClick = onRefreshToolkit,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh category", modifier = Modifier.size(18.dp))
                }
            }
        }

        item(key = "detail_feedback") {
            AnimatedVisibility(visible = toolkitFeedback != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = toolkitFeedback.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissFeedback) { Text("OK") }
                    }
                }
            }
        }

        when (category) {
            ToolkitCategory.DIAGNOSTICS -> {
                hardwareDiagnosticsItems(
                    batteryHardware = batteryHardware,
                    displayMetrics = displayMetrics,
                    sensors = sensors,
                    activeSensorType = activeSensorType,
                    activeSensorReading = activeSensorReading,
                    onRunBatteryTest = onRunBatteryTest,
                    onLaunchDisplayTest = onLaunchDisplayTest,
                    onStartSensorTest = onStartSensorTest,
                    onStopSensorTest = onStopSensorTest
                )
            }

            ToolkitCategory.USB_TOOLS -> {
                usbToolsItems(
                    usbDevices = systemInfo.usbDevices,
                    bootableUsbCapability = bootableUsbCapability,
                    bootImageInspectionState = bootImageInspectionState,
                    onRefreshUsb = onRefreshToolkit,
                    onPickBootImage = onPickBootImage,
                    onResetBootImageInspection = onResetBootImageInspection
                )
            }

            ToolkitCategory.CLEANER -> {
                storageCleanerItems(
                    storage = storage,
                    duplicateState = duplicateState,
                    cleanupFeedback = cleanupFeedback,
                    cleanerScanResult = cleanerScanResult,
                    isScanningCleaner = isScanningCleaner,
                    cleanupReport = cleanupReport,
                    quarantineBeforeDelete = quarantineBeforeDelete,
                    onToggleCleanerCategory = onToggleCleanerCategory,
                    onToggleQuarantineBeforeDelete = onToggleQuarantineBeforeDelete,
                    onRescanAppCleaner = onRescanAppCleaner,
                    onPickSafFolderForCleaner = onPickSafFolderForCleaner,
                    onExecuteAdvancedClean = onExecuteAdvancedClean,
                    onDismissCleanupReport = onDismissCleanupReport,
                    onDismissFeedback = onDismissCleanupFeedback,
                    onClearAppCache = onClearAppCache,
                    onOpenSystemStorage = onOpenSystemStorage,
                    onPickDuplicateFolder = onPickDuplicateFolder,
                    onToggleDuplicate = onToggleDuplicate,
                    onRequestDeleteDuplicates = onRequestDeleteDuplicates,
                    onResetDuplicateScan = onResetDuplicateScan,
                    onSpeakCleanupSummary = onSpeakCleanupSummary
                )
            }

            ToolkitCategory.NETWORK_AUDIT -> {
                networkAndSystemAuditItems(
                    wifi = wifi,
                    systemInfo = systemInfo,
                    storageVolumes = storageVolumes,
                    locationDeniedNote = locationDeniedNote,
                    onRefreshNetwork = onRefreshNetwork,
                    onOpenWirelessSettings = onOpenWirelessSettings,
                    onRequestLocationForSsid = onRequestLocationForSsid,
                    onBrowseVolume = onBrowseVolume,
                    onOpenStorageSettings = onOpenSystemStorage
                )
            }

            ToolkitCategory.RECOVERY -> {
                fileRecoveryItems(
                    recoveryScanState = recoveryScanState,
                    quarantineFiles = quarantineFiles,
                    onPickRecoveryFolder = onPickRecoveryFolder,
                    onResetRecoveryScan = onResetRecoveryScan,
                    onExportRecoverableFile = onExportRecoverableFile,
                    onRestoreQuarantinedFile = onRestoreQuarantinedFile,
                    onPurgeQuarantinedFile = onPurgeQuarantinedFile
                )
            }
        }
    }
}

private data class CategoryMeta(
    val icon: ImageVector,
    val accent: Color,
    val statusText: String,
    val statusSeverity: SeverityLevel
)

@Composable
private fun categoryMeta(
    category: ToolkitCategory,
    storage: StorageSummary,
    wifi: WifiSecurityAudit,
    systemInfo: SystemIntegrityInfo,
    batteryHardware: BatteryHardwareSnapshot,
    bootableUsbCapability: BootableUsbCapability,
    quarantineCount: Int
): CategoryMeta {
    return when (category) {
        ToolkitCategory.DIAGNOSTICS -> CategoryMeta(
            icon = Icons.Filled.Build,
            accent = MaterialTheme.colorScheme.secondary,
            statusText = "${batteryHardware.levelPct}% • ${batteryHardware.testStatus.label}",
            statusSeverity = when (batteryHardware.testStatus) {
                HardwareTestStatus.PASSED -> SeverityLevel.SAFE
                HardwareTestStatus.ATTENTION -> SeverityLevel.CAUTION
                HardwareTestStatus.FAILED -> SeverityLevel.ALERT
                HardwareTestStatus.NOT_RUN -> SeverityLevel.INFO
            }
        )
        ToolkitCategory.USB_TOOLS -> CategoryMeta(
            icon = Icons.Filled.Usb,
            accent = MaterialTheme.colorScheme.primary,
            statusText = when {
                systemInfo.usbDevices.isNotEmpty() -> "${systemInfo.usbDevices.size} USB"
                bootableUsbCapability.hasUsbHostFeature -> "OTG Ready"
                else -> "Sandbox"
            },
            statusSeverity = if (systemInfo.usbDevices.isNotEmpty() || bootableUsbCapability.hasUsbHostFeature) {
                SeverityLevel.SAFE
            } else {
                SeverityLevel.INFO
            }
        )
        ToolkitCategory.CLEANER -> CategoryMeta(
            icon = Icons.Filled.CleaningServices,
            accent = MaterialTheme.colorScheme.tertiary,
            statusText = "${formatBytes(storage.totalAppCacheBytes)} cache",
            statusSeverity = if (storage.usedFraction > 0.85f) SeverityLevel.CAUTION else SeverityLevel.SAFE
        )
        ToolkitCategory.NETWORK_AUDIT -> CategoryMeta(
            icon = Icons.Filled.Security,
            accent = MaterialTheme.colorScheme.primary,
            statusText = if (wifi.isConnected) "${wifi.auditScore}/100" else "Offline",
            statusSeverity = when {
                !wifi.isConnected -> SeverityLevel.INFO
                wifi.auditScore >= 80 -> SeverityLevel.SAFE
                wifi.auditScore >= 55 -> SeverityLevel.CAUTION
                else -> SeverityLevel.ALERT
            }
        )
        ToolkitCategory.RECOVERY -> CategoryMeta(
            icon = Icons.Filled.RestoreFromTrash,
            accent = MaterialTheme.colorScheme.secondary,
            statusText = "$quarantineCount staged",
            statusSeverity = SeverityLevel.INFO
        )
    }
}
