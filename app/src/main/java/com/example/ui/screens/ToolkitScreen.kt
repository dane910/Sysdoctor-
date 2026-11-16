package com.example.ui.screens

import android.content.Intent
import android.hardware.Sensor
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diagnostics.formatBytes
import com.example.model.BatteryHardwareSnapshot
import com.example.model.BootImageInspectionState
import com.example.model.BootableUsbCapability
import com.example.model.DisplayMetricsSnapshot
import com.example.model.HardwareTestStatus
import com.example.model.LiveSensorReading
import com.example.model.QuarantinedFile
import com.example.model.RecoverableFileItem
import com.example.model.RecoveryScanState
import com.example.model.SensorDescriptor
import com.example.model.SeverityLevel
import com.example.model.StorageVolumeDetail
import com.example.ui.components.DisplayDiagnosticDialog
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

private enum class ToolkitSection(val label: String) {
    HARDWARE("Hardware Tests"),
    RECOVERY("File Recovery"),
    PARTITIONS("Partitions"),
    BOOTABLE_USB("Bootable USB")
}

@Composable
fun ToolkitScreen(
    batteryHardware: BatteryHardwareSnapshot,
    displayMetrics: DisplayMetricsSnapshot,
    sensors: List<SensorDescriptor>,
    activeSensorType: Int?,
    activeSensorReading: LiveSensorReading?,
    storageVolumes: List<StorageVolumeDetail>,
    recoveryScanState: RecoveryScanState,
    quarantineFiles: List<QuarantinedFile>,
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
    onRefreshToolkit: () -> Unit,
    onDismissFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    bootableUsbCapability: BootableUsbCapability = BootableUsbCapability(),
    bootImageInspectionState: BootImageInspectionState = BootImageInspectionState.Idle,
    onInspectBootImage: (Uri) -> Unit = {},
    onResetBootImageInspection: () -> Unit = {}
) {
    val context = LocalContext.current
    var activeSection by remember { mutableStateOf(ToolkitSection.HARDWARE) }
    var showDisplayDialog by remember { mutableStateOf(false) }
    var pendingRecoverySourceUri by remember { mutableStateOf<Uri?>(null) }
    var pendingQuarantineRestoreId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { onStopSensorTest() }
    }

    val bootImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onInspectBootImage(uri)
    }

    val recoveryFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) onStartRecoveryScan(uri)
    }

    val browseVolumeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { _ ->
        // Grants user access to inspect volume in system SAF picker
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("toolkit_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Device Hardware & Data Toolkit",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Battery, display & sensor tests, file recovery, and partition inspection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolkitSection.entries.forEach { section ->
                    FilterChip(
                        selected = activeSection == section,
                        onClick = { activeSection = section },
                        label = { Text(section.label, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("toolkit_tab_${section.name.lowercase()}")
                    )
                }
            }
        }

        item {
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
                        TextButton(onClick = onDismissFeedback) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        when (activeSection) {
            ToolkitSection.HARDWARE -> {
                item {
                    BatteryDiagnosticCard(
                        snapshot = batteryHardware,
                        onRunTest = onRunBatteryTest
                    )
                }
                item {
                    DisplayDiagnosticCard(
                        metrics = displayMetrics,
                        onLaunchTest = { showDisplayDialog = true }
                    )
                }
                item {
                    LiveSensorSuiteCard(
                        sensors = sensors,
                        activeSensorType = activeSensorType,
                        activeReading = activeSensorReading,
                        onStartSensor = onStartSensorTest,
                        onStopSensor = onStopSensorTest
                    )
                }
            }

            ToolkitSection.RECOVERY -> {
                item {
                    RecoveryScannerCard(
                        scanState = recoveryScanState,
                        onPickFolder = { recoveryFolderLauncher.launch(null) },
                        onReset = onResetRecoveryScan
                    )
                }
                if (recoveryScanState is RecoveryScanState.Results && recoveryScanState.candidates.isNotEmpty()) {
                    items(recoveryScanState.candidates, key = { it.uriString }) { item ->
                        RecoverableFileCard(
                            item = item,
                            onExport = {
                                pendingRecoverySourceUri = Uri.parse(item.uriString)
                                val cleanName = item.name.removePrefix(".trashed-").substringAfter('-')
                                    .ifBlank { item.name }
                                exportRecoveredFileLauncher.launch(cleanName)
                            }
                        )
                    }
                }
                item {
                    QuarantineVaultCard(
                        files = quarantineFiles,
                        onRestore = { file ->
                            pendingQuarantineRestoreId = file.id
                            restoreQuarantineLauncher.launch(file.originalName)
                        },
                        onPurge = onPurgeQuarantinedFile
                    )
                }
            }

            ToolkitSection.PARTITIONS -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Storage Volumes & Partition Controls",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Inspect mounted partitions, block geometry, and access modes. Repartitioning protected /system slots requires Fastboot/Recovery outside the Android OS sandbox.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { browseVolumeLauncher.launch(null) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("browse_volume_button")
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Browse Volume")
                                }
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("open_partition_storage_settings")
                                ) {
                                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("OS Storage")
                                }
                            }
                        }
                    }
                }
                items(storageVolumes, key = { it.id }) { vol ->
                    StorageVolumeCard(volume = vol)
                }
            }

            ToolkitSection.BOOTABLE_USB -> {
                item {
                    BootableUsbCapabilityCard(
                        capability = bootableUsbCapability,
                        onRefresh = onRefreshToolkit
                    )
                }
                item {
                    BootImageInspectorCard(
                        state = bootImageInspectionState,
                        onPickImage = {
                            bootImagePickerLauncher.launch(arrayOf("*/*"))
                        },
                        onReset = onResetBootImageInspection
                    )
                }
                item {
                    BootableUsbSetupGuideCard()
                }
            }
        }
    }
}

