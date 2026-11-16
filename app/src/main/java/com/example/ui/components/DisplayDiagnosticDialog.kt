package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.HardwareTestStatus

@Composable
fun DisplayDiagnosticDialog(
    onDismiss: () -> Unit,
    onResult: (HardwareTestStatus) -> Unit
) {
    val stages = listOf(
        "Touch Grid Test" to Color(0xFF0B1220),
        "Pure Red (Subpixel Check)" to Color(0xFFFF0000),
        "Pure Green (Subpixel Check)" to Color(0xFF00FF00),
        "Pure Blue (Subpixel Check)" to Color(0xFF0000FF),
        "Pure White (Uniformity)" to Color(0xFFFFFFFF),
        "Pure Black (Backlight Bleed)" to Color(0xFF000000),
        "Spectrum Gradient (Banding)" to Color(0xFF00E5FF)
    )
    var stageIndex by remember { mutableIntStateOf(0) }
    var touchedCells by remember { mutableStateOf(setOf<Pair<Int, Int>>()) }

    val cols = 6
    val rows = 10
    val totalCells = cols * rows
    val stageName = stages[stageIndex].first
    val stageColor = stages[stageIndex].second
    val isDarkOverlay = stageIndex == 0 || stageIndex == 3 || stageIndex == 5

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (stageIndex == 6) {
                        Modifier.background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFFFF0000),
                                    Color(0xFFFFFF00),
                                    Color(0xFF00FF00),
                                    Color(0xFF00FFFF),
                                    Color(0xFF0000FF),
                                    Color(0xFFFF00FF)
                                )
                            )
                        )
                    } else {
                        Modifier.background(stageColor)
                    }
                )
                .then(
                    if (stageIndex > 0) {
                        Modifier.clickable { stageIndex = (stageIndex + 1) % stages.size }
                    } else {
                        Modifier
                    }
                )
        ) {
            if (stageIndex == 0) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                val c = ((pos.x / size.width) * cols).toInt().coerceIn(0, cols - 1)
                                val r = ((pos.y / size.height) * rows).toInt().coerceIn(0, rows - 1)
                                touchedCells = touchedCells + (c to r)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                val pos = change.position
                                val c = ((pos.x / size.width) * cols).toInt().coerceIn(0, cols - 1)
                                val r = ((pos.y / size.height) * rows).toInt().coerceIn(0, rows - 1)
                                touchedCells = touchedCells + (c to r)
                            }
                        }
                ) {
                    val cellW = size.width / cols
                    val cellH = size.height / rows
                    for (r in 0 until rows) {
                        for (c in 0 until cols) {
                            val topLeft = Offset(c * cellW, r * cellH)
                            val cellSize = Size(cellW, cellH)
                            if ((c to r) in touchedCells) {
                                drawRect(
                                    color = Color(0xFF10B981).copy(alpha = 0.45f),
                                    topLeft = topLeft,
                                    size = cellSize
                                )
                            }
                            drawRect(
                                color = Color(0xFF26354F),
                                topLeft = topLeft,
                                size = cellSize,
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stageName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (stageIndex == 0) {
                            "Drag across grid cells to verify digitizer (${touchedCells.size}/$totalCells zones hit)"
                        } else {
                            "Tap screen to cycle color pattern (${stageIndex + 1}/${stages.size})"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { stageIndex = (stageIndex + 1) % stages.size },
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("display_next_pattern_button")
                        ) {
                            Text("Next Pattern")
                        }
                        Button(
                            onClick = { onResult(HardwareTestStatus.PASSED) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("display_pass_button")
                        ) {
                            Text("Pass")
                        }
                        OutlinedButton(
                            onClick = { onResult(HardwareTestStatus.FAILED) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("display_fail_button")
                        ) {
                            Text("Issue", color = if (isDarkOverlay) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
