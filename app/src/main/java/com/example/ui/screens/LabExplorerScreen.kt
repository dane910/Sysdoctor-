package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.example.model.AdvisorUiState
import com.example.model.ArchitectureTopic
import com.example.model.FeasibilityLevel
import com.example.model.SeverityLevel
import com.example.ui.components.SeverityBadge

@Composable
fun LabExplorerScreen(
    topics: List<ArchitectureTopic>,
    advisorState: AdvisorUiState,
    onSpeakTopic: (ArchitectureTopic) -> Unit,
    onAskAdvisor: (String) -> Unit,
    onClearAdvisor: () -> Unit,
    onSpeakCustomText: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedTopicId by remember { mutableStateOf<String?>(topics.firstOrNull()?.id) }
    var promptText by remember { mutableStateOf("") }

    val quickPrompts = listOf(
        "Can my phone act as a bootable USB drive without root?",
        "How does Android A/B partition recovery work?",
        "Best non-root network security audit workflow?"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Security & Recovery Lab",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Explore what Android's sandbox permits vs. ADB, Fastboot & Kernel requirements",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Architecture & Feasibility Explorer Cards
        items(topics, key = { it.id }) { topic ->
            val expanded = expandedTopicId == topic.id
            ArchitectureTopicCard(
                topic = topic,
                expanded = expanded,
                onToggleExpand = {
                    expandedTopicId = if (expanded) null else topic.id
                },
                onSpeakTopic = { onSpeakTopic(topic) }
            )
        }

        // Gemini Security & Recovery Advisor
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "AI Systems & Recovery Advisor",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Context-aware guidance using your live telemetry",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickPrompts.forEach { sample ->
                            AssistChip(
                                onClick = { promptText = sample },
                                label = { Text(sample, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("Ask about recovery, USB OTG, Wi-Fi hardening…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("advisor_input"),
                        minLines = 2,
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { onAskAdvisor(promptText) },
                            enabled = promptText.isNotBlank() && advisorState !is AdvisorUiState.Loading,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("ask_advisor_button")
                        ) {
                            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ask Advisor")
                        }
                        if (advisorState !is AdvisorUiState.Idle) {
                            TextButton(
                                onClick = onClearAdvisor,
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    AnimatedVisibility(visible = advisorState !is AdvisorUiState.Idle) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            when (advisorState) {
                                AdvisorUiState.Loading -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Analyzing device telemetry…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                is AdvisorUiState.Streaming -> {
                                    Text(
                                        text = advisorState.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                is AdvisorUiState.Success -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(
                                            text = advisorState.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        OutlinedButton(
                                            onClick = {
                                                onSpeakCustomText("Advisor Guidance", advisorState.text)
                                            },
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag("speak_advisor_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.VolumeUp,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Listen with Gemini TTS")
                                        }
                                    }
                                }
                                is AdvisorUiState.Error -> {
                                    Text(
                                        text = advisorState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                AdvisorUiState.Idle -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchitectureTopicCard(
    topic: ArchitectureTopic,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSpeakTopic: () -> Unit
) {
    val badgeSeverity = when (topic.feasibility) {
        FeasibilityLevel.STANDARD_SANDBOX -> SeverityLevel.SAFE
        FeasibilityLevel.ADB_OR_OEM -> SeverityLevel.INFO
        FeasibilityLevel.REQUIRES_ROOT_KERNEL -> SeverityLevel.CAUTION
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .testTag("topic_card_${topic.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SeverityBadge(
                        severity = badgeSeverity,
                        customLabel = topic.feasibility.label
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = topic.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse topic" else "Expand topic",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = topic.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Android Sandbox Reality",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = topic.sandboxReality,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Recommended Workflows",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    topic.actionableSteps.forEachIndexed { idx, step ->
                        Text(
                            text = "${idx + 1}. $step",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    OutlinedButton(
                        onClick = onSpeakTopic,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("speak_topic_${topic.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VolumeUp,
                            contentDescription = "Listen to guide",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Listen (gemini-3.1-flash-tts-preview)")
                    }
                }
            }
        }
    }
}