@Composable
private fun BatteryDiagnosticCard(
    snapshot: BatteryHardwareSnapshot,
    onRunTest: () -> Unit
) {
    val severity = when (snapshot.testStatus) {
        HardwareTestStatus.PASSED -> SeverityLevel.SAFE
        HardwareTestStatus.ATTENTION -> SeverityLevel.CAUTION
        HardwareTestStatus.FAILED -> SeverityLevel.ALERT
        HardwareTestStatus.NOT_RUN -> SeverityLevel.INFO
    }

    Card(
        modifier = Modifier.testTag("toolkit_battery_card"),
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
                    Icon(Icons.Filled.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Battery Hardware Test", style = MaterialTheme.typography.titleLarge)
                }
                SeverityBadge(severity = severity, customLabel = snapshot.testStatus.label)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = snapshot.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            ToolkitSpecRow("Health / Tech", "${snapshot.healthLabel} (${snapshot.technology})")
            ToolkitSpecRow("Charge / Source", "${snapshot.levelPct}% • ${snapshot.statusLabel} (${snapshot.pluggedSource})")
            ToolkitSpecRow("Temp / Voltage", "${snapshot.temperatureC}°C • ${snapshot.voltageMv} mV")
            snapshot.currentNowMa?.let { ToolkitSpecRow("Instant Current", "$it mA") }
            snapshot.chargeCounterMah?.let { ToolkitSpecRow("Charge Counter", "$it mAh") }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRunTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("run_battery_test_button")
            ) {
                Text("Run Battery Diagnostic")
            }
        }
    }
}

@Composable
private fun DisplayDiagnosticCard(
    metrics: DisplayMetricsSnapshot,
    onLaunchTest: () -> Unit
) {
    val severity = when (metrics.testStatus) {
        HardwareTestStatus.PASSED -> SeverityLevel.SAFE
        HardwareTestStatus.ATTENTION -> SeverityLevel.CAUTION
        HardwareTestStatus.FAILED -> SeverityLevel.ALERT
        HardwareTestStatus.NOT_RUN -> SeverityLevel.INFO
    }

    Card(
        modifier = Modifier.testTag("toolkit_display_card"),
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
                    Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Display & Touch Test", style = MaterialTheme.typography.titleLarge)
                }
                SeverityBadge(severity = severity, customLabel = metrics.testStatus.label)
            }
            Spacer(modifier = Modifier.height(10.dp))
            ToolkitSpecRow("Resolution", "${metrics.widthPx} × ${metrics.heightPx} px")
            ToolkitSpecRow("Density", "${metrics.densityDpi} dpi (${metrics.xdpi.toInt()}×${metrics.ydpi.toInt()} ppi)")
            ToolkitSpecRow("Refresh Rate", "${metrics.refreshRateHz.toInt()} Hz")
            ToolkitSpecRow("HDR / Wide Color", "${if (metrics.isHdrCapable) "HDR" else "SDR"} • ${if (metrics.isWideColorGamut) "Wide Gamut" else "sRGB"}")

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onLaunchTest,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("launch_display_test_button")
            ) {
                Text("Launch Pixel & Touch Test")
            }
        }
    }
}

