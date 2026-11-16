package com.example.ui.components.toolkit

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diagnostics.formatBytes
import com.example.model.BootImageInspectionState
import com.example.model.BootableUsbCapability
import com.example.model.SeverityLevel
import com.example.model.UsbDeviceInfo
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.JetBrainsMonoFontFamily

fun LazyListScope.usbToolsItems(
    usbDevices: List<UsbDeviceInfo>,
    bootableUsbCapability: BootableUsbCapability,
    bootImageInspectionState: BootImageInspectionState,
    onRefreshUsb: () -> Unit,
    onPickBootImage: () -> Unit,
    onResetBootImageInspection: () -> Unit
) {
    item(key = "usb_otg_inspector") {
        UsbOtgHostInspectorCard(
            usbDevices = usbDevices,
            onRefresh = onRefreshUsb
        )
    }
    item(key = "usb_boot_capability") {
        BootableUsbCapabilityCard(
            capability = bootableUsbCapability,
            onRefresh = onRefreshUsb
        )
    }
    item(key = "usb_boot_image_inspector") {
        BootImageInspectorCard(
            state = bootImageInspectionState,
            onPickImage = onPickBootImage,
            onReset = onResetBootImageInspection
        )
    }
    item(key = "usb_setup_playbook") {
        BootableUsbSetupGuideCard()
    }
}

@Composable
private fun UsbOtgHostInspectorCard(
    usbDevices: List<UsbDeviceInfo>,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.testTag("usb_otg_inspector_card"),
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
                        imageVector = Icons.Filled.Usb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "USB OTG Host Inspector",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_usb_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan USB")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (usbDevices.isEmpty()) {
                Text(
                    text = "No external USB OTG devices attached. Connect a USB-C flash drive or adapter to inspect hardware descriptors, VID/PID, and interface classes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                usbDevices.forEach { dev ->
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Text(
                            text = dev.productName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${dev.deviceClassLabel} • VID ${dev.vendorIdHex} : PID ${dev.productIdHex} • ${dev.interfaceCount} iface(s)",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
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
                    Text("Bootable USB Probe", style = MaterialTheme.typography.titleLarge)
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
            ToolkitSpecRow("USB OTG Host", if (capability.hasUsbHostFeature) "Supported" else "Not Exposed")
            ToolkitSpecRow("USB Devices / UMS", "${capability.attachedUsbCount} attached (${capability.attachedMassStorageCount} storage)")
            ToolkitSpecRow("Kernel ConfigFS", if (capability.configFsPathExists) "Present (${if (capability.configFsReadable) "Readable" else "Locked"})" else "Restricted / Absent")
            ToolkitSpecRow("UMS Gadget Driver", if (capability.massStorageFunctionDetected) "Detected" else "Unprivileged / Absent")
            ToolkitSpecRow("Root Binary (su)", if (capability.suBinaryDetected) "Detected" else "Not Detected (Stock)")
            ToolkitSpecRow("Staging Space", formatBytes(capability.stagingFreeBytes))

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onRefresh,
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .testTag("reprobe_bootable_usb_button")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Re-Probe USB & Kernel Capabilities")
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
            Text(
                text = "Bootable .ISO / .IMG Inspector",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Verify ISO-9660, El Torito, MBR/GPT hybrid boot headers, FAT32 4 GiB limits, and SHA-256 digests before copying to Ventoy or OTG drives.",
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
                text = "USB Boot Setup Playbook",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose your hardware path below:",
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
                    "1. Prepare a Ventoy-formatted USB drive once on a PC (exFAT holds ISOs > 4 GiB).",
                    "2. Download your OS/recovery .ISO and verify its SHA-256 + El Torito header above.",
                    "3. Connect the USB drive via USB-C OTG and copy the .ISO onto it via Files / SAF.",
                    "4. Boot the target PC into its Boot Menu (F12/F11/F8/Del) and select the USB drive."
                )
            } else {
                listOf(
                    "1. Requires an unlocked bootloader, root, and a kernel with CONFIG_USB_CONFIGFS_MASS_STORAGE=y.",
                    "2. Stage a raw hybrid .ISO or .IMG on /data matching the target PC CPU architecture.",
                    "3. Configure /config/usb_gadget/g1/functions/mass_storage.0/lun.0/file and rebind UDC.",
                    "4. Connect phone to target PC USB port and select the emulated LUN in UEFI Boot Menu."
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
