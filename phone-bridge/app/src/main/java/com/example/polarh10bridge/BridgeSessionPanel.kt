package com.example.polarh10bridge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SessionBannerRed = Color(0xFFC1121F)
private val SessionTextDark = Color(0xFF1A1A1A)
private val SessionPanelBg = Color(0xFFF7F7F8)
private val SessionPanelBorder = Color(0xFFE0E0E0)

/** Wire `emitted_at` is UTC ISO; show device-local wall time for caregivers. */
internal fun formatEmittedAtForUi(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return trimmed
    return try {
        val instant = Instant.parse(trimmed)
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (_: Exception) {
        trimmed.take(19).replace('T', ' ')
    }
}

@Composable
fun BridgeSessionPanel(
    mode: BridgeSessionMode,
    kind: BridgeSessionKind,
    active: Boolean,
    sessionId: String?,
    ibiCount: Int,
    lastRmssdMs: Double?,
    sensorConnected: Boolean,
    /** Tech Feather sim counts as a source for Start (no Polar required). */
    featherSimActive: Boolean = false,
    /** Live Feather GATT client connected/streaming. */
    featherBleConnected: Boolean = false,
    lastRitualSessionId: String? = null,
    lastRitualAcked: Boolean = false,
    lastRitualRmssdMs: Double? = null,
    lastRitualEmittedAt: String? = null,
    onModeSelected: (BridgeSessionMode) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSendLastRitual: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val canStart = !active && (sensorConnected || featherSimActive || featherBleConnected)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SessionPanelBg)
                .border(1.dp, SessionPanelBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Capture session",
            color = SessionTextDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text =
                "Stream — live + rolling RMSSD (VNS-TA). " +
                    "Record HRV — HRV recording on stop (FlareTracker). " +
                    "Hertz & Hearts: either.",
            color = SessionTextDark.copy(alpha = 0.62f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionModeChoice(
                label = "Stream",
                selected = mode == BridgeSessionMode.Stream,
                enabled = !active,
                onClick = { onModeSelected(BridgeSessionMode.Stream) },
                modifier = Modifier.weight(1f),
            )
            SessionModeChoice(
                label = "Record HRV",
                selected = mode == BridgeSessionMode.Record,
                enabled = !active,
                onClick = { onModeSelected(BridgeSessionMode.Record) },
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text =
                buildString {
                    append(if (active) "Running" else "Idle")
                    append(" · ")
                    append(kind.uiLabel())
                    if (!sessionId.isNullOrBlank()) {
                        append(" · ")
                        append(sessionId)
                    }
                },
            color = SessionTextDark.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text =
                buildString {
                    append("IBIs: $ibiCount")
                    append(" · RMSSD: ")
                    when {
                        active && mode == BridgeSessionMode.Record -> append("TBD")
                        lastRmssdMs != null ->
                            append(String.format(Locale.US, "%.1f ms", lastRmssdMs))
                        // Stream settle (and until first rolling value): clearer than "—"
                        active && mode == BridgeSessionMode.Stream -> append("unsettled")
                        else -> append("—")
                    }
                },
            color = SessionTextDark.copy(alpha = 0.7f),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
        )

        if (!lastRitualSessionId.isNullOrBlank()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text =
                        buildString {
                            append("Last HRV · ")
                            append(if (lastRitualAcked) "sent" else "pending")
                            lastRitualRmssdMs?.let {
                                append(" · ")
                                append(String.format(Locale.US, "%.0f ms", it))
                            }
                            lastRitualEmittedAt?.takeIf { it.isNotBlank() }?.let { at ->
                                append(" · ")
                                append(formatEmittedAtForUi(at))
                            }
                        },
                    color = SessionTextDark.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                if (onSendLastRitual != null) {
                    TextButton(
                        onClick = onSendLastRitual,
                        enabled = !active,
                    ) {
                        Text(
                            "Send HRV",
                            color = SessionBannerRed.copy(alpha = if (active) 0.38f else 1f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        if (!sensorConnected && !featherSimActive && !featherBleConnected) {
            Text(
                text =
                    "Find a source on the data path (Polar or Feather), or Change ECG sensor → Simulate, before starting.",
                color = SessionTextDark.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        } else if (featherBleConnected && !sensorConnected) {
            Text(
                text = "Feather BLE linked — Start uses source_device FEATHER.",
                color = SessionTextDark.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        } else if (featherSimActive && !sensorConnected) {
            Text(
                text = "Feather sim active — Start uses source_device FEATHER.",
                color = SessionTextDark.copy(alpha = 0.55f),
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onStart,
                enabled = canStart,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = SessionBannerRed,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFBDBDBD),
                        disabledContentColor = Color.White.copy(alpha = 0.85f),
                    ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("Start", fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onStop,
                enabled = active,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = SessionBannerRed,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFBDBDBD),
                        disabledContentColor = Color.White.copy(alpha = 0.85f),
                    ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("Stop", fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SessionModeChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unselectedRing = Color(0xFF757575)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = if (enabled) onClick else null,
            enabled = enabled,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = SessionBannerRed,
                    unselectedColor = unselectedRing,
                    disabledSelectedColor = SessionBannerRed.copy(alpha = 0.45f),
                    disabledUnselectedColor = unselectedRing.copy(alpha = 0.45f),
                ),
        )
        TextButton(
            onClick = onClick,
            enabled = enabled,
        ) {
            Text(
                text = label,
                color =
                    when {
                        !enabled && selected -> SessionBannerRed.copy(alpha = 0.55f)
                        !enabled -> SessionTextDark.copy(alpha = 0.45f)
                        selected -> SessionBannerRed
                        else -> SessionTextDark.copy(alpha = 0.85f)
                    },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}
