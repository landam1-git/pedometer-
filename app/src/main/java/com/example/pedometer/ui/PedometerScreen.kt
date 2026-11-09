package com.example.pedometer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pedometer.R
import com.example.pedometer.data.UiStepState
import java.text.NumberFormat
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PedometerScreen(
    state: UiStepState,
    hasSensor: Boolean,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onGoalChange: (Int) -> Unit,
    onStrideChange: (Int) -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = { showSettings = true }) {
                        Text(stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when {
                !hasSensor -> MessageCard(stringResource(R.string.no_sensor))
                !hasPermission -> MessageCard(
                    text = stringResource(R.string.permission_rationale),
                    action = stringResource(R.string.grant_permission),
                    onAction = onRequestPermission,
                )
            }

            ProgressRing(steps = state.todaySteps, goal = state.dailyGoal, progress = state.goalProgress)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    label = stringResource(R.string.distance),
                    value = String.format(Locale.getDefault(), "%.2f km", state.distanceKm),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.calories),
                    value = "${state.calories} kcal",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.goal),
                    value = "${(state.goalProgress * 100).toInt()}%",
                    modifier = Modifier.weight(1f),
                )
            }

            WeekChart(state)
        }
    }

    if (showSettings) {
        SettingsDialog(
            goal = state.dailyGoal,
            strideCm = state.strideLengthCm,
            onDismiss = { showSettings = false },
            onSave = { goal, stride ->
                onGoalChange(goal)
                onStrideChange(stride)
                showSettings = false
            },
        )
    }
}

@Composable
private fun ProgressRing(steps: Int, goal: Int, progress: Float) {
    val animated by animateFloatAsState(targetValue = progress.coerceIn(0f, 1f), label = "progress")
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    val format = NumberFormat.getInstance()

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(240.dp)) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val stroke = Stroke(width = 22.dp.toPx(), cap = StrokeCap.Round)
            drawArc(track, startAngle = 135f, sweepAngle = 270f, useCenter = false, style = stroke)
            drawArc(fill, startAngle = 135f, sweepAngle = 270f * animated, useCenter = false, style = stroke)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = format.format(steps),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.of_goal, format.format(goal)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WeekChart(state: UiStepState) {
    val days = state.lastDays(7)
    val maxSteps = maxOf(days.maxOf { it.second }, state.dailyGoal, 1)
    val bar = MaterialTheme.colorScheme.primary
    val barMuted = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val goalLine = MaterialTheme.colorScheme.tertiary
    val goal = state.dailyGoal

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.last_7_days), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                val slot = size.width / days.size
                val barWidth = slot * 0.55f
                days.forEachIndexed { i, (_, steps) ->
                    val h = size.height * steps / maxSteps
                    drawRoundRect(
                        color = if (steps >= goal) bar else barMuted,
                        topLeft = Offset(i * slot + (slot - barWidth) / 2, size.height - h),
                        size = Size(barWidth, h),
                        cornerRadius = CornerRadius(6.dp.toPx()),
                    )
                }
                val goalY = size.height - size.height * goal / maxSteps
                drawLine(goalLine, Offset(0f, goalY), Offset(size.width, goalY), strokeWidth = 2.dp.toPx())
            }
            Row(Modifier.fillMaxWidth()) {
                days.forEach { (date, steps) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            compact(steps),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun compact(steps: Int): String =
    if (steps >= 1000) String.format(Locale.getDefault(), "%.1fk", steps / 1000f) else steps.toString()

@Composable
private fun MessageCard(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onErrorContainer)
            if (action != null) Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SettingsDialog(
    goal: Int,
    strideCm: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
) {
    var goalText by remember { mutableStateOf(goal.toString()) }
    var strideText by remember { mutableStateOf(strideCm.toString()) }
    val goalValue = goalText.toIntOrNull()?.takeIf { it in 100..200_000 }
    val strideValue = strideText.toIntOrNull()?.takeIf { it in 20..250 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.daily_goal)) },
                    isError = goalValue == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = strideText,
                    onValueChange = { strideText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.stride_length)) },
                    isError = strideValue == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = goalValue != null && strideValue != null,
                onClick = { onSave(goalValue!!, strideValue!!) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
