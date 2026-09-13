package com.quietrays.tonarc.presentation.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quietrays.tonarc.R
import com.quietrays.tonarc.presentation.viewmodel.AbRepeatState
import com.quietrays.tonarc.utils.formatDuration
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val SPEED_PRESETS = listOf(0.5f, 0.75f, 0.9f, 1.0f, 1.1f, 1.25f, 1.5f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioToolsBottomSheet(
    abRepeatState: AbRepeatState,
    playbackSpeed: Float,
    playbackPitchSemitones: Int,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSetPointA: (Long) -> Unit,
    onSetPointB: (Long) -> Unit,
    onAdjustPointA: (Long) -> Unit,
    onAdjustPointB: (Long) -> Unit,
    onToggleLoop: () -> Unit,
    onClearLoop: () -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onPlaybackPitchChange: (Int) -> Unit,
    onResetAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = AbsoluteSmoothCornerShape(28.dp, 60),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.audio_tools_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.audio_tools_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = onResetAll) {
                        Text(
                            text = stringResource(R.string.audio_tools_reset_all),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Segmented Tab Selector
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(stringResource(R.string.audio_tools_tab_speed_pitch))
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(stringResource(R.string.audio_tools_tab_ab_repeat))
                        if (abRepeatState.isEnabled && abRepeatState.isValidLoop) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            when (selectedTab) {
                0 -> {
                    SpeedAndPitchSection(
                        playbackSpeed = playbackSpeed,
                        playbackPitchSemitones = playbackPitchSemitones,
                        onPlaybackSpeedChange = onPlaybackSpeedChange,
                        onPlaybackPitchChange = onPlaybackPitchChange
                    )
                }
                1 -> {
                    AbRepeatSection(
                        abRepeatState = abRepeatState,
                        currentPositionMs = currentPositionMs,
                        totalDurationMs = totalDurationMs,
                        onSetPointA = onSetPointA,
                        onSetPointB = onSetPointB,
                        onAdjustPointA = onAdjustPointA,
                        onAdjustPointB = onAdjustPointB,
                        onToggleLoop = onToggleLoop,
                        onClearLoop = onClearLoop
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedAndPitchSection(
    playbackSpeed: Float,
    playbackPitchSemitones: Int,
    onPlaybackSpeedChange: (Float) -> Unit,
    onPlaybackPitchChange: (Int) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Playback Speed Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.audio_tools_speed_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.2fx", playbackSpeed),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Preset chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(SPEED_PRESETS) { preset ->
                        val isSelected = abs(playbackSpeed - preset) < 0.02f
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPlaybackSpeedChange(preset) }
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.2fx", preset),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Precision Slider
                Slider(
                    value = playbackSpeed,
                    onValueChange = { onPlaybackSpeedChange((it * 20f).roundToInt() / 20f) },
                    valueRange = 0.25f..2.50f,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "0.25x",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.audio_tools_speed_normal) + " (1.0x)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "2.50x",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Pitch Shifter Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.audio_tools_pitch_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (playbackPitchSemitones != 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        Text(
                            text = if (playbackPitchSemitones == 0) {
                                stringResource(R.string.audio_tools_pitch_original)
                            } else {
                                stringResource(R.string.audio_tools_pitch_semitones, playbackPitchSemitones)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (playbackPitchSemitones != 0) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Stepper Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onPlaybackPitchChange(playbackPitchSemitones - 1) },
                        enabled = playbackPitchSemitones > -12,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.audio_tools_pitch_minus))
                    }

                    FilledTonalButton(
                        onClick = { onPlaybackPitchChange(0) },
                        enabled = playbackPitchSemitones != 0,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.audio_tools_reset))
                    }

                    OutlinedButton(
                        onClick = { onPlaybackPitchChange(playbackPitchSemitones + 1) },
                        enabled = playbackPitchSemitones < 12,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.audio_tools_pitch_plus))
                    }
                }

                // Pitch Slider
                Slider(
                    value = playbackPitchSemitones.toFloat(),
                    onValueChange = { onPlaybackPitchChange(it.roundToInt()) },
                    valueRange = -12f..12f,
                    steps = 23,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "-12 st",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "0 st",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "+12 st",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AbRepeatSection(
    abRepeatState: AbRepeatState,
    currentPositionMs: Long,
    totalDurationMs: Long,
    onSetPointA: (Long) -> Unit,
    onSetPointB: (Long) -> Unit,
    onAdjustPointA: (Long) -> Unit,
    onAdjustPointB: (Long) -> Unit,
    onToggleLoop: () -> Unit,
    onClearLoop: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (abRepeatState.isLoopActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.audio_tools_ab_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (abRepeatState.isLoopActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (abRepeatState.isLoopActive) {
                            stringResource(R.string.audio_tools_ab_loop_active)
                        } else {
                            stringResource(R.string.audio_tools_ab_desc)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (abRepeatState.isLoopActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (abRepeatState.isValidLoop) {
                    Switch(
                        checked = abRepeatState.isEnabled,
                        onCheckedChange = { onToggleLoop() }
                    )
                }
            }
        }

        // Mini Timeline Preview
        if (totalDurationMs > 0L) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val tertiaryColor = MaterialTheme.colorScheme.tertiary
                    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    ) {
                        val trackHeight = 8.dp.toPx()
                        val centerY = size.height / 2f
                        val trackWidth = size.width

                        // Base track
                        drawRoundRect(
                            color = surfaceVariant,
                            topLeft = Offset(0f, centerY - trackHeight / 2f),
                            size = Size(trackWidth, trackHeight),
                            cornerRadius = CornerRadius(trackHeight / 2f)
                        )

                        val fractionA = abRepeatState.pointA?.let { (it.toFloat() / totalDurationMs).coerceIn(0f, 1f) }
                        val fractionB = abRepeatState.pointB?.let { (it.toFloat() / totalDurationMs).coerceIn(0f, 1f) }

                        // Loop range band
                        if (fractionA != null && fractionB != null && fractionB > fractionA) {
                            val startX = fractionA * trackWidth
                            val endX = fractionB * trackWidth
                            drawRoundRect(
                                color = if (abRepeatState.isEnabled) primaryColor.copy(alpha = 0.5f) else primaryColor.copy(alpha = 0.2f),
                                topLeft = Offset(startX, centerY - trackHeight / 2f),
                                size = Size(endX - startX, trackHeight),
                                cornerRadius = CornerRadius(trackHeight / 2f)
                            )
                        }

                        // Marker A
                        fractionA?.let { fA ->
                            val xPos = fA * trackWidth
                            drawLine(
                                color = primaryColor,
                                start = Offset(xPos, centerY - 10.dp.toPx()),
                                end = Offset(xPos, centerY + 10.dp.toPx()),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Marker B
                        fractionB?.let { fB ->
                            val xPos = fB * trackWidth
                            drawLine(
                                color = tertiaryColor,
                                start = Offset(xPos, centerY - 10.dp.toPx()),
                                end = Offset(xPos, centerY + 10.dp.toPx()),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Playhead indicator
                        val playheadFraction = (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                        drawCircle(
                            color = primaryColor,
                            radius = 4.dp.toPx(),
                            center = Offset(playheadFraction * trackWidth, centerY)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "A: " + (abRepeatState.pointA?.let { formatDuration(it) } ?: "--:--"),
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Now: " + formatDuration(currentPositionMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "B: " + (abRepeatState.pointB?.let { formatDuration(it) } ?: "--:--"),
                            style = MaterialTheme.typography.labelSmall,
                            color = tertiaryColor,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Point A Marker Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.audio_tools_ab_point_a),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = abRepeatState.pointA?.let { formatDuration(it) } ?: "--:--",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onAdjustPointA(-1000L) },
                        enabled = abRepeatState.pointA != null,
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(stringResource(R.string.audio_tools_ab_fine_tune_minus))
                    }

                    FilledTonalButton(
                        onClick = { onAdjustPointA(1000L) },
                        enabled = abRepeatState.pointA != null,
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(stringResource(R.string.audio_tools_ab_fine_tune_plus))
                    }

                    Button(
                        onClick = { onSetPointA(currentPositionMs) }
                    ) {
                        Text(stringResource(R.string.audio_tools_ab_set_a))
                    }
                }
            }
        }

        // Point B Marker Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.audio_tools_ab_point_b),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = abRepeatState.pointB?.let { formatDuration(it) } ?: "--:--",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onAdjustPointB(-1000L) },
                        enabled = abRepeatState.pointB != null,
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(stringResource(R.string.audio_tools_ab_fine_tune_minus))
                    }

                    FilledTonalButton(
                        onClick = { onAdjustPointB(1000L) },
                        enabled = abRepeatState.pointB != null,
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(stringResource(R.string.audio_tools_ab_fine_tune_plus))
                    }

                    Button(
                        onClick = { onSetPointB(currentPositionMs) }
                    ) {
                        Text(stringResource(R.string.audio_tools_ab_set_b))
                    }
                }
            }
        }

        // Action Buttons Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onClearLoop,
                enabled = abRepeatState.pointA != null || abRepeatState.pointB != null,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.audio_tools_ab_clear))
            }

            Button(
                onClick = onToggleLoop,
                enabled = abRepeatState.isValidLoop,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Repeat,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (abRepeatState.isEnabled) stringResource(R.string.audio_tools_ab_disable)
                    else stringResource(R.string.audio_tools_ab_enable)
                )
            }
        }
    }
}
