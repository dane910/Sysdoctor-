package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BatteryTrendMetric
import com.example.model.BatteryTrendPoint
import com.example.model.SeverityLevel
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.compose.common.shader.verticalGradient
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shader.ShaderProvider
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.util.Locale

@Composable
fun BatteryTrendChartCard(
    trendPoints: List<BatteryTrendPoint>,
    selectedMetric: BatteryTrendMetric,
    onSelectMetric: (BatteryTrendMetric) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val recordedPoints = remember(trendPoints) { trendPoints.filter { it.hasSample } }
    val recordedCount = recordedPoints.size
    val latestScore = recordedPoints.lastOrNull()?.healthScore

    val lineAccent: Color = when (selectedMetric) {
        BatteryTrendMetric.HEALTH_SCORE -> MaterialTheme.colorScheme.secondary
        BatteryTrendMetric.TEMPERATURE -> MaterialTheme.colorScheme.tertiary
        BatteryTrendMetric.CHARGE_LEVEL -> MaterialTheme.colorScheme.primary
        BatteryTrendMetric.VOLTAGE -> MaterialTheme.colorScheme.primary
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    val dayLabelsKey = remember { ExtraStore.Key<List<String>>() }

    LaunchedEffect(trendPoints, selectedMetric) {
        if (recordedPoints.isEmpty()) return@LaunchedEffect
        val labels = trendPoints.map { it.dayLabel }
        val xVals = recordedPoints.map { it.dayIndex.toDouble() }
        val yVals = recordedPoints.map { point ->
            when (selectedMetric) {
                BatteryTrendMetric.HEALTH_SCORE -> point.healthScore.toDouble()
                BatteryTrendMetric.TEMPERATURE -> point.avgTempC.toDouble()
                BatteryTrendMetric.CHARGE_LEVEL -> point.levelPct.toDouble()
                BatteryTrendMetric.VOLTAGE -> (point.voltageMv / 1000.0)
            }
        }
        modelProducer.runTransaction {
            lineSeries {
                series(x = xVals, y = yVals)
            }
            extras { store ->
                store[dayLabelsKey] = labels
            }
        }
    }

    val (minY, maxY) = when (selectedMetric) {
        BatteryTrendMetric.HEALTH_SCORE -> 0.0 to 100.0
        BatteryTrendMetric.TEMPERATURE -> 15.0 to 50.0
        BatteryTrendMetric.CHARGE_LEVEL -> 0.0 to 100.0
        BatteryTrendMetric.VOLTAGE -> 3.0 to 4.6
    }

    val rangeProvider = remember(minY, maxY) {
        CartesianLayerRangeProvider.fixed(
            minX = 0.0,
            maxX = 6.0,
            minY = minY,
            maxY = maxY
        )
    }

    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val axisLabelComponent = rememberTextComponent(
        color = axisLabelColor
    )

    val bottomFormatter = remember(dayLabelsKey) {
        CartesianValueFormatter { context, x, _ ->
            val labels = context.model.extraStore.getOrNull(dayLabelsKey)
            labels?.getOrNull(x.toInt()) ?: ""
        }
    }

    val startFormatter = remember(selectedMetric) {
        CartesianValueFormatter { _, y, _ ->
            when (selectedMetric) {
                BatteryTrendMetric.HEALTH_SCORE -> "${y.toInt()}"
                BatteryTrendMetric.TEMPERATURE -> "${y.toInt()}°"
                BatteryTrendMetric.CHARGE_LEVEL -> "${y.toInt()}%"
                BatteryTrendMetric.VOLTAGE -> String.format(Locale.US, "%.1fV", y)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("battery_trend_chart_card"),
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
                    .testTag("battery_trend_toggle"),
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
                            .background(lineAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = "7-day battery health trend",
                            tint = lineAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "7-Day Battery Trend",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (expanded) {
                                "On-device Vico telemetry history"
                            } else {
                                latestScore?.let { "Latest score: $it/100 • Tap to expand" }
                                    ?: "Tap to view 7-day trend chart"
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
                    severity = if (recordedCount > 0) SeverityLevel.SAFE else SeverityLevel.INFO,
                    customLabel = "$recordedCount/7d"
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse 7-day battery trend" else "Expand 7-day battery trend",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BatteryTrendMetric.entries.forEach { metric ->
                    FilterChip(
                        selected = metric == selectedMetric,
                        onClick = { onSelectMetric(metric) },
                        label = {
                            Text(
                                text = metric.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = lineAccent.copy(alpha = 0.2f),
                            selectedLabelColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag("battery_metric_chip_${metric.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (recordedPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Collecting initial battery snapshot…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                val pointComponent = rememberShapeComponent(
                    fill = fill(lineAccent),
                    shape = CorneredShape.Pill
                )
                val areaGradient = ShaderProvider.verticalGradient(
                    arrayOf(lineAccent.copy(alpha = 0.35f), Color.Transparent)
                )

                CartesianChartHost(
                    chart = rememberCartesianChart(
                        rememberLineCartesianLayer(
                            lineProvider = LineCartesianLayer.LineProvider.series(
                                LineCartesianLayer.rememberLine(
                                    fill = LineCartesianLayer.LineFill.single(fill(lineAccent)),
                                    areaFill = LineCartesianLayer.AreaFill.single(fill(areaGradient)),
                                    pointProvider = LineCartesianLayer.PointProvider.single(
                                        LineCartesianLayer.point(
                                            component = pointComponent,
                                            size = 8.dp
                                        )
                                    )
                                )
                            ),
                            rangeProvider = rangeProvider
                        ),
                        startAxis = VerticalAxis.rememberStart(
                            label = axisLabelComponent,
                            valueFormatter = startFormatter
                        ),
                        bottomAxis = HorizontalAxis.rememberBottom(
                            label = axisLabelComponent,
                            valueFormatter = bottomFormatter
                        )
                    ),
                    modelProducer = modelProducer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("vico_battery_trend_chart")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            val latest = recordedPoints.lastOrNull()
            val sevenDayAvgTemp = if (recordedPoints.isNotEmpty()) {
                recordedPoints.map { it.avgTempC }.average().toFloat()
            } else {
                0f
            }
            val sevenDayPeakTemp = recordedPoints.maxOfOrNull { it.maxTempC } ?: 0f

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TrendSummaryStat(
                    label = "Latest Score",
                    value = latest?.let { "${it.healthScore}/100" } ?: "—"
                )
                TrendSummaryStat(
                    label = "7d Avg Temp",
                    value = if (recordedPoints.isNotEmpty()) {
                        String.format(Locale.US, "%.1f°C", sevenDayAvgTemp)
                    } else "—"
                )
                TrendSummaryStat(
                    label = "7d Peak Temp",
                    value = if (recordedPoints.isNotEmpty()) {
                        String.format(Locale.US, "%.1f°C", sevenDayPeakTemp)
                    } else "—"
                )
            }

            if (recordedCount < 7) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$recordedCount of 7 days logged locally. Real daily averages accumulate automatically whenever diagnostics run.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
                }
            }
        }
    }
}

@Composable
private fun TrendSummaryStat(label: String, value: String) {
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
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
