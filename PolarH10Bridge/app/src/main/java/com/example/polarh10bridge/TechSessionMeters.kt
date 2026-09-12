package com.example.polarh10bridge

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

private val TechTextDark = Color(0xFF1A1A1A)
private val TechPanelBg = Color(0xFFEEF3F7)
private val TechPanelBorder = Color(0xFFCFD8DC)
private val TechFlagBg = Color(0xFFFFF3CD)
private val TechOkBg = Color(0xFFE8F5E9)
private val TechInfoBlue = Color(0xFF0B57D0)
private val TechHelpDialogBg = Color(0xFF2B2B2B)
private val TechHelpText = Color(0xFFE8E8E8)
private val TechHelpTextMuted = Color(0xFFC8C8C8)

private val QualityFlagHelp: List<Pair<String, String>> =
    listOf(
        "insufficient_beats" to "Not enough IBIs after settle/trims to compute a solid RMSSD.",
        "no_stable_window" to "No rolling analysis window passed the quality gates.",
        "short_session" to "Capture shorter than the intended settle (~45 s) + analysis (~60 s).",
        "end_divergence" to "Late-window RMSSD is much lower than the selected plateau (informational).",
        "high_skip_rate" to "Many beats were excluded upstream (≥10% skipped).",
    )

/**
 * Technician meters for setup/testing. Hidden in Patient view.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TechSessionMeters(
    sessionActive: Boolean,
    sessionMode: BridgeSessionMode,
    sessionStartedElapsedMs: Long,
    settleTrimSec: Double,
    /** Full-session target (settle + analysis), seconds. */
    sessionTargetSec: Double,
    ibiCount: Int,
    recentHrBpm: Double?,
    /** Null means show placeholder (e.g. Record in progress). */
    displayRmssdMs: Double?,
    acceptedBeats: Int,
    qualityFlags: List<String>,
    sensorContact: SensorContactState,
    /** BLE link strength only — never treat as skin contact. */
    connectedSensorRssi: Int?,
    featherSimActive: Boolean = false,
    onToggleFeatherSim: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var showFlagHelp by remember { mutableStateOf(false) }
    LaunchedEffect(sessionActive) {
        while (true) {
            nowElapsed = SystemClock.elapsedRealtime()
            delay(250)
        }
    }

    val elapsedSec =
        if (sessionActive && sessionStartedElapsedMs > 0L) {
            ((nowElapsed - sessionStartedElapsedMs).coerceAtLeast(0L) / 1000.0)
        } else {
            0.0
        }
    val settleTarget = settleTrimSec.coerceAtLeast(1.0)
    val settleRemaining = max(0.0, settleTarget - elapsedSec)
    val settled = sessionActive && settleRemaining <= 0.0

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TechPanelBg)
                .border(1.dp, TechPanelBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = "Tech meters",
            color = TechTextDark,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "For setup/testing — switch to Patient view when the patient is watching.",
            color = TechTextDark.copy(alpha = 0.62f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        Text(
            text =
                buildString {
                    append("Mode: ")
                    append(sessionMode.wireValue())
                    append(" · ")
                    append(
                        if (!sessionActive) {
                            "Idle"
                        } else if (settled) {
                            "Settled"
                        } else {
                            val rem = ceil(settleRemaining).toInt()
                            val tot = ceil(settleTarget).toInt()
                            "Settle ${tot - rem}/$tot s"
                        },
                    )
                },
            color = TechTextDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        if (sessionActive) {
            Text(
                text =
                    "Elapsed " +
                        formatElapsedClock(elapsedSec.toLong()) +
                        " / " +
                        formatElapsedClock(sessionTargetSec.toLong().coerceAtLeast(1L)),
                color = TechTextDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Text(
            text =
                buildString {
                    append("Contact: ")
                    append(
                        when (sensorContact) {
                            SensorContactState.InContact -> "skin OK"
                            SensorContactState.NoContact -> "no skin contact (RR/ECG gated)"
                            SensorContactState.Unknown -> "unknown (sensor may not report)"
                        },
                    )
                },
            color =
                if (sensorContact == SensorContactState.NoContact) {
                    Color(0xFFB3261E)
                } else {
                    TechTextDark.copy(alpha = 0.85f)
                },
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text =
                buildString {
                    append("IBIs: $ibiCount")
                    recentHrBpm?.let {
                        append(" · ~")
                        append(String.format(Locale.US, "%.0f", it))
                        append(" bpm")
                    }
                    connectedSensorRssi?.let {
                        append(" · ")
                        append(it)
                        append(" dBm link")
                    }
                },
            color = TechTextDark.copy(alpha = 0.85f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )

        Text(
            text =
                buildString {
                    append("Bridge RMSSD: ")
                    if (displayRmssdMs != null) {
                        append(String.format(Locale.US, "%.1f ms", displayRmssdMs))
                    } else if (sessionActive && sessionMode == BridgeSessionMode.Record) {
                        append("TBD (on stop)")
                    } else if (sessionActive && sessionMode == BridgeSessionMode.Stream) {
                        // Settle period (and until first rolling emit): clearer than "—"
                        append("unsettled")
                    } else {
                        append("—")
                    }
                },
            color = TechTextDark.copy(alpha = 0.85f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = "Accepted beats: $acceptedBeats",
            color = TechTextDark.copy(alpha = 0.85f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            Text(
                text = "Quality flags",
                color = TechTextDark,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Box(
                modifier =
                    Modifier
                        .padding(start = 6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(TechInfoBlue)
                        .clickable(role = Role.Button) { showFlagHelp = true },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "i",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (qualityFlags.isEmpty()) {
                FlagChip(
                    text =
                        if (sessionActive && settled) {
                            "Looking good — no issues flagged"
                        } else if (sessionActive) {
                            "Settling — no issues flagged yet"
                        } else {
                            "No issues flagged"
                        },
                    ok = true,
                )
            } else {
                qualityFlags.forEach { flag ->
                    FlagChip(text = flag, ok = false)
                }
            }
        }

        if (onToggleFeatherSim != null) {
            TextButton(
                onClick = onToggleFeatherSim,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(
                    text =
                        if (featherSimActive) {
                            "Stop Feather sim"
                        } else {
                            "Simulate Feather IBIs"
                        },
                    color = TechInfoBlue,
                    fontSize = 13.sp,
                )
            }
            Text(
                text =
                    if (featherSimActive) {
                        "Synthetic IBIs → bridge as source_device FEATHER (no BLE box)."
                    } else {
                        "Phone-first Feather path test without reflashing the ECG box."
                    },
                color = TechTextDark.copy(alpha = 0.62f),
                fontSize = 11.sp,
                lineHeight = 13.sp,
            )
        }
    }

    if (showFlagHelp) {
        AlertDialog(
            onDismissRequest = { showFlagHelp = false },
            containerColor = TechHelpDialogBg,
            title = {
                Text(
                    text = "Quality flags",
                    color = TechHelpText,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Flags appear when the bridge RMSSD math sees a problem. Empty/positive means none of these fired.",
                        fontSize = 13.sp,
                        color = TechHelpTextMuted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    QualityFlagHelp.forEach { (name, desc) ->
                        Text(
                            text = name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = TechHelpText,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            text = desc,
                            fontSize = 12.sp,
                            color = TechHelpTextMuted,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFlagHelp = false }) {
                    Text("Close", color = TechHelpText)
                }
            },
        )
    }
}

@Composable
private fun FlagChip(
    text: String,
    ok: Boolean,
) {
    Surface(
        color = if (ok) TechOkBg else TechFlagBg,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = text,
            color = TechTextDark,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private fun formatElapsedClock(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    } else {
        String.format(Locale.US, "%d:%02d", m, sec)
    }
}
