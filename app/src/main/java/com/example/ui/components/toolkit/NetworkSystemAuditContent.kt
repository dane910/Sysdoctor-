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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WifiLock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diagnostics.formatBytes
import com.example.model.SeverityLevel
import com.example.model.StorageVolumeDetail
import com.example.model.SystemIntegrityInfo
import com.example.model.WifiSecurityAudit
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.JetBrainsMonoFontFamily

fun LazyListScope.networkAndSystemAuditItems(
    wifi: WifiSecurityAudit,
    systemInfo: SystemIntegrityInfo,
    storageVolumes: List<StorageVolumeDetail>,
    locationDeniedNote: Boolean,
    onRefreshNetwork: () -> Unit,
    onOpenWirelessSettings: () -> Unit,
    onRequestLocationForSsid: () -> Unit,
    onBrowseVolume: () -> Unit,
    onOpenStorageSettings: () -> Unit
) {
    item(key = "audit_wifi_card") {
        Card(
            modifier = Modifier.testTag("toolkit_network_audit_card"),
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
                        Icon(
                            imageVector = Icons.Filled.WifiLock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Network Security Audit",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    SeverityBadge(
                        severity = when {
                            !wifi.isConnected -> SeverityLevel.INFO
                            wifi.auditScore >= 80 -> SeverityLevel.SAFE
                            wifi.auditScore >= 55 -> SeverityLevel.CAUTION
                            else -> SeverityLevel.ALERT
                        },
                        customLabel = if (wifi.isConnected) "${wifi.auditScore}/100" else "Offline"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                ToolkitSpecRow("Transport", wifi.transportLabel)
                if (wifi.isWifi) {
                    ToolkitSpecRow("SSID", wifi.ssid)
                    ToolkitSpecRow("BSSID", wifi.bssid)
                    ToolkitSpecRow("Encryption", wifi.securityProtocol)
                    ToolkitSpecRow("Band / Speed", "${wifi.bandLabel} • ${wifi.linkSpeedMbps} Mbps (${wifi.rssiDbm} dBm)")
                }
                ToolkitSpecRow("Local IP", wifi.localIp)
                ToolkitSpecRow(
                    "Gateway",
                    wifi.gatewayIp + (wifi.gatewayLatencyMs?.let { " (${it} ms)" } ?: "")
                )
                ToolkitSpecRow(
                    "DNS Servers",
                    wifi.dnsServers.joinToString(", ").ifBlank { "—" } +
                        (wifi.dnsLatencyMs?.let { " (${it} ms)" } ?: "")
                )
                ToolkitSpecRow(
                    "Private DNS (DoT)",
                    if (wifi.isPrivateDnsActive) "Active (${wifi.privateDnsHost ?: "Opportunistic"})" else "Disabled"
                )
                ToolkitSpecRow("VPN Tunnel", if (wifi.isVpnActive) "Active" else "Not Detected")

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onRefreshNetwork,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("reaudit_network_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Scan")
                    }
                    OutlinedButton(
                        onClick = onOpenWirelessSettings,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("network_settings_button")
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Net Settings")
                    }
                }

                if (wifi.isWifi && !wifi.hasLocationPermission) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onRequestLocationForSsid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("unlock_ssid_button")
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grant Location to Reveal Wi-Fi SSID/BSSID")
                    }
                    if (locationDeniedNote) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Location permission declined — SSID remains redacted per Android privacy rules, while encryption & DNS checks continue working.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    items(wifi.findings, key = { "finding_${it.title}" }) { finding ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = finding.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SeverityBadge(severity = finding.severity)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = finding.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    item(key = "audit_os_integrity") {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OS Integrity & Build Telemetry",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    SeverityBadge(
                        severity = if (systemInfo.isTestKeys) SeverityLevel.CAUTION else SeverityLevel.SAFE,
                        customLabel = if (systemInfo.isTestKeys) "test-keys" else "release-keys"
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                ToolkitSpecRow("Model", "${systemInfo.manufacturer} ${systemInfo.model}")
                ToolkitSpecRow("Android / API", "Android ${systemInfo.androidVersion} (API ${systemInfo.apiLevel})")
                ToolkitSpecRow("Security Patch", systemInfo.securityPatch)
                ToolkitSpecRow("Build Type / Tags", "${systemInfo.buildType} / ${systemInfo.buildTags}")
                ToolkitSpecRow("Kernel", systemInfo.kernelVersion)
                ToolkitSpecRow("CPU", "${systemInfo.cpuCores} cores (${systemInfo.cpuAbis})")
                ToolkitSpecRow("RAM", "${formatBytes(systemInfo.availableRamBytes)} free / ${formatBytes(systemInfo.totalRamBytes)}")

                if (systemInfo.partitions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Partition & Mount Sandbox View",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    systemInfo.partitions.forEach { part ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = part.mountPoint,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = part.accessMode,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = "FS: ${part.fileSystem} • ${formatBytes(part.freeBytes)} free of ${formatBytes(part.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    item(key = "partition_controls_header") {
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
                    text = "Inspect mounted storage volumes, block geometry, and OS storage settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBrowseVolume,
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
                        onClick = onOpenStorageSettings,
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

    items(storageVolumes, key = { "vol_${it.id}" }) { vol ->
        StorageVolumeCard(volume = vol)
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
