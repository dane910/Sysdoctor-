package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WifiLock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.diagnostics.formatBytes
import com.example.model.SeverityLevel
import com.example.model.SystemIntegrityInfo
import com.example.model.WifiSecurityAudit
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun AuditAndSystemScreen(
    wifi: WifiSecurityAudit,
    systemInfo: SystemIntegrityInfo,
    onRefreshNetwork: () -> Unit,
    onRefreshAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var locationDeniedNote by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationDeniedNote = !granted
        onRefreshNetwork()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Network & System Audit",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Live Wi-Fi posture, partitions & USB OTG inspection",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Wi-Fi Security Audit Card
        item {
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
                                imageVector = Icons.Filled.WifiLock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Current Network Security",
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
                    KeyValueRow("Transport", wifi.transportLabel)
                    if (wifi.isWifi) {
                        KeyValueRow("SSID", wifi.ssid)
                        KeyValueRow("BSSID", wifi.bssid)
                        KeyValueRow("Encryption", wifi.securityProtocol)
                        KeyValueRow("Band / Speed", "${wifi.bandLabel} • ${wifi.linkSpeedMbps} Mbps (${wifi.rssiDbm} dBm)")
                    }
                    KeyValueRow("Local IP", wifi.localIp)
                    KeyValueRow(
                        "Gateway",
                        wifi.gatewayIp + (wifi.gatewayLatencyMs?.let { " (${it} ms)" } ?: "")
                    )
                    KeyValueRow(
                        "DNS Servers",
                        wifi.dnsServers.joinToString(", ").ifBlank { "—" } +
                            (wifi.dnsLatencyMs?.let { " (${it} ms)" } ?: "")
                    )
                    KeyValueRow(
                        "Private DNS (DoT)",
                        if (wifi.isPrivateDnsActive) "Active (${wifi.privateDnsHost ?: "Opportunistic"})" else "Disabled"
                    )
                    KeyValueRow("VPN Tunnel", if (wifi.isVpnActive) "Active" else "Not Detected")

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
                            Text("Re-Audit")
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                                }
                            },
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
                            onClick = {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
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

        // Findings List
        items(wifi.findings) { finding ->
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

        // System Integrity & Build Telemetry
        item {
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
                                text = "System Files & OS Integrity",
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
                    KeyValueRow("Model", "${systemInfo.manufacturer} ${systemInfo.model}")
                    KeyValueRow("Android / API", "Android ${systemInfo.androidVersion} (API ${systemInfo.apiLevel})")
                    KeyValueRow("Security Patch", systemInfo.securityPatch)
                    KeyValueRow("Build Type / Tags", "${systemInfo.buildType} / ${systemInfo.buildTags}")
                    KeyValueRow("Kernel", systemInfo.kernelVersion)
                    KeyValueRow("CPU", "${systemInfo.cpuCores} cores (${systemInfo.cpuAbis})")
                    KeyValueRow("RAM", "${formatBytes(systemInfo.availableRamBytes)} free / ${formatBytes(systemInfo.totalRamBytes)}")
                    KeyValueRow("Battery", "${systemInfo.batteryLevelPct}% • ${systemInfo.batteryTempC}°C • ${systemInfo.batteryVoltageMv} mV")

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

        // USB OTG Host Inspector
        item {
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
                            onClick = onRefreshAll,
                            modifier = Modifier.testTag("refresh_usb_button")
                        ) {
                            Text("Scan USB")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    if (systemInfo.usbDevices.isEmpty()) {
                        Text(
                            text = "No external USB OTG devices attached. Plug in a USB-C flash drive or adapter to inspect its hardware descriptor, VID/PID, and device class.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        systemInfo.usbDevices.forEach { dev ->
                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = dev.productName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${dev.deviceClassLabel} • VID ${dev.vendorIdHex} : PID ${dev.productIdHex} • ${dev.interfaceCount} iface(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMonoFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.58f)
        )
    }
}
