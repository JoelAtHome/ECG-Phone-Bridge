package com.example.polarh10bridge

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    ibiCount: Int,
    recentHrBpm: Double?,
    lastRmssdMs: Double?,
    acceptedBeats: Int,
    qualityFlags: List<String>,
    connectedSensorRssi: Int?,
    modifier: Modifier = Modifier,
) {
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
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
                        append(" dBm")
                    }
                },
            color = TechTextDark.copy(alpha = 0.85f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        Text(
            text =
                buildString {
                    append("Bridge RMSSD: ")
                    if (lastRmssdMs != null) {
                        append(String.format(Locale.US, "%.1f ms", lastRmssdMs))
                    } else {
                        append("—")
                    }
                    append(" · accepted beats: ")
                    append(acceptedBeats)
                },
            color = TechTextDark.copy(alpha = 0.85f),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        Text(
            text = "Quality flags",
            color = TechTextDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        FlowRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (qualityFlags.isEmpty()) {
                FlagChip(text = "none", ok = true)
            } else {
                qualityFlags.forEach { flag ->
                    FlagChip(text = flag, ok = false)
                }
            }
        }
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
