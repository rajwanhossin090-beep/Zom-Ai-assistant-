package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.live.AssistantStatus
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.GlowGreen
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonCyanLight
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MjOrbVisualizer(
    status: AssistantStatus,
    micAmplitude: Float,
    speakerAmplitude: Float,
    modifier: Modifier = Modifier,
    onOrbClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    // Breathing pulse for IDLE state
    val breathingRadiusScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_pulse"
    )

    // Rotation angle for THINKING state
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_angle"
    )

    // Smooth animated amplitude response
    val animAmplitude = remember { Animatable(0f) }
    val currentAmp = if (status == AssistantStatus.SPEAKING) speakerAmplitude else micAmplitude

    LaunchedEffect(currentAmp) {
        animAmplitude.animateTo(
            targetValue = currentAmp,
            animationSpec = tween(durationMillis = 80)
        )
    }

    Box(
        modifier = modifier
            .testTag("mj_orb_visualizer")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOrbClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = (size.minDimension / 2.8f)

            when (status) {
                AssistantStatus.IDLE -> {
                    // IDLE: Subtle breathing glow & ambient halo
                    val radius = baseRadius * breathingRadiusScale

                    // Outer ambient halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonViolet.copy(alpha = 0.35f), Color.Transparent),
                            center = center,
                            radius = radius * 1.6f
                        ),
                        radius = radius * 1.6f,
                        center = center
                    )

                    // Outer ring stroke
                    drawCircle(
                        color = NeonCyan.copy(alpha = 0.4f),
                        radius = radius * 1.15f,
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Core glowing orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonCyan, NeonViolet, Color(0xFF0F0F23)),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }

                AssistantStatus.LISTENING -> {
                    // LISTENING: Active listening waveform & pulsing energy rings
                    val ampFactor = 1f + (animAmplitude.value * 0.8f)
                    val radius = baseRadius * ampFactor

                    // Multi-layer reactive energy rings
                    for (i in 1..3) {
                        val ringRadius = radius * (1f + i * 0.22f * animAmplitude.value)
                        val alpha = (0.6f - i * 0.15f).coerceAtLeast(0.1f)
                        drawCircle(
                            color = GlowGreen.copy(alpha = alpha),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = (4 - i).dp.toPx())
                        )
                    }

                    // Core listening orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(GlowGreen, NeonCyan, Color(0xFF081A1B)),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )

                    // Radial waveform spikes around circle
                    val points = 32
                    val path = Path()
                    for (i in 0 until points) {
                        val angle = (i * 2 * PI / points).toFloat()
                        val spike = sin((angle * 6) + (animAmplitude.value * 10)) * (20.dp.toPx() * animAmplitude.value)
                        val r = radius + 10.dp.toPx() + spike
                        val x = center.x + r * cos(angle)
                        val y = center.y + r * sin(angle)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                    drawPath(
                        path = path,
                        color = GlowGreen.copy(alpha = 0.8f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                AssistantStatus.THINKING -> {
                    // THINKING: Pulsing rotating neon ring with orbital nodes
                    val radius = baseRadius * 0.95f

                    // Inner core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonMagenta, NeonViolet, DarkBackground),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )

                    // Rotating segmented outer neon ring
                    val ringRadius = radius * 1.3f
                    val sweepAngle = 120f
                    drawArc(
                        color = NeonMagenta,
                        startAngle = rotationAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                        size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2),
                        style = Stroke(width = 5.dp.toPx())
                    )
                    drawArc(
                        color = NeonCyan,
                        startAngle = rotationAngle + 180f,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                        size = androidx.compose.ui.geometry.Size(ringRadius * 2, ringRadius * 2),
                        style = Stroke(width = 5.dp.toPx())
                    )

                    // Orbital energy nodes
                    val nodeAngle = (rotationAngle * PI / 180f).toFloat()
                    val nodeX = center.x + ringRadius * cos(nodeAngle)
                    val nodeY = center.y + ringRadius * sin(nodeAngle)
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = Offset(nodeX, nodeY)
                    )
                }

                AssistantStatus.SPEAKING -> {
                    // SPEAKING: Dynamic sine wave & magenta/cyan pulse
                    val ampFactor = 1f + (animAmplitude.value * 0.9f)
                    val radius = baseRadius * ampFactor

                    // Expanding output waves
                    val waveCount = 4
                    for (i in 0 until waveCount) {
                        val waveRadius = radius * (1f + (i + 1) * 0.18f * animAmplitude.value)
                        drawCircle(
                            color = NeonMagenta.copy(alpha = (0.5f - i * 0.1f).coerceAtLeast(0.05f)),
                            radius = waveRadius,
                            center = center,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    // Core speaking orb gradient
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonMagenta, NeonCyanLight, Color(0xFF1F082B)),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )

                    // Center audio wave path
                    val wavePath = Path()
                    val waveWidth = radius * 1.4f
                    val startX = center.x - waveWidth / 2f
                    val waveSteps = 40
                    wavePath.moveTo(startX, center.y)

                    for (i in 0..waveSteps) {
                        val progress = i.toFloat() / waveSteps
                        val x = startX + progress * waveWidth
                        val waveAmp = sin(progress * 4 * PI + (animAmplitude.value * 8)).toFloat() * (35.dp.toPx() * animAmplitude.value)
                        val y = center.y + waveAmp
                        wavePath.lineTo(x, y)
                    }

                    drawPath(
                        path = wavePath,
                        color = Color.White,
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }
        }
    }
}
