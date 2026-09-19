package com.example.polarh10bridge

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.polarh10bridge.feather.FeatherPatientProfile
import com.example.polarh10bridge.feather.FeatherProfileSummary
import kotlinx.coroutines.delay
import java.util.Locale
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
private val ProfileMatchAlertBg = Color(0xFFFEF3C7)
private val ProfileMatchAlertBorder = Color(0xFFD97706)
private val ProfileMatchAlertText = Color(0xFF92400E)
/** Patient picker popup — force light surface (theme may be dark). */
private val PatientMenuBg = Color(0xFFFFFFFF)
private val PatientMenuText = Color(0xFF1A1A1A)

/** PC patient hint failed to resolve — needs Tech attention. */
private fun isFeatherProfileMatchAttention(status: String): Boolean {
    val s = status.trim()
    return s.startsWith("No Feather profile", ignoreCase = true) ||
        s.startsWith("Ambiguous Feather profile", ignoreCase = true)
}

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
    featherBlePhase: String = "Idle",
    featherBleDetail: String = "",
    featherBleLastIbiMs: Int? = null,
    featherBleConnected: Boolean = false,
    /** MCU LO+/LO−; null until status notify. Warn only when useLeadsOff && leadsOff. */
    featherLeadsOff: Boolean? = null,
    featherUseLeadsOff: Boolean? = null,
    featherEcgTraceMv: List<Float> = emptyList(),
    featherEcgTracePeaks: List<Boolean> = emptyList(),
    featherEcgSampleHz: Int = 250,
    featherEcgPacketCount: Int = 0,
    featherProfiles: List<FeatherProfileSummary> = emptyList(),
    featherActiveProfileId: String = "demo",
    featherActiveDisplayName: String = "Demo",
    featherProfileStatus: String = "",
    featherActiveCoeffs: Map<String, String> = emptyMap(),
    featherCoeffsEpoch: Int = 0,
    /** When true, Offline fields mirror Tuner (read-only); Store/Get/Send locked. */
    tunerLinked: Boolean = false,
    onSelectFeatherProfile: ((String) -> Unit)? = null,
    onAddFeatherPatient: ((String) -> Unit)? = null,
    onRenameFeatherPatient: ((String) -> Unit)? = null,
    onDeleteFeatherProfile: ((String) -> Unit)? = null,
    onSaveFeatherProfileCoeffs: ((Map<String, String>) -> Unit)? = null,
    onGetFeatherOffline: (() -> Unit)? = null,
    onSendFeatherOffline: ((Map<String, String>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var nowElapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var showFlagHelp by remember { mutableStateOf(false) }
    var showAddPatient by remember { mutableStateOf(false) }
    var showRenamePatient by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var patientMenuExpanded by remember { mutableStateOf(false) }
    var addPatientName by remember { mutableStateOf("") }
    var renamePatientName by remember { mutableStateOf("") }
    var deleteConfirmTyped by remember { mutableStateOf("") }
    var coeffDraft by remember { mutableStateOf(featherActiveCoeffs) }
    var offlineCoeffsExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(featherActiveProfileId, featherCoeffsEpoch) {
        coeffDraft = featherActiveCoeffs
    }
    LaunchedEffect(sessionActive) {
        while (true) {
            nowElapsed = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    val profileMatchAttention = isFeatherProfileMatchAttention(featherProfileStatus)

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
                            val settledSec =
                                elapsedSec.toLong().coerceIn(0L, settleTarget.toLong())
                            "Settling " +
                                formatElapsedClock(settledSec) +
                                " / " +
                                formatElapsedClock(settleTarget.toLong().coerceAtLeast(1L))
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
                    val featherLodWarn =
                        featherUseLeadsOff == true && featherLeadsOff == true
                    when {
                        featherLodWarn -> append("check electrodes (leads off)")
                        sensorContact == SensorContactState.InContact -> append("skin OK")
                        sensorContact == SensorContactState.NoContact ->
                            append("no skin contact (RR/ECG gated)")
                        featherBleConnected || featherSimActive -> {
                            when {
                                featherUseLeadsOff == false ->
                                    append("leads-off N/A (2-lead / disabled)")
                                featherLeadsOff == false -> append("leads OK (Feather)")
                                else -> append("not reported (Feather)")
                            }
                        }
                        else -> append("unknown (sensor may not report)")
                    }
                },
            color =
                if (sensorContact == SensorContactState.NoContact ||
                    (featherUseLeadsOff == true && featherLeadsOff == true)
                ) {
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp,
                    style =
                        TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle =
                                LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.Both,
                                ),
                        ),
                    modifier = Modifier.offset(y = (-0.5).dp),
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

        if (onSelectFeatherProfile != null && onSaveFeatherProfileCoeffs != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clickable(
                            role = Role.Button,
                            onClick = { offlineCoeffsExpanded = !offlineCoeffsExpanded },
                        )
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Offline ECG-box tuning coefficients",
                        color = TechTextDark,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                    if (!offlineCoeffsExpanded) {
                        Text(
                            text =
                                buildString {
                                    append(featherActiveDisplayName)
                                    append(" (")
                                    append(featherActiveProfileId)
                                    append(")")
                                    if (tunerLinked) append(" · Tuner linked")
                                },
                            color = TechTextDark.copy(alpha = 0.62f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
                Text(
                    text = if (offlineCoeffsExpanded) "▾" else "▸",
                    color = TechTextDark.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (profileMatchAttention && featherProfileStatus.isNotBlank()) {
                FeatherProfileMatchAttentionBanner(
                    message = featherProfileStatus,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                )
            }
            if (offlineCoeffsExpanded) {
                if (onSelectFeatherProfile != null) {
                    // Box + DropdownMenu (not ExposedDropdownMenuBox): Material3's exposed
                    // box often reserves full menu height in-layout → huge gap below the field.
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                    ) {
                        OutlinedTextField(
                            value = "$featherActiveDisplayName ($featherActiveProfileId)",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Active patient", fontSize = 11.sp) },
                            trailingIcon = {
                                Text(
                                    text = if (patientMenuExpanded) "▴" else "▾",
                                    color = TechTextDark.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                )
                            },
                            textStyle =
                                TextStyle(
                                    color = TechTextDark,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TechTextDark,
                                    unfocusedTextColor = TechTextDark,
                                    focusedBorderColor = TechInfoBlue,
                                    unfocusedBorderColor = TechPanelBorder,
                                    focusedLabelColor = TechInfoBlue,
                                    unfocusedLabelColor = TechTextDark.copy(alpha = 0.55f),
                                    cursorColor = TechInfoBlue,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // TextField consumes taps; overlay opens the menu.
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .clickable { patientMenuExpanded = true },
                        )
                        DropdownMenu(
                            expanded = patientMenuExpanded,
                            onDismissRequest = { patientMenuExpanded = false },
                            containerColor = PatientMenuBg,
                        ) {
                            featherProfiles.forEach { summary ->
                                val selected = summary.profileId == featherActiveProfileId
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text =
                                                buildString {
                                                    append(summary.displayName)
                                                    append(" (")
                                                    append(summary.profileId)
                                                    append(")")
                                                    if (selected) append(" ✓")
                                                },
                                            color = if (selected) TechInfoBlue else PatientMenuText,
                                            fontWeight =
                                                if (selected) {
                                                    FontWeight.SemiBold
                                                } else {
                                                    FontWeight.Normal
                                                },
                                            fontSize = 14.sp,
                                        )
                                    },
                                    onClick = {
                                        onSelectFeatherProfile(summary.profileId)
                                        patientMenuExpanded = false
                                    },
                                    colors =
                                        MenuDefaults.itemColors(
                                            textColor = PatientMenuText,
                                        ),
                                )
                            }
                            if (onAddFeatherPatient != null) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "+ Add new patient…",
                                            color = TechInfoBlue,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                        )
                                    },
                                    onClick = {
                                        patientMenuExpanded = false
                                        addPatientName = ""
                                        showAddPatient = true
                                    },
                                    colors =
                                        MenuDefaults.itemColors(
                                            textColor = TechInfoBlue,
                                        ),
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Active: $featherActiveDisplayName ($featherActiveProfileId)",
                        color = TechTextDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (featherProfileStatus.isNotBlank() && !profileMatchAttention) {
                    Text(
                        text = featherProfileStatus,
                        color = TechTextDark.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 0.dp),
                ) {
                    if (onRenameFeatherPatient != null) {
                        TextButton(
                            onClick = {
                                renamePatientName = featherActiveDisplayName
                                showRenamePatient = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier =
                                Modifier
                                    .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                                    .heightIn(max = 32.dp),
                        ) {
                            Text("Edit name", color = TechInfoBlue, fontSize = 13.sp)
                        }
                    }
                    if (onDeleteFeatherProfile != null) {
                        TextButton(
                            onClick = {
                                deleteConfirmTyped = ""
                                showDeleteConfirm = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier =
                                Modifier
                                    .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                                    .heightIn(max = 32.dp),
                        ) {
                            Text("Delete name", color = TechInfoBlue, fontSize = 13.sp)
                        }
                    }
                }
                Text(
                    text =
                        if (tunerLinked) {
                            "Tuner linked — Offline is a read-only mirror of Tuner. " +
                                "Edit on PC; Store/Get/Send on phone are locked."
                        } else {
                            "Offline working set. Get from library · Store to library · " +
                                "Send to Feather (MCU). Connect Feather pushes active profile once."
                        },
                    color = TechTextDark.copy(alpha = 0.62f),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.padding(top = 0.dp, bottom = 4.dp),
                )
                FeatherPatientProfile.EDITABLE_COEFF_KEYS.forEach { key ->
                    OutlinedTextField(
                        value = coeffDraft[key].orEmpty(),
                        onValueChange = { v ->
                            if (!tunerLinked) {
                                coeffDraft = coeffDraft + (key to v)
                            }
                        },
                        enabled = !tunerLinked,
                        readOnly = tunerLinked,
                        label = { Text(key, fontSize = 11.sp) },
                        singleLine = true,
                        textStyle =
                            TextStyle(
                                color = TechTextDark,
                                fontSize = 13.sp,
                                lineHeight = 16.sp,
                            ),
                        keyboardOptions =
                            KeyboardOptions(
                                keyboardType =
                                    if (key.contains("frac") || key.contains("outlier")) {
                                        KeyboardType.Decimal
                                    } else {
                                        KeyboardType.Number
                                    },
                            ),
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TechTextDark,
                                unfocusedTextColor = TechTextDark,
                                disabledTextColor = TechTextDark.copy(alpha = 0.72f),
                                focusedBorderColor = TechInfoBlue,
                                unfocusedBorderColor = TechPanelBorder,
                                disabledBorderColor = TechPanelBorder.copy(alpha = 0.55f),
                                focusedLabelColor = TechInfoBlue,
                                unfocusedLabelColor = TechTextDark.copy(alpha = 0.55f),
                                disabledLabelColor = TechTextDark.copy(alpha = 0.45f),
                                cursorColor = TechInfoBlue,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                    )
                }
                val coeffsDirty =
                    FeatherPatientProfile.EDITABLE_COEFF_KEYS.any { key ->
                        coeffDraft[key].orEmpty() != featherActiveCoeffs[key].orEmpty()
                    }
                // When Tuner-linked, epoch updates from echo replace the draft — dirty vs echo is N/A.
                val libraryDirty =
                    if (tunerLinked) {
                        false
                    } else {
                        coeffsDirty
                    }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    if (onGetFeatherOffline != null) {
                        TextButton(
                            onClick = onGetFeatherOffline,
                            enabled = !tunerLinked && libraryDirty,
                        ) {
                            Text(
                                text = "Get",
                                color =
                                    if (!tunerLinked && libraryDirty) {
                                        TechInfoBlue
                                    } else {
                                        TechTextDark.copy(alpha = 0.35f)
                                    },
                                fontSize = 13.sp,
                            )
                        }
                    }
                    TextButton(
                        onClick = { onSaveFeatherProfileCoeffs(coeffDraft) },
                        enabled = !tunerLinked && libraryDirty,
                    ) {
                        Text(
                            text = "Store",
                            color =
                                if (!tunerLinked && libraryDirty) {
                                    TechInfoBlue
                                } else {
                                    TechTextDark.copy(alpha = 0.35f)
                                },
                            fontSize = 13.sp,
                        )
                    }
                    if (onSendFeatherOffline != null && featherBleConnected) {
                        TextButton(
                            onClick = { onSendFeatherOffline(coeffDraft) },
                            enabled = !tunerLinked,
                        ) {
                            Text(
                                text = "Send to Feather",
                                color =
                                    if (!tunerLinked) {
                                        TechInfoBlue
                                    } else {
                                        TechTextDark.copy(alpha = 0.35f)
                                    },
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "ECG-Box detector",
            color = TechTextDark,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text =
                buildString {
                    val human = featherHumanPhase(featherBlePhase, featherBleDetail)
                    if (human.isNotBlank()) {
                        append(human)
                    } else {
                        append(featherBlePhase)
                    }
                    if (featherBleConnected && featherBleLastIbiMs != null) {
                        append(" · Last IBI: ")
                        append(featherBleLastIbiMs)
                        append(" ms")
                    }
                },
            color = TechTextDark.copy(alpha = 0.75f),
            fontSize = 12.sp,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (!featherBleConnected && !featherSimActive) {
            Text(
                text = "Find Feather on the data path (auto-starts the detector).",
                color = TechTextDark.copy(alpha = 0.62f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            text =
                when {
                    featherEcgPacketCount > 0 && featherEcgTraceMv.isNotEmpty() ->
                        "ECG strip — ${featherEcgPacketCount} pkts, " +
                            "~${featherEcgTraceMv.size * 1000 / featherEcgSampleHz.coerceAtLeast(1)} ms @ ${featherEcgSampleHz} Hz" +
                            if (featherEcgTracePeaks.any { it }) " · R markers" else ""
                    featherBleConnected ->
                        "ECG strip — waiting for live Feather samples…"
                    featherSimActive ->
                        "ECG strip — waiting for sim samples…"
                    else ->
                        "ECG strip — Find Feather on the data path, or Change ECG sensor → Simulate"
                },
            color = TechTextDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 10.dp),
        )
        FeatherEcgStrip(
            samplesMv = featherEcgTraceMv,
            peakFlags = featherEcgTracePeaks,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE3F2FD))
                    .border(2.dp, TechInfoBlue.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
        )
        Text(
            text =
                "Find on the data path connects ECG-Box, pushes active coeffs, and starts the detector. " +
                    "Disconnect from the sensor pill.",
            color = TechTextDark.copy(alpha = 0.62f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
    }

    if (showAddPatient && onAddFeatherPatient != null) {
        AlertDialog(
            onDismissRequest = { showAddPatient = false },
            containerColor = TechHelpDialogBg,
            title = {
                Text(
                    text = "Add patient",
                    color = TechHelpText,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column {
                    Text(
                        text = "Clones demo coeffs into a new phone-local profile.",
                        color = TechHelpTextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = addPatientName,
                        onValueChange = { addPatientName = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TechHelpText,
                                unfocusedTextColor = TechHelpText,
                                focusedBorderColor = TechInfoBlue,
                                unfocusedBorderColor = TechPanelBorder,
                                focusedLabelColor = TechInfoBlue,
                                unfocusedLabelColor = TechHelpTextMuted,
                                cursorColor = TechInfoBlue,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddFeatherPatient(addPatientName)
                        showAddPatient = false
                    },
                    enabled = addPatientName.trim().isNotEmpty(),
                ) {
                    Text("Add", color = TechInfoBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPatient = false }) {
                    Text("Cancel", color = TechHelpTextMuted)
                }
            },
        )
    }

    if (showRenamePatient && onRenameFeatherPatient != null) {
        AlertDialog(
            onDismissRequest = { showRenamePatient = false },
            containerColor = TechHelpDialogBg,
            title = {
                Text(
                    text = "Edit name",
                    color = TechHelpText,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column {
                    Text(
                        text =
                            "Changes the display name only. Profile id ($featherActiveProfileId) " +
                                "stays the same — use the person’s name so PC apps can match.",
                        color = TechHelpTextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    OutlinedTextField(
                        value = renamePatientName,
                        onValueChange = { renamePatientName = it },
                        label = { Text("Display name") },
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TechHelpText,
                                unfocusedTextColor = TechHelpText,
                                focusedBorderColor = TechInfoBlue,
                                unfocusedBorderColor = TechPanelBorder,
                                focusedLabelColor = TechInfoBlue,
                                unfocusedLabelColor = TechHelpTextMuted,
                                cursorColor = TechInfoBlue,
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameFeatherPatient(renamePatientName)
                        showRenamePatient = false
                    },
                    enabled = renamePatientName.trim().isNotEmpty(),
                ) {
                    Text("Save", color = TechInfoBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenamePatient = false }) {
                    Text("Cancel", color = TechHelpTextMuted)
                }
            },
        )
    }

    if (showDeleteConfirm && onDeleteFeatherProfile != null) {
        val deleteUnlocked = deleteConfirmTyped.trim() == "DELETE"
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                deleteConfirmTyped = ""
            },
            containerColor = TechHelpDialogBg,
            title = {
                Text(
                    text = "Delete patient profile?",
                    color = TechHelpText,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column {
                    Text(
                        text =
                            "Permanently remove “$featherActiveDisplayName” ($featherActiveProfileId) " +
                                "and its coeffs from this phone. Cannot undo. Keep at least one profile.",
                        color = TechHelpTextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    Text(
                        text = "Type DELETE to confirm.",
                        color = TechHelpText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    OutlinedTextField(
                        value = deleteConfirmTyped,
                        onValueChange = { deleteConfirmTyped = it },
                        label = { Text("DELETE") },
                        singleLine = true,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TechHelpText,
                                unfocusedTextColor = TechHelpText,
                                focusedBorderColor = Color(0xFFFF8A80),
                                unfocusedBorderColor = TechPanelBorder,
                                focusedLabelColor = Color(0xFFFF8A80),
                                unfocusedLabelColor = TechHelpTextMuted,
                                cursorColor = Color(0xFFFF8A80),
                            ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFeatherProfile(featherActiveProfileId)
                        showDeleteConfirm = false
                        deleteConfirmTyped = ""
                    },
                    enabled = deleteUnlocked,
                ) {
                    Text(
                        "Delete",
                        color =
                            if (deleteUnlocked) {
                                Color(0xFFFF8A80)
                            } else {
                                TechHelpTextMuted
                            },
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        deleteConfirmTyped = ""
                    },
                ) {
                    Text("Cancel", color = TechHelpTextMuted)
                }
            },
        )
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
private fun FeatherProfileMatchAttentionBanner(
    message: String,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "profileMatchAlert")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "profileMatchAlertPulse",
    )
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .background(ProfileMatchAlertBg.copy(alpha = 0.55f + 0.45f * pulseAlpha))
                .border(
                    width = 2.dp,
                    color = ProfileMatchAlertBorder.copy(alpha = pulseAlpha),
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            text = message,
            color = ProfileMatchAlertText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun FeatherEcgStrip(
    samplesMv: List<Float>,
    peakFlags: List<Boolean> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val stroke = TechInfoBlue
    val mid = Color(0xFF90A4AE)
    val peakMark = Color(0xFFC62828)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas
        // Midline
        drawLine(
            color = mid.copy(alpha = 0.45f),
            start = Offset(0f, h * 0.5f),
            end = Offset(w, h * 0.5f),
            strokeWidth = 1f,
        )
        if (samplesMv.size < 2) return@Canvas
        var minV = samplesMv[0]
        var maxV = samplesMv[0]
        for (v in samplesMv) {
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }
        var span = maxV - minV
        if (span < 0.05f) {
            // Flat / tiny signal — keep a visible band around mid
            span = 0.2f
            val midV = (minV + maxV) * 0.5f
            minV = midV - span * 0.5f
            maxV = midV + span * 0.5f
        }
        val pad = span * 0.08f
        minV -= pad
        maxV += pad
        span = maxV - minV
        val path = Path()
        val last = samplesMv.lastIndex
        samplesMv.forEachIndexed { i, v ->
            val x = if (last == 0) 0f else w * (i.toFloat() / last.toFloat())
            val y = h * (1f - ((v - minV) / span))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = stroke,
            style =
                Stroke(
                    width = 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
        )
        // MCU lookback R markers — sparse; same scale as the wave (no extra jitter).
        if (peakFlags.size == samplesMv.size) {
            val tick = (h * 0.12f).coerceIn(6f, 14f)
            for (i in peakFlags.indices) {
                if (!peakFlags[i]) continue
                val x = if (last == 0) 0f else w * (i.toFloat() / last.toFloat())
                val y = h * (1f - ((samplesMv[i] - minV) / span))
                drawLine(
                    color = peakMark,
                    start = Offset(x, (y - tick).coerceAtLeast(0f)),
                    end = Offset(x, (y + tick).coerceAtMost(h)),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = peakMark,
                    radius = 3.5f,
                    center = Offset(x, y),
                )
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