@Composable
private fun LiveSensorSuiteCard(
    sensors: List<SensorDescriptor>,
    activeSensorType: Int?,
    activeReading: LiveSensorReading?,
    onStartSensor: (Int) -> Unit,
    onStopSensor: () -> Unit
) {
    val testableTypes = listOf(
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_MAGNETIC_FIELD,
        Sensor.TYPE_LIGHT,
        Sensor.TYPE_PROXIMITY,
        Sensor.TYPE_PRESSURE
    )
    val testableSensors = remember(sensors) {
        sensors.filter { it.type in testableTypes }.distinctBy { it.type }
    }

    Card(
        modifier = Modifier.testTag("toolkit_sensors_card"),
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
                    Icon(Icons.Filled.Sensors, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hardware Sensors (${sensors.size})", style = MaterialTheme.typography.titleLarge)
                }
                if (activeSensorType != null) {
                    OutlinedButton(
                        onClick = onStopSensor,
                        modifier = Modifier.testTag("stop_sensor_test_button")
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }

            if (activeReading != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "LIVE: ${activeReading.sensorName}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = activeReading.values.joinToString("  |  ") {
                                String.format(Locale.US, "%.2f %s", it, activeReading.unitLabel)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Accuracy: ${activeReading.accuracyLabel} • Samples: ${activeReading.sampleCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            if (testableSensors.isEmpty()) {
                Text(
                    text = "No standard motion or environmental sensors exposed by this runtime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                testableSensors.forEach { sensor ->
                    val isRunning = activeSensorType == sensor.type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sensor.typeLabel,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${sensor.vendor} • ${sensor.powerMa} mA",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(
                            onClick = { if (isRunning) onStopSensor() else onStartSensor(sensor.type) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("test_sensor_${sensor.type}")
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isRunning) "Stop" else "Test")
                        }
                    }
                }
            }
        }
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
                text = "Pick any shared folder (DCIM, Downloads, Documents, or USB OTG) to locate soft-deleted .trashed files, .Trash folders, backups, and verify JPEG/PNG/PDF/MP4 magic-byte headers.",
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

@Composable
private fun StorageVolumeCard(volume: StorageVolumeDetail) {
    Card(
        modifier = Modifier.testTag("partition_volume_${volume.id}"),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = volume.description,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                SeverityBadge(
                    severity = if (volume.isReadOnly) SeverityLevel.INFO else SeverityLevel.SAFE,
                    customLabel = if (volume.isReadOnly) "Read-Only" else "Read-Write"
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Mount: ${volume.mountPath} • Block size: ${volume.blockSizeBytes} B • ${volume.state}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (volume.totalBytes > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { volume.usedFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Used: ${formatBytes(volume.usedBytes)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily)
                    )
                    Text(
                        text = "${formatBytes(volume.freeBytes)} free / ${formatBytes(volume.totalBytes)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolkitSpecRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BootableUsbCapabilityCard(
    capability: BootableUsbCapability,
    onRefresh: () -> Unit
) {
    val badgeSeverity = when {
        capability.suBinaryDetected && capability.massStorageFunctionDetected -> SeverityLevel.SAFE
        capability.hasUsbHostFeature -> SeverityLevel.INFO
        else -> SeverityLevel.CAUTION
    }
    val badgeLabel = when {
        capability.suBinaryDetected && capability.massStorageFunctionDetected -> "UMS Ready"
        capability.hasUsbHostFeature -> "OTG Ready"
        else -> "Sandbox Only"
    }

    Card(
        modifier = Modifier.testTag("toolkit_bootable_usb_card"),
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
                    Icon(Icons.Filled.Usb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Bootable USB Readiness Probe", style = MaterialTheme.typography.titleLarge)
                }
                SeverityBadge(severity = badgeSeverity, customLabel = badgeLabel)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = capability.verdictTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (capability.verdictDetail.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = capability.verdictDetail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            ToolkitSpecRow("USB OTG Host", if (capability.hasUsbHostFeature) "Supported" else "Unavailable")
            ToolkitSpecRow("Attached USB / MSC", "${capability.attachedUsbCount} total • ${capability.attachedMassStorageCount} storage")
            ToolkitSpecRow(
                "Kernel ConfigFS",
                when {
                    capability.massStorageFunctionDetected -> "mass_storage detected"
                    capability.configFsReadable -> "Readable (no UMS)"
                    capability.configFsPathExists -> "SELinux Blocked"
                    else -> "Restricted / Not Exposed"
                }
            )
            ToolkitSpecRow("Root Binary Path", if (capability.suBinaryDetected) "Detected" else "Not Present (Stock)")
            ToolkitSpecRow("ISO Staging Space", formatBytes(capability.stagingFreeBytes))
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("reprobe_usb_gadget_button")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Re-Probe USB & Kernel Gadget")
            }
        }
    }
}

@Composable
private fun BootImageInspectorCard(
    state: BootImageInspectionState,
    onPickImage: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier.testTag("boot_image_inspector_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ISO / Recovery Image Verifier", style = MaterialTheme.typography.titleLarge)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Inspect real ISO-9660, El Torito, MBR/GPT hybrid boot records, FAT32 4 GiB limits, and compute the full SHA-256 digest before staging or flashing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (state) {
                BootImageInspectionState.Idle -> {
                    Button(
                        onClick = onPickImage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("pick_boot_image_button")
                    ) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Inspect .ISO or .IMG File")
                    }
                }
                is BootImageInspectionState.Inspecting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Hashing ${state.fileName} (${formatBytes(state.bytesProcessed)})…",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onReset) { Text("Cancel") }
                    }
                }
                is BootImageInspectionState.Ready -> {
                    val info = state.inspection
                    SeverityBadge(
                        severity = if (info.hasElToritoBoot || info.hasMbrSignature) SeverityLevel.SAFE else SeverityLevel.CAUTION,
                        customLabel = info.bootCompatibilityLabel
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ToolkitSpecRow("File", info.fileName)
                    ToolkitSpecRow("Size", formatBytes(info.sizeBytes))
                    info.volumeLabel?.let { ToolkitSpecRow("ISO Volume ID", it) }
                    ToolkitSpecRow("ISO-9660 / El Torito", "${if (info.isIso9660) "Yes" else "No"} / ${if (info.hasElToritoBoot) "Yes" else "No"}")
                    ToolkitSpecRow("MBR / GPT Header", "${if (info.hasMbrSignature) "0x55AA" else "No"} / ${if (info.hasGptHeader) "EFI PART" else "No"}")
                    ToolkitSpecRow(
                        "FAT32 Compatibility",
                        if (info.exceedsFat32Limit) "Exceeds 4 GiB (Use exFAT/Ventoy)" else "Fits FAT32 (< 4 GiB)"
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "SHA-256: ${info.sha256Hex}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onPickImage,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Inspect Another Image")
                    }
                }
                is BootImageInspectionState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onPickImage) { Text("Retry Image Selection") }
                }
            }
        }
    }
}

