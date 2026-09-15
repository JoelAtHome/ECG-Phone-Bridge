package com.example.polarh10bridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

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

/**
 * Full-screen session coach. Reuses Find / session Start via callbacks; no Simulate or Tech chrome.
 */
@Composable
internal fun StartupWizardOverlay(
    state: BridgeScreenState,
    phoneIpHint: String?,
    initialRole: WizardRole,
    initialJob: WizardJob,
    onRoleChosen: (WizardRole) -> Unit,
    onJobChosen: (WizardJob) -> Unit,
    onSourceKindChosen: (SourceKind) -> Unit,
    onFindSource: () -> Unit,
    onStartSession: () -> Unit,
    onFinished: (markCompleted: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var role by remember { mutableStateOf(initialRole) }
    var job by remember { mutableStateOf(initialJob) }
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
        }
        onJobChosen(job)
    }
    LaunchedEffect(sensorKind) {
        onSourceKindChosen(sensorKind)
    }

    val sensorLinked =
        when (sensorKind) {
            SourceKind.PolarH10 -> state.sensorConnected
            SourceKind.Feather -> state.featherBleConnected
            SourceKind.Simulate -> false
        }

    fun goNext() {
        step =
            when (step) {
                WizardStep.Role ->
                    if (role == WizardRole.Patient) {
                        if (permissionsOk) WizardStep.Sensor else WizardStep.Permissions
                    } else {
                        WizardStep.Job
                    }
                WizardStep.Job ->
                    if (permissionsOk) WizardStep.Sensor else WizardStep.Permissions
                WizardStep.Permissions -> WizardStep.Sensor
                WizardStep.Sensor -> WizardStep.Connect
                WizardStep.Connect ->
                    if (job == WizardJob.Breathe) WizardStep.Ready else WizardStep.Host
                WizardStep.Host -> WizardStep.Ready
                WizardStep.Ready -> step
            }
    }

    fun goBack() {
        step =
            when (step) {
                WizardStep.Role -> WizardStep.Role
                WizardStep.Job -> WizardStep.Role
                WizardStep.Permissions ->
                    if (role == WizardRole.Patient) WizardStep.Role else WizardStep.Job
                WizardStep.Sensor ->
                    if (permissionsOk) {
                        if (role == WizardRole.Patient) WizardStep.Role else WizardStep.Job
                    } else {
                        WizardStep.Permissions
                    }
                WizardStep.Connect -> WizardStep.Sensor
                WizardStep.Host -> WizardStep.Connect
                WizardStep.Ready ->
                    if (job == WizardJob.Breathe) WizardStep.Connect else WizardStep.Host
            }
    }

    val canContinue =
        when (step) {
            WizardStep.Role, WizardStep.Job, WizardStep.Sensor -> true
            WizardStep.Permissions -> permissionsOk
            WizardStep.Connect -> sensorLinked
            WizardStep.Host ->
                hostChoice == HostChoice.PhoneAlone || state.pcBridgeConnected
            WizardStep.Ready -> true
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
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = "Start session",
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = TextDark,
            )
            Text(
                text = stepSubtitle(step),
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
                            subtitle = "Breathing pacer — no capture controls",
                            onClick = {
                                role = WizardRole.Patient
                                job = WizardJob.Breathe
                            },
                        )
                    }
                    WizardStep.Job -> {
                        WizardJob.entries.forEach { option ->
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
                            Text("Allow permissions")
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
                        WizardRadio(
                            selected = sensorKind == SourceKind.PolarH10,
                            title = SourceKind.PolarH10.displayName(),
                            subtitle = SourceKind.PolarH10.pickerSubtitle(),
                            onClick = { sensorKind = SourceKind.PolarH10 },
                        )
                        WizardRadio(
                            selected = sensorKind == SourceKind.Feather,
                            title = SourceKind.Feather.displayName(),
                            subtitle = SourceKind.Feather.pickerSubtitle(),
                            onClick = { sensorKind = SourceKind.Feather },
                        )
                    }
                    WizardStep.Connect -> {
                        Text(
                            text =
                                when (sensorKind) {
                                    SourceKind.PolarH10 ->
                                        "Wet the Polar H10 strap, wear it, then tap Find."
                                    else ->
                                        "Power the ECG-Box, keep it nearby, then tap Find."
                                },
                            fontSize = 14.sp,
                            color = TextDark,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onFindSource,
                            colors = ButtonDefaults.buttonColors(containerColor = BannerRed),
                        ) {
                            Text("Find sensor")
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text =
                                if (sensorLinked) {
                                    val name =
                                        state.connectedSensorName.ifBlank {
                                            sensorKind.displayName()
                                        }
                                    "Connected: $name"
                                } else {
                                    state.featherInProgressLine()
                                        ?: "Waiting for sensor…"
                                },
                            fontSize = 13.sp,
                            color = TextDark.copy(alpha = 0.75f),
                        )
                    }
                    WizardStep.Host -> {
                        WizardRadio(
                            selected = hostChoice == HostChoice.WaitForPc,
                            title = "Wait for PC",
                            subtitle =
                                "Open FlareTracker Companion, VNS-TA, or Hertz & Hearts " +
                                    "on this Wi‑Fi.",
                            onClick = { hostChoice = HostChoice.WaitForPc },
                        )
                        WizardRadio(
                            selected = hostChoice == HostChoice.PhoneAlone,
                            title = "Phone alone for now",
                            subtitle =
                                if (job == WizardJob.RecordHrv) {
                                    "Record on the phone; Send HRV when a PC connects."
                                } else {
                                    "You can still stream once a PC links later."
                                },
                            onClick = { hostChoice = HostChoice.PhoneAlone },
                        )
                        if (hostChoice == HostChoice.WaitForPc) {
                            val ip = phoneIpHint ?: state.phoneWifiIpv4
                            Text(
                                text =
                                    if (state.pcBridgeConnected) {
                                        val app = state.pcClientApp?.ifBlank { null } ?: "PC"
                                        "PC linked ($app)."
                                    } else if (ip != null) {
                                        "On your PC, connect to $ip:${state.bridgePort}"
                                    } else {
                                        "Waiting for Wi‑Fi IP…"
                                    },
                                fontSize = 13.sp,
                                color = TextDark.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                    WizardStep.Ready -> {
                        val hostLine =
                            when {
                                job == WizardJob.Breathe -> "No PC needed"
                                hostChoice == HostChoice.PhoneAlone -> "Phone alone"
                                state.pcBridgeConnected -> "PC linked"
                                else -> "PC optional"
                            }
                        Text(
                            text =
                                "${role.name} · ${job.title()}\n" +
                                    "${sensorKind.displayName()} · $hostLine",
                            fontSize = 15.sp,
                            color = TextDark,
                            lineHeight = 22.sp,
                        )
                        Spacer(Modifier.height(16.dp))
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
                                onFinished(true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BannerRed),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(primaryLabel)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onFinished(true) }) {
                    Text("Exit to main screen", color = TextDark.copy(alpha = 0.7f))
                }
                Row {
                    if (step != WizardStep.Role) {
                        TextButton(onClick = { goBack() }) {
                            Text("Back", color = TextDark)
                        }
                    }
                    if (step != WizardStep.Ready) {
                        Button(
                            onClick = { goNext() },
                            enabled = canContinue,
                            colors = ButtonDefaults.buttonColors(containerColor = BannerRed),
                        ) {
                            Text("Continue")
                        }
                    }
                }
            }
        }
    }
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
        Column(modifier = Modifier.padding(start = 4.dp)) {
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

private fun stepSubtitle(step: WizardStep): String =
    when (step) {
        WizardStep.Role -> "Who is using this phone?"
        WizardStep.Job -> "What do you want to do?"
        WizardStep.Permissions -> "Allow sensor access"
        WizardStep.Sensor -> "Which ECG sensor?"
        WizardStep.Connect -> "Connect the sensor"
        WizardStep.Host -> "PC / host link"
        WizardStep.Ready -> "Ready"
    }
