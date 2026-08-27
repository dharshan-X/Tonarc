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

    // ==========================================
    // AUTHENTIC AUDIOPHILE S-SHAPED TONEARM (WITH GROOVE JITTER & PLATTER SWAY)
    // ==========================================

    // High-Frequency Mechanical Groove Chatter & Bass Jitter ("Stuttering / Flutter")
    val rotRad = Math.toRadians(frame.rotationAngle.toDouble()).toFloat()
    val isSpinning = frame.rotationAngle > 0f
    val flutterHigh = sin(rotRad * 16.5f) * 0.75f + cos(rotRad * 29.3f) * 0.45f
    val bassThumpJitter = (frame.bassEnergy * 2.8f) * (sin(rotRad * 11.2f) * 0.85f + cos(rotRad * 33.7f) * 0.55f)
    val totalJitter = if (isSpinning) (flutterHigh + bassThumpJitter) else 0f

    // Platter Eccentricity (Subtle Slow Radial Sway ~0.55 Hz rotation frequency)
    val slowSway = if (isSpinning) sin(rotRad * 1.0f) * (vinylRadius * 0.014f) else 0f

    // Dynamic Tracking Coordinates
    val baseNeedleRadius = vinylRadius * 0.51f + slowSway
    val needleAngle = 0.25f // Angular position on record
    val baseNeedleX = center.x + baseNeedleRadius * cos(needleAngle)
    val baseNeedleY = center.y - baseNeedleRadius * sin(needleAngle)

    val pivot = Offset(center.x + vinylRadius * 0.82f, center.y - vinylRadius * 0.74f)
    val needleTarget = Offset(
        baseNeedleX + totalJitter * 0.7f,
        baseNeedleY + totalJitter * 0.5f
    )

    // Headshell dimensions & alignment vector
    val headshellLength = vinylRadius * 0.18f
    val headshellAngleRad = 1.18f // ~67.5 degrees pointing towards the groove tangent
    val dirX = cos(headshellAngleRad)
    val dirY = -sin(headshellAngleRad)
    val u = Offset(dirX, -dirY) // Direction vector from mount to needle
    val v = Offset(u.y, -u.x)   // Normal vector (rightwards)

    val headshellMount = Offset(
        needleTarget.x - u.x * headshellLength,
        needleTarget.y - u.y * headshellLength
    )

    // S-Curve Control Points (Responsive damped beam compliance)
    val armCp1 = Offset(pivot.x + vinylRadius * 0.08f, pivot.y + (headshellMount.y - pivot.y) * 0.32f)
    val armCp2 = Offset(headshellMount.x - vinylRadius * 0.07f + totalJitter * 0.2f, pivot.y + (headshellMount.y - pivot.y) * 0.68f)

    val armPath = Path().apply {
        moveTo(pivot.x, pivot.y)
        cubicTo(
            armCp1.x, armCp1.y,
            armCp2.x, armCp2.y,
            headshellMount.x, headshellMount.y
        )
    }

    // 1. TONEARM DROP SHADOW (Deep physical elevation above spinning record)
    val shadowOffset = Offset(10f, 14f)
    val shadowPath = Path().apply {
        moveTo(pivot.x + shadowOffset.x, pivot.y + shadowOffset.y)
        cubicTo(
            armCp1.x + shadowOffset.x, armCp1.y + shadowOffset.y,
            armCp2.x + shadowOffset.x, armCp2.y + shadowOffset.y,
            headshellMount.x + shadowOffset.x, headshellMount.y + shadowOffset.y
        )
    }
    drawPath(
        path = shadowPath,
        color = Color.Black.copy(alpha = 0.35f),
        style = Stroke(width = 12f, cap = StrokeCap.Round)
    )
    drawCircle(
        color = Color.Black.copy(alpha = 0.30f),
        radius = 14f,
        center = needleTarget + shadowOffset
    )

    // 2. COUNTERWEIGHT ASSEMBLY (Behind pivot, angled top-right)
    val cwAngleRad = -0.785f // -45 degrees
    val cwDir = Offset(cos(cwAngleRad), sin(cwAngleRad))
    val cwNormal = Offset(-cwDir.y, cwDir.x)
    val cwShaftEnd = pivot + cwDir * (vinylRadius * 0.18f)
    val cwCenter = pivot + cwDir * (vinylRadius * 0.11f)

    // Counterweight Shaft (Stainless Steel Rod)
    drawLine(
        color = Color(0xFF64748B),
        start = pivot,
        end = cwShaftEnd,
        strokeWidth = 6.5f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFFCBD5E1),
        start = pivot + Offset(0f, -1f),
        end = cwShaftEnd + Offset(0f, -1f),
        strokeWidth = 2f,
        cap = StrokeCap.Round
    )

    // Counterweight Main Cylinder (Heavy Solid Metal Ring)
    val cwRadius = 14f
    val cwHalfThickness = 8f
    val cwP1 = cwCenter - cwDir * cwHalfThickness - cwNormal * cwRadius
    val cwP2 = cwCenter + cwDir * cwHalfThickness - cwNormal * cwRadius
    val cwP3 = cwCenter + cwDir * cwHalfThickness + cwNormal * cwRadius
    val cwP4 = cwCenter - cwDir * cwHalfThickness + cwNormal * cwRadius

    val cwPath = Path().apply {
        moveTo(cwP1.x, cwP1.y)
        lineTo(cwP2.x, cwP2.y)
        lineTo(cwP3.x, cwP3.y)
        lineTo(cwP4.x, cwP4.y)
        close()
    }
    drawPath(
        path = cwPath,
        brush = Brush.linearGradient(
            0.0f to Color(0xFF475569),
            0.35f to Color(0xFFE2E8F0),
            0.65f to Color(0xFF94A3B8),
            1.0f to Color(0xFF334155),
            start = cwCenter - cwNormal * cwRadius,
            end = cwCenter + cwNormal * cwRadius
        )
    )
    drawPath(
        path = cwPath,
        color = Color(0xFF1E293B),
        style = Stroke(width = 1.5f)
    )

    // Counterweight Tracking Force Dial (Knurled Black Band)
    val dialCenter = cwCenter - cwDir * (cwHalfThickness - 3f)
    drawLine(
        color = Color(0xFF0F172A),
        start = dialCenter - cwNormal * (cwRadius + 1f),
        end = dialCenter + cwNormal * (cwRadius + 1f),
        strokeWidth = 3f
    )

    // 3. GIMBAL / PIVOT BEARING HOUSING
    // Outer Base Plate
    drawCircle(
        color = Color(0xFF1E293B),
        radius = 24f,
        center = pivot,
        style = Fill
    )
    drawCircle(
        brush = Brush.sweepGradient(
            0.0f to Color(0xFF94A3B8),
            0.25f to Color(0xFFE2E8F0),
            0.5f to Color(0xFF475569),
            0.75f to Color(0xFFCBD5E1),
            1.0f to Color(0xFF94A3B8),
            center = pivot
        ),
        radius = 24f,
        center = pivot,
        style = Stroke(width = 2.5f)
    )

    // Cueing Arm Bar / Tonearm Rest Pin
    val restPinStart = pivot + Offset(-12f, 10f)
    val restPinEnd = pivot + Offset(-18f, 22f)
    drawLine(
        color = Color(0xFF64748B),
        start = restPinStart,
        end = restPinEnd,
        strokeWidth = 3.5f,
        cap = StrokeCap.Round
    )

    // Gimbal Ring Assembly
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to Color(0xFFE2E8F0),
            0.7f to Color(0xFF94A3B8),
            1.0f to Color(0xFF475569),
            center = pivot,
            radius = 16f
        ),
        radius = 16f,
        center = pivot,
        style = Fill
    )
    drawCircle(
        color = Color(0xFF0F172A),
        radius = 16f,
        center = pivot,
        style = Stroke(width = 1.5f)
    )

    // Center Bearing Pivot & Screw Accent
    drawCircle(
        color = Color(0xFF334155),
        radius = 9f,
        center = pivot,
        style = Fill
    )
    drawCircle(
        color = Color(0xFFF1F5F9),
        radius = 4.5f,
        center = pivot,
        style = Fill
    )
    drawLine(
        color = Color(0xFF475569),
        start = pivot + Offset(-3f, 0f),
        end = pivot + Offset(3f, 0f),
        strokeWidth = 1.2f
    )

    // 4. SCULPTED S-SHAPED TONEARM TUBE (Multi-layer Metallic Shading)
    // Base Tube Body
    drawPath(
        path = armPath,
        color = Color(0xFF64748B),
        style = Stroke(width = 8.5f, cap = StrokeCap.Round)
    )
    // Metallic Silver Core
    drawPath(
        path = armPath,
        brush = Brush.linearGradient(
            0.0f to Color(0xFFE2E8F0),
            0.5f to Color(0xFFF8FAFC),
            1.0f to Color(0xFFCBD5E1),
            start = pivot,
            end = headshellMount
        ),
        style = Stroke(width = 6.5f, cap = StrokeCap.Round)
    )
    // Specular Highlight Ridge
    drawPath(
        path = armPath,
        color = Color.White.copy(alpha = 0.90f),
        style = Stroke(width = 2.2f, cap = StrokeCap.Round)
    )

    // 5. HEADSHELL & PHONO CARTRIDGE ASSEMBLY
    // Arm / Headshell Locking Collar Ring
    drawLine(
        color = Color(0xFF334155),
        start = headshellMount - v * 6f,
        end = headshellMount + v * 6f,
        strokeWidth = 5f,
        cap = StrokeCap.Butt
    )
    drawLine(
        color = Color(0xFFCBD5E1),
        start = headshellMount - v * 6f,
        end = headshellMount + v * 6f,
        strokeWidth = 2f,
        cap = StrokeCap.Butt
    )

    // Headshell Body (Aerodynamic angled wedge)
    val hsRearWidth = 10f
    val hsFrontWidth = 14f
    val hsLength = headshellLength * 0.72f
    val hsFrontCenter = headshellMount + u * hsLength

    val hsP1 = headshellMount - v * (hsRearWidth * 0.5f)
    val hsP2 = headshellMount + v * (hsRearWidth * 0.5f)
    val hsP3 = hsFrontCenter + v * (hsFrontWidth * 0.5f)
    val hsP4 = hsFrontCenter - v * (hsFrontWidth * 0.5f)

    val headshellPath = Path().apply {
        moveTo(hsP1.x, hsP1.y)
        lineTo(hsP2.x, hsP2.y)
        lineTo(hsP3.x, hsP3.y)
        lineTo(hsP4.x, hsP4.y)
        close()
    }

    // Headshell Shell Fill & Bevel
    drawPath(
        path = headshellPath,
        brush = Brush.linearGradient(
            0.0f to Color(0xFF1E293B),
            0.5f to Color(0xFF334155),
            1.0f to Color(0xFF0F172A),
            start = hsP1,
            end = hsP3
        )
    )
    drawPath(
        path = headshellPath,
        color = Color(0xFF94A3B8),
        style = Stroke(width = 1.5f)
    )

    // Headshell Top Weight-Reduction Slots
    val slotCenter = headshellMount + u * (hsLength * 0.45f)
    drawLine(
        color = Color(0xFF0F172A),
        start = slotCenter - v * 3f - u * 4f,
        end = slotCenter - v * 3f + u * 4f,
        strokeWidth = 1.8f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = Color(0xFF0F172A),
        start = slotCenter + v * 3f - u * 4f,
        end = slotCenter + v * 3f + u * 4f,
        strokeWidth = 1.8f,
        cap = StrokeCap.Round
    )

    // Finger Lift Hook (Iconic cueing hook on outer side)
    val hookRoot = headshellMount + u * (hsLength * 0.5f) + v * (hsFrontWidth * 0.45f)
    val hookCp = hookRoot + v * 12f - u * 2f
    val hookEnd = hookRoot + v * 14f - u * 10f
    val hookPath = Path().apply {
        moveTo(hookRoot.x, hookRoot.y)
        quadraticTo(hookCp.x, hookCp.y, hookEnd.x, hookEnd.y)
    }
    drawPath(
        path = hookPath,
        color = Color(0xFFE2E8F0),
        style = Stroke(width = 2.5f, cap = StrokeCap.Round)
    )

    // Phono Cartridge Body (Vibrant Accent Color / Audiophile Cartridge)
    val cartridgeColor = if (style == VisualizerStyle.MONOCHROME) Color(0xFFE2E8F0) else baseColor
    val cartLength = headshellLength * 0.4f
    val cartWidth = 10f
    val cartStart = hsFrontCenter - u * (cartLength * 0.3f)
    val cartEnd = hsFrontCenter + u * (cartLength * 0.7f)

    val cartP1 = cartStart - v * (cartWidth * 0.5f)
    val cartP2 = cartStart + v * (cartWidth * 0.5f)
    val cartP3 = cartEnd + v * (cartWidth * 0.4f)
    val cartP4 = cartEnd - v * (cartWidth * 0.4f)

    val cartPath = Path().apply {
        moveTo(cartP1.x, cartP1.y)
        lineTo(cartP2.x, cartP2.y)
        lineTo(cartP3.x, cartP3.y)
        lineTo(cartP4.x, cartP4.y)
        close()
    }
    drawPath(
        path = cartPath,
        color = cartridgeColor,
        style = Fill
    )
    drawPath(
        path = cartPath,
        color = Color.White.copy(alpha = 0.5f),
        style = Stroke(width = 1f)
    )

    // Cantilever Needle & Diamond Stylus Tip
    drawLine(
        color = Color(0xFFCBD5E1),
        start = cartEnd,
        end = needleTarget,
        strokeWidth = 2.2f,
        cap = StrokeCap.Round
    )
    // Diamond Stylus Contact Glow & Acoustic Spark
    val sparkPulse = (0.45f + frame.bassEnergy * 0.55f).coerceIn(0f, 1f)
    val glowRadius = 4.5f + frame.bassEnergy * 3.5f + kotlin.math.abs(totalJitter) * 0.5f
    drawCircle(
        color = if (style == VisualizerStyle.MONOCHROME) Color.White.copy(alpha = 0.35f * sparkPulse)
                else baseColor.copy(alpha = 0.45f * sparkPulse),
        radius = glowRadius,
        center = needleTarget,
        style = Fill
    )
    drawCircle(
        color = Color.White.copy(alpha = sparkPulse),
        radius = 2.4f,
        center = needleTarget,
        style = Fill
    )
}
