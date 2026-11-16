package com.example.ui.components

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diagnostics.formatBytes
import com.example.model.SeverityLevel
import com.example.model.StorageSummary
import com.example.model.SystemIntegrityInfo
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun ResourceUtilizationGraphCard(
    storage: StorageSummary,
    systemInfo: SystemIntegrityInfo,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    val totalStorage = storage.totalBytes.coerceAtLeast(1L)
    val cacheBytes = storage.totalAppCacheBytes.coerceAtMost(storage.usedBytes)
    val appAndSystemBytes = (storage.usedBytes - cacheBytes).coerceAtLeast(0L)
    val freeStorageBytes = storage.availableBytes.coerceAtLeast(0L)

    val appStorageFraction = (appAndSystemBytes.toFloat() / totalStorage).coerceIn(0f, 1f)
    val cacheStorageFraction = (cacheBytes.toFloat() / totalStorage).coerceIn(0f, 1f - appStorageFraction)
    val freeStorageFraction = (1f - appStorageFraction - cacheStorageFraction).coerceIn(0f, 1f)

    val ramUsedFraction = systemInfo.ramUsedFraction.coerceIn(0f, 1f)
    val ramFreeFraction = (1f - ramUsedFraction).coerceIn(0f, 1f)

    val storagePct = (storage.usedFraction * 100).toInt()
    val ramPct = (ramUsedFraction * 100).toInt()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("resource_graph_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { expanded = !expanded }
                    .minimumInteractiveComponentSize()
                    .padding(horizontal = 2.dp, vertical = 2.dp)
                    .testTag("resource_graph_toggle"),
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BarChart,
                            contentDescription = "Storage and memory utilization graph",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Storage & Memory Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (expanded) {
                                "Live volume & RAM allocation ratios"
                            } else {
                                "Storage $storagePct% • RAM $ramPct% used • Tap to expand"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                SeverityBadge(
                    severity = if (storagePct >= 85 || ramPct >= 85) SeverityLevel.CAUTION else SeverityLevel.SAFE,
                    customLabel = "$storagePct% / $ramPct%"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse resource graph" else "Expand resource graph",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = spring()) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring()) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .testTag("resource_graph_body"),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Storage Segmented Bar Graph
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Internal Storage (${formatBytes(storage.totalBytes)})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$storagePct% allocated",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        SegmentedDistributionBar(
                            segments = listOf(
                                appStorageFraction to MaterialTheme.colorScheme.primary,
                                cacheStorageFraction to MaterialTheme.colorScheme.tertiary,
                                freeStorageFraction to MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            GraphLegendItem(
                                color = MaterialTheme.colorScheme.primary,
                                label = "Data",
                                value = formatBytes(appAndSystemBytes)
                            )
                            GraphLegendItem(
                                color = MaterialTheme.colorScheme.tertiary,
                                label = "Cache",
                                value = formatBytes(cacheBytes)
                            )
                            GraphLegendItem(
                                color = MaterialTheme.colorScheme.secondary,
                                label = "Free",
                                value = formatBytes(freeStorageBytes)
                            )
                        }
                    }

                    // RAM Segmented Bar Graph
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "System Memory (${formatBytes(systemInfo.totalRamBytes)})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$ramPct% active",
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        SegmentedDistributionBar(
                            segments = listOf(
                                ramUsedFraction to MaterialTheme.colorScheme.secondary,
                                ramFreeFraction to MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            GraphLegendItem(
                                color = MaterialTheme.colorScheme.secondary,
                                label = "Active RAM",
                                value = formatBytes(systemInfo.usedRamBytes)
                            )
                            GraphLegendItem(
                                color = MaterialTheme.colorScheme.primary,
                                label = "Available RAM",
                                value = formatBytes(systemInfo.availableRamBytes)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedDistributionBar(
    segments: List<Pair<Float, Color>>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        segments.forEach { (fraction, color) ->
            if (fraction > 0.005f) {
                Box(
                    modifier = Modifier
                        .weight(fraction)
                        .fillMaxHeight()
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun GraphLegendItem(
    color: Color,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
