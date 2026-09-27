package com.quietrays.tonarc.presentation.visualizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AudioVisualizerView(
    mode: VisualizerMode,
    style: VisualizerStyle,
    isPlaying: Boolean,
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer
) {
    val frameData by rememberVisualizerFrame(
        isPlaying = isPlaying,
        currentPositionMs = currentPositionMs
    )

    val primaryColor = when (style) {
        VisualizerStyle.ACCENT -> accentColor
        VisualizerStyle.GRADIENT -> Color.Unspecified // Computed per mode
        VisualizerStyle.MONOCHROME -> Color.White
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (mode) {
                VisualizerMode.SPECTRUM_BARS -> drawSpectrumBars(frameData, style, primaryColor, containerColor)
                VisualizerMode.FLUID_WAVE -> drawFluidWave(frameData, style, primaryColor, containerColor)
                VisualizerMode.CIRCULAR_PULSE -> drawCircularPulse(frameData, style, primaryColor, containerColor)
                VisualizerMode.VINYL_TURNTABLE -> drawVinylTurntable(frameData, style, primaryColor, containerColor)
            }
        }
    }
}

private fun DrawScope.drawSpectrumBars(
    frame: VisualizerFrameData,
    style: VisualizerStyle,
    baseColor: Color,
    containerColor: Color
) {
    val bands = frame.frequencyBands
    val bandCount = bands.size
    val totalWidth = size.width
    val maxHeight = size.height
    val spacing = 3f
    val barWidth = (totalWidth - (bandCount - 1) * spacing) / bandCount

    for (i in 0 until bandCount) {
        val magnitude = bands[i].coerceIn(0.04f, 1f)
        val barHeight = maxHeight * magnitude
        val left = i * (barWidth + spacing)
        val top = maxHeight - barHeight

        val brush = when (style) {
            VisualizerStyle.GRADIENT -> {
                val fraction = i.toFloat() / bandCount
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF00E5FF),
                        Color(0xFF7C4DFF),
                        Color(0xFFFF4081)
                    ),
                    startY = top,
                    endY = maxHeight
                )
            }
            VisualizerStyle.MONOCHROME -> {
                Brush.verticalGradient(
                    colors = listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.4f)),
                    startY = top,
                    endY = maxHeight
                )
            }
            VisualizerStyle.ACCENT -> {
                Brush.verticalGradient(
                    colors = listOf(baseColor, baseColor.copy(alpha = 0.4f)),
                    startY = top,
                    endY = maxHeight
                )
            }
        }

        drawRoundRect(
            brush = brush,
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
        )

        // Peak highlight cap
        val capHeight = 3f
        drawRoundRect(
            color = if (style == VisualizerStyle.MONOCHROME) Color.White else baseColor.copy(alpha = 0.85f),
            topLeft = Offset(left, (top - capHeight - 2f).coerceAtLeast(0f)),
            size = Size(barWidth, capHeight),
            cornerRadius = CornerRadius(capHeight / 2f, capHeight / 2f)
        )
    }
}

