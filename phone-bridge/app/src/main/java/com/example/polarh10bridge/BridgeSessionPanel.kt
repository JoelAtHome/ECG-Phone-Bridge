package com.example.polarh10bridge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.polarh10bridge.ritual.RitualPackageStore
import com.example.polarh10bridge.ritual.RitualRecordingSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SessionBannerRed = Color(0xFFC1121F)
private val SessionTextDark = Color(0xFF1A1A1A)
private val SessionPanelBg = Color(0xFFF7F7F8)
private val SessionPanelBorder = Color(0xFFE0E0E0)

/**
 * Standing Capture-panel line. The phone ring keeps the last [max] Record packages.
 * At capacity the next Stop still saves; it drops the oldest.
 */
internal fun hrvRecordingCapacityLine(
    stored: Int,
    max: Int = RitualPackageStore.MAX_PACKAGES,
): String {
    val counts = hrvStorageCounts(stored, max)
    return if (hrvStorageRemaining(stored, max) == 0) {
        "Phone keeps the last $max recordings — $counts. Next replaces the oldest."
    } else {
        "Phone keeps the last $max recordings — $counts."
    }
}

/** Toast after a Record Stop that persisted a package. */
internal fun recordStopStorageToast(
    stored: Int,
    max: Int = RitualPackageStore.MAX_PACKAGES,
    pcConnected: Boolean,
): String {
    val counts = hrvStorageCounts(stored, max)
    val saved =
        if (hrvStorageRemaining(stored, max) == 0) {
            "HRV saved — $counts. Next replaces the oldest"
        } else {
            "HRV saved — $counts"
        }
    return if (pcConnected) saved else "$saved. Upload in FT or HnH"
}

private fun hrvStorageRemaining(stored: Int, max: Int): Int {
    val cap = max.coerceAtLeast(0)
    return cap - stored.coerceIn(0, cap)
}

private fun hrvStorageCounts(stored: Int, max: Int): String {
    val kept = stored.coerceIn(0, max.coerceAtLeast(0))
    val remaining = hrvStorageRemaining(kept, max)
    return "$kept stored, $remaining more can be stored"
}

/** `m:ss` for a saved recording. Hours roll into the minute count (`125:00`). */
internal fun formatDurationForUi(durationS: Double): String {
    if (durationS.isNaN()) return "0:00"
    val seconds = durationS.toLong().coerceIn(0L, 24L * 60L * 60L)
    return String.format(Locale.US, "%d:%02d", seconds / 60L, seconds % 60L)
}

/** One Capture-panel line: time, RMSSD, duration, patient, sent or pending. */
internal fun hrvRecordingRowLabel(row: RitualRecordingSummary): String {
    val parts = ArrayList<String>(5)
    val whenLabel = formatEmittedAtForUi(row.emittedAt).ifBlank { "—" }
    parts.add(whenLabel)
    parts.add(
        if (row.rmssdMs != null) {
            String.format(Locale.US, "%.0f ms", row.rmssdMs)
        } else {
            "— ms"
        },
    )
    parts.add(formatDurationForUi(row.durationS))
    row.profileDisplayName?.trim()?.takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    parts.add(if (row.acked) "sent" else "pending")
    return parts.joinToString(" · ")
}

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
    ritualRecordings: List<RitualRecordingSummary> = emptyList(),
    ritualStoredCount: Int = 0,
    onModeSelected: (BridgeSessionMode) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSendRitual: (String) -> Unit = {},
    onDeleteRitual: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val canStart = !active && (sensorConnected || featherSimActive || featherBleConnected)
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val pendingDelete = ritualRecordings.firstOrNull { it.sessionId == pendingDeleteId }
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
        Text(
            text = hrvRecordingCapacityLine(ritualStoredCount),
            color = SessionTextDark.copy(alpha = 0.62f),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        ritualRecordings.forEach { row ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = hrvRecordingRowLabel(row),
                    color = SessionTextDark.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onSendRitual(row.sessionId) },
                    enabled = !active,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        "Send",
                        color = SessionBannerRed.copy(alpha = if (active) 0.38f else 1f),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
                TextButton(
                    onClick = { pendingDeleteId = row.sessionId },
                    enabled = !active,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text(
                        "Delete",
                        color = SessionBannerRed.copy(alpha = if (active) 0.38f else 1f),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        if (pendingDelete != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteId = null },
                title = { Text("Delete recording?") },
                text = {
                    Text(
                        "Remove this recording from the phone.\n\n${hrvRecordingRowLabel(pendingDelete)}",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val id = pendingDelete.sessionId
                            pendingDeleteId = null
                            onDeleteRitual(id)
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteId = null }) {
                        Text("Cancel")
                    }
                },
            )
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