@Composable
private fun BootableUsbSetupGuideCard() {
    var useRootedPath by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.testTag("bootable_usb_setup_guide_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Implementation & Setup Playbook",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Android exposes MTP/ADB over USB-C by default. Choose your hardware path below:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !useRootedPath,
                    onClick = { useRootedPath = false },
                    label = { Text("Non-Root (USB OTG)") },
                    modifier = Modifier.testTag("guide_chip_otg")
                )
                FilterChip(
                    selected = useRootedPath,
                    onClick = { useRootedPath = true },
                    label = { Text("Rooted Kernel (UMS)") },
                    modifier = Modifier.testTag("guide_chip_rooted")
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            val steps = if (!useRootedPath) {
                listOf(
                    "1. Prepare a Ventoy-formatted USB flash drive or microSD card once on any PC (exFAT partition holds ISOs > 4 GiB).",
                    "2. Download the OS/recovery ISO (e.g., Ubuntu, Windows, SystemRescue) on your phone and verify its SHA-256 + El Torito header above.",
                    "3. Connect the Ventoy USB drive via USB-C OTG adapter and copy the verified .iso onto the USB volume via Android Files / SAF.",
                    "4. Plug the USB stick into the target PC, enter Boot Menu (F12/F11/F8/Del), and disable Secure Boot if booting unsigned recovery tools."
                )
            } else {
                listOf(
                    "1. Requires an unlocked bootloader, root (Magisk/KernelSU), and a custom kernel compiled with CONFIG_USB_CONFIGFS_MASS_STORAGE=y.",
                    "2. Stage a raw hybrid .iso or .img file on /data with matching target PC CPU architecture (x86_64 vs ARM64).",
                    "3. Mount ConfigFS (/config/usb_gadget/g1), create functions/mass_storage.0/lun.0, write the ISO path to lun.0/file (ro=1 for CD-ROM), and rebind UDC.",
                    "4. Connect phone directly to PC USB-A/USB-C port via a data-capable cable, power on PC into UEFI Boot Menu, and select the emulated USB LUN."
                )
            }
            steps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
    }
}
