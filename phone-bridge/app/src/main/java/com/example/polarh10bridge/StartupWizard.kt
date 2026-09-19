package com.example.polarh10bridge

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.polarh10bridge.feather.FeatherProfileSummary
import kotlinx.coroutines.delay

/** Caregiver vs patient — maps to Tech / Patient view. */
enum class WizardRole {
    Caregiver,
    Patient,
    ;

    fun prefValue(): String =
        when (this) {
            Caregiver -> "caregiver"
            Patient -> "patient"
        }

    companion object {
        fun fromPref(raw: String?): WizardRole =
            when (raw?.trim()?.lowercase()) {
                "patient" -> Patient
                else -> Caregiver
            }
    }
}

/** Job-shaped session coach outcomes. */
enum class WizardJob {
    RecordHrv,
    Stream,
    Breathe,
    ;

    fun prefValue(): String =
        when (this) {
            RecordHrv -> "record"
            Stream -> "stream"
            Breathe -> "breathe"
        }

    fun title(): String =
        when (this) {
            RecordHrv -> "Record HRV"
            Stream -> "Stream"
            Breathe -> "Just breathe"
        }

    fun subtitle(): String =
        when (this) {
            RecordHrv -> "FlareTracker / saved HRV on the phone"
            Stream -> "VNS-TA or live host stream"
            Breathe -> "Patient breathing pacer only"
        }

    fun needsSensorAndHost(): Boolean = this != Breathe

    companion object {
        fun fromPref(raw: String?): WizardJob =
            when (raw?.trim()?.lowercase()) {
                "stream" -> Stream
                "breathe" -> Breathe
                else -> RecordHrv
            }
    }
}

private enum class WizardStep {
    Role,
    Job,
    Permissions,
    Sensor,
    Connect,
    Host,
    Ready,
    FinishTip,
}

private enum class HostChoice {
    WaitForPc,
    PhoneAlone,
}

private val UnselectedRadioRing = Color(0xFF757575)

internal fun bridgeBlePermissionsGranted(context: android.content.Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }
}

