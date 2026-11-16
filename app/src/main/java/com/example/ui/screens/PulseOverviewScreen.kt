package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BatteryTrendMetric
import com.example.model.BatteryTrendPoint
import com.example.model.StorageSummary
import com.example.model.SystemIntegrityInfo
import com.example.model.WifiSecurityAudit
import com.example.ui.components.DiagnosticArcGauge

@Composable
fun PulseOverviewScreen(
    storage: StorageSummary,
    wifi: WifiSecurityAudit,
    systemInfo: SystemIntegrityInfo,
    batteryTrend7Days: List<BatteryTrendPoint> = emptyList(),
    selectedBatteryMetric: BatteryTrendMetric = BatteryTrendMetric.HEALTH_SCORE,
    onSelectBatteryMetric: (BatteryTrendMetric) -> Unit = {},
    isRefreshing: Boolean,
    auditBanner: String? = null,
    onDismissAuditBanner: () -> Unit = {},
    onRefreshAll: () -> Unit,
    onNavigateToCleaner: () -> Unit,
    onNavigateToToolkit: () -> Unit = {},
    onNavigateToAudit: () -> Unit,
    onNavigateToLab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val storageFreeRatio = 1f - storage.usedFraction
    val healthScore = computeCompositeHealthScore(storageFreeRatio, wifi, systemInfo)
    val gaugeColor = when {
        healthScore >= 80 -> MaterialTheme.colorScheme.secondary
        healthScore >= 55 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("sysdoctor_entry_dashboard"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // SysDoctor Hero Telemetry Card
        item {
            Card(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_hero_telemetry),
                        contentDescription = "SysDoctor telemetry background",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .clip(MaterialTheme.shapes.large)
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xEB0B1220),
                                        Color(0xB80B1220)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SYSDOCTOR DIAGNOSTICS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${systemInfo.manufacturer} ${systemInfo.model}".trim()
                                        .ifBlank { "Android Device" },
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Android ${systemInfo.androidVersion} • Patch ${systemInfo.securityPatch}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFCBD5E1)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFF172338).copy(alpha = 0.9f),
                                        border = BorderStroke(1.dp, Color(0xFF26354F))
                                    ) {
                                        Text(
                                            text = if (systemInfo.isTestKeys) "test-keys" else "release-keys",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (systemInfo.isTestKeys) Color(0xFFF59E0B) else Color(0xFF10B981),
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = if (wifi.isConnected) {
                                            "Net: ${wifi.transportLabel}"
                                        } else {
                                            "Net: Offline"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            DiagnosticArcGauge(
                                score = healthScore,
                                label = "INDEX",
                                accentColor = gaugeColor,
                                size = 104.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRefreshAll,
                            enabled = !isRefreshing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("run_diagnostics_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isRefreshing) "Scanning…" else "Run Audit")
                        }
                    }
                }
            }
        }

        // Audit Completed Feedback Banner
        item {
            AnimatedVisibility(visible = auditBanner != null) {
                Card(
                    modifier = Modifier.testTag("audit_complete_banner"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = auditBanner.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onNavigateToAudit) {
                                Text("Full Audit")
                            }
                            TextButton(onClick = onDismissAuditBanner) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
            }
        }

        // Summarized Device Diagnostic Dashboard (Storage Status, Battery Health, 7-Day Trend, CPU, RAM, Network)
        item {
            DiagnosticsDashboard(
                systemInfo = systemInfo,
                storage = storage,
                wifi = wifi,
                batteryTrend7Days = batteryTrend7Days,
                selectedBatteryMetric = selectedBatteryMetric,
                onSelectBatteryMetric = onSelectBatteryMetric,
                isRefreshing = isRefreshing,
                onRefresh = onRefreshAll,
                onOpenStorage = onNavigateToCleaner,
                onOpenAudit = onNavigateToAudit
            )
        }

        // Quick Action Suite
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "TOOLKIT MODULES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ToolkitActionRow(
                    title = "Advanced Cleaner & Duplicate Finder",
                    description = "Deep-clean system/app cache, old log traces, residual app data & SHA-256 duplicates.",
                    icon = Icons.Filled.CleaningServices,
                    accent = MaterialTheme.colorScheme.primary,
                    tag = "quick_cleaner_card",
                    onClick = onNavigateToCleaner
                )
                ToolkitActionRow(
                    title = "Hardware Diagnostics, Recovery & Partitions",
                    description = "Test battery, display & live sensors, recover soft-deleted files, and inspect storage volumes.",
                    icon = Icons.Filled.Build,
                    accent = MaterialTheme.colorScheme.secondary,
                    tag = "quick_toolkit_card",
                    onClick = onNavigateToToolkit
                )
                ToolkitActionRow(
                    title = "Wi-Fi Security, Partition & USB OTG Audit",
                    description = "Inspect WPA3/DNS/VPN posture, read-only system mounts, and attached USB drives.",
                    icon = Icons.Filled.Security,
                    accent = MaterialTheme.colorScheme.secondary,
                    tag = "quick_audit_card",
                    onClick = onNavigateToAudit
                )
                ToolkitActionRow(
                    title = "Recovery, Bootable USB & Sandbox Lab",
                    description = "Explore feasibility of ConfigFS bootable USB, A/B recovery, rooting trade-offs & Gemini Advisor.",
                    icon = Icons.Filled.Science,
                    accent = MaterialTheme.colorScheme.tertiary,
                    tag = "quick_lab_card",
                    onClick = onNavigateToLab
                )
            }
        }
    }
}

@Composable
private fun ToolkitActionRow(
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open module",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun computeCompositeHealthScore(
    storageFreeRatio: Float,
    wifi: WifiSecurityAudit,
    systemInfo: SystemIntegrityInfo
): Int {
    var score = 100
    if (storageFreeRatio < 0.10f) score -= 25
    else if (storageFreeRatio < 0.20f) score -= 10

    if (wifi.isConnected) {
        val netPenalty = ((100 - wifi.auditScore) * 0.45f).toInt()
        score -= netPenalty
    }
    if (systemInfo.isTestKeys) score -= 15
    if (systemInfo.batteryTempC >= 42f) score -= 10
    return score.coerceIn(10, 100)
}
