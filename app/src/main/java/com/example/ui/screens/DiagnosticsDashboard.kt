package com.example.ui.screens

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.model.BatteryTrendMetric
import com.example.model.BatteryTrendPoint
import com.example.model.SeverityLevel
import com.example.model.StorageSummary
import com.example.model.SystemIntegrityInfo
import com.example.model.WifiSecurityAudit
import com.example.ui.components.BatteryTrendChartCard
import com.example.ui.components.ResourceUtilizationGraphCard
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun DiagnosticsDashboard(
    systemInfo: SystemIntegrityInfo,
    storage: StorageSummary = StorageSummary(),
    wifi: WifiSecurityAudit? = null,
    batteryTrend7Days: List<BatteryTrendPoint> = emptyList(),
    selectedBatteryMetric: BatteryTrendMetric = BatteryTrendMetric.HEALTH_SCORE,
    onSelectBatteryMetric: (BatteryTrendMetric) -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    onOpenStorage: (() -> Unit)? = null,
    onOpenBattery: (() -> Unit)? = null,
    onOpenAudit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var telemetryExpanded by rememberSaveable { mutableStateOf(false) }
    var graphsExpanded by rememberSaveable { mutableStateOf(false) }

    val storageSeverity = when {
        storage.totalBytes <= 0L -> SeverityLevel.INFO
        storage.usedFraction >= 0.90f -> SeverityLevel.ALERT
        storage.usedFraction >= 0.75f -> SeverityLevel.CAUTION
        else -> SeverityLevel.SAFE
    }
    val storageStatusLabel = when (storageSeverity) {
        SeverityLevel.ALERT -> "Critical"
        SeverityLevel.CAUTION -> "Low Space"
        SeverityLevel.SAFE -> "Healthy"
        SeverityLevel.INFO -> "Scanning"
    }
    val storageAccent = when (storageSeverity) {
        SeverityLevel.ALERT -> MaterialTheme.colorScheme.error
        SeverityLevel.CAUTION -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    val batteryHealthIssue = systemInfo.batteryHealthLabel.equals("Overheat", ignoreCase = true) ||
        systemInfo.batteryHealthLabel.equals("Dead", ignoreCase = true) ||
        systemInfo.batteryHealthLabel.equals("Over Voltage", ignoreCase = true)
    val batterySeverity = when {
        batteryHealthIssue || systemInfo.batteryTempC >= 43f || systemInfo.batteryLevelPct <= 15 -> SeverityLevel.ALERT
        systemInfo.batteryHealthLabel.equals("Cold", ignoreCase = true) ||
            systemInfo.batteryTempC >= 38f ||
            systemInfo.batteryLevelPct <= 30 -> SeverityLevel.CAUTION
        else -> SeverityLevel.SAFE
    }
    val batteryAccent = when (batterySeverity) {
        SeverityLevel.ALERT -> MaterialTheme.colorScheme.error
        SeverityLevel.CAUTION -> MaterialTheme.colorScheme.tertiary
        SeverityLevel.SAFE -> MaterialTheme.colorScheme.secondary
        SeverityLevel.INFO -> MaterialTheme.colorScheme.primary
    }

    val cpuPct = systemInfo.cpuUsagePct
    val cpuSeverity = when {
        cpuPct == null -> SeverityLevel.INFO
        cpuPct >= 85 -> SeverityLevel.ALERT
        cpuPct >= 65 -> SeverityLevel.CAUTION
        else -> SeverityLevel.SAFE
    }

    val ramSeverity = when {
        systemInfo.isLowMemory || systemInfo.ramUsedFraction >= 0.90f -> SeverityLevel.ALERT
        systemInfo.ramUsedFraction >= 0.75f -> SeverityLevel.CAUTION
        else -> SeverityLevel.SAFE
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("diagnostics_dashboard"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Collapsible Telemetry Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { telemetryExpanded = !telemetryExpanded }
                .minimumInteractiveComponentSize()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("dashboard_telemetry_toggle"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SYSTEM TELEMETRY",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (telemetryExpanded) {
                        "Device Health Summary"
                    } else {
                        "Storage ${(storage.usedFraction * 100).toInt()}% • Battery ${systemInfo.batteryLevelPct}% • RAM ${(systemInfo.ramUsedFraction * 100).toInt()}%"
                    },
                    style = if (telemetryExpanded) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                    color = if (telemetryExpanded) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onRefresh != null) {
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("dashboard_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh device health metrics",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Icon(
                imageVector = if (telemetryExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (telemetryExpanded) "Collapse telemetry" else "Expand telemetry",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = telemetryExpanded,
            enter = expandVertically(animationSpec = spring()) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring()) + fadeOut()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWide = maxWidth >= 560.dp

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StorageSummaryCard(
                                storage = storage,
                                severity = storageSeverity,
                                statusLabel = storageStatusLabel,
                                accentColor = storageAccent,
                                onClick = onOpenStorage,
                                modifier = Modifier.weight(1f)
                            )
                            BatteryHealthCard(
                                systemInfo = systemInfo,
                                severity = batterySeverity,
                                accentColor = batteryAccent,
                                onClick = onOpenBattery,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        StorageSummaryCard(
                            storage = storage,
                            severity = storageSeverity,
                            statusLabel = storageStatusLabel,
                            accentColor = storageAccent,
                            onClick = onOpenStorage
                        )
                        BatteryHealthCard(
                            systemInfo = systemInfo,
                            severity = batterySeverity,
                            accentColor = batteryAccent,
                            onClick = onOpenBattery
                        )
                    }

                    // Secondary Compute & Network Telemetry
                    if (isWide && wifi != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            HealthMetricCard(
                                title = "CPU Load",
                                primaryValue = cpuPct?.let { "$it%" } ?: "N/A",
                                progress = ((cpuPct ?: 0) / 100f).coerceIn(0f, 1f),
                                statusLabel = "${systemInfo.cpuCores} Cores",
                                detailLine = systemInfo.cpuMetricSource,
                                icon = Icons.Filled.DeveloperBoard,
                                severity = cpuSeverity,
                                accentColor = MaterialTheme.colorScheme.primary,
                                testTagName = "dashboard_cpu_card",
                                modifier = Modifier.weight(1f)
                            )
                            HealthMetricCard(
                                title = "Available RAM",
                                primaryValue = formatBytes(systemInfo.availableRamBytes),
                                progress = 1f - systemInfo.ramUsedFraction,
                                statusLabel = if (systemInfo.isLowMemory) "Low Mem" else "${((1f - systemInfo.ramUsedFraction) * 100).toInt()}% Free",
                                detailLine = "of ${formatBytes(systemInfo.totalRamBytes)} total",
                                icon = Icons.Filled.Memory,
                                severity = ramSeverity,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                testTagName = "dashboard_ram_card",
                                modifier = Modifier.weight(1f)
                            )
                            NetworkPostureCard(
                                wifi = wifi,
                                onClick = onOpenAudit,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HealthMetricCard(
                                title = "CPU Load",
                                primaryValue = cpuPct?.let { "$it%" } ?: "N/A",
                                progress = ((cpuPct ?: 0) / 100f).coerceIn(0f, 1f),
                                statusLabel = "${systemInfo.cpuCores} Cores",
                                detailLine = systemInfo.cpuMetricSource,
                                icon = Icons.Filled.DeveloperBoard,
                                severity = cpuSeverity,
                                accentColor = MaterialTheme.colorScheme.primary,
                                testTagName = "dashboard_cpu_card",
                                modifier = Modifier.weight(1f)
                            )
                            HealthMetricCard(
                                title = "Available RAM",
                                primaryValue = formatBytes(systemInfo.availableRamBytes),
                                progress = 1f - systemInfo.ramUsedFraction,
                                statusLabel = if (systemInfo.isLowMemory) "Low Mem" else "${((1f - systemInfo.ramUsedFraction) * 100).toInt()}% Free",
                                detailLine = "of ${formatBytes(systemInfo.totalRamBytes)}",
                                icon = Icons.Filled.Memory,
                                severity = ramSeverity,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                testTagName = "dashboard_ram_card",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (wifi != null) {
                            NetworkPostureCard(
                                wifi = wifi,
                                onClick = onOpenAudit
                            )
                        }
                    }
                }
            }
        }

        // Collapsible Graphs & Trends Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { graphsExpanded = !graphsExpanded }
                .minimumInteractiveComponentSize()
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .testTag("dashboard_graphs_toggle"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "GRAPHS & TRENDS",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (graphsExpanded) "Telemetry Charts" else "Battery trend & resource graphs hidden",
                    style = if (graphsExpanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                    color = if (graphsExpanded) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = if (graphsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (graphsExpanded) "Collapse graphs" else "Expand graphs",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = graphsExpanded,
            enter = expandVertically(animationSpec = spring()) + fadeIn(),
            exit = shrinkVertically(animationSpec = spring()) + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ResourceUtilizationGraphCard(
                    storage = storage,
                    systemInfo = systemInfo
                )
            }
        }
    }
}

@Composable
private fun StorageSummaryCard(
    storage: StorageSummary,
    severity: SeverityLevel,
    statusLabel: String,
    accentColor: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val usedPct = (storage.usedFraction * 100).toInt()
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag("dashboard_storage_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.medium
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
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Storage,
                            contentDescription = "Storage status",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Storage Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                SeverityBadge(severity = severity, customLabel = statusLabel)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$usedPct% Used",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${formatBytes(storage.availableBytes)} free",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { storage.usedFraction.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelemetrySpecColumn(label = "Used", value = formatBytes(storage.usedBytes))
                TelemetrySpecColumn(label = "Total", value = formatBytes(storage.totalBytes))
                TelemetrySpecColumn(label = "App Cache", value = formatBytes(storage.totalAppCacheBytes))
                if (onClick != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Files",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open storage file navigator",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryHealthCard(
    systemInfo: SystemIntegrityInfo,
    severity: SeverityLevel,
    accentColor: Color,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val healthText = systemInfo.batteryHealthLabel.ifBlank { "Normal" }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag("dashboard_battery_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.medium
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
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (systemInfo.isBatteryCharging) {
                                Icons.Filled.BatteryChargingFull
                            } else {
                                Icons.Filled.BatteryStd
                            },
                            contentDescription = "Battery health",
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Battery Health",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                SeverityBadge(severity = severity, customLabel = healthText)
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${systemInfo.batteryLevelPct}%",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accentColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = if (systemInfo.isBatteryCharging) "Charging" else "On Battery",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (systemInfo.batteryLevelPct / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetrySpecColumn(label = "Condition", value = healthText)
                TelemetrySpecColumn(label = "Temp", value = "${systemInfo.batteryTempC}°C")
                TelemetrySpecColumn(label = "Voltage", value = "${systemInfo.batteryVoltageMv} mV")
            }
        }
    }
}

@Composable
private fun NetworkPostureCard(
    wifi: WifiSecurityAudit,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val netSeverity = when {
        !wifi.isConnected -> SeverityLevel.INFO
        wifi.auditScore >= 80 -> SeverityLevel.SAFE
        wifi.auditScore >= 55 -> SeverityLevel.CAUTION
        else -> SeverityLevel.ALERT
    }
    val accent = when (netSeverity) {
        SeverityLevel.SAFE -> MaterialTheme.colorScheme.secondary
        SeverityLevel.CAUTION -> MaterialTheme.colorScheme.tertiary
        SeverityLevel.ALERT -> MaterialTheme.colorScheme.error
        SeverityLevel.INFO -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag("dashboard_network_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
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
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (wifi.isConnected) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                            contentDescription = "Network posture",
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Network Audit",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                SeverityBadge(
                    severity = netSeverity,
                    customLabel = if (wifi.isConnected) "${wifi.auditScore}/100" else "Offline"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (wifi.isConnected) wifi.securityProtocol else "Disconnected",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (wifi.isConnected) {
                    "${wifi.transportLabel} • Private DNS: ${if (wifi.isPrivateDnsActive) "On" else "Off"}"
                } else {
                    "Connect to Wi-Fi or cellular to inspect security"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TelemetrySpecColumn(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HealthMetricCard(
    title: String,
    primaryValue: String,
    progress: Float,
    statusLabel: String,
    detailLine: String,
    icon: ImageVector,
    severity: SeverityLevel,
    accentColor: Color,
    testTagName: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTagName),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
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
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                SeverityBadge(severity = severity, customLabel = statusLabel)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = primaryValue,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50)),
                color = accentColor,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = detailLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