private fun bridgeBlePermissionArray(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    } else {
        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

private fun wizardPcAppLabel(clientApp: String?): String =
    when (clientApp?.trim()?.lowercase()) {
        "hertz_and_hearts", "hnh" -> "Hertz & Hearts"
        "vns_ta" -> "VNS-TA"
        "flaretracker" -> "FlareTracker"
        "ecg_box_tuner" -> "ECG-Box Tuner"
        null, "" -> "PC"
        else -> clientApp.trim()
    }

private fun stripTrailingEllipsis(raw: String): String =
    raw.trimEnd().trimEnd('.', '…', '·', ' ')

@Composable
private fun AnimatedEllipsisText(
    base: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    color: Color = TextDark.copy(alpha = 0.75f),
) {
    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(base) {
        while (true) {
            delay(450)
            dotCount = if (dotCount >= 3) 1 else dotCount + 1
        }
    }
    Text(
        text = stripTrailingEllipsis(base) + ".".repeat(dotCount),
        modifier = modifier,
        fontSize = fontSize,
        color = color,
    )
}

/**
 * Full-screen session coach. Reuses Find / session Start via callbacks; no Simulate or Tech chrome.
 * Caregiver Ready confirms active Feather patient (pick only — no coeff edit).
 */
@Composable
internal fun StartupWizardOverlay(
    state: BridgeScreenState,
    phoneIpHint: String?,
    readPhoneWifiIpv4: () -> String?,
    onRequestWifiIpRefresh: () -> Unit,
    initialRole: WizardRole,
    initialJob: WizardJob,
    onRoleChosen: (WizardRole) -> Unit,
    onJobChosen: (WizardJob) -> Unit,
    onSourceKindChosen: (SourceKind) -> Unit,
    onFindSource: () -> Unit,
    onDisconnectSensor: () -> Unit,
    onStartSession: () -> Unit,
    onSelectFeatherProfile: (String) -> Unit,
    onFinished: (markCompleted: Boolean) -> Unit,
    availableUpdate: AvailableAppUpdate?,
    onAvailableUpdateChange: (AvailableAppUpdate?) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var role by remember { mutableStateOf(initialRole) }
    var job by remember {
        mutableStateOf(
            if (initialRole == WizardRole.Patient) WizardJob.Breathe else initialJob,
        )
    }
    var step by remember { mutableStateOf(WizardStep.Role) }
    var sensorKind by remember {
        mutableStateOf(
            when (state.selectedSourceKind) {
                SourceKind.Simulate -> SourceKind.PolarH10
                else -> state.selectedSourceKind
            },
        )
    }
    var hostChoice by remember { mutableStateOf(HostChoice.WaitForPc) }
    var permissionsOk by remember { mutableStateOf(bridgeBlePermissionsGranted(context)) }
    var findPressed by remember { mutableStateOf(false) }
    var showPatientPicker by remember { mutableStateOf(false) }
    var resolvedWifiIp by remember {
        mutableStateOf(phoneIpHint ?: state.phoneWifiIpv4)
    }
    var wifiRefreshEpoch by remember { mutableIntStateOf(0) }
    val peekPhoneWifiIpv4 by rememberUpdatedState(readPhoneWifiIpv4)
    val requestWifiIpRefresh by rememberUpdatedState(onRequestWifiIpRefresh)

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            permissionsOk = bridgeBlePermissionsGranted(context)
        }

    LaunchedEffect(role) {
        onRoleChosen(role)
    }
    LaunchedEffect(job, role) {
        if (role == WizardRole.Patient) {
            job = WizardJob.Breathe
        } else if (job == WizardJob.Breathe) {
            // Caregiver no longer offers Just breathe — coerce leftover pref.
            job = WizardJob.RecordHrv
        }
        onJobChosen(job)
    }
    LaunchedEffect(sensorKind) {
        onSourceKindChosen(sensorKind)
        findPressed = false
    }

    // Keep probing after Wi‑Fi is turned on mid-wizard (DHCP often lags the radio-on event).
    LaunchedEffect(step, hostChoice, phoneIpHint, state.phoneWifiIpv4, wifiRefreshEpoch) {
        resolvedWifiIp =
            preferMoreLikelyLanDisplayIp(
                phoneIpHint ?: state.phoneWifiIpv4,
                peekPhoneWifiIpv4(),
            )
        if (step != WizardStep.Host || hostChoice != HostChoice.WaitForPc) {
            return@LaunchedEffect
        }
        requestWifiIpRefresh()
        var best = resolvedWifiIp
        repeat(80) { attempt ->
            val cur = peekPhoneWifiIpv4() ?: wifiIpv4String(context)
            if (cur != null) {
                best = preferMoreLikelyLanDisplayIp(best, cur)
                resolvedWifiIp = best
            }
            if (best != null && !isLikelyTenSlashEightLanIpv4String(best)) {
                return@LaunchedEffect
            }
            if (best != null && isLikelyTenSlashEightLanIpv4String(best) && attempt >= 8) {
                return@LaunchedEffect
            }
            delay(250)
        }
    }

    DisposableEffect(context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    c: Context?,
                    intent: Intent?,
                ) {
                    requestWifiIpRefresh()
                    wifiRefreshEpoch++
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }

    val sensorLinked =
        when (sensorKind) {
            SourceKind.PolarH10 -> state.sensorConnected
            SourceKind.Feather -> state.featherBleConnected
            SourceKind.Simulate -> state.featherSimActive
        }

    fun stepAfterJobOrRoleTowardCapture(): WizardStep =
        when {
            !job.needsSensorAndHost() -> WizardStep.Ready
            !permissionsOk -> WizardStep.Permissions
            else -> WizardStep.Sensor
        }

    fun goNext() {
        step =
            when (step) {
                WizardStep.Role ->
                    if (role == WizardRole.Patient) {
                        WizardStep.Ready
                    } else {
                        WizardStep.Job
                    }
                WizardStep.Job -> stepAfterJobOrRoleTowardCapture()
                WizardStep.Permissions -> WizardStep.Sensor
                WizardStep.Sensor -> WizardStep.Connect
                WizardStep.Connect -> WizardStep.Host
                WizardStep.Host -> WizardStep.Ready
                WizardStep.Ready -> WizardStep.FinishTip
                WizardStep.FinishTip -> step
            }
    }

    fun goBack() {
        step =
            when (step) {
                WizardStep.Role -> WizardStep.Role
                WizardStep.Job -> WizardStep.Role
                WizardStep.Permissions -> WizardStep.Job
                WizardStep.Sensor ->
                    if (permissionsOk) WizardStep.Job else WizardStep.Permissions
                WizardStep.Connect -> WizardStep.Sensor
                WizardStep.Host -> WizardStep.Connect
                WizardStep.Ready ->
                    when {
                        role == WizardRole.Patient -> WizardStep.Role
                        !job.needsSensorAndHost() -> WizardStep.Job
                        else -> WizardStep.Host
                    }
                WizardStep.FinishTip -> WizardStep.FinishTip
            }
    }

    val canContinue =
        when (step) {
            WizardStep.Role, WizardStep.Job, WizardStep.Sensor -> true
            WizardStep.Permissions -> permissionsOk
            WizardStep.Connect -> sensorLinked
            WizardStep.Host ->
                hostChoice == HostChoice.PhoneAlone || state.pcBridgeConnected
            WizardStep.Ready, WizardStep.FinishTip -> true
        }

    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                .background(UiWhite),
        color = UiWhite,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.statusBars.union(
                            WindowInsets.displayCutout.only(WindowInsetsSides.Top),
                        ),
                    ),
        ) {
            val update = availableUpdate
            if (update != null) {
                AppUpdateBanner(
                    update = update,
                    onGetUpdate = { uriHandler.openUri(update.releaseUrl) },
                    onLater = {
                        dismissAvailableAppUpdate(context, update.tagName)
                        onAvailableUpdateChange(null)
                    },
                )
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
            Text(
                text = "Start session",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = TextDark,
            )
            Text(
                text = stepSubtitle(step, job),
                fontSize = 13.sp,
                color = TextDark.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                when (step) {
                    WizardStep.Role -> {
                        WizardRadio(
                            selected = role == WizardRole.Caregiver,
                            title = "Caregiver",
                            subtitle = "Tech view — Record, Stream, Send HRV",
                            onClick = { role = WizardRole.Caregiver },
                        )
                        WizardRadio(
                            selected = role == WizardRole.Patient,
                            title = "Patient",
                            subtitle = "Breathing pacer only — skips sensor setup",
                            onClick = {
                                role = WizardRole.Patient
                                job = WizardJob.Breathe
                            },
                        )
                    }
                    WizardStep.Job -> {
                        listOf(WizardJob.RecordHrv, WizardJob.Stream).forEach { option ->
                            WizardRadio(
                                selected = job == option,
                                title = option.title(),
                                subtitle = option.subtitle(),
                                onClick = { job = option },
                            )
                        }
                    }
                    WizardStep.Permissions -> {
                        Text(
                            text =
                                "Bluetooth (and location on older Android) is required " +
                                    "to find your ECG sensor.",
                            fontSize = 14.sp,
                            color = TextDark,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { permissionLauncher.launch(bridgeBlePermissionArray()) },
                            colors = ButtonDefaults.buttonColors(containerColor = BannerRed),
                        ) {
                            Text("Allow permissions", maxLines = 1, softWrap = false)
                        }
                        if (permissionsOk) {
                            Text(
                                text = "Permissions granted.",
                                fontSize = 13.sp,
                                color = TextDark.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    WizardStep.Sensor -> {
                        val polarConnected = state.sensorConnected
                        val featherConnected = state.featherBleConnected
                        if (polarConnected || featherConnected) {
                            val parts = mutableListOf<String>()
                            if (polarConnected) {
                                parts +=
                                    if (!featherConnected &&
                                        state.connectedSensorName.isNotBlank()
                                    ) {
                                        state.connectedSensorName
                                    } else {
                                        "Polar H10"
                                    }
                            }
                            if (featherConnected) {
                                parts +=
                                    if (!polarConnected &&
                                        state.connectedSensorName.isNotBlank()
                                    ) {
                                        state.connectedSensorName
                                    } else {
                                        "ECG-Box-Feather"
                                    }
                            }
                            Text(
                                text = "Already connected: ${parts.joinToString(" · ")}",
                                fontSize = 13.sp,
                                color = TextDark.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        WizardRadio(
                            selected = sensorKind == SourceKind.PolarH10,
                            title = SourceKind.PolarH10.displayName(),
                            subtitle =
                                if (polarConnected) {
                                    "Connected now · ${SourceKind.PolarH10.pickerSubtitle()}"
                                } else {
                                    SourceKind.PolarH10.pickerSubtitle()
                                },
                            onClick = { sensorKind = SourceKind.PolarH10 },
                        )
                        WizardRadio(
                            selected = sensorKind == SourceKind.Feather,
                            title = SourceKind.Feather.displayName(),
                            subtitle =
                                if (featherConnected) {
                                    "Connected now · ${SourceKind.Feather.pickerSubtitle()}"
                                } else {
                                    SourceKind.Feather.pickerSubtitle()
                                },
                            onClick = { sensorKind = SourceKind.Feather },
                        )
                        WizardRadio(
                            selected = sensorKind == SourceKind.Simulate,
                            title = SourceKind.Simulate.displayName(),
                            subtitle =
                                if (state.featherSimActive) {
                                    "Active now · ${SourceKind.Simulate.pickerSubtitle()}"
                                } else {
                                    "${SourceKind.Simulate.pickerSubtitle()} — troubleshooting"
                                },
                            onClick = { sensorKind = SourceKind.Simulate },
                        )
                    }
                    WizardStep.Connect -> {
                        if (!sensorLinked) {
                            Text(
                                text =
                                    when (sensorKind) {
                                        SourceKind.PolarH10 ->
                                            "Wet the Polar H10 strap, wear it, then tap " +
                                                "Find sensor button, or go back to choose a " +
                                                "different sensor type."
                                        SourceKind.Feather ->
                                            "Power the ECG-Box, keep it nearby, then tap " +
                                                "Find sensor button, or go back to choose a " +
                                                "different sensor type."
                                        SourceKind.Simulate ->
                                            "No hardware needed. Tap Find sensor to start " +
                                                "synthetic IBI + ECG (troubleshooting), or go " +
                                                "back to choose a different sensor type."
                                    },
                                fontSize = 14.sp,
                                color = TextDark,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    findPressed = true
                                    onFindSource()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BannerRed),
                            ) {
                                Text("Find sensor", maxLines = 1, softWrap = false)
                            }
                            if (findPressed) {
                                Spacer(Modifier.height(12.dp))
                                val waitBase =
                                    when (sensorKind) {
                                        SourceKind.Simulate ->
                                            if (state.featherSimActive) {
                                                "Simulate active"
                                            } else {
                                                "Starting Simulate"
                                            }
                                        else ->
                                            state.featherInProgressLine()
                                                ?: when {
                                                    state.bleConnecting -> "Connecting"
                                                    state.bleScanning ->
                                                        "Looking for ${sensorKind.displayName()}"
                                                    else -> "Waiting for sensor"
                                                }
                                    }
                                if (sensorKind == SourceKind.Simulate && state.featherSimActive) {
                                    Text(
                                        text = "Simulate active",
                                        fontSize = 13.sp,
                                        color = TextDark.copy(alpha = 0.75f),
                                    )
                                } else {
                                    AnimatedEllipsisText(base = waitBase)
                                }
                            }
                        } else {
                            val name =
                                when {
                                    sensorKind == SourceKind.Simulate -> "Simulate"
                                    else ->
                                        state.connectedSensorName.ifBlank {
                                            sensorKind.displayName()
                                        }
                                }
                            Text(
                                text = "Connected: $name",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark,
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    findPressed = false
                                    onDisconnectSensor()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BannerRed),
                            ) {
                                Text(
                                    text =
                                        if (sensorKind == SourceKind.Simulate) {
                                            "Stop Simulate"
                                        } else {
                                            "Disconnect sensor"
                                        },
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                    WizardStep.Host -> {
                        if (state.pcBridgeConnected) {
                            Text(
                                text =
                                    "PC linked: ${wizardPcAppLabel(state.pcClientApp)}.",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextDark,
                            )
                            Text(
                                text =
                                    "You chose ${job.title()}. Continue when you are ready " +
                                        "to start.",
                                fontSize = 13.sp,
                                color = TextDark.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            Text(
                                text =
                                    when (job) {
                                        WizardJob.RecordHrv ->
                                            "You chose Record HRV. Link a PC now for live " +
                                                "upload, or stay phone-alone and Send HRV later."
                                        WizardJob.Stream ->
                                            "You chose Stream. Link a PC on this Wi‑Fi for " +
                                                "the live session, or continue phone-alone."
                                        WizardJob.Breathe ->
                                            "Optional PC link."
                                    },
                                fontSize = 14.sp,
                                color = TextDark,
                            )
                            Spacer(Modifier.height(8.dp))
                            WizardRadio(
                                selected = hostChoice == HostChoice.WaitForPc,
                                title = "Wait for PC",
                                subtitle =
                                    when (job) {
                                        WizardJob.RecordHrv ->
                                            "Open FlareTracker Companion, Hertz & Hearts, " +
                                                "or ECG-Box Tuner on this Wi‑Fi."
                                        WizardJob.Stream ->
                                            "Open VNS-TA, Hertz & Hearts, or ECG-Box Tuner " +
                                                "on this Wi‑Fi."
                                        WizardJob.Breathe ->
                                            "Open a host app on this Wi‑Fi."
                                    },
                                onClick = { hostChoice = HostChoice.WaitForPc },
                            )
                            WizardRadio(
                                selected = hostChoice == HostChoice.PhoneAlone,
                                title = "Phone alone for now",
                                subtitle =
                                    when (job) {
                                        WizardJob.RecordHrv ->
                                            "Send HRV from Tech when a PC connects later."
                                        WizardJob.Stream ->
                                            "A PC can link later; stream needs the host open."
                                        WizardJob.Breathe ->
                                            "Continue without a PC."
                                    },
                                onClick = { hostChoice = HostChoice.PhoneAlone },
                            )
                            if (hostChoice == HostChoice.WaitForPc) {
                                val ip = resolvedWifiIp
                                if (ip != null) {
                                    Text(
                                        text = "On your PC, connect to $ip:${state.bridgePort}",
                                        fontSize = 13.sp,
                                        color = TextDark.copy(alpha = 0.75f),
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                } else {
                                    AnimatedEllipsisText(
                                        base = "Waiting for Wi‑Fi IP",
                                        modifier = Modifier.padding(top = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                    WizardStep.Ready -> {
                        val showPatientConfirm = job.needsSensorAndHost()
                        val summary =
                            if (!showPatientConfirm) {
                                "${role.name} · ${job.title()}"
                            } else {
                                val hostLine =
                                    when {
                                        hostChoice == HostChoice.PhoneAlone -> "Phone alone"
                                        state.pcBridgeConnected ->
                                            "PC linked (${wizardPcAppLabel(state.pcClientApp)})"
                                        else -> "PC optional"
                                    }
                                "${role.name} · ${job.title()}\n" +
                                    "${sensorKind.displayName()} · $hostLine"
                            }
                        Text(
                            text = summary,
                            fontSize = 15.sp,
                            color = TextDark,
                            lineHeight = 22.sp,
                        )
                        if (showPatientConfirm) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Patient: ${state.featherActiveDisplayName}",
                                fontSize = 15.sp,
                                color = TextDark,
                                fontWeight = FontWeight.SemiBold,
                                lineHeight = 22.sp,
                            )
                            TextButton(onClick = { showPatientPicker = true }) {
                                Text(
                                    text = "Change patient…",
                                    color = BannerRed,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                        Spacer(
                            modifier =
                                Modifier.height(if (showPatientConfirm) 8.dp else 16.dp),
                        )
                        val primaryLabel =
                            when (job) {
                                WizardJob.RecordHrv -> "Start recording"
                                WizardJob.Stream -> "Start stream"
                                WizardJob.Breathe -> "Go to breathing pacer"
                            }
                        Button(
                            onClick = {
                                if (job == WizardJob.RecordHrv || job == WizardJob.Stream) {
                                    onStartSession()
                                }
                                step = WizardStep.FinishTip
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BannerRed),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(primaryLabel, maxLines = 1, softWrap = false)
                        }
                    }
                    WizardStep.FinishTip -> {
                        Text(
                            text =
                                when (job) {
                                    WizardJob.RecordHrv, WizardJob.Stream ->
                                        "When this session is finished, tap Stop on the " +
                                            "Capture panel on the main screen."
                                    WizardJob.Breathe ->
                                        "Use Start / Stop on the breathing pacer on the " +
                                            "main screen."
                                },
                            fontSize = 15.sp,
                            color = TextDark,
                            lineHeight = 22.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { onFinished(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = BannerRed),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Go to main screen", maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
            ) {
                TextButton(
                    onClick = { onFinished(true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Exit this Wizard to Main screen",
                        color = TextDark.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (step != WizardStep.FinishTip) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (step != WizardStep.Role) {
                            TextButton(onClick = { goBack() }) {
                                Text("Back", color = TextDark, maxLines = 1, softWrap = false)
                            }
                        }
                        if (step != WizardStep.Ready) {
                            Button(
                                onClick = { goNext() },
                                enabled = canContinue,
                                colors = ButtonDefaults.buttonColors(containerColor = BannerRed),
                                modifier = Modifier.defaultMinSize(minWidth = 132.dp),
                            ) {
                                Text(
                                    text = "Continue",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }

            if (showPatientPicker) {
                WizardPatientPickerDialog(
                    profiles = state.featherProfiles,
                    activeProfileId = state.featherActiveProfileId,
                    onSelect = { id ->
                        onSelectFeatherProfile(id)
                        showPatientPicker = false
                    },
                    onDismiss = { showPatientPicker = false },
                )
            }
            }
        }
    }
}


@Composable
private fun WizardPatientPickerDialog(
    profiles: List<FeatherProfileSummary>,
    activeProfileId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Text(
                text = "Active patient",
                color = TextDark,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                if (profiles.isEmpty()) {
                    Text(
                        text = "No patient profiles on this phone.",
                        color = TextDark.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                    )
                } else {
                    profiles.forEach { summary ->
                        val selected = summary.profileId == activeProfileId
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(summary.profileId) }
                                    .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text =
                                    buildString {
                                        append(summary.displayName)
                                        if (selected) append(" ✓")
                                    },
                                color = TextDark,
                                fontWeight =
                                    if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextDark)
            }
        },
    )
}


@Composable
private fun WizardRadio(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                )
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors =
                RadioButtonDefaults.colors(
                    selectedColor = BannerRed,
                    unselectedColor = UnselectedRadioRing,
                ),
        )
        Column(modifier = Modifier.padding(start = 4.dp).weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = TextDark,
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextDark.copy(alpha = 0.7f),
            )
        }
    }
}

private fun stepSubtitle(
    step: WizardStep,
    job: WizardJob,
): String =
    when (step) {
        WizardStep.Role -> "Who is using this phone?"
        WizardStep.Job -> "What do you want to do?"
        WizardStep.Permissions -> "Allow sensor access"
        WizardStep.Sensor -> "Which ECG sensor?"
        WizardStep.Connect -> "Connect the sensor"
        WizardStep.Host ->
            when (job) {
                WizardJob.RecordHrv -> "PC link for Record HRV"
                WizardJob.Stream -> "PC link for Stream"
                WizardJob.Breathe -> "PC / host link"
            }
        WizardStep.Ready -> "Ready"
        WizardStep.FinishTip -> "When you are done"
    }
