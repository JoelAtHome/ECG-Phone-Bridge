package com.example.polarh10bridge

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos

private val PacerBannerRed = Color(0xFFC1121F)
private val PacerTextDark = Color(0xFF1A1A1A)
private val PacerPanelBg = Color(0xFFF7F7F8)
private val PacerPanelBorder = Color(0xFFE0E0E0)
private val PacerIdleRing = Color(0xFFBDBDBD)
private val PacerInhaleFill = Color(0xFF64B5F6)
private val PacerExhaleFill = Color(0xFF81C784)

/** Built-in patient breathing presets (phone-owned; hosts do not drive this UI). */
enum class BreathPacePreset(
    val label: String,
    val inhaleSec: Float,
    val exhaleSec: Float,
) {
    COHERENCE("5.5 / 5.5", 5.5f, 5.5f),
    SIX_BPM("5 / 5", 5f, 5f),
    RELAX("4 / 6", 4f, 6f),
    BOX("4 / 4", 4f, 4f),
    ;

    val breathsPerMinute: Float
        get() = 60f / (inhaleSec + exhaleSec)
}

private enum class BreathPhase {
    Idle,
    Inhale,
    Exhale,
}

/**
 * Smooth patient breathing pacer driven by wall-clock frames (not Compose infinite
 * transitions), so expand/contract stays fluid alongside BLE/TCP bridge work.
 */
@Composable
fun PatientBreathingPacer(
    modifier: Modifier = Modifier,
    initialPreset: BreathPacePreset = BreathPacePreset.COHERENCE,
    onPresetChanged: (BreathPacePreset) -> Unit = {},
) {
    var preset by remember { mutableStateOf(initialPreset) }
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(BreathPhase.Idle) }
    var scale by remember { mutableFloatStateOf(0.42f) }

    KeepScreenOnWhile(running)

    LaunchedEffect(running, preset) {
        if (!running) {
            phase = BreathPhase.Idle
            scale = 0.42f
            return@LaunchedEffect
        }
        val cycleStartNs = withFrameNanos { it }
        val inhaleNs = (preset.inhaleSec * 1_000_000_000.0).toLong()
        val exhaleNs = (preset.exhaleSec * 1_000_000_000.0).toLong()
        val cycleNs = inhaleNs + exhaleNs
        while (true) {
            withFrameNanos { now ->
                val elapsed = (now - cycleStartNs) % cycleNs
                if (elapsed < inhaleNs) {
                    phase = BreathPhase.Inhale
                    val t = elapsed.toDouble() / inhaleNs.toDouble()
                    scale = lerp(0.42f, 1f, easeInOutCos(t.toFloat()))
                } else {
                    phase = BreathPhase.Exhale
                    val t = (elapsed - inhaleNs).toDouble() / exhaleNs.toDouble()
                    scale = lerp(1f, 0.42f, easeInOutCos(t.toFloat()))
                }
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PacerPanelBg)
                .border(1.dp, PacerPanelBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Breathing pacer",
            color = PacerTextDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "For the patient on this phone — hosts should not run a PC pacer.",
            color = PacerTextDark.copy(alpha = 0.62f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BreathPacePreset.entries.forEach { option ->
                val selected = option == preset
                TextButton(
                    onClick = {
                        preset = option
                        onPresetChanged(option)
                    },
                    modifier = Modifier.weight(1f),
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = if (selected) PacerBannerRed else PacerTextDark.copy(alpha = 0.7f),
                        ),
                ) {
                    Text(
                        text = option.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }

        Text(
            text = String.format(Locale.US, "%.1f breaths / min", preset.breathsPerMinute),
            color = PacerTextDark.copy(alpha = 0.55f),
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Box(
            modifier =
                Modifier
                    .size(168.dp)
                    .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(168.dp)) {
                val maxR = size.minDimension * 0.46f
                val r = maxR * scale
                val fill =
                    when (phase) {
                        BreathPhase.Inhale -> PacerInhaleFill.copy(alpha = 0.35f)
                        BreathPhase.Exhale -> PacerExhaleFill.copy(alpha = 0.35f)
                        BreathPhase.Idle -> PacerIdleRing.copy(alpha = 0.18f)
                    }
                val stroke =
                    when (phase) {
                        BreathPhase.Inhale -> PacerInhaleFill
                        BreathPhase.Exhale -> PacerExhaleFill
                        BreathPhase.Idle -> PacerIdleRing
                    }
                drawCircle(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(fill, fill.copy(alpha = 0.05f)),
                            center = center,
                            radius = r,
                        ),
                    radius = r,
                    center = center,
                )
                drawCircle(
                    color = stroke,
                    radius = r,
                    center = center,
                    style = Stroke(width = 3.dp.toPx()),
                )
                // Guide ring at max size
                drawCircle(
                    color = PacerIdleRing.copy(alpha = 0.35f),
                    radius = maxR,
                    center = Offset(center.x, center.y),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text =
                        when (phase) {
                            BreathPhase.Inhale -> "Inhale"
                            BreathPhase.Exhale -> "Exhale"
                            BreathPhase.Idle -> "Ready"
                        },
                    color = PacerTextDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Button(
            onClick = { running = !running },
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = if (running) Color(0xFF455A64) else PacerBannerRed,
                    contentColor = Color.White,
                ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(0.55f),
        ) {
            Text(
                text = if (running) "Stop pacer" else "Start pacer",
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun KeepScreenOnWhile(enabled: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = true
        }
        onDispose {
            if (enabled) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                view.keepScreenOn = false
            }
        }
    }
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)

/** Smooth ease for inhale/exhale expansion (cosine half-cycle). */
private fun easeInOutCos(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return (0.5 - 0.5 * cos(x * PI)).toFloat()
}
