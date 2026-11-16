package com.example.ui.components.toolkit

import android.hardware.Sensor
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.BatteryHardwareSnapshot
import com.example.model.DisplayMetricsSnapshot
import com.example.model.HardwareTestStatus
import com.example.model.LiveSensorReading
import com.example.model.SensorDescriptor
import com.example.model.SeverityLevel
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale

fun LazyListScope.hardwareDiagnosticsItems(
    batteryHardware: BatteryHardwareSnapshot,
    displayMetrics: DisplayMetricsSnapshot,
    sensors: List<SensorDescriptor>,
    activeSensorType: Int?,
    activeSensorReading: LiveSensorReading?,
    onRunBatteryTest: () -> Unit,
    onLaunchDisplayTest: () -> Unit,
    onStartSensorTest: (Int) -> Unit,
    onStopSensorTest: () -> Unit
) {
    item(key = "diag_battery") {
        BatteryDiagnosticCard(
            snapshot = batteryHardware,
            onRunTest = onRunBatteryTest
        )
    }
    item(key = "diag_display") {
        DisplayDiagnosticCard(
            metrics = displayMetrics,
            onLaunchTest = onLaunchDisplayTest
        )
    }
    item(key = "diag_sensors") {
        LiveSensorSuiteCard(
            sensors = sensors,
            activeSensorType = activeSensorType,
            activeReading = activeSensorReading,
            onStartSensor = onStartSensorTest,
            onStopSensor = onStopSensorTest
        )
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