private fun DrawScope.drawFluidWave(
    frame: VisualizerFrameData,
    style: VisualizerStyle,
    baseColor: Color,
    containerColor: Color
) {
    val wave = frame.wavePoints
    val pointCount = wave.size
    val width = size.width
    val height = size.height
    val centerY = height * 0.7f

    val brush = when (style) {
        VisualizerStyle.GRADIENT -> Brush.horizontalGradient(
            colors = listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF), Color(0xFFFF5252))
        )
        VisualizerStyle.MONOCHROME -> Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.7f), Color.Transparent),
            startY = 0f,
            endY = height
        )
        VisualizerStyle.ACCENT -> Brush.verticalGradient(
            colors = listOf(baseColor.copy(alpha = 0.6f), Color.Transparent),
            startY = 0f,
            endY = height
        )
    }

    // Layer 1 - Primary fluid wave
    val path = Path()
    path.moveTo(0f, centerY)

    val stepX = width / (pointCount - 1)
    for (i in 0 until pointCount) {
        val x = i * stepX
        val y = centerY + wave[i] * (height * 0.35f)
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            val prevX = (i - 1) * stepX
            val prevY = centerY + wave[i - 1] * (height * 0.35f)
            val cX = (prevX + x) / 2f
            path.cubicTo(cX, prevY, cX, y, x, y)
        }
    }

    path.lineTo(width, height)
    path.lineTo(0f, height)
    path.close()

    drawPath(path = path, brush = brush, style = Fill)

    // Layer 2 - Wave outline highlight
    val outlinePath = Path()
    for (i in 0 until pointCount) {
        val x = i * stepX
        val y = centerY + wave[i] * (height * 0.35f)
        if (i == 0) outlinePath.moveTo(x, y) else outlinePath.lineTo(x, y)
    }
    drawPath(
        path = outlinePath,
        color = if (style == VisualizerStyle.MONOCHROME) Color.White else baseColor,
        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawCircularPulse(
    frame: VisualizerFrameData,
    style: VisualizerStyle,
    baseColor: Color,
    containerColor: Color
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = minOf(size.width, size.height) / 2f
    val baseRadius = maxRadius * 0.65f
    val bands = frame.frequencyBands
    val bass = frame.bassEnergy

    // Outer concentric pulse rings
    val ringCount = 4
    for (r in 1..ringCount) {
        val ringProgress = (r / ringCount.toFloat())
        val ringRadius = baseRadius + (maxRadius - baseRadius) * ringProgress * (0.8f + bass * 0.4f)
        val alpha = (1f - ringProgress) * (0.25f + bass * 0.35f)

        drawCircle(
            color = if (style == VisualizerStyle.MONOCHROME) Color.White.copy(alpha = alpha) else baseColor.copy(alpha = alpha),
            radius = ringRadius.coerceAtMost(maxRadius),
            center = center,
            style = Stroke(width = 2.5f)
        )
    }

    // Radial frequency spikes
    val spikeCount = 36
    val angleStep = (2.0 * PI / spikeCount).toFloat()
    for (i in 0 until spikeCount) {
        val bandIdx = (i % bands.size)
        val spikeHeight = bands[bandIdx] * (maxRadius - baseRadius) * 0.85f
        val angle = i * angleStep

        val startX = center.x + cos(angle) * baseRadius
        val startY = center.y + sin(angle) * baseRadius
        val endX = center.x + cos(angle) * (baseRadius + spikeHeight)
        val endY = center.y + sin(angle) * (baseRadius + spikeHeight)

        val spikeColor = when (style) {
            VisualizerStyle.GRADIENT -> Color.hsv((i * 360f / spikeCount), 0.8f, 1f)
            VisualizerStyle.MONOCHROME -> Color.White.copy(alpha = 0.8f)
            VisualizerStyle.ACCENT -> baseColor.copy(alpha = 0.75f)
        }

        drawLine(
            color = spikeColor,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = 3.5f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawVinylTurntable(
    frame: VisualizerFrameData,
    style: VisualizerStyle,
    baseColor: Color,
    containerColor: Color
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val vinylRadius = minOf(size.width, size.height) * 0.46f

    rotate(degrees = frame.rotationAngle, pivot = center) {
        // Vinyl Outer Edge / Disc Base
        drawCircle(
            color = Color(0xFF141414),
            radius = vinylRadius,
            center = center,
            style = Fill
        )

        // Vinyl Grooves (Subtle concentric tracks)
        val grooveStep = 7f
        var currentR = vinylRadius * 0.38f
        while (currentR < vinylRadius * 0.94f) {
            drawCircle(
                color = Color(0xFF222222),
                radius = currentR,
                center = center,
                style = Stroke(width = 1.2f)
            )
            currentR += grooveStep
        }

        // Vinyl Light Reflection Sheen (Radial Highlight Cones)
        val sheenBrush = Brush.sweepGradient(
            0.0f to Color.Transparent,
            0.12f to Color.White.copy(alpha = 0.08f),
            0.25f to Color.Transparent,
            0.5f to Color.Transparent,
            0.62f to Color.White.copy(alpha = 0.08f),
            0.75f to Color.Transparent,
            1.0f to Color.Transparent,
            center = center
        )
        drawCircle(
            brush = sheenBrush,
            radius = vinylRadius,
            center = center,
            style = Fill
        )

        // Center Label Disc
        val labelRadius = vinylRadius * 0.35f
        drawCircle(
            color = if (style == VisualizerStyle.MONOCHROME) Color.DarkGray else baseColor,
            radius = labelRadius,
            center = center,
            style = Fill
        )

        // Center Spindle Hole
        drawCircle(
            color = Color(0xFFE0E0E0),
            radius = labelRadius * 0.18f,
            center = center,
            style = Fill
        )
        drawCircle(
            color = Color(0xFF1A1A1A),
            radius = labelRadius * 0.12f,
            center = center,
            style = Fill
        )
    }

    // Stylized Tonearm
    val armPivot = Offset(size.width * 0.88f, size.height * 0.12f)
    val armTarget = Offset(center.x + vinylRadius * 0.6f, center.y - vinylRadius * 0.2f)

    // Arm Base
    drawCircle(
        color = Color(0xFFB0BEC5),
        radius = 12f,
        center = armPivot,
        style = Fill
    )

    // Arm Line
    drawLine(
        color = Color(0xFFCFD8DC),
        start = armPivot,
        end = armTarget,
        strokeWidth = 4f,
        cap = StrokeCap.Round
    )

    // Cartridge / Needle Head
    drawCircle(
        color = baseColor,
        radius = 6f,
        center = armTarget,
        style = Fill
    )
}
