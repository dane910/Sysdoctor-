package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Usb
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.BatteryTrendMetric
import com.example.model.BatteryTrendPoint
import com.example.model.StorageSummary
import com.example.model.SystemIntegrityInfo
import com.example.model.ToolkitCategory
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
    onOpenToolkitCategory: (ToolkitCategory) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val storageFreeRatio = 1f - storage.usedFraction
    val healthScore = computeCompositeHealthScore(storageFreeRatio, wifi, systemInfo)
    val gaugeColor = when {
        healthScore >= 80 -> MaterialTheme.colorScheme.secondary
        healthScore >= 55 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    var quickAccessExpanded by rememberSaveable { mutableStateOf(true) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("sysdoctor_entry_dashboard"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                                    text = "SYSDOCTOR DASHBOARD",
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
                                size = 100.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
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
                            Text(if (isRefreshing) "Scanning system files…" else "Scan")
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
                            TextButton(onClick = { onOpenToolkitCategory(ToolkitCategory.NETWORK_AUDIT) }) {
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

        // Collapsible Telemetry & Collapsible Graphs
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
                onOpenStorage = { onOpenToolkitCategory(ToolkitCategory.CLEANER) },
                onOpenBattery = { onOpenToolkitCategory(ToolkitCategory.DIAGNOSTICS) },
                onOpenAudit = { onOpenToolkitCategory(ToolkitCategory.NETWORK_AUDIT) }
            )
        }

        // Collapsible Quick Toolkit Shortcuts
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { quickAccessExpanded = !quickAccessExpanded }
                        .minimumInteractiveComponentSize()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .testTag("dashboard_quick_toolkit_toggle"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "QUICK TOOLKIT",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (quickAccessExpanded) "Direct Category Launch" else "Diagnostics, USB Tools, Cleaner & Audit",
                            style = if (quickAccessExpanded) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                            color = if (quickAccessExpanded) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (quickAccessExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (quickAccessExpanded) "Collapse quick toolkit" else "Expand quick toolkit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = quickAccessExpanded,
                    enter = expandVertically(animationSpec = spring()) + fadeIn(),
                    exit = shrinkVertically(animationSpec = spring()) + fadeOut()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuickCategoryTile(
                                title = "Diagnostics",
                                subtitle = "Battery, display & sensors",
                                icon = Icons.Filled.Build,
                                accent = MaterialTheme.colorScheme.secondary,
                                tag = "quick_diagnostics_card",
                                onClick = { onOpenToolkitCategory(ToolkitCategory.DIAGNOSTICS) },
                                modifier = Modifier.weight(1f)
                            )
                            QuickCategoryTile(
                                title = "USB Tools",
                                subtitle = "OTG host & boot images",
                                icon = Icons.Filled.Usb,
                                accent = MaterialTheme.colorScheme.primary,
                                tag = "quick_usb_card",
                                onClick = { onOpenToolkitCategory(ToolkitCategory.USB_TOOLS) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QuickCategoryTile(
                                title = "Cleaner",
                                subtitle = "Cache & SHA-256 dupes",
                                icon = Icons.Filled.CleaningServices,
                                accent = MaterialTheme.colorScheme.tertiary,
                                tag = "quick_cleaner_card",
                                onClick = { onOpenToolkitCategory(ToolkitCategory.CLEANER) },
                                modifier = Modifier.weight(1f)
                            )
                            QuickCategoryTile(
                                title = "System Audit",
                                subtitle = "Wi-Fi, DNS & partitions",
                                icon = Icons.Filled.Security,
                                accent = MaterialTheme.colorScheme.secondary,
                                tag = "quick_audit_card",
                                onClick = { onOpenToolkitCategory(ToolkitCategory.NETWORK_AUDIT) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickCategoryTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
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
