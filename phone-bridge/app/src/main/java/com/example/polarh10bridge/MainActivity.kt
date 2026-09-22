package com.example.polarh10bridge

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.polarh10bridge.ui.theme.ECGPhoneBridgeTheme
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarSensorSetting
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

internal val BannerRed = Color(0xFFC1121F)
internal val UiWhite = Color.White
internal val TextDark = Color(0xFF1A1A1A)

/** UDP port: Hertz & Hearts broadcasts here; we reply so the PC can find this phone. */
private const val PHONE_UDP_DISCOVERY_PORT = 45124

private const val PHONE_UDP_DISCOVER_PREFIX = "HnH_PHONE_BRIDGE_DISCOVER_V1"
private const val BRIDGE_PREFS_NAME = "bridge_prefs"
private const val BRIDGE_PORT_PREF_KEY = "bridge_port"
private const val BRIDGE_BG_KEEPALIVE_PREF_KEY = "bridge_bg_keepalive"
private const val BRIDGE_PACER_PRESET_PREF_KEY = "bridge_pacer_preset"
private const val BRIDGE_SESSION_MODE_PREF_KEY = "bridge_session_mode"
private const val BRIDGE_TECH_VIEW_PREF_KEY = "bridge_tech_view"
private const val BRIDGE_SOURCE_KIND_PREF_KEY = "bridge_source_kind"
private const val BRIDGE_WIZARD_COMPLETED_PREF_KEY = "bridge_wizard_completed"
private const val BRIDGE_WIZARD_LAST_ROLE_PREF_KEY = "bridge_wizard_last_role"
private const val BRIDGE_WIZARD_LAST_JOB_PREF_KEY = "bridge_wizard_last_job"
private const val BRIDGE_PROTOCOL_ID = "phone_bridge_ndjson_v1"
private const val BRIDGE_PORT_DEFAULT = 8765
private const val BRIDGE_PORT_MIN = 1024
private const val BRIDGE_PORT_MAX = 65535
private const val BLE_ROW_STALE_MS = 5_000L
private const val BLE_ROW_PRUNE_INTERVAL_MS = 1_000L
/** Watchdog: restart RSSI scan if the stack dropped it (and pacing for Polar fallback when MAC unknown). */
private const val BLE_RSSI_RESUBSCRIBE_MS = 2_000L
/**
 * If no RSSI sample arrives for this long while connected, force re-arm the LE scan.
 * Android can leave a scan "running" that never delivers results for an already-connected peripheral.
 */
private const val BLE_RSSI_STALE_REARM_MS = 4_000L
/** Polar-only fallback: short burst scan when we have no BLE address to match (rare). */
private const val BLE_RSSI_POLAR_BURST_MS = 3_500L
/** App-level TCP traffic so idle Wi-Fi/NAT paths do not drop a quiet PC link (~60s). */
private const val BRIDGE_LINK_PING_MS = 10_000L
/**
 * Scan probes open TCP and close without sending a line. Wait this long before
 * discarding them. A real host sends client_info immediately.
 */
private const val BRIDGE_PROVISIONAL_READ_MS = 8_000
/** Min time between on-screen connected-sensor dBm updates (scan may run faster). */
private const val BLE_RSSI_UI_THROTTLE_MS = 1_500L

internal data class BleDeviceRow(
    val deviceId: String,
    val address: String,
    val displayName: String,
    val rssi: Int,
    val lastSeenElapsedMs: Long,
)

private fun normBleAddr(s: String): String = s.trim().lowercase().replace(":", "")

private fun looksLikeBleMac(s: String): Boolean {
    val t = s.trim()
    return t.contains(':') && t.length >= 12
}

private fun samePhysicalBleRow(row: BleDeviceRow, info: PolarDeviceInfo): Boolean {
    if (row.deviceId.equals(info.deviceId, ignoreCase = true)) return true
    val ra = normBleAddr(row.address)
    val ia = normBleAddr(info.address)
    if (ra.length >= 8 && ia.length >= 8 && ra == ia) return true
    val rid = normBleAddr(row.deviceId)
    val iid = normBleAddr(info.deviceId)
    if (ia.isNotEmpty() && rid == ia) return true
    if (ra.isNotEmpty() && iid == ra) return true
    return false
}

private fun mergePolarScanRow(row: BleDeviceRow, info: PolarDeviceInfo): BleDeviceRow {
    val rssi = maxOf(row.rssi, info.rssi)
    val displayName =
        when {
            info.rssi > row.rssi && info.name.isNotBlank() -> info.name.trim()
            row.displayName.isNotBlank() -> row.displayName
            else -> info.name.ifBlank { "Polar device" }
        }
    val deviceId = preferredPolarConnectId(row.deviceId, info.deviceId)
    val address =
        when {
            info.address.isNotBlank() -> info.address
            else -> row.address
        }
    return BleDeviceRow(
        deviceId = deviceId,
        address = address,
        displayName = displayName,
        rssi = rssi,
        lastSeenElapsedMs = SystemClock.elapsedRealtime(),
    )
}

private fun preferredPolarConnectId(a: String, b: String): String {
    val aMac = looksLikeBleMac(a)
    val bMac = looksLikeBleMac(b)
    return when {
        !aMac && bMac -> a
        aMac && !bMac -> b
        else -> b
    }
}

private fun connectedSensorSingleLine(name: String, deviceId: String): String {
    val n = name.trim()
    val id = deviceId.trim()
    if (n.isEmpty()) return id.ifEmpty { "Sensor" }
    if (id.isEmpty()) return n
    if (n.equals(id, ignoreCase = true)) return n
    val nu = n.uppercase(Locale.US)
    val iu = id.uppercase(Locale.US)
    if (nu.endsWith(iu)) return n.trimEnd()
    if (nu.contains(iu)) return n
    return n
}

internal data class BridgeScreenState(
    val bleDialogVisible: Boolean = false,
    val bleScanning: Boolean = false,
    val bleRows: List<BleDeviceRow> = emptyList(),
    val bleSelectedId: String? = null,
    val bleConnecting: Boolean = false,
    val sensorConnected: Boolean = false,
    val connectedSensorName: String = "",
    val connectedSensorId: String = "",
    val connectedSensorAddress: String = "",
    val connectedSensorRssi: Int? = null,
    val phoneWifiIpv4: String? = null,
    val phoneWifiSubnetMask: String? = null,
    val bridgePort: Int = BRIDGE_PORT_DEFAULT,
    val keepAliveInBackground: Boolean = true,
    val foregroundServiceActive: Boolean = false,
    /** Outbound TCP session: PC connected to this phone's bridge port. */
    val pcBridgeConnected: Boolean = false,
    val pcBridgeIp: String? = null,
    val pcBridgeUserName: String? = null,
    val pcClientApp: String? = null,
    val sessionMode: BridgeSessionMode = BridgeSessionMode.Stream,
    val sessionKind: BridgeSessionKind = BridgeSessionKind.Session,
    val sessionActive: Boolean = false,
    val sessionId: String? = null,
    val sessionIbiCount: Int = 0,
    val lastRmssdMs: Double? = null,
    val techView: Boolean = false,
    val sessionStartedElapsedMs: Long = 0L,
    val settleTrimSec: Double = 45.0,
    val sessionTargetSec: Double = 105.0,
    val recentHrBpm: Double? = null,
    /** Skin/electrode contact from sensor HR — not BLE RSSI. */
    val sensorContact: SensorContactState = SensorContactState.Unknown,
    /**
     * Feather MCU lead-off when [featherUseLeadsOff] is true. Null until first
     * status/QC notify with LOD fields (or after disconnect reset).
     */
    val featherLeadsOff: Boolean? = null,
    /** Compile-time MCU `USE_LEADS_OFF`; when false, hosts ignore [featherLeadsOff]. */
    val featherUseLeadsOff: Boolean? = null,
    val lastAcceptedBeats: Int = 0,
    val lastQualityFlags: List<String> = emptyList(),
    /** Tech-only: inject synthetic Feather IBIs into the bridge edge (no BLE box required). */
    val featherSimActive: Boolean = false,
    /** Tech Feather GATT client (live ECG-Box-Feather). */
    val featherBleConnected: Boolean = false,
    val featherBlePhase: String = "Idle",
    val featherBleDetail: String = "",
    val featherBleLastIbiMs: Int? = null,
    /** Rolling Feather ECG (mV) for Tech strip; also forwarded to host while streaming. */
    val featherEcgTraceMv: List<Float> = emptyList(),
    /** Parallel to [featherEcgTraceMv] — MCU lookback R markers (v2+); empty if unsupported. */
    val featherEcgTracePeaks: List<Boolean> = emptyList(),
    val featherEcgSampleHz: Int = 250,
    val featherEcgPacketCount: Int = 0,
    /** Tech patient profile picker (Feather coeffs). */
    val featherProfiles: List<com.example.polarh10bridge.feather.FeatherProfileSummary> = emptyList(),
    val featherActiveProfileId: String = "demo",
    val featherActiveDisplayName: String = "Demo",
    val featherProfileStatus: String = "",
    /** PC patient hint would override active Feather profile — Tech confirm. */
    val featherProfileHintPending:
        com.example.polarh10bridge.feather.FeatherProfileHintMatcher.PendingConfirm? = null,
    /** Editable coeff draft source; bump [featherCoeffsEpoch] whenever disk SoR changes. */
    val featherActiveCoeffs: Map<String, String> = emptyMap(),
    val featherCoeffsEpoch: Int = 0,
    val selectedSourceKind: SourceKind = SourceKind.PolarH10,
    /** Feather Find overlay; hide without cancel keeps BLE work going. */
    val featherConnectOverlayVisible: Boolean = false,
    /** Last Record ritual on disk (Tech line + Send). */
    val lastRitualSessionId: String? = null,
    val lastRitualAcked: Boolean = false,
    val lastRitualRmssdMs: Double? = null,
    val lastRitualEmittedAt: String? = null,
)

class MainActivity : ComponentActivity() {
    private lateinit var polarApi: PolarBleApi
    private val disposables = CompositeDisposable()
    private val bridgeExecutor = Executors.newSingleThreadExecutor()
    private val discoveryExecutor = Executors.newSingleThreadExecutor()
    /** Socket writes must not run on the UI thread (NetworkOnMainThreadException drops Stop). */
    private val bridgeWriteExecutor = Executors.newSingleThreadExecutor()
    /**
     * One reader thread per PC socket. A Scan probe must be readable while the live
     * host is still blocked in readLine(); a single thread cannot do both.
     */
    private val bridgeReadExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rrStreamingStarted = false
    private var ecgStreamingStarted = false

    private var hrDisposable: io.reactivex.rxjava3.disposables.Disposable? = null
    private val sessionController = BridgeSessionController()
    private var ecgDisposable: io.reactivex.rxjava3.disposables.Disposable? = null

    /**
     * When Polar reports supported no-contact, drop RR/ECG from the host wire and
     * official RMSSD intake. RSSI never drives this flag.
     */
    @Volatile
    private var telemetryAllowedByContact = true
    private var lastPublishedContactState: SensorContactState? = null
    private var bleSearchDisposable: Disposable? = null
    private var featherProfileStore: com.example.polarh10bridge.feather.FeatherProfileStore? = null
    private var ritualPackageStore: com.example.polarh10bridge.ritual.RitualPackageStore? = null
    /** Avoid double auto-push of the same package on one TCP session. */
    private var ritualAutoPushSentSessionId: String? = null
    private val ritualClientInfoFallbackRunnable =
        Runnable {
            maybeAutoPushRitual(screenState.value.pcClientApp, forceHnHCompatible = true)
        }
    private var featherBleClient: com.example.polarh10bridge.feather.FeatherBleClient? = null
    private val featherLeadOffTracker =
        com.example.polarh10bridge.feather.FeatherLeadOffTracker()
    /** After Tech Keep on a PC patient hint, suppress re-prompt for the same pc_user. */
    private var featherHintKeepDebounceKey: String? = null
    private var featherHintKeepDebounceUntilElapsedMs: Long = 0L
    private var featherSimIbiIndex = 0
    private var featherSimElapsedMs = 0L
    private var featherSimCurrentIbiMs = 800
    private var featherSimEcgPhase01 = 0.0
    private val featherSimRunnable =
        object : Runnable {
            override fun run() {
                if (!screenState.value.featherSimActive) return
                // RSA (~12 breaths/min) + slow wander so HR breathes and RMSSD drifts.
                val rr =
                    com.example.polarh10bridge.feather.FeatherSimIbi.nextIbiMs(
                        elapsedSimMs = featherSimElapsedMs,
                        beatIndex = featherSimIbiIndex,
                    )
                featherSimIbiIndex++
                featherSimElapsedMs += rr.toLong()
                featherSimCurrentIbiMs = rr
                // Align synthetic R with this beat boundary.
                featherSimEcgPhase01 = 0.0
                ingestSourceRrMs(rr, updateHrEveryBeat = true)
                mainHandler.postDelayed(this, rr.toLong())
            }
        }
    private val featherSimEcgRunnable =
        object : Runnable {
            override fun run() {
                if (!screenState.value.featherSimActive) return
                val batchN = 20
                val hz = com.example.polarh10bridge.feather.FeatherSimEcg.SAMPLE_HZ
                val (nextPhase, samples) =
                    com.example.polarh10bridge.feather.FeatherSimEcg.fillBatchMv(
                        phase01 = featherSimEcgPhase01,
                        ibiMs = featherSimCurrentIbiMs.toDouble(),
                        count = batchN,
                        sampleHz = hz,
                    )
                featherSimEcgPhase01 = nextPhase
                ingestFeatherEcgMv(hz, samples.toList())
                mainHandler.postDelayed(this, batchN * 1000L / hz)
            }
        }
    /** Direct LE scan for RSSI; Polar search often never re-emits the connected peripheral. */
    private var rssiLeScanCallback: ScanCallback? = null
    private var bleRssiPolarFallbackDisposable: Disposable? = null

    private var lastConnectedRssiUiElapsedMs = 0L
    /** Wall clock of last RSSI sample received (pre-throttle); used to detect zombie LE scans. */
    private var lastConnectedRssiSampleElapsedMs = 0L
    private var pendingConnectedRssiValue: Int? = null
    private var pendingConnectedRssiAddressNorm: String? = null
    private var rssiUiThrottleFlushScheduled = false

    private val rssiUiThrottleFlushRunnable =
        Runnable {
            rssiUiThrottleFlushScheduled = false
            val v = pendingConnectedRssiValue ?: return@Runnable
            val addrNorm = pendingConnectedRssiAddressNorm
            pendingConnectedRssiValue = null
            pendingConnectedRssiAddressNorm = null
            updateScreen { current ->
                if (!current.sensorConnected) return@updateScreen current
                if (addrNorm != null &&
                    normBleAddr(current.connectedSensorAddress) != addrNorm
                ) {
                    return@updateScreen current
                }
                current.copy(connectedSensorRssi = v)
            }
            lastConnectedRssiUiElapsedMs = SystemClock.elapsedRealtime()
        }

    private val writerLock = Any()

    /** Keeps TCP warm and mirrors recording state to the PC during long Record sessions. */
    private val bridgeWireKeepAliveRunnable =
        object : Runnable {
            override fun run() {
                if (sessionController.isActive() && bridgeWriter != null) {
                    sendBridgeJsonLine(sessionController.sessionStateJson().toString())
                }
                if (sessionController.isActive()) {
                    mainHandler.postDelayed(this, 15_000L)
                }
            }
        }

    /** Keeps the PC TCP path warm when no ECG/RR is flowing. Hosts ignore type ping. */
    private val bridgeLinkPingRunnable =
        object : Runnable {
            override fun run() {
                if (bridgeClient == null) return
                sendBridgeJsonLine("""{"type":"ping","role":"phone"}""")
                if (bridgeClient != null) {
                    mainHandler.postDelayed(this, BRIDGE_LINK_PING_MS)
                }
            }
        }

    @Volatile
    private var bridgeWriter: java.io.PrintWriter? = null
    @Volatile
    private var bridgeClient: Socket? = null
    private var bridgeWifiLock: WifiManager.WifiLock? = null

    private val discoverySocketLock = Any()
    @Volatile
    private var udpDiscoverySocket: DatagramSocket? = null
    private val bridgeServerSocketLock = Any()
    @Volatile
    private var bridgeServerSocket: ServerSocket? = null
    @Volatile
    private var bridgePort: Int = BRIDGE_PORT_DEFAULT

    private val screenState = mutableStateOf(BridgeScreenState())

    /** Bumped when link IPv4 may have changed so Compose restarts IP-hint polling (Handler updates alone can be missed). */
    private val bridgeIpHintRefreshSession = mutableStateOf(0)

    private fun loadBridgePortPref(): Int {
        val prefs = getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getInt(BRIDGE_PORT_PREF_KEY, BRIDGE_PORT_DEFAULT)
        return saved.coerceIn(BRIDGE_PORT_MIN, BRIDGE_PORT_MAX)
    }

    private fun saveBridgePortPref(port: Int) {
        getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(BRIDGE_PORT_PREF_KEY, port.coerceIn(BRIDGE_PORT_MIN, BRIDGE_PORT_MAX))
            .apply()
    }

    private fun loadKeepAliveInBackgroundPref(): Boolean =
        getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(BRIDGE_BG_KEEPALIVE_PREF_KEY, true)

    private fun saveKeepAliveInBackgroundPref(enabled: Boolean) {
        getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(BRIDGE_BG_KEEPALIVE_PREF_KEY, enabled)
            .apply()
    }

    private fun loadSessionModePref(): BridgeSessionMode {
        val raw =
            getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(BRIDGE_SESSION_MODE_PREF_KEY, null)
        return BridgeSessionMode.fromWire(raw) ?: BridgeSessionMode.Stream
    }

    private fun saveSessionModePref(mode: BridgeSessionMode) {
        getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(BRIDGE_SESSION_MODE_PREF_KEY, mode.wireValue())
            .apply()
    }

    private fun loadTechViewPref(): Boolean =
        getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(BRIDGE_TECH_VIEW_PREF_KEY, false)

    private fun saveTechViewPref(enabled: Boolean) {
        getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(BRIDGE_TECH_VIEW_PREF_KEY, enabled)
            .apply()
    }

    private fun setTechView(enabled: Boolean) {
        saveTechViewPref(enabled)
        if (!enabled && screenState.value.featherSimActive) {
            setFeatherSimActive(false)
        }
        updateScreen {
            val kind =
                if (!enabled && it.selectedSourceKind == SourceKind.Simulate) {
                    SourceKind.Feather
                } else {
                    it.selectedSourceKind
                }
            it.copy(techView = enabled, selectedSourceKind = kind)
        }
    }

    private fun loadSourceKindPref(): SourceKind =
        SourceKind.fromPref(
            getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(BRIDGE_SOURCE_KIND_PREF_KEY, null),
        )

    private fun loadWizardCompletedPref(): Boolean =
        getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(BRIDGE_WIZARD_COMPLETED_PREF_KEY, false)

    private fun saveWizardCompletedPref(completed: Boolean) {
        getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(BRIDGE_WIZARD_COMPLETED_PREF_KEY, completed)
            .apply()
    }

    private fun loadWizardLastRolePref(): WizardRole =
        WizardRole.fromPref(
            getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(BRIDGE_WIZARD_LAST_ROLE_PREF_KEY, null),
        )

    private fun saveWizardLastRolePref(role: WizardRole) {
        getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(BRIDGE_WIZARD_LAST_ROLE_PREF_KEY, role.prefValue())
            .apply()
    }

    private fun loadWizardLastJobPref(): WizardJob =
        WizardJob.fromPref(
            getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(BRIDGE_WIZARD_LAST_JOB_PREF_KEY, null),
        )

    private fun saveWizardLastJobPref(job: WizardJob) {
        getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(BRIDGE_WIZARD_LAST_JOB_PREF_KEY, job.prefValue())
            .apply()
    }

    private fun saveSourceKindPref(kind: SourceKind) {
        // Simulate is session/Tech-only — persist as Feather so Patient never restores it.
        val persisted =
            if (kind == SourceKind.Simulate) SourceKind.Feather else kind
        getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(BRIDGE_SOURCE_KIND_PREF_KEY, persisted.wireValue())
            .apply()
    }

    private fun setSelectedSourceKind(kind: SourceKind) {
        val prev = screenState.value.selectedSourceKind
        when (kind) {
            SourceKind.Simulate -> {
                // Re-tap toggles sim (same as the old Tech link).
                if (prev == SourceKind.Simulate) {
                    setFeatherSimActive(!screenState.value.featherSimActive)
                    return
                }
                if (screenState.value.sensorConnected ||
                    screenState.value.featherBleConnected
                ) {
                    // UI shows FeatherSimBlockedDialog; refuse silently here.
                    Log.w("HnHBridge", "Refuse Simulate while live sensor connected")
                    return
                }
                setFeatherSimActive(true)
                return
            }
            SourceKind.PolarH10 -> {
                if (kind == prev) return
                disconnectFeatherBle()
                setFeatherSimActive(false)
            }
            SourceKind.Feather -> {
                if (kind == prev) {
                    // Live Feather selected again while sim was on Simulate path already handled.
                    return
                }
                setFeatherSimActive(false)
                if (screenState.value.sensorConnected) {
                    disconnectConnectedSensor()
                }
            }
        }
        saveSourceKindPref(kind)
        updateScreen {
            it.copy(
                selectedSourceKind = kind,
                featherConnectOverlayVisible = false,
            )
        }
    }

    private fun beginFindSource() {
        when (screenState.value.selectedSourceKind) {
            SourceKind.PolarH10 -> {
                if (screenState.value.featherBleConnected ||
                    screenState.value.featherBlePhase != "Idle"
                ) {
                    disconnectFeatherBle()
                }
                setFeatherSimActive(false)
                beginSensorScan()
            }
            SourceKind.Feather -> {
                if (screenState.value.sensorConnected) {
                    disconnectConnectedSensor()
                }
                updateScreen { it.copy(featherConnectOverlayVisible = true) }
                beginFeatherBleTest()
            }
            SourceKind.Simulate -> {
                if (screenState.value.sensorConnected ||
                    screenState.value.featherBleConnected
                ) {
                    Log.w("HnHBridge", "Refuse Feather sim while live sensor connected")
                    return
                }
                setFeatherSimActive(!screenState.value.featherSimActive)
            }
        }
    }

    private fun sourceDeviceWire(): String = screenState.value.sourceDeviceWire()

    private fun ensureFeatherBleClient(): com.example.polarh10bridge.feather.FeatherBleClient {
        featherBleClient?.let { return it }
        val client =
            com.example.polarh10bridge.feather.FeatherBleClient(
                context = this,
                mainHandler = mainHandler,
                listener =
                    object : com.example.polarh10bridge.feather.FeatherBleClient.Listener {
                        override fun onPhase(
                            phase: com.example.polarh10bridge.feather.FeatherBleClient.Phase,
                            detail: String,
                        ) {
                            val connected =
                                phase ==
                                    com.example.polarh10bridge.feather.FeatherBleClient.Phase.Ready ||
                                    phase ==
                                    com.example.polarh10bridge.feather.FeatherBleClient.Phase
                                        .Streaming ||
                                    phase ==
                                    com.example.polarh10bridge.feather.FeatherBleClient.Phase
                                        .Discovering ||
                                    phase ==
                                    com.example.polarh10bridge.feather.FeatherBleClient.Phase
                                        .Connecting
                            if (connected) {
                                saveSourceKindPref(SourceKind.Feather)
                            }
                            updateScreen {
                                it.copy(
                                    featherBlePhase = phase.name,
                                    featherBleDetail = detail,
                                    featherBleConnected = connected ||
                                        (it.featherBleConnected &&
                                            phase !=
                                            com.example.polarh10bridge.feather.FeatherBleClient
                                                .Phase.Idle &&
                                            phase !=
                                            com.example.polarh10bridge.feather.FeatherBleClient
                                                .Phase.Error &&
                                            phase !=
                                            com.example.polarh10bridge.feather.FeatherBleClient
                                                .Phase.Scanning),
                                    // Feather GATT has no contact bit — never fake InContact.
                                    sensorContact =
                                        if (it.sensorConnected) {
                                            it.sensorContact
                                        } else {
                                            SensorContactState.Unknown
                                        },
                                    connectedSensorName =
                                        if (connected) {
                                            com.example.polarh10bridge.feather.FeatherBleContract
                                                .ADVERTISED_NAME_PRIMARY
                                        } else if (
                                            phase ==
                                                com.example.polarh10bridge.feather.FeatherBleClient
                                                    .Phase.Idle ||
                                                phase ==
                                                com.example.polarh10bridge.feather.FeatherBleClient
                                                    .Phase.Error
                                        ) {
                                            if (it.sensorConnected) it.connectedSensorName else ""
                                        } else {
                                            it.connectedSensorName
                                        },
                                    selectedSourceKind =
                                        if (connected) {
                                            SourceKind.Feather
                                        } else {
                                            it.selectedSourceKind
                                        },
                                    featherConnectOverlayVisible =
                                        when (phase) {
                                            com.example.polarh10bridge.feather.FeatherBleClient
                                                .Phase.Streaming,
                                            com.example.polarh10bridge.feather.FeatherBleClient
                                                .Phase.Idle,
                                            -> false
                                            else -> it.featherConnectOverlayVisible
                                        },
                                )
                            }
                            if (phase ==
                                com.example.polarh10bridge.feather.FeatherBleClient.Phase.Idle ||
                                phase ==
                                com.example.polarh10bridge.feather.FeatherBleClient.Phase.Error
                            ) {
                                if (phase ==
                                    com.example.polarh10bridge.feather.FeatherBleClient.Phase.Idle
                                ) {
                                    featherLeadOffTracker.reset()
                                }
                                updateScreen {
                                    it.copy(
                                        featherBleConnected = false,
                                        featherBleLastIbiMs =
                                            if (phase ==
                                                com.example.polarh10bridge.feather.FeatherBleClient
                                                    .Phase.Idle
                                            ) {
                                                null
                                            } else {
                                                it.featherBleLastIbiMs
                                            },
                                        featherEcgTraceMv =
                                            if (phase ==
                                                com.example.polarh10bridge.feather.FeatherBleClient
                                                    .Phase.Idle
                                            ) {
                                                emptyList()
                                            } else {
                                                it.featherEcgTraceMv
                                            },
                                        featherEcgTracePeaks =
                                            if (phase ==
                                                com.example.polarh10bridge.feather.FeatherBleClient
                                                    .Phase.Idle
                                            ) {
                                                emptyList()
                                            } else {
                                                it.featherEcgTracePeaks
                                            },
                                        featherEcgPacketCount =
                                            if (phase ==
                                                com.example.polarh10bridge.feather.FeatherBleClient
                                                    .Phase.Idle
                                            ) {
                                                0
                                            } else {
                                                it.featherEcgPacketCount
                                            },
                                        featherLeadsOff =
                                            if (phase ==
                                                com.example.polarh10bridge.feather.FeatherBleClient
                                                    .Phase.Idle
                                            ) {
                                                null
                                            } else {
                                                it.featherLeadsOff
                                            },
                                        featherUseLeadsOff =
                                            if (phase ==
                                                com.example.polarh10bridge.feather.FeatherBleClient
                                                    .Phase.Idle
                                            ) {
                                                null
                                            } else {
                                                it.featherUseLeadsOff
                                            },
                                    )
                                }
                            }
                        }

                        override fun onIbiMs(values: List<Int>) {
                            for (rr in values) {
                                if (rr <= 0) continue
                                updateScreen { it.copy(featherBleLastIbiMs = rr) }
                                ingestSourceRrMs(rr, updateHrEveryBeat = true)
                            }
                        }

                        override fun onStatusJson(json: String) {
                            Log.d("HnHBridge", "Feather status: $json")
                            // Keep UI detail human (phase strings). Raw status JSON is for Tuner/logs.
                            forwardMcuCoeffsStatusToTunerIfNeeded(json)
                            handleFeatherLeadOffStatus(json)
                        }

                        override fun onEcgSamplesUv(
                            sampleHz: Int,
                            samplesUv: List<Int>,
                            peakFlags: List<Boolean>,
                        ) {
                            if (samplesUv.isEmpty()) return
                            val mv =
                                com.example.polarh10bridge.feather.FeatherPacketCodec
                                    .samplesUvToMv(samplesUv)
                                    .map { it.toFloat() }
                            ingestFeatherEcgMv(sampleHz, mv, peakFlags)
                        }
                    },
            )
        featherBleClient = client
        return client
    }

    private fun beginFeatherBleTest() {
        if (screenState.value.sensorConnected) {
            Log.w("HnHBridge", "Refuse Feather BLE while Polar connected — disconnect first")
            updateScreen {
                it.copy(
                    featherBlePhase = "Error",
                    featherBleDetail = "Disconnect Polar first",
                )
            }
            return
        }
        setFeatherSimActive(false)
        val active = featherProfileStore?.loadActive()
        refreshFeatherProfileUi(
            status = "Connect will push ${active?.displayName ?: "Demo"} coeffs",
        )
        val coeffsBytes =
            active?.let {
                com.example.polarh10bridge.feather.FeatherPacketCodec.encodeCoeffsJson(
                    it.coeffsForBleWrite(),
                )
            }
        ensureFeatherBleClient().connectForTest(coeffsJsonUtf8 = coeffsBytes)
    }

    private fun refreshFeatherProfileUi(status: String? = null) {
        val store = featherProfileStore ?: return
        // Do not call ensureFactoryProfiles here — polishDemo ran on every Tuner
        // save and could rewrite demo coeffs. Seeding stays on store init / first use.
        val active = store.loadActive()
        val coeffs =
            com.example.polarh10bridge.feather.FeatherPatientProfile.coeffDraftFrom(active)
        updateScreen {
            it.copy(
                featherProfiles = store.listSummaries(),
                featherActiveProfileId = active.profileId,
                featherActiveDisplayName = active.displayName,
                featherProfileStatus = status ?: it.featherProfileStatus,
                featherActiveCoeffs = coeffs,
                featherCoeffsEpoch = it.featherCoeffsEpoch + 1,
            )
        }
    }

    private fun selectFeatherProfile(profileId: String) {
        val store = featherProfileStore ?: return
        val selected = store.setActiveProfileId(profileId)
        if (selected == null) {
            refreshFeatherProfileUi(status = "Profile not found")
            return
        }
        refreshFeatherProfileUi(status = "Active: ${selected.displayName}")
        pushActiveProfileToTunerIfLinked()
    }

    private fun clearFeatherProfileHintPending() {
        updateScreen { it.copy(featherProfileHintPending = null) }
    }

    private fun sendFeatherProfileHintStatus(message: String) {
        if (!screenState.value.pcBridgeConnected) return
        sendBridgeJsonLine(
            JSONObject()
                .put("type", "status")
                .put("message", message)
                .put("connected", true)
                .toString(),
        )
    }

    /**
     * PC `client_info.pc_user` → local Feather profile. Confirm on Tech when Feather/Simulate
     * would override the active patient. Tuner is excluded (owns profile_* sync).
     */
    private fun handleFeatherProfileHintFromClientInfo(
        pcUser: String?,
        clientApp: String?,
    ) {
        val matcher = com.example.polarh10bridge.feather.FeatherProfileHintMatcher
        if (clientApp.equals("ecg_box_tuner", ignoreCase = true)) {
            clearFeatherProfileHintPending()
            return
        }
        val hint = pcUser?.trim().orEmpty()
        if (hint.isEmpty()) {
            clearFeatherProfileHintPending()
            return
        }
        val store = featherProfileStore ?: return
        val summaries = store.listSummaries()
        val active = store.loadActive()
        when (val resolved = matcher.resolve(hint, summaries)) {
            is com.example.polarh10bridge.feather.FeatherProfileHintMatcher.Result.None -> {
                clearFeatherProfileHintPending()
                val msg = "No Feather profile for $hint"
                refreshFeatherProfileUi(status = msg)
                sendFeatherProfileHintStatus(msg)
            }
            is com.example.polarh10bridge.feather.FeatherProfileHintMatcher.Result.Ambiguous -> {
                clearFeatherProfileHintPending()
                val msg = "Ambiguous Feather profile for $hint"
                refreshFeatherProfileUi(status = msg)
                sendFeatherProfileHintStatus(msg)
            }
            is com.example.polarh10bridge.feather.FeatherProfileHintMatcher.Result.Match -> {
                if (resolved.profileId == active.profileId) {
                    clearFeatherProfileHintPending()
                    val msg = "Feather profile already: ${resolved.displayName}"
                    refreshFeatherProfileUi(status = msg)
                    sendFeatherProfileHintStatus(msg)
                    return
                }
                val source = screenState.value.selectedSourceKind
                if (source != SourceKind.Feather && source != SourceKind.Simulate) {
                    clearFeatherProfileHintPending()
                    return
                }
                val debounceKey = matcher.debounceKey(hint)
                val now = android.os.SystemClock.elapsedRealtime()
                if (debounceKey == featherHintKeepDebounceKey &&
                    now < featherHintKeepDebounceUntilElapsedMs
                ) {
                    return
                }
                if (debounceKey != featherHintKeepDebounceKey) {
                    featherHintKeepDebounceKey = null
                    featherHintKeepDebounceUntilElapsedMs = 0L
                }
                val appLabel = matcher.clientAppLabel(clientApp)
                val pending =
                    com.example.polarh10bridge.feather.FeatherProfileHintMatcher.PendingConfirm(
                        pcUser = hint,
                        clientApp = clientApp,
                        matchedProfileId = resolved.profileId,
                        matchedDisplayName = resolved.displayName,
                        currentProfileId = active.profileId,
                        currentDisplayName = active.displayName,
                    )
                updateScreen {
                    it.copy(
                        featherProfileHintPending = pending,
                        featherProfileStatus =
                            "$appLabel selected ${resolved.displayName} — confirm Switch?",
                    )
                }
                sendFeatherProfileHintStatus(
                    "Feather profile confirm: ${resolved.displayName}?",
                )
            }
        }
    }

    private fun acceptFeatherProfileHint() {
        val pending = screenState.value.featherProfileHintPending ?: return
        featherHintKeepDebounceKey = null
        featherHintKeepDebounceUntilElapsedMs = 0L
        clearFeatherProfileHintPending()
        selectFeatherProfile(pending.matchedProfileId)
        sendFeatherProfileHintStatus(
            "Feather profile switched: ${pending.matchedDisplayName}",
        )
    }

    private fun dismissFeatherProfileHint() {
        val pending = screenState.value.featherProfileHintPending ?: return
        val matcher = com.example.polarh10bridge.feather.FeatherProfileHintMatcher
        featherHintKeepDebounceKey = matcher.debounceKey(pending.pcUser)
        featherHintKeepDebounceUntilElapsedMs =
            android.os.SystemClock.elapsedRealtime() + matcher.keepDebounceMs()
        clearFeatherProfileHintPending()
        refreshFeatherProfileUi(status = "Kept Feather profile: ${pending.currentDisplayName}")
        sendFeatherProfileHintStatus(
            "Feather profile kept: ${pending.currentDisplayName}",
        )
    }

    private fun addFeatherPatient(displayName: String) {
        val store = featherProfileStore ?: return
        val created =
            try {
                store.addPatient(displayName)
            } catch (e: Exception) {
                refreshFeatherProfileUi(status = "Add failed: ${e.message}")
                return
            }
        refreshFeatherProfileUi(status = "Added ${created.displayName}")
        pushActiveProfileToTunerIfLinked()
    }

    private fun renameFeatherPatientDisplayName(newDisplayName: String) {
        val store = featherProfileStore ?: return
        val id = store.activeProfileId()
        val (saved, msg) = store.renameDisplayName(id, newDisplayName)
        if (saved == null) {
            refreshFeatherProfileUi(status = msg)
            return
        }
        refreshFeatherProfileUi(status = msg)
        pushActiveProfileToTunerIfLinked()
    }

    private fun deleteFeatherProfile(profileId: String) {
        val store = featherProfileStore ?: return
        val (ok, msg) = store.deleteProfile(profileId)
        refreshFeatherProfileUi(status = msg)
        if (!ok) return
        pushActiveProfileToTunerIfLinked()
    }

    private fun isTunerLinked(): Boolean =
        screenState.value.pcClientApp.equals("ecg_box_tuner", ignoreCase = true)

    /** Offline ← phone library (SoR). Does not touch MCU. */
    private fun getFeatherOfflineFromLibrary() {
        refreshFeatherProfileUi(status = "Get — Offline from library")
    }

    /** Offline → phone library (SoR). Does not push MCU (use Send to Feather). */
    private fun storeFeatherOfflineCoeffs(draft: Map<String, String>) {
        val store = featherProfileStore ?: return
        val active = store.loadActive()
        val (merged, err) =
            com.example.polarh10bridge.feather.FeatherPatientProfile.mergeEditableCoeffDraft(
                active.coeffs,
                draft,
            )
        if (err != null) {
            refreshFeatherProfileUi(status = err)
            return
        }
        val saved = store.save(active.copy(coeffs = merged))
        store.setActiveProfileId(saved.profileId)
        refreshFeatherProfileUi(status = "Store — library updated (${saved.displayName})")
        pushActiveProfileToTunerIfLinked()
    }

    /** Offline → MCU over BLE. Does not write phone library. */
    private fun sendFeatherOfflineToMcu(draft: Map<String, String>) {
        if (!screenState.value.featherBleConnected) {
            refreshFeatherProfileUi(status = "Send failed — Connect Feather first")
            return
        }
        val store = featherProfileStore ?: return
        val active = store.loadActive()
        val (merged, err) =
            com.example.polarh10bridge.feather.FeatherPatientProfile.mergeEditableCoeffDraft(
                active.coeffs,
                draft,
            )
        if (err != null) {
            refreshFeatherProfileUi(status = err)
            return
        }
        val bytes =
            com.example.polarh10bridge.feather.FeatherPacketCodec.encodeCoeffsJson(
                active.copy(coeffs = merged).coeffsForBleWrite(),
            )
        featherBleClient?.writeCoeffsJson(bytes)
        refreshFeatherProfileUi(status = "Send — Offline to Feather (not stored)")
    }

    private fun saveFeatherActiveCoeffs(draft: Map<String, String>) {
        // Back-compat alias for older call sites → Store (SoR only).
        storeFeatherOfflineCoeffs(draft)
    }

    private fun disconnectFeatherBle() {
        featherBleClient?.disconnect()
        featherLeadOffTracker.reset()
        updateScreen {
            it.copy(
                featherBleConnected = false,
                featherBlePhase = "Idle",
                featherBleDetail = "idle",
                featherBleLastIbiMs = null,
                featherEcgTraceMv = emptyList(),
                featherEcgTracePeaks = emptyList(),
                featherEcgPacketCount = 0,
                featherLeadsOff = null,
                featherUseLeadsOff = null,
                connectedSensorName =
                    if (it.sensorConnected) it.connectedSensorName else "",
                sensorContact =
                    if (it.sensorConnected) {
                        it.sensorContact
                    } else {
                        SensorContactState.Unknown
                    },
            )
        }
    }

    /**
     * Edge-forward MCU `use_leads_off` / `leads_off` on NDJSON `status`.
     * Does not map into [SensorContactState] / `sensor_quality`.
     */
    private fun handleFeatherLeadOffStatus(json: String) {
        val snap = featherLeadOffTracker.observe(json) ?: return
        updateScreen {
            it.copy(
                featherUseLeadsOff = snap.useLeadsOff,
                featherLeadsOff = snap.leadsOff,
            )
        }
        sendBridgeJsonLine(snap.toStatusJson(connected = true))
    }

    /** Live beats carry source_device so hosts can name the sensor without a session_state line. */
    private fun sendSourceRrLine(rr: Int) {
        sendBridgeJsonLine(
            JSONObject()
                .put("type", "rr")
                .put("rr_ms", rr)
                .put("source_device", sourceDeviceWire())
                .toString(),
        )
    }

    private fun ingestSourceRrMs(rr: Int, updateHrEveryBeat: Boolean = false) {
        if (rr <= 0) return
        val now = SystemClock.elapsedRealtime()
        sessionController.onRrMs(rr, now)
        sendSourceRrLine(rr)
        val rolling =
            sessionController.maybeRollingRmssdJson(
                now,
                sourceDeviceWire(),
            )
        if (rolling != null) {
            sendBridgeJsonLine(rolling.toString())
            val value = rolling.optDouble("rmssd_ms", Double.NaN)
            updateScreen {
                it.copy(
                    sessionIbiCount = sessionController.ibiCount,
                    lastRmssdMs = value.takeIf { v -> !v.isNaN() } ?: it.lastRmssdMs,
                    recentHrBpm = sessionController.recentHrBpm(),
                )
            }
        } else if (
            sessionController.isActive() &&
                (updateHrEveryBeat || sessionController.ibiCount % 5 == 0)
        ) {
            // During Feather sim, also refresh a preview RMSSD so Tech meters move
            // between the 30s wire snapshots (same calculator, UI-only).
            val previewRmssd =
                if (updateHrEveryBeat &&
                    sessionController.activeMode == BridgeSessionMode.Stream
                ) {
                    sessionController.refreshQualitySnapshot().rmssdMs
                } else {
                    null
                }
            updateScreen {
                it.copy(
                    sessionIbiCount = sessionController.ibiCount,
                    recentHrBpm = sessionController.recentHrBpm(),
                    lastRmssdMs = previewRmssd ?: it.lastRmssdMs,
                )
            }
        }
    }

    private fun ingestFeatherEcgMv(
        sampleHz: Int,
        samplesMv: List<Float>,
        peakFlags: List<Boolean> = emptyList(),
    ) {
        if (samplesMv.isEmpty()) return
        val hz = sampleHz.coerceAtLeast(1)
        val peaksAligned =
            if (peakFlags.size == samplesMv.size) {
                peakFlags
            } else {
                List(samplesMv.size) { false }
            }
        updateScreen { state ->
            val priorPeaks =
                if (state.featherEcgTracePeaks.size == state.featherEcgTraceMv.size) {
                    state.featherEcgTracePeaks
                } else {
                    List(state.featherEcgTraceMv.size) { false }
                }
            val merged = ArrayList<Float>(state.featherEcgTraceMv.size + samplesMv.size)
            merged.addAll(state.featherEcgTraceMv)
            merged.addAll(samplesMv)
            val mergedPeaks = ArrayList<Boolean>(priorPeaks.size + peaksAligned.size)
            mergedPeaks.addAll(priorPeaks)
            mergedPeaks.addAll(peaksAligned)
            val maxSamples = (hz * 3).coerceIn(250, 1000)
            val trimmed: List<Float>
            val trimmedPeaks: List<Boolean>
            if (merged.size > maxSamples) {
                val from = merged.size - maxSamples
                trimmed = merged.subList(from, merged.size).toList()
                trimmedPeaks = mergedPeaks.subList(from, mergedPeaks.size).toList()
            } else {
                trimmed = merged
                trimmedPeaks = mergedPeaks
            }
            state.copy(
                featherEcgTraceMv = trimmed,
                featherEcgTracePeaks = trimmedPeaks,
                featherEcgSampleHz = hz,
                featherEcgPacketCount = state.featherEcgPacketCount + 1,
            )
        }
        // Tech strip always; gate host NDJSON + ritual when MCU reports open leads.
        if (!com.example.polarh10bridge.feather.FeatherEcgExportGate.shouldForwardToHosts(
                featherLeadOffTracker.last(),
            )
        ) {
            return
        }
        val samplesJson = samplesMv.joinToString(prefix = "[", postfix = "]")
        val peaksJson =
            peaksAligned.joinToString(prefix = "[", postfix = "]") { if (it) "1" else "0" }
        sendBridgeJsonLine(
            """{"type":"ecg","source_device":"${sourceDeviceWire()}","sample_rate_hz":$hz,"samples_mv":$samplesJson,"peak_flags":$peaksJson}""",
        )
        sessionController.onEcgMv(hz, samplesMv)
    }

    private fun setFeatherSimActive(enabled: Boolean) {
        if (enabled == screenState.value.featherSimActive) return
        if (enabled && screenState.value.sensorConnected) {
            Log.w("HnHBridge", "Refuse Feather sim while Polar sensor is connected")
            return
        }
        if (enabled && screenState.value.featherBleConnected) {
            Log.w("HnHBridge", "Refuse Feather sim while live Feather BLE is connected")
            return
        }
        mainHandler.removeCallbacks(featherSimRunnable)
        mainHandler.removeCallbacks(featherSimEcgRunnable)
        if (!enabled) {
            updateScreen {
                it.copy(
                    featherSimActive = false,
                    featherEcgTraceMv = emptyList(),
                    featherEcgTracePeaks = emptyList(),
                    featherEcgPacketCount = 0,
                )
            }
            return
        }
        featherSimIbiIndex = 0
        featherSimElapsedMs = 0L
        featherSimCurrentIbiMs = 800
        featherSimEcgPhase01 = 0.0
        featherProfileStore?.ensureFactoryProfiles()
        saveSourceKindPref(SourceKind.Simulate)
        updateScreen {
            it.copy(
                featherSimActive = true,
                selectedSourceKind = SourceKind.Simulate,
                // Sim has no contact sensor — leave Unknown (do not fake "Skin contact OK").
                featherEcgTraceMv = emptyList(),
                featherEcgTracePeaks = emptyList(),
                featherEcgPacketCount = 0,
            )
        }
        mainHandler.post(featherSimRunnable)
        mainHandler.post(featherSimEcgRunnable)
    }

    private fun resetSensorContactGate() {
        telemetryAllowedByContact = true
        lastPublishedContactState = null
    }

    private fun applyHrContactSample(
        contactStatusSupported: Boolean,
        contactStatus: Boolean,
        hrBpm: Int,
    ) {
        val state =
            SensorContactGate.fromPolarHrSample(
                contactStatusSupported = contactStatusSupported,
                contactStatus = contactStatus,
            )
        telemetryAllowedByContact = SensorContactGate.shouldForwardTelemetry(state)
        if (state != lastPublishedContactState) {
            lastPublishedContactState = state
            sendSensorQualityLine(state)
        }
        val current = screenState.value
        val newHr = if (hrBpm > 0) hrBpm.toDouble() else current.recentHrBpm
        if (current.sensorContact != state || current.recentHrBpm != newHr) {
            updateScreen {
                it.copy(
                    sensorContact = state,
                    recentHrBpm = newHr,
                )
            }
        }
    }

    /** Additive host hint. Unknown types are ignored by older clients. */
    private fun sendSensorQualityLine(state: SensorContactState) {
        if (bridgeClient == null) return
        val rssi = screenState.value.connectedSensorRssi
        val json =
            buildString {
                append("""{"type":"sensor_quality","contact_state":"${state.wireValue()}"""")
                when (state) {
                    SensorContactState.InContact ->
                        append(""","contact":true,"contact_supported":true""")
                    SensorContactState.NoContact ->
                        append(""","contact":false,"contact_supported":true""")
                    SensorContactState.Unknown ->
                        append(""","contact_supported":false""")
                }
                if (rssi != null) {
                    append(""","rssi_dbm":$rssi""")
                }
                // Explicit: hosts must not treat rssi_dbm as on-chest / contact.
                append(""","rssi_is_contact":false}""")
            }
        sendBridgeJsonLine(json)
    }

    private fun syncSessionUiFromController(
        lastRmssdMs: Double? = null,
        clearRmssd: Boolean = false,
    ) {
        val result = sessionController.lastComputeResult
        val rmssdToShow =
            when {
                clearRmssd -> null
                lastRmssdMs != null -> lastRmssdMs
                sessionController.isActive() &&
                    sessionController.activeMode == BridgeSessionMode.Record -> null
                else -> result?.rmssdMs ?: screenState.value.lastRmssdMs
            }
        updateScreen {
            it.copy(
                sessionMode = sessionController.preferredMode,
                sessionKind = sessionController.preferredKind,
                sessionActive = sessionController.isActive(),
                sessionId = sessionController.sessionId,
                sessionIbiCount = sessionController.ibiCount,
                lastRmssdMs = rmssdToShow,
                sessionStartedElapsedMs = sessionController.sessionStartedElapsedMs,
                settleTrimSec = sessionController.settleTrimSec,
                sessionTargetSec = sessionController.sessionTargetSec,
                recentHrBpm = sessionController.recentHrBpm(),
                lastAcceptedBeats = result?.quality?.acceptedBeats ?: it.lastAcceptedBeats,
                lastQualityFlags = result?.quality?.flags ?: it.lastQualityFlags,
            )
        }
    }

    private fun refreshTechQualityUi() {
        if (!screenState.value.techView) return
        if (sessionController.isActive()) {
            sessionController.refreshQualitySnapshot()
        }
        syncSessionUiFromController()
    }

    private fun setPreferredSessionMode(mode: BridgeSessionMode) {
        if (sessionController.isActive()) return
        sessionController.preferredMode = mode
        sessionController.preferredKind =
            when (mode) {
                BridgeSessionMode.Record -> BridgeSessionKind.Ritual
                BridgeSessionMode.Stream -> BridgeSessionKind.Session
            }
        saveSessionModePref(mode)
        syncSessionUiFromController()
    }

    private fun startBridgeSession(
        mode: BridgeSessionMode = sessionController.preferredMode,
        kind: BridgeSessionKind = sessionController.preferredKind,
        requestedSessionId: String? = null,
    ) {
        if (sessionController.isActive()) return
        val stateJson =
            sessionController.start(
                mode = mode,
                kind = kind,
                requestedSessionId = requestedSessionId,
                nowElapsedMs = SystemClock.elapsedRealtime(),
                sourceDevice = sourceDeviceWire(),
            )
        saveSessionModePref(mode)
        sendBridgeJsonLine(stateJson.toString())
        mainHandler.removeCallbacks(bridgeWireKeepAliveRunnable)
        mainHandler.postDelayed(bridgeWireKeepAliveRunnable, 15_000L)
        syncSessionUiFromController(clearRmssd = true)
    }

    private fun stopBridgeSession() {
        if (!sessionController.isActive()) return
        mainHandler.removeCallbacks(bridgeWireKeepAliveRunnable)
        val wasRecord = sessionController.activeMode == BridgeSessionMode.Record
        val result =
            sessionController.stop(
                sourceDevice = sourceDeviceWire(),
                nowElapsedMs = SystemClock.elapsedRealtime(),
            )
        val rmssdValue =
            result.rmssd?.optDouble("rmssd_ms", Double.NaN)?.takeIf { !it.isNaN() }
        result.ritualPackage?.let { pkg ->
            ritualPackageStore?.save(pkg)
            refreshRitualUiFromStore()
        }
        if (wasRecord && result.ritualPackage != null) {
            sendRitualPackage(result.ritualPackage, com.example.polarh10bridge.ritual.RitualTransferReason.LiveStop)
            if (!screenState.value.pcBridgeConnected) {
                mainHandler.post {
                    Toast
                        .makeText(
                            this,
                            "HRV saved — upload in FT or HnH",
                            Toast.LENGTH_LONG,
                        ).show()
                }
            }
        } else {
            result.rmssd?.let { sendBridgeJsonLine(it.toString()) }
            result.sessionState?.let { sendBridgeJsonLine(it.toString()) }
        }
        if (rmssdValue != null) {
            syncSessionUiFromController(lastRmssdMs = rmssdValue)
        } else {
            syncSessionUiFromController(clearRmssd = true)
        }
    }

    private fun refreshRitualUiFromStore() {
        val summary = ritualPackageStore?.summaryUi()
        updateScreen {
            it.copy(
                lastRitualSessionId = summary?.sessionId,
                lastRitualAcked = summary?.acked ?: false,
                lastRitualRmssdMs = summary?.rmssdMs,
                lastRitualEmittedAt = summary?.emittedAt,
            )
        }
    }

    private fun sendRitualPackage(
        pkg: com.example.polarh10bridge.ritual.RitualPackage,
        reason: com.example.polarh10bridge.ritual.RitualTransferReason,
    ) {
        if (bridgeClient == null) return
        val lines =
            com.example.polarh10bridge.ritual.RitualWireCodec.buildTransferLines(pkg, reason)
        for (line in lines) {
            sendBridgeJsonLine(line)
        }
        if (reason == com.example.polarh10bridge.ritual.RitualTransferReason.DelayedPush ||
            reason == com.example.polarh10bridge.ritual.RitualTransferReason.ReconnectReplay
        ) {
            ritualAutoPushSentSessionId = pkg.sessionId
        }
        // Optimistic UI: hosts may not yet send ritual_ack; mark sent after TCP queue.
        ritualPackageStore?.markAcked(pkg.sessionId)
        refreshRitualUiFromStore()
    }

    private fun maybeAutoPushRitual(
        clientApp: String?,
        forceHnHCompatible: Boolean = false,
    ) {
        if (sessionController.isActive()) return
        val app = if (forceHnHCompatible && clientApp.isNullOrBlank()) null else clientApp
        if (!com.example.polarh10bridge.ritual.RitualWireCodec.shouldAutoPush(app)) return
        val pkg = ritualPackageStore?.latestUnacked() ?: return
        if (pkg.sessionId == ritualAutoPushSentSessionId) return
        sendRitualPackage(pkg, com.example.polarh10bridge.ritual.RitualTransferReason.DelayedPush)
    }

    private fun sendLastRitualManual() {
        val store = ritualPackageStore ?: return
        val pkg = store.latest() ?: return
        sendRitualPackage(pkg, com.example.polarh10bridge.ritual.RitualTransferReason.ManualSend)
        mainHandler.post {
            Toast
                .makeText(
                    this,
                    if (screenState.value.pcBridgeConnected) {
                        "Sent HRV ${pkg.sessionId}"
                    } else {
                        "No PC connected — HRV stays on phone"
                    },
                    Toast.LENGTH_SHORT,
                ).show()
        }
    }

    private fun handleRitualAck(sessionId: String) {
        val id = sessionId.trim()
        if (id.isEmpty()) return
        ritualPackageStore?.markAcked(id)
        refreshRitualUiFromStore()
    }

    private fun handleRitualRequest(payload: JSONObject) {
        val store = ritualPackageStore ?: return
        val rawId = if (payload.has("session_id") && !payload.isNull("session_id")) {
            payload.optString("session_id", "").trim().ifEmpty { null }
        } else {
            null
        }
        val pkg =
            if (rawId != null) {
                store.get(rawId)
            } else {
                store.latestUnacked() ?: store.latest()
            } ?: return
        sendRitualPackage(pkg, com.example.polarh10bridge.ritual.RitualTransferReason.ManualSend)
    }

    private fun restartBridgeServerIfNeeded() {
        synchronized(bridgeServerSocketLock) {
            try {
                bridgeClient?.close()
            } catch (_: Exception) {
            }
            bridgeClient = null
            bridgeWriter = null
            mainHandler.removeCallbacks(bridgeLinkPingRunnable)
            releaseBridgeWifiLock()
            try {
                bridgeServerSocket?.close()
            } catch (_: Exception) {
            }
            bridgeServerSocket = null
        }
        updateScreen {
            it.copy(
                pcBridgeConnected = false,
                pcBridgeIp = null,
                pcBridgeUserName = null,
                featherProfileHintPending = null,
            )
        }
    }

    private fun shouldKeepBridgeAliveInBackground(): Boolean {
        val s = screenState.value
        return s.keepAliveInBackground &&
            (s.sensorConnected || s.featherBleConnected || s.pcBridgeConnected)
    }

    private fun startBridgeForegroundService() {
        val intent = Intent(this, BridgeForegroundService::class.java).apply {
            action = BridgeForegroundService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateScreen { it.copy(foregroundServiceActive = true) }
    }

    private fun stopBridgeForegroundService() {
        val intent = Intent(this, BridgeForegroundService::class.java).apply {
            action = BridgeForegroundService.ACTION_STOP
        }
        startService(intent)
        updateScreen { it.copy(foregroundServiceActive = false) }
    }

    private val bleScanStopRunnable = Runnable { stopBleScan() }
    private val bleRssiResubscribeRunnable =
        object : Runnable {
            override fun run() {
                val state = screenState.value
                if (!state.sensorConnected) return
                if (state.bleScanning) {
                    mainHandler.postDelayed(this, BLE_RSSI_RESUBSCRIBE_MS)
                    return
                }
                if (state.connectedSensorAddress.isNotBlank()) {
                    val now = SystemClock.elapsedRealtime()
                    val sampleStale =
                        lastConnectedRssiSampleElapsedMs == 0L ||
                            now - lastConnectedRssiSampleElapsedMs >= BLE_RSSI_STALE_REARM_MS
                    if (rssiLeScanCallback == null || sampleStale) {
                        // Drop zombie scans that stay registered but never deliver results.
                        stopConnectedRssiLeScan()
                        startConnectedRssiLeScan()
                    }
                } else {
                    stopConnectedRssiPolarFallback()
                    startConnectedRssiPolarFallback()
                    mainHandler.postDelayed({ stopConnectedRssiPolarFallback() }, BLE_RSSI_POLAR_BURST_MS)
                }
                mainHandler.postDelayed(this, BLE_RSSI_RESUBSCRIBE_MS)
            }
        }
    private val bleRowPruneRunnable =
        object : Runnable {
            override fun run() {
                if (!screenState.value.bleScanning) return
                pruneStaleBleRows()
                mainHandler.postDelayed(this, BLE_ROW_PRUNE_INTERVAL_MS)
            }
        }

    private val wifiIpLinkRetry500Ms = Runnable { applyPhoneWifiLinkFromNetworkToScreenState() }
    private val wifiIpLinkRetry1500Ms = Runnable { applyPhoneWifiLinkFromNetworkToScreenState() }
    private val wifiIpLinkRetry3500Ms = Runnable { applyPhoneWifiLinkFromNetworkToScreenState() }
    private val wifiIpLinkRetry8000Ms = Runnable { applyPhoneWifiLinkFromNetworkToScreenState() }

    private fun applyPhoneWifiLinkFromNetworkToScreenState() {
        val beforeIp = screenState.value.phoneWifiIpv4
        // Always probe TRANSPORT_WIFI (includes soft AP / hotspot). Do not clear on
        // isWifiEnabled=false — that flag is often off while hotspot is serving the PC.
        val netInfo = wifiNetworkInfo(this)
        screenState.value =
            screenState.value.copy(
                phoneWifiIpv4 = netInfo.ipv4,
                phoneWifiSubnetMask = netInfo.subnetMask,
            )
        if (beforeIp != screenState.value.phoneWifiIpv4) {
            bridgeIpHintRefreshSession.value = bridgeIpHintRefreshSession.value + 1
        }
    }

    private fun schedulePhoneWifiLinkRefreshWithRetries() {
        mainHandler.removeCallbacks(wifiIpLinkRetry500Ms)
        mainHandler.removeCallbacks(wifiIpLinkRetry1500Ms)
        mainHandler.removeCallbacks(wifiIpLinkRetry3500Ms)
        mainHandler.removeCallbacks(wifiIpLinkRetry8000Ms)
        applyPhoneWifiLinkFromNetworkToScreenState()
        mainHandler.postDelayed(wifiIpLinkRetry500Ms, 500L)
        mainHandler.postDelayed(wifiIpLinkRetry1500Ms, 1500L)
        // DHCP often lands after the radio-on broadcast; keep probing a bit longer.
        mainHandler.postDelayed(wifiIpLinkRetry3500Ms, 3500L)
        mainHandler.postDelayed(wifiIpLinkRetry8000Ms, 8000L)
    }

    private fun cancelScheduledPhoneWifiLinkRefresh() {
        mainHandler.removeCallbacks(wifiIpLinkRetry500Ms)
        mainHandler.removeCallbacks(wifiIpLinkRetry1500Ms)
        mainHandler.removeCallbacks(wifiIpLinkRetry3500Ms)
        mainHandler.removeCallbacks(wifiIpLinkRetry8000Ms)
    }

    private fun pruneStaleBleRows() {
        val now = SystemClock.elapsedRealtime()
        updateScreen { current ->
            val pruned = current.bleRows.filter { now - it.lastSeenElapsedMs <= BLE_ROW_STALE_MS }
            val selected =
                current.bleSelectedId
                    ?.takeIf { id -> pruned.any { it.deviceId == id } }
            current.copy(bleRows = pruned, bleSelectedId = selected)
        }
    }

    private fun updateScreen(copy: (BridgeScreenState) -> BridgeScreenState) {
        mainHandler.post { screenState.value = copy(screenState.value) }
    }

    private fun sendBridgeJsonLine(json: String) {
        val client = bridgeClient ?: return
        val payload = (json + "\n").toByteArray(Charsets.UTF_8)
        bridgeWriteExecutor.execute {
            synchronized(writerLock) {
                if (bridgeClient !== client) return@execute
                try {
                    val out = client.getOutputStream()
                    out.write(payload)
                    out.flush()
                } catch (e: Exception) {
                    Log.e("HnHBridge", "bridge write failed", e)
                    closeBridgeClient(client, "write failed")
                }
            }
        }
    }

    /** Drop a dead PC socket so accept() can take the next host. PrintWriter swallows IO errors. */
    private fun closeBridgeClient(client: Socket, reason: String) {
        synchronized(writerLock) {
            if (bridgeClient !== client) return
            Log.d("HnHBridge", "closing PC socket: $reason")
            bridgeWriter = null
            bridgeClient = null
        }
        try {
            client.close()
        } catch (_: Exception) {
        }
    }

    /**
     * Accept the socket but do not make it the live PC link until it sends a line.
     * HnH/VNS-TA Scan opens TCP and closes immediately; that must not kick a live host.
     * A restarted host sends client_info and then replaces a stuck socket.
     */
    private fun observeInboundClient(client: Socket) {
        client.keepAlive = true
        client.tcpNoDelay = true
        try {
            client.soTimeout = BRIDGE_PROVISIONAL_READ_MS
        } catch (_: Exception) {
        }
        Log.d("HnHBridge", "TCP accept from ${client.inetAddress?.hostAddress}")
        bridgeReadExecutor.execute { runBridgeClientSession(client) }
    }

    /** Promote [client] to the one PC link and close the previous host socket. */
    private fun commitBridgeClient(client: Socket): Boolean {
        val previous: Socket?
        try {
            previous = synchronized(writerLock) {
                if (client.isClosed) return false
                val writer = java.io.PrintWriter(
                    java.io.OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8),
                    true,
                )
                val old = bridgeClient
                bridgeClient = client
                bridgeWriter = writer
                old
            }
            try {
                client.soTimeout = 0
            } catch (_: Exception) {
            }
            if (previous != null && previous !== client) {
                Log.d(
                    "HnHBridge",
                    "replacing previous PC socket (${previous.inetAddress?.hostAddress})",
                )
                try {
                    previous.close()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            Log.e("HnHBridge", "PC attach failed", e)
            try {
                client.close()
            } catch (_: Exception) {
            }
            synchronized(writerLock) {
                if (bridgeClient === client) {
                    bridgeWriter = null
                    bridgeClient = null
                }
            }
            return false
        }

        Log.d("HnHBridge", "PC session from ${client.inetAddress?.hostAddress}")
        acquireBridgeWifiLock()
        mainHandler.removeCallbacks(bridgeLinkPingRunnable)
        mainHandler.postDelayed(bridgeLinkPingRunnable, BRIDGE_LINK_PING_MS)
        mainHandler.post {
            screenState.value =
                screenState.value.copy(
                    pcBridgeConnected = true,
                    pcBridgeIp = client.inetAddress?.hostAddress,
                    pcBridgeUserName = null,
                    pcClientApp = null,
                    featherProfileHintPending = null,
                )
        }

        sendBridgeJsonLine(
            JSONObject()
                .put("type", "status")
                .put("message", "Phone bridge connected")
                .put("connected", true)
                .put("protocol", BRIDGE_PROTOCOL_ID)
                .toString(),
        )
        lastPublishedContactState?.let { sendSensorQualityLine(it) }
            ?: run {
                val known = screenState.value.sensorContact
                if (known != SensorContactState.Unknown) {
                    lastPublishedContactState = known
                    sendSensorQualityLine(known)
                }
            }
        // Lead-off is edge-triggered. A host that connects after the edge
        // (leads already open) must still see the current snapshot.
        featherLeadOffTracker.last()?.let { snap ->
            sendBridgeJsonLine(snap.toStatusJson(connected = true))
        }
        if (sessionController.isActive()) {
            sendBridgeJsonLine(sessionController.sessionStateJson().toString())
            mainHandler.removeCallbacks(bridgeWireKeepAliveRunnable)
            mainHandler.postDelayed(bridgeWireKeepAliveRunnable, 1_000L)
        } else {
            ritualAutoPushSentSessionId = null
            mainHandler.removeCallbacks(ritualClientInfoFallbackRunnable)
            mainHandler.postDelayed(ritualClientInfoFallbackRunnable, 2_500L)
            if (ritualPackageStore?.latestUnacked() == null) {
                sessionController.lastWireStopForReplay()?.let { replay ->
                    replay.rmssd?.let { sendBridgeJsonLine(it.toString()) }
                    replay.sessionState?.let { sendBridgeJsonLine(it.toString()) }
                }
            }
        }
        return true
    }

    private fun runBridgeClientSession(client: Socket) {
        var committed = false
        try {
            val input = client.getInputStream().bufferedReader(Charsets.UTF_8)
            var first = input.readLine()
            while (first != null && first.isBlank()) {
                first = input.readLine()
            }
            if (first == null) {
                Log.d(
                    "HnHBridge",
                    "ignoring TCP client with no data (${client.inetAddress?.hostAddress})",
                )
                return
            }
            if (!commitBridgeClient(client)) return
            committed = true
            handlePcBridgeInboundLine(first)
            while (true) {
                val line = input.readLine()
                if (line == null) {
                    Log.d("HnHBridge", "PC closed TCP (EOF)")
                    break
                }
                handlePcBridgeInboundLine(line)
            }
        } catch (e: SocketTimeoutException) {
            if (!committed) {
                Log.d(
                    "HnHBridge",
                    "ignoring silent TCP client (${client.inetAddress?.hostAddress})",
                )
            } else {
                Log.d("HnHBridge", "TCP read timeout: ${e.message}")
            }
        } catch (e: SocketException) {
            if (committed) {
                Log.d("HnHBridge", "TCP connection lost: ${e.message}")
            } else {
                Log.d("HnHBridge", "TCP probe closed: ${e.message}")
            }
        } catch (e: Exception) {
            if (committed) {
                Log.e("HnHBridge", "TCP read error", e)
            } else {
                Log.d("HnHBridge", "TCP probe ended: ${e.message}")
            }
        } finally {
            val droppedCurrent: Boolean
            synchronized(writerLock) {
                droppedCurrent = bridgeClient === client
                if (droppedCurrent) {
                    bridgeWriter = null
                    bridgeClient = null
                }
            }
            try {
                client.close()
            } catch (_: Exception) {
            }
            if (droppedCurrent) {
                mainHandler.removeCallbacks(ritualClientInfoFallbackRunnable)
                mainHandler.removeCallbacks(bridgeLinkPingRunnable)
                releaseBridgeWifiLock()
                ritualAutoPushSentSessionId = null
                mainHandler.post {
                    updateScreen {
                        it.copy(
                            pcBridgeConnected = false,
                            pcBridgeIp = null,
                            pcBridgeUserName = null,
                            pcClientApp = null,
                            featherProfileHintPending = null,
                        )
                    }
                    refreshFeatherProfileUi(status = "PC disconnected")
                    syncSessionUiFromController()
                }
                Log.d("HnHBridge", "PC bridge TCP closed (capture continues on phone until Stop)")
            }
        }
    }

    private fun acquireBridgeWifiLock() {
        try {
            val lock = bridgeWifiLock ?: run {
                val wm = applicationContext.getSystemService(WifiManager::class.java) ?: return
                val mode =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        @Suppress("DEPRECATION")
                        WifiManager.WIFI_MODE_FULL_HIGH_PERF
                    }
                wm.createWifiLock(mode, "ecg-phone-bridge-link").also {
                    it.setReferenceCounted(false)
                    bridgeWifiLock = it
                }
            }
            if (!lock.isHeld) lock.acquire()
        } catch (e: Exception) {
            Log.d("HnHBridge", "Wi-Fi lock not acquired: ${e.message}")
        }
    }

    private fun releaseBridgeWifiLock() {
        try {
            val lock = bridgeWifiLock ?: return
            if (lock.isHeld) lock.release()
        } catch (_: Exception) {
        }
    }

    private fun handlePcBridgeInboundLine(line: String) {
        val raw = line.trim()
        if (raw.isEmpty()) return
        try {
            val payload = JSONObject(raw)
            when (payload.optString("type").lowercase(Locale.US)) {
                "client_info" -> {
                    val user = payload.optString("pc_user", "").trim().ifEmpty { null }
                    val app = payload.optString("client_app", "").trim().ifEmpty { null }
                    updateScreen { it.copy(pcBridgeUserName = user, pcClientApp = app) }
                    mainHandler.removeCallbacks(ritualClientInfoFallbackRunnable)
                    mainHandler.post { maybeAutoPushRitual(app) }
                    mainHandler.post { handleFeatherProfileHintFromClientInfo(user, app) }
                    if (app.equals("ecg_box_tuner", ignoreCase = true)) {
                        sendActiveFeatherProfileToPc()
                        // Light tech stream: ensure Feather notifies if already connected.
                        mainHandler.post {
                            if (screenState.value.featherBleConnected) {
                                featherBleClient?.startStream()
                            }
                            sendBridgeJsonLine(
                                JSONObject()
                                    .put("type", "status")
                                    .put(
                                        "message",
                                        if (screenState.value.featherBleConnected) {
                                            "Tuner tech stream — Feather ECG+markers"
                                        } else {
                                            "Tuner linked — Connect Feather on phone for Live ECG"
                                        },
                                    )
                                    .toString(),
                            )
                        }
                    }
                }
                "ritual_ack" -> {
                    val sid = payload.optString("session_id", "")
                    mainHandler.post { handleRitualAck(sid) }
                }
                "ritual_request" -> {
                    mainHandler.post { handleRitualRequest(payload) }
                }
                "session_control" -> {
                    when (payload.optString("action").lowercase(Locale.US)) {
                        "start" -> {
                            val mode =
                                if (payload.has("mode")) {
                                    BridgeSessionMode.fromWire(payload.optString("mode"))
                                } else {
                                    null
                                } ?: sessionController.preferredMode
                            val kind =
                                if (payload.has("kind")) {
                                    BridgeSessionKind.fromWire(payload.optString("kind"))
                                } else {
                                    null
                                } ?: when (mode) {
                                    BridgeSessionMode.Record -> BridgeSessionKind.Ritual
                                    BridgeSessionMode.Stream -> BridgeSessionKind.Session
                                }
                            val sid =
                                payload.optString("session_id", "").trim().ifEmpty { null }
                            mainHandler.post {
                                startBridgeSession(mode = mode, kind = kind, requestedSessionId = sid)
                            }
                        }
                        "stop" -> {
                            mainHandler.post { stopBridgeSession() }
                        }
                    }
                }
                "profile_get_active" -> sendActiveFeatherProfileToPc()
                "profile_list" -> sendFeatherProfileListToPc()
                "profile_get" -> {
                    val id = payload.optString("profile_id", "").trim()
                    sendFeatherProfileToPc(id)
                }
                "profile_put" -> handleFeatherProfilePutFromPc(payload)
                "coeffs_push" -> handleCoeffsPushFromPc(payload)
                "offline_echo" -> handleOfflineEchoFromPc(payload)
                "coeffs_get" -> handleCoeffsGetFromPc()
                else -> {
                    // Ignore unknown types for forward compatibility.
                }
            }
        } catch (_: Exception) {
            // Keep stream compatibility with older/newer clients.
        }
    }

    private fun pushActiveProfileToTunerIfLinked() {
        val app = screenState.value.pcClientApp ?: return
        if (!app.equals("ecg_box_tuner", ignoreCase = true)) return
        sendActiveFeatherProfileToPc()
    }

    private fun sendActiveFeatherProfileToPc() {
        val store = featherProfileStore
        if (store == null) {
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "profile_error")
                    .put("ok", false)
                    .put("message", "Profile store not ready")
                    .toString(),
            )
            return
        }
        val active = store.loadActive()
        sendBridgeJsonLine(
            JSONObject()
                .put("type", "profile")
                .put("active", true)
                .put("profile", active.toJsonObject())
                .toString(),
        )
    }

    private fun sendFeatherProfileToPc(profileId: String) {
        val store = featherProfileStore
        if (store == null) {
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "profile_error")
                    .put("ok", false)
                    .put("message", "Profile store not ready")
                    .toString(),
            )
            return
        }
        if (profileId.isEmpty()) {
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "profile_error")
                    .put("ok", false)
                    .put("message", "profile_id required")
                    .toString(),
            )
            return
        }
        val profile = store.load(profileId)
        if (profile == null) {
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "profile_error")
                    .put("ok", false)
                    .put("message", "Profile not found")
                    .toString(),
            )
            return
        }
        val activeId = store.activeProfileId()
        sendBridgeJsonLine(
            JSONObject()
                .put("type", "profile")
                .put("active", profile.profileId == activeId)
                .put("profile", profile.toJsonObject())
                .toString(),
        )
    }

    private fun sendFeatherProfileListToPc() {
        val store = featherProfileStore
        if (store == null) {
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "profile_error")
                    .put("ok", false)
                    .put("message", "Profile store not ready")
                    .toString(),
            )
            return
        }
        val summaries = org.json.JSONArray()
        for (s in store.listSummaries()) {
            summaries.put(
                JSONObject()
                    .put("profile_id", s.profileId)
                    .put("display_name", s.displayName),
            )
        }
        sendBridgeJsonLine(
            JSONObject()
                .put("type", "profile_list")
                .put("active_profile_id", store.activeProfileId())
                .put("profiles", summaries)
                .toString(),
        )
    }

    private fun handleFeatherProfilePutFromPc(payload: JSONObject) {
        val store = featherProfileStore
        if (store == null) {
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "profile_error")
                    .put("ok", false)
                    .put("message", "Profile store not ready")
                    .toString(),
            )
            return
        }
        val profileObj = payload.optJSONObject("profile")
        if (profileObj == null) {
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "profile_error")
                    .put("ok", false)
                    .put("message", "profile object required")
                    .toString(),
            )
            return
        }
        try {
            val incoming =
                com.example.polarh10bridge.feather.FeatherPatientProfile.fromJsonObject(profileObj)
            val existing = store.load(incoming.profileId)
            if (existing == null) {
                sendBridgeJsonLine(
                    JSONObject()
                        .put("type", "profile_error")
                        .put("ok", false)
                        .put("message", "Unknown profile_id (Tuner cannot create patients yet)")
                        .toString(),
                )
                return
            }
            // Merge onto existing so missing hardware/session_timing from the PC
            // cannot drop demo_seed or wipe factory metadata.
            val mergedCoeffs =
                linkedMapOf<String, Any?>().apply {
                    putAll(existing.coeffs)
                    putAll(incoming.coeffs)
                }
            val saved =
                store.save(
                    existing.copy(
                        displayName =
                            incoming.displayName.ifBlank { existing.displayName },
                        coeffs = mergedCoeffs,
                        hardware =
                            if (incoming.hardware.isNotEmpty()) {
                                linkedMapOf<String, Any?>().apply {
                                    putAll(existing.hardware)
                                    putAll(incoming.hardware)
                                }
                            } else {
                                existing.hardware
                            },
                        sessionTiming =
                            if (incoming.sessionTiming.isNotEmpty()) {
                                incoming.sessionTiming
                            } else {
                                existing.sessionTiming
                            },
                        createdAt = existing.createdAt,
                    ),
                )
            // SoR only — MCU RAM is updated by Tuner Send (`coeffs_push`) or Tech
            // Connect / Save-while-connected, not by profile_put.
            mainHandler.post {
                refreshFeatherProfileUi(status = "Tuner saved ${saved.displayName}")
            }
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "profile_ack")
                    .put("ok", true)
                    .put("profile_id", saved.profileId)
                    .put("message", "Saved ${saved.displayName}")
                    .toString(),
            )
        } catch (e: Exception) {
            Log.e("HnHBridge", "profile_put failed", e)
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "profile_error")
                    .put("ok", false)
                    .put("message", e.message ?: "profile_put failed")
                    .toString(),
            )
        }
    }

    /** Tuner Send → MCU RAM over BLE only (does not touch phone profile SoR). */
    private fun handleCoeffsPushFromPc(payload: JSONObject) {
        val coeffsObj = payload.optJSONObject("coeffs")
        if (coeffsObj == null) {
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "coeffs_ack")
                    .put("ok", false)
                    .put("message", "coeffs object required")
                    .toString(),
            )
            return
        }
        if (!screenState.value.featherBleConnected) {
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "coeffs_ack")
                    .put("ok", false)
                    .put("ble", false)
                    .put(
                        "message",
                        "Feather not connected — Connect Feather on phone, then Send",
                    )
                    .toString(),
            )
            return
        }
        val map = linkedMapOf<String, Any?>()
        val keys = coeffsObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = coeffsObj.get(k)
            map[k] = if (v == JSONObject.NULL) null else v
        }
        mainHandler.post {
            try {
                val bytes =
                    com.example.polarh10bridge.feather.FeatherPacketCodec.encodeCoeffsJson(map)
                featherBleClient?.writeCoeffsJson(bytes)
                sendBridgeJsonLine(
                    JSONObject()
                        .put("type", "coeffs_ack")
                        .put("ok", true)
                        .put("ble", true)
                        .put("message", "Pushed coeffs to Feather")
                        .toString(),
                )
            } catch (e: Exception) {
                Log.e("HnHBridge", "coeffs_push failed", e)
                sendBridgeJsonLine(
                    JSONObject()
                        .put("type", "coeffs_ack")
                        .put("ok", false)
                        .put("ble", true)
                        .put("message", e.message ?: "coeffs_push failed")
                        .toString(),
                )
            }
        }
    }

    /**
     * Tuner Offline mirror while linked — display only (no SoR write).
     * Fields stay read-only in Tech UI until the Tuner disconnects.
     */
    private fun handleOfflineEchoFromPc(payload: JSONObject) {
        if (!isTunerLinked()) return
        val coeffsObj = payload.optJSONObject("coeffs") ?: return
        val draft = linkedMapOf<String, String>()
        for (key in com.example.polarh10bridge.feather.FeatherPatientProfile.EDITABLE_COEFF_KEYS) {
            if (!coeffsObj.has(key)) continue
            val v = coeffsObj.opt(key)
            if (v == null || v == JSONObject.NULL) continue
            draft[key] = v.toString()
        }
        if (draft.isEmpty()) return
        val merged =
            linkedMapOf<String, String>().apply {
                putAll(screenState.value.featherActiveCoeffs)
                putAll(draft)
            }
        mainHandler.post {
            updateScreen {
                it.copy(
                    featherActiveCoeffs = merged,
                    featherCoeffsEpoch = it.featherCoeffsEpoch + 1,
                    featherProfileStatus = "Tuner Offline echo (read-only)",
                )
            }
        }
    }

    /** Tuner Refresh Online → BLE get_coeffs; reply arrives via status notify. */
    private fun handleCoeffsGetFromPc() {
        if (!screenState.value.featherBleConnected) {
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "mcu_coeffs")
                    .put("ok", false)
                    .put(
                        "message",
                        "Feather not connected — Connect Feather on phone, then Refresh Online",
                    )
                    .toString(),
            )
            return
        }
        mainHandler.post {
            try {
                featherBleClient?.requestCoeffs()
            } catch (e: Exception) {
                Log.e("HnHBridge", "coeffs_get failed", e)
                sendBridgeJsonLine(
                    JSONObject()
                        .put("type", "mcu_coeffs")
                        .put("ok", false)
                        .put("message", e.message ?: "coeffs_get failed")
                        .toString(),
                )
            }
        }
    }

    /** Forward Feather status `{"type":"coeffs",...}` to Tuner as `mcu_coeffs`. */
    private fun forwardMcuCoeffsStatusToTunerIfNeeded(json: String) {
        if (!isTunerLinked()) return
        try {
            val obj = JSONObject(json)
            if (!obj.optString("type").equals("coeffs", ignoreCase = true)) return
            val coeffs = obj.optJSONObject("coeffs") ?: return
            sendBridgeJsonLine(
                JSONObject()
                    .put("type", "mcu_coeffs")
                    .put("ok", true)
                    .put("coeffs", coeffs)
                    .toString(),
            )
        } catch (_: Exception) {
            // Ignore non-JSON status / heartbeat payloads.
        }
    }

    private fun extractEcgMillivolts(ecgData: PolarEcgData): List<Float> {
        val out = ArrayList<Float>(ecgData.samples.size)
        for (sample in ecgData.samples) {
            try {
                val cls = sample.javaClass

                val uvValue: Number? = run {
                    val candidates = arrayOf("voltage", "microVolts", "uV", "uv", "value")
                    for (name in candidates) {
                        try {
                            val f = cls.getDeclaredField(name)
                            f.isAccessible = true
                            val v = f.get(sample)
                            if (v is Number) return@run v
                        } catch (_: Exception) {
                        }
                    }
                    null
                }

                if (uvValue != null) {
                    out.add(uvValue.toFloat() / 1000f)
                    continue
                }

                val m = Regex("-?\\d+").find(sample.toString())
                if (m != null) {
                    out.add(m.value.toFloat() / 1000f)
                }
            } catch (_: Exception) {
            }
        }
        return out
    }

    private fun requestConnectedSensorRssiUiUpdate(
        rssi: Int,
        connectedAddressNorm: String?,
    ) {
        val now = SystemClock.elapsedRealtime()
        lastConnectedRssiSampleElapsedMs = now
        pendingConnectedRssiValue = rssi
        pendingConnectedRssiAddressNorm = connectedAddressNorm
        val due = now - lastConnectedRssiUiElapsedMs >= BLE_RSSI_UI_THROTTLE_MS
        if (due) {
            mainHandler.removeCallbacks(rssiUiThrottleFlushRunnable)
            rssiUiThrottleFlushScheduled = false
            rssiUiThrottleFlushRunnable.run()
            return
        }
        if (!rssiUiThrottleFlushScheduled) {
            val delay = BLE_RSSI_UI_THROTTLE_MS - (now - lastConnectedRssiUiElapsedMs)
            rssiUiThrottleFlushScheduled = true
            mainHandler.postDelayed(
                rssiUiThrottleFlushRunnable,
                delay.coerceAtLeast(1L),
            )
        }
    }

    private fun clearConnectedRssiUiThrottle() {
        mainHandler.removeCallbacks(rssiUiThrottleFlushRunnable)
        rssiUiThrottleFlushScheduled = false
        pendingConnectedRssiValue = null
        pendingConnectedRssiAddressNorm = null
        lastConnectedRssiUiElapsedMs = 0L
        lastConnectedRssiSampleElapsedMs = 0L
    }

    private fun stopBleScan() {
        mainHandler.removeCallbacks(bleScanStopRunnable)
        mainHandler.removeCallbacks(bleRowPruneRunnable)
        bleSearchDisposable?.dispose()
        bleSearchDisposable = null
        updateScreen { it.copy(bleScanning = false) }
    }

    @SuppressLint("MissingPermission")
    private fun startConnectedRssiLeScan() {
        val state = screenState.value
        if (!state.sensorConnected) return
        val addr = state.connectedSensorAddress.trim()
        if (addr.isEmpty()) return
        if (rssiLeScanCallback != null) return

        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null || !adapter.isEnabled) return
        val scanner = adapter.bluetoothLeScanner ?: return
        val targetNorm = normBleAddr(addr)

        val callback =
            object : ScanCallback() {
                override fun onScanResult(
                    callbackType: Int,
                    result: ScanResult,
                ) {
                    if (normBleAddr(result.device.address) != targetNorm) return
                    requestConnectedSensorRssiUiUpdate(result.rssi, targetNorm)
                }

                override fun onBatchScanResults(results: MutableList<ScanResult>) {
                    for (r in results) {
                        onScanResult(0, r)
                    }
                }

                override fun onScanFailed(errorCode: Int) {
                    Log.w("HnHBridge", "RSSI LE scan failed errorCode=$errorCode")
                    val self = rssiLeScanCallback
                    rssiLeScanCallback = null
                    try {
                        adapter.bluetoothLeScanner?.stopScan(self)
                    } catch (_: Exception) {
                    }
                    if (screenState.value.sensorConnected &&
                        !screenState.value.bleScanning &&
                        screenState.value.connectedSensorAddress.isNotBlank()
                    ) {
                        mainHandler.postDelayed({ startConnectedRssiLeScan() }, 750L)
                    }
                }
            }

        rssiLeScanCallback = callback
        try {
            scanner.startScan(
                null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                callback,
            )
        } catch (e: Exception) {
            Log.w("HnHBridge", "RSSI LE scan start failed", e)
            rssiLeScanCallback = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopConnectedRssiLeScan() {
        val cb = rssiLeScanCallback ?: return
        rssiLeScanCallback = null
        try {
            val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (_: Exception) {
        }
    }

    private fun startConnectedRssiPolarFallback() {
        val state = screenState.value
        if (!state.sensorConnected) return
        val connectedId = state.connectedSensorId
        val connectedAddress = state.connectedSensorAddress
        val connectedName = state.connectedSensorName
        if (connectedId.isEmpty() && connectedAddress.isEmpty() && connectedName.isEmpty()) return
        if (bleRssiPolarFallbackDisposable?.isDisposed == false) return

        val connectedRow =
            BleDeviceRow(
                deviceId = connectedId,
                address = connectedAddress,
                displayName = connectedName,
                rssi = Int.MIN_VALUE,
                lastSeenElapsedMs = SystemClock.elapsedRealtime(),
            )
        try {
            bleRssiPolarFallbackDisposable =
                polarApi.searchForDevice()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { info ->
                            if (samePhysicalBleRow(connectedRow, info) ||
                                sameConnectedDevice(connectedId, connectedAddress, connectedName, info)
                            ) {
                                requestConnectedSensorRssiUiUpdate(info.rssi, null)
                            }
                        },
                        { err -> Log.d("HnHBridge", "RSSI Polar fallback stopped: ${err.message}") },
                    )
        } catch (e: Exception) {
            Log.d("HnHBridge", "RSSI Polar fallback unavailable: ${e.message}")
        }
    }

    private fun stopConnectedRssiPolarFallback() {
        bleRssiPolarFallbackDisposable?.dispose()
        bleRssiPolarFallbackDisposable = null
    }

    private fun startConnectedRssiMonitor() {
        val state = screenState.value
        if (!state.sensorConnected) return
        if (state.connectedSensorAddress.isNotBlank()) {
            startConnectedRssiLeScan()
        }
    }

    private fun stopConnectedRssiMonitor() {
        clearConnectedRssiUiThrottle()
        stopConnectedRssiLeScan()
        stopConnectedRssiPolarFallback()
    }

    private fun stopConnectedRssiPolling() {
        mainHandler.removeCallbacks(bleRssiResubscribeRunnable)
        stopConnectedRssiMonitor()
    }

    /** After sensor dialog dismiss / reconnect, resume live dBm while still connected. */
    private fun resumeConnectedRssiMonitoring() {
        if (!screenState.value.sensorConnected) return
        startConnectedRssiMonitor()
        mainHandler.removeCallbacks(bleRssiResubscribeRunnable)
        mainHandler.post(bleRssiResubscribeRunnable)
    }

    private fun sameConnectedDevice(
        connectedId: String,
        connectedAddress: String,
        connectedName: String,
        info: PolarDeviceInfo,
    ): Boolean {
        if (connectedId.equals(info.deviceId, ignoreCase = true)) return true
        if (connectedAddress.equals(info.address, ignoreCase = true) && connectedAddress.isNotBlank()) return true
        val cid = normBleAddr(connectedId)
        val cad = normBleAddr(connectedAddress)
        val iid = normBleAddr(info.deviceId)
        val iad = normBleAddr(info.address)
        if ((cid.isNotEmpty() && cid == iid) || (cid.isNotEmpty() && cid == iad)) return true
        if ((cad.isNotEmpty() && cad == iid) || (cad.isNotEmpty() && cad == iad)) return true
        return connectedName.isNotBlank() &&
            info.name.isNotBlank() &&
            connectedName.equals(info.name, ignoreCase = true)
    }

    private fun scheduleBleScanAutoStop() {
        mainHandler.removeCallbacks(bleScanStopRunnable)
        mainHandler.postDelayed(bleScanStopRunnable, 12_000L)
    }

    private fun beginSensorScan() {
        mainHandler.removeCallbacks(bleScanStopRunnable)
        mainHandler.removeCallbacks(bleRowPruneRunnable)
        stopConnectedRssiPolling()

        bleSearchDisposable?.dispose()
        bleSearchDisposable = null

        updateScreen {
            it.copy(
                bleDialogVisible = true,
                bleScanning = true,
                bleRows = emptyList(),
                bleSelectedId = null,
                bleConnecting = false,
            )
        }

        try {
            bleSearchDisposable = polarApi.searchForDevice()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { info: PolarDeviceInfo ->
                        val now = SystemClock.elapsedRealtime()
                        val oldRows = screenState.value.bleRows
                        val oldCount = oldRows.size
                        val rows =
                            oldRows
                                .filter { now - it.lastSeenElapsedMs <= BLE_ROW_STALE_MS }
                                .toMutableList()
                        val idx = rows.indexOfFirst { samePhysicalBleRow(it, info) }
                        var sel = screenState.value.bleSelectedId
                        if (idx >= 0) {
                            val prev = rows[idx]
                            rows[idx] = mergePolarScanRow(prev, info)
                            if (sel != null &&
                                (sel == prev.deviceId || normBleAddr(sel) == normBleAddr(prev.address))
                            ) {
                                sel = rows[idx].deviceId
                            }
                        } else {
                            rows.add(
                                BleDeviceRow(
                                    deviceId = info.deviceId,
                                    address = info.address,
                                    displayName = info.name.ifBlank { "Polar device" },
                                    rssi = info.rssi,
                                    lastSeenElapsedMs = now,
                                ),
                            )
                        }
                        val newList = rows.sortedByDescending { it.rssi }
                        when {
                            newList.size == 1 -> sel = newList.first().deviceId
                            newList.size > 1 && oldCount == 1 -> sel = null
                            newList.isEmpty() -> sel = null
                        }
                        screenState.value =
                            screenState.value.copy(bleRows = newList, bleSelectedId = sel)
                    },
                    { err ->
                        Log.e("HnHBridge", "BLE search error", err)
                        stopBleScan()
                    },
                )
        } catch (e: Exception) {
            Log.e("HnHBridge", "BLE search start failed", e)
            updateScreen {
                it.copy(bleScanning = false)
            }
        }
        mainHandler.post(bleRowPruneRunnable)
        scheduleBleScanAutoStop()
    }

    private fun disconnectConnectedSensor() {
        val id = screenState.value.connectedSensorId
        val featherUp = screenState.value.featherBleConnected
        if (!screenState.value.sensorConnected &&
            id.isEmpty() &&
            !featherUp &&
            !screenState.value.featherSimActive
        ) {
            return
        }
        setFeatherSimActive(false)
        if (featherUp || screenState.value.featherBlePhase != "Idle") {
            disconnectFeatherBle()
        }
        stopConnectedRssiPolling()
        stopBleScan()
        if (id.isNotEmpty()) {
            try {
                polarApi.disconnectFromDevice(id)
            } catch (e: Exception) {
                Log.e("HnHBridge", "disconnectFromDevice failed", e)
            }
        }
        // Optimistic UI clear; Polar callback also clears if/when it fires.
        hrDisposable?.dispose()
        hrDisposable = null
        rrStreamingStarted = false
        ecgDisposable?.dispose()
        ecgDisposable = null
        ecgStreamingStarted = false
        resetSensorContactGate()
        updateScreen {
            it.copy(
                sensorConnected = false,
                connectedSensorName = "",
                connectedSensorId = "",
                connectedSensorAddress = "",
                connectedSensorRssi = null,
                sensorContact = SensorContactState.Unknown,
                recentHrBpm = null,
                bleDialogVisible = false,
                bleConnecting = false,
                bleRows = emptyList(),
                bleSelectedId = null,
            )
        }
    }

    private fun cancelSensorDialog() {
        stopBleScan()
        updateScreen {
            it.copy(
                bleDialogVisible = false,
                bleConnecting = false,
                bleSelectedId = null,
                bleRows = emptyList(),
            )
        }
        resumeConnectedRssiMonitoring()
    }

    private fun confirmSensorSelection() {
        val id = screenState.value.bleSelectedId ?: return
        val connectedId = screenState.value.connectedSensorId
        val isAlreadyConnectedSelection =
            screenState.value.sensorConnected &&
                connectedId.isNotEmpty() &&
                connectedId.equals(id, ignoreCase = true)

        if (isAlreadyConnectedSelection) {
            stopBleScan()
            updateScreen {
                it.copy(
                    bleDialogVisible = false,
                    bleConnecting = false,
                    bleRows = emptyList(),
                    bleSelectedId = null,
                )
            }
            // beginSensorScan() stopped RSSI polling; must resume like Cancel does.
            resumeConnectedRssiMonitoring()
            return
        }

        stopBleScan()
        try {
            if (screenState.value.sensorConnected && connectedId.isNotEmpty()) {
                try {
                    polarApi.disconnectFromDevice(connectedId)
                } catch (e: Exception) {
                    Log.w("HnHBridge", "disconnect before sensor switch", e)
                }
            }
            polarApi.connectToDevice(id)
            updateScreen { it.copy(bleConnecting = true) }
        } catch (e: Exception) {
            Log.e("HnHBridge", "connectToDevice failed", e)
            updateScreen { it.copy(bleConnecting = false) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideNavigationBarSticky()
        bridgePort = loadBridgePortPref()
        val keepAlivePref = loadKeepAliveInBackgroundPref()
        val sessionModePref = loadSessionModePref()
        featherProfileStore =
            com.example.polarh10bridge.feather.FeatherProfileStore(
                File(filesDir, "feather_profiles"),
            )
        featherProfileStore?.ensureFactoryProfiles()
        ritualPackageStore =
            com.example.polarh10bridge.ritual.RitualPackageStore(
                File(filesDir, "ritual_packages"),
            )
        sessionController.preferredMode = sessionModePref
        sessionController.preferredKind =
            when (sessionModePref) {
                BridgeSessionMode.Record -> BridgeSessionKind.Ritual
                BridgeSessionMode.Stream -> BridgeSessionKind.Session
            }
        val techViewPref = loadTechViewPref()
        val sourceKindPref = loadSourceKindPref()
        val activeProfile = featherProfileStore?.loadActive()
        val activeCoeffs =
            activeProfile?.let {
                com.example.polarh10bridge.feather.FeatherPatientProfile.coeffDraftFrom(it)
            }.orEmpty()
        val ritualSummary = ritualPackageStore?.summaryUi()
        screenState.value =
            screenState.value.copy(
                bridgePort = bridgePort,
                keepAliveInBackground = keepAlivePref,
                foregroundServiceActive = BridgeForegroundService.isRunning,
                sessionMode = sessionModePref,
                sessionKind = sessionController.preferredKind,
                techView = techViewPref,
                selectedSourceKind = sourceKindPref,
                settleTrimSec = sessionController.settleTrimSec,
                sessionTargetSec = sessionController.sessionTargetSec,
                featherProfiles = featherProfileStore?.listSummaries().orEmpty(),
                featherActiveProfileId = activeProfile?.profileId ?: "demo",
                featherActiveDisplayName = activeProfile?.displayName ?: "Demo",
                featherActiveCoeffs = activeCoeffs,
                featherCoeffsEpoch = 1,
                lastRitualSessionId = ritualSummary?.sessionId,
                lastRitualAcked = ritualSummary?.acked ?: false,
                lastRitualRmssdMs = ritualSummary?.rmssdMs,
                lastRitualEmittedAt = ritualSummary?.emittedAt,
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ),
                1001,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001,
            )
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                1001,
            )
        }

        polarApi = PolarBleApiDefaultImpl.defaultImplementation(
            applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
                PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
            ),
        )

        polarApi.setApiCallback(
            object : PolarBleApiCallback() {
                override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                    Log.d("HnHBridge", "Polar connected: ${polarDeviceInfo.deviceId}")
                    val rssi =
                        screenState.value.bleRows.find {
                            it.deviceId == polarDeviceInfo.deviceId ||
                                normBleAddr(it.address) == normBleAddr(polarDeviceInfo.address) ||
                                normBleAddr(it.deviceId) == normBleAddr(polarDeviceInfo.deviceId)
                        }?.rssi
                    mainHandler.post {
                        disconnectFeatherBle()
                        setFeatherSimActive(false)
                        saveSourceKindPref(SourceKind.PolarH10)
                        val nameFromRow =
                            screenState.value.bleRows.find {
                                it.deviceId == polarDeviceInfo.deviceId ||
                                    normBleAddr(it.address) == normBleAddr(polarDeviceInfo.address)
                            }?.displayName
                        screenState.value =
                            screenState.value.copy(
                                bleDialogVisible = false,
                                bleConnecting = false,
                                selectedSourceKind = SourceKind.PolarH10,
                                sensorConnected = true,
                                connectedSensorName =
                                    polarDeviceInfo.name.ifBlank {
                                        nameFromRow ?: "Polar H10"
                                    },
                                connectedSensorId = polarDeviceInfo.deviceId,
                                connectedSensorAddress = polarDeviceInfo.address.orEmpty(),
                                connectedSensorRssi = rssi,
                                sensorContact = SensorContactState.Unknown,
                                bleRows = emptyList(),
                                bleSelectedId = null,
                            )
                        resetSensorContactGate()
                        resumeConnectedRssiMonitoring()
                    }
                }

                override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                    Log.d("HnHBridge", "Polar disconnected: ${polarDeviceInfo.deviceId}")

                    hrDisposable?.dispose()
                    hrDisposable = null
                    rrStreamingStarted = false

                    ecgDisposable?.dispose()
                    ecgDisposable = null
                    ecgStreamingStarted = false
                    resetSensorContactGate()

                    mainHandler.post {
                        screenState.value =
                            screenState.value.copy(
                                sensorConnected = false,
                                connectedSensorName = "",
                                connectedSensorId = "",
                                connectedSensorAddress = "",
                                connectedSensorRssi = null,
                                sensorContact = SensorContactState.Unknown,
                                recentHrBpm = null,
                            )
                    }
                    stopConnectedRssiPolling()
                }

                override fun disInformationReceived(identifier: String, disInfo: DisInfo) {}

                override fun htsNotificationReceived(
                    identifier: String,
                    data: PolarHealthThermometerData,
                ) {}

                override fun bleSdkFeatureReady(
                    identifier: String,
                    feature: PolarBleApi.PolarBleSdkFeature,
                ) {
                    Log.d("HnHBridge", "feature ready: $feature for $identifier")
                    if (feature != PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) return

                    if (hrDisposable?.isDisposed == false) {
                        Log.d("HnHBridge", "HR stream subscription already active; skip")
                    } else {
                        rrStreamingStarted = true
                        Log.d("HnHBridge", "Starting HR stream once")

                        hrDisposable = polarApi.startHrStreaming(identifier)
                            .observeOn(Schedulers.io())
                            .subscribe(
                                { hrData ->
                                    for (sample in hrData.samples) {
                                        applyHrContactSample(
                                            contactStatusSupported = sample.contactStatusSupported,
                                            contactStatus = sample.contactStatus,
                                            hrBpm = sample.hr,
                                        )
                                        val allowTelemetry = telemetryAllowedByContact
                                        Log.d(
                                            "HnHBridge",
                                            "HR=${sample.hr} rr=${sample.rrsMs} " +
                                                "contact=${sample.contactStatus}/" +
                                                "supported=${sample.contactStatusSupported} " +
                                                "forward=$allowTelemetry",
                                        )
                                        if (!allowTelemetry) continue
                                        for (rr in sample.rrsMs) {
                                            if (rr > 0) {
                                                val now = SystemClock.elapsedRealtime()
                                                sessionController.onRrMs(rr, now)
                                                sendSourceRrLine(rr)
                                                val rolling =
                                                    sessionController.maybeRollingRmssdJson(
                                                        now,
                                                        sourceDeviceWire(),
                                                    )
                                                if (rolling != null) {
                                                    sendBridgeJsonLine(rolling.toString())
                                                    val value =
                                                        rolling.optDouble("rmssd_ms", Double.NaN)
                                                    updateScreen {
                                                        it.copy(
                                                            sessionIbiCount = sessionController.ibiCount,
                                                            lastRmssdMs =
                                                                value.takeIf { v -> !v.isNaN() }
                                                                    ?: it.lastRmssdMs,
                                                        )
                                                    }
                                                } else if (
                                                    sessionController.isActive() &&
                                                        sessionController.ibiCount % 5 == 0
                                                ) {
                                                    updateScreen {
                                                        it.copy(sessionIbiCount = sessionController.ibiCount)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                { err -> Log.e("HnHBridge", "HR stream error", err) },
                            )
                        disposables.add(hrDisposable!!)
                    }

                    if (ecgDisposable?.isDisposed == false || ecgStreamingStarted) {
                        Log.d("HnHBridge", "ECG stream already active; skip")
                        return
                    }

                    ecgStreamingStarted = true
                    Log.d("HnHBridge", "Starting ECG stream once")

                    ecgDisposable = polarApi.requestStreamSettings(
                        identifier,
                        PolarBleApi.PolarDeviceDataType.ECG,
                    )
                        .toFlowable()
                        .flatMap { sensorSetting: PolarSensorSetting ->
                            Log.d("HnHBridge", "ECG settings ready")
                            polarApi.startEcgStreaming(identifier, sensorSetting.maxSettings())
                        }
                        .observeOn(Schedulers.io())
                        .subscribe(
                            { ecgData: PolarEcgData ->
                                if (!telemetryAllowedByContact) {
                                    return@subscribe
                                }
                                val samplesMv = extractEcgMillivolts(ecgData)
                                if (samplesMv.isEmpty()) {
                                    Log.d("HnHBridge", "ECG batch empty after parse; skipping")
                                    return@subscribe
                                }

                                val batch = if (samplesMv.size > 130) samplesMv.takeLast(130) else samplesMv
                                val json =
                                    """{"type":"ecg","source_device":"${sourceDeviceWire()}","sample_rate_hz":130,"samples_mv":$batch}"""
                                sendBridgeJsonLine(json)
                                sessionController.onEcgMv(130, batch)
                                Log.d("HnHBridge", "ECG batch sent size=${batch.size}")
                            },
                            { err ->
                                Log.e("HnHBridge", "ECG stream error", err)
                                ecgStreamingStarted = false
                            },
                        )
                    disposables.add(ecgDisposable!!)
                }
            },
        )

        discoveryExecutor.execute {
            val buf = ByteArray(2048)
            while (!Thread.currentThread().isInterrupted) {
                var socket: DatagramSocket? = null
                try {
                    socket =
                        DatagramSocket().apply {
                            reuseAddress = true
                            bind(InetSocketAddress(PHONE_UDP_DISCOVERY_PORT))
                        }
                    synchronized(discoverySocketLock) {
                        udpDiscoverySocket = socket
                    }
                    Log.d("HnHBridge", "UDP discovery listening on port $PHONE_UDP_DISCOVERY_PORT")
                    while (!Thread.currentThread().isInterrupted) {
                        val p = DatagramPacket(buf, buf.size)
                        socket.receive(p)
                        val text = String(p.data, 0, p.length, Charsets.UTF_8).trim()
                        if (!text.startsWith(PHONE_UDP_DISCOVER_PREFIX)) continue
                        val hostLabel = Build.MODEL.orEmpty().ifBlank { "Android" }
                        val replyJson =
                            JSONObject()
                                .put("app", "ECG-Phone-Bridge")
                                .put("role", "phone_bridge")
                                .put("hostname", hostLabel)
                                .put("port", bridgePort)
                                .put("protocol", BRIDGE_PROTOCOL_ID)
                                .put("bridge_version", applicationContext.appVersionName())
                                .put(
                                    "features",
                                    org.json.JSONArray(
                                        listOf(
                                            "stream",
                                            "record",
                                            "rmssd_snapshot",
                                            "feather_profiles",
                                            "ritual_persist",
                                        ),
                                    ),
                                )
                                .toString() + "\n"
                        val replyBytes = replyJson.toByteArray(Charsets.UTF_8)
                        socket.send(DatagramPacket(replyBytes, replyBytes.size, p.socketAddress))
                    }
                } catch (e: SocketException) {
                    if (Thread.currentThread().isInterrupted) break
                    Log.d("HnHBridge", "UDP discovery: ${e.message}")
                } catch (e: Exception) {
                    Log.e("HnHBridge", "UDP discovery error", e)
                } finally {
                    synchronized(discoverySocketLock) {
                        if (udpDiscoverySocket === socket) {
                            udpDiscoverySocket = null
                        }
                    }
                    try {
                        socket?.close()
                    } catch (_: Exception) {
                    }
                    if (!Thread.currentThread().isInterrupted) {
                        try {
                            Thread.sleep(1500)
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            }
        }

        bridgeExecutor.execute {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    ServerSocket().use { server ->
                        server.reuseAddress = true
                        val listenPort = bridgePort
                        synchronized(bridgeServerSocketLock) {
                            bridgeServerSocket = server
                        }
                        server.bind(InetSocketAddress(listenPort))
                        Log.d("HnHBridge", "TCP listening on port $listenPort")

                        while (!Thread.currentThread().isInterrupted) {
                            // Keep accept() free. Probes are ignored; a host that sends a line replaces.
                            val client = server.accept()
                            observeInboundClient(client)
                        }
                    }
                } catch (e: Exception) {
                    if (Thread.currentThread().isInterrupted) break
                    Log.e("HnHBridge", "Bridge server error", e)
                    synchronized(bridgeServerSocketLock) {
                        if (bridgeServerSocket != null) {
                            bridgeServerSocket = null
                        }
                    }
                    try {
                        Thread.sleep(2000)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }

        setContent {
            ECGPhoneBridgeTheme {
                val state by screenState
                val ipHintRefreshSession by bridgeIpHintRefreshSession
                var showStartupWizard by remember {
                    // Session coach: every cold start (Activity create). Exit dismisses until
                    // process death or ☰ Start session. Pref still records last finish for hosts.
                    mutableStateOf(true)
                }
                // Shared so the banner is visible on the wizard overlay, not only Tech/Patient.
                var availableUpdate by remember { mutableStateOf<AvailableAppUpdate?>(null) }
                BridgeMainScreen(
                    state = state,
                    ipHintRefreshSession = ipHintRefreshSession,
                    readPhoneWifiIpv4 = { screenState.value.phoneWifiIpv4 },
                    onWifiRadioAvailabilityChanged = { _ ->
                        schedulePhoneWifiLinkRefreshWithRetries()
                        bridgeIpHintRefreshSession.value = bridgeIpHintRefreshSession.value + 1
                    },
                    onFindSource = { beginFindSource() },
                    onChangeSourceKind = { setSelectedSourceKind(it) },
                    onDisconnectSensor = { disconnectConnectedSensor() },
                    onSaveBridgePort = { newPort ->
                        val clamped = newPort.coerceIn(BRIDGE_PORT_MIN, BRIDGE_PORT_MAX)
                        bridgePort = clamped
                        saveBridgePortPref(clamped)
                        updateScreen { it.copy(bridgePort = clamped) }
                        restartBridgeServerIfNeeded()
                    },
                    onSaveKeepAliveInBackground = { enabled ->
                        saveKeepAliveInBackgroundPref(enabled)
                        updateScreen { it.copy(keepAliveInBackground = enabled) }
                        if (!enabled) {
                            stopBridgeForegroundService()
                        }
                    },
                    onSessionModeSelected = { mode -> setPreferredSessionMode(mode) },
                    onStartSession = { startBridgeSession() },
                    onStopSession = { stopBridgeSession() },
                    onSendLastRitual = { sendLastRitualManual() },
                    onToggleTechView = { setTechView(!screenState.value.techView) },
                    onOpenStartupWizard = { showStartupWizard = true },
                    onRefreshTechMeters = { refreshTechQualityUi() },
                    onSelectFeatherProfile = { id -> selectFeatherProfile(id) },
                    onAddFeatherPatient = { name -> addFeatherPatient(name) },
                    onRenameFeatherPatient = { name -> renameFeatherPatientDisplayName(name) },
                    onDeleteFeatherProfile = { id -> deleteFeatherProfile(id) },
                    onSaveFeatherProfileCoeffs = { draft -> storeFeatherOfflineCoeffs(draft) },
                    onGetFeatherOffline = { getFeatherOfflineFromLibrary() },
                    onSendFeatherOffline = { draft -> sendFeatherOfflineToMcu(draft) },
                    onAcceptFeatherProfileHint = { acceptFeatherProfileHint() },
                    onDismissFeatherProfileHint = { dismissFeatherProfileHint() },
                    availableUpdate = availableUpdate,
                    onAvailableUpdateChange = { availableUpdate = it },
                )
                if (showStartupWizard) {
                    StartupWizardOverlay(
                        state = state,
                        phoneIpHint = state.phoneWifiIpv4,
                        readPhoneWifiIpv4 = { screenState.value.phoneWifiIpv4 },
                        onRequestWifiIpRefresh = {
                            schedulePhoneWifiLinkRefreshWithRetries()
                            bridgeIpHintRefreshSession.value = bridgeIpHintRefreshSession.value + 1
                        },
                        initialRole = loadWizardLastRolePref(),
                        initialJob = loadWizardLastJobPref(),
                        onRoleChosen = { role ->
                            saveWizardLastRolePref(role)
                            setTechView(role == WizardRole.Caregiver)
                        },
                        onJobChosen = { job ->
                            saveWizardLastJobPref(job)
                            when (job) {
                                WizardJob.RecordHrv ->
                                    setPreferredSessionMode(BridgeSessionMode.Record)
                                WizardJob.Stream ->
                                    setPreferredSessionMode(BridgeSessionMode.Stream)
                                WizardJob.Breathe -> Unit
                            }
                        },
                        onSourceKindChosen = { kind -> setSelectedSourceKind(kind) },
                        onFindSource = { beginFindSource() },
                        onDisconnectSensor = { disconnectConnectedSensor() },
                        onStartSession = { startBridgeSession() },
                        onSelectFeatherProfile = { id -> selectFeatherProfile(id) },
                        onFinished = { markCompleted ->
                            if (markCompleted) {
                                saveWizardCompletedPref(true)
                            }
                            showStartupWizard = false
                        },
                        availableUpdate = availableUpdate,
                        onAvailableUpdateChange = { availableUpdate = it },
                    )
                }
                if (state.bleDialogVisible) {
                    SensorListDialog(
                        scanning = state.bleScanning,
                        connecting = state.bleConnecting,
                        rows = state.bleRows,
                        selectedId = state.bleSelectedId,
                        onSelect = { id ->
                            screenState.value = screenState.value.copy(bleSelectedId = id)
                        },
                        onDismissRequest = { cancelSensorDialog() },
                        onCancel = { cancelSensorDialog() },
                        onOk = { confirmSensorSelection() },
                    )
                }
                if (state.featherConnectOverlayVisible) {
                    FeatherConnectingDialog(
                        phase = state.featherBlePhase,
                        detail = state.featherBleDetail,
                        onCancel = {
                            disconnectFeatherBle()
                            updateScreen { it.copy(featherConnectOverlayVisible = false) }
                        },
                        onDismissRequest = {
                            updateScreen { it.copy(featherConnectOverlayVisible = false) }
                        },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideNavigationBarSticky()
        screenState.value =
            screenState.value.copy(
                foregroundServiceActive = BridgeForegroundService.isRunning,
            )
        // Always refresh — Wi-Fi client off does not mean no LAN IP (mobile hotspot).
        schedulePhoneWifiLinkRefreshWithRetries()
        bridgeIpHintRefreshSession.value = bridgeIpHintRefreshSession.value + 1
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideNavigationBarSticky()
        }
    }

    /** Bottom nav auto-hides; swipe edge briefly reveals it (does not keep a permanent chrome strip). */
    private fun hideNavigationBarSticky() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.navigationBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onStart() {
        super.onStart()
        stopBridgeForegroundService()
    }

    override fun onStop() {
        // Do not start keep-alive when the user is actually leaving/finishing the app.
        if (!isChangingConfigurations && !isFinishing && shouldKeepBridgeAliveInBackground()) {
            startBridgeForegroundService()
        }
        super.onStop()
    }

    override fun onDestroy() {
        // Swipe-away / Back-finish: tear down fully so PC hosts see the link drop.
        if (isFinishing) {
            stopBridgeForegroundService()
        }
        // Skip teardown only when background keep-alive FGS is holding the process.
        // Orientation is handled via android:configChanges so this Activity is not recreated.
        val keepAlive =
            BridgeForegroundService.isRunning && !isFinishing && !isChangingConfigurations
        if (keepAlive) {
            super.onDestroy()
            return
        }
        rrStreamingStarted = false
        ecgStreamingStarted = false
        mainHandler.removeCallbacks(featherSimRunnable)
        mainHandler.removeCallbacks(featherSimEcgRunnable)
        featherBleClient?.disconnect()
        featherBleClient = null

        hrDisposable?.dispose()
        hrDisposable = null

        ecgDisposable?.dispose()
        ecgDisposable = null

        bleSearchDisposable?.dispose()
        bleSearchDisposable = null
        stopConnectedRssiPolling()

        mainHandler.removeCallbacks(bridgeWireKeepAliveRunnable)
        mainHandler.removeCallbacks(bridgeLinkPingRunnable)
        releaseBridgeWifiLock()
        bridgeClient?.let { client ->
            try {
                client.close()
            } catch (_: Exception) {
            }
        }
        bridgeClient = null
        bridgeWriter = null

        synchronized(discoverySocketLock) {
            try {
                udpDiscoverySocket?.close()
            } catch (_: Exception) {
            }
            udpDiscoverySocket = null
        }
        synchronized(bridgeServerSocketLock) {
            try {
                bridgeServerSocket?.close()
            } catch (_: Exception) {
            }
            bridgeServerSocket = null
        }

        disposables.clear()
        cancelScheduledPhoneWifiLinkRefresh()
        discoveryExecutor.shutdownNow()
        bridgeExecutor.shutdownNow()
        bridgeWriteExecutor.shutdownNow()
        bridgeReadExecutor.shutdownNow()

        if (::polarApi.isInitialized) {
            polarApi.shutDown()
        }
        super.onDestroy()
    }
}

@Composable
private fun BridgeMainScreen(
    state: BridgeScreenState,
    /** Activity bumps this when IPv4 data may have changed (forces LaunchedEffect to poll again). */
    ipHintRefreshSession: Int,
    /** Live read from Activity screen state (Compose may miss invalidations from Handler-delayed updates). */
    readPhoneWifiIpv4: () -> String?,
    onWifiRadioAvailabilityChanged: (enabled: Boolean) -> Unit,
    onFindSource: () -> Unit,
    onChangeSourceKind: (SourceKind) -> Unit,
    onDisconnectSensor: () -> Unit,
    onSaveBridgePort: (Int) -> Unit,
    onSaveKeepAliveInBackground: (Boolean) -> Unit,
    onSessionModeSelected: (BridgeSessionMode) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onSendLastRitual: () -> Unit,
    onToggleTechView: () -> Unit,
    onOpenStartupWizard: () -> Unit,
    onRefreshTechMeters: () -> Unit,
    onSelectFeatherProfile: (String) -> Unit,
    onAddFeatherPatient: (String) -> Unit,
    onRenameFeatherPatient: (String) -> Unit,
    onDeleteFeatherProfile: (String) -> Unit,
    onSaveFeatherProfileCoeffs: (Map<String, String>) -> Unit,
    onGetFeatherOffline: () -> Unit,
    onSendFeatherOffline: (Map<String, String>) -> Unit,
    onAcceptFeatherProfileHint: () -> Unit,
    onDismissFeatherProfileHint: () -> Unit,
    availableUpdate: AvailableAppUpdate?,
    onAvailableUpdateChange: (AvailableAppUpdate?) -> Unit,
) {
    val context = LocalContext.current
    var wifiRadioEnabled by remember(context) {
        mutableStateOf(isWifiRadioOn(context))
    }
    val onWifiRadioAvailabilityChangedState by rememberUpdatedState(onWifiRadioAvailabilityChanged)
    val peekPhoneWifiIpv4 by rememberUpdatedState(readPhoneWifiIpv4)
    var connectHintIpv4 by remember { mutableStateOf<String?>(null) }
    var showSourcePicker by remember { mutableStateOf(false) }
    var showConnectedSensorActions by remember { mutableStateOf(false) }
    var showSimBlocked by remember { mutableStateOf(false) }
    LaunchedEffect(state.phoneWifiIpv4, ipHintRefreshSession) {
        connectHintIpv4 = null
        var best: String? = null
        repeat(48) { attempt ->
            val cur = peekPhoneWifiIpv4() ?: wifiIpv4String(context)
            if (cur != null) {
                best = preferMoreLikelyLanDisplayIp(best, cur)
                connectHintIpv4 = best
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
        val act = context as? ComponentActivity
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val now = isWifiRadioOn(context)
                    val prev = wifiRadioEnabled
                    wifiRadioEnabled = now
                    if (now != prev) {
                        onWifiRadioAvailabilityChangedState(now)
                    }
                }
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val lifecycleObserver =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val now = isWifiRadioOn(context)
                    val prev = wifiRadioEnabled
                    wifiRadioEnabled = now
                    if (now != prev) {
                        onWifiRadioAvailabilityChangedState(now)
                    }
                }
            }
        act?.lifecycle?.addObserver(lifecycleObserver)
        val nowRadio = isWifiRadioOn(context)
        wifiRadioEnabled = nowRadio
        onWifiRadioAvailabilityChangedState(nowRadio)
        onDispose {
            act?.lifecycle?.removeObserver(lifecycleObserver)
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
        }
    }
    val versionName = remember(context) { context.appVersionName() }
    var menuExpanded by remember { mutableStateOf(false) }
    var showConnectionSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var updateCheckInProgress by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val menuScope = rememberCoroutineScope()
    val onAvailableUpdateChangeState by rememberUpdatedState(onAvailableUpdateChange)

    LaunchedEffect(versionName) {
        if (versionName.isBlank()) return@LaunchedEffect
        // Always hit GitHub on cold start so a release published after the last
        // cached check (6h) is not missed. Cache still applies to non-forced calls.
        onAvailableUpdateChangeState(
            checkForAvailableAppUpdate(context, versionName, forceNetwork = true),
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(UiWhite)
                .windowInsetsPadding(WindowInsets.navigationBars),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(
                            WindowInsets.statusBars.union(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Top),
                            ),
                        )
                        .background(BannerRed)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "ECG Phone Bridge",
                        color = UiWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box {
                        TextButton(onClick = { menuExpanded = true }) {
                            Text(text = "\u2630", color = UiWhite, fontSize = 20.sp)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Connection settings") },
                                onClick = {
                                    menuExpanded = false
                                    showConnectionSettings = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Start session") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenStartupWizard()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (state.techView) {
                                            "Switch to Patient view"
                                        } else {
                                            "Switch to Tech view"
                                        },
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleTechView()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Check for updates") },
                                onClick = {
                                    menuExpanded = false
                                    if (versionName.isBlank() || updateCheckInProgress) return@DropdownMenuItem
                                    updateCheckInProgress = true
                                    menuScope.launch {
                                        try {
                                            val result =
                                                checkForAppUpdateDetailed(
                                                    context,
                                                    versionName,
                                                    forceNetwork = true,
                                                    ignoreDismissed = true,
                                                )
                                            val msg =
                                                when (result) {
                                                    is AppUpdateCheckResult.Available -> {
                                                        onAvailableUpdateChange(result.update)
                                                        "Update available: ${result.update.versionLabel}"
                                                    }
                                                    AppUpdateCheckResult.UpToDate -> {
                                                        onAvailableUpdateChange(null)
                                                        "You're up to date ($versionName)"
                                                    }
                                                    AppUpdateCheckResult.Failed ->
                                                        "Could not check for updates"
                                                }
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        } catch (_: Exception) {
                                            Toast
                                                .makeText(
                                                    context,
                                                    "Could not check for updates",
                                                    Toast.LENGTH_SHORT,
                                                )
                                                .show()
                                        } finally {
                                            updateCheckInProgress = false
                                        }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    menuExpanded = false
                                    showAbout = true
                                },
                            )
                        }
                    }
                }
            }
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
            val showWifiClientOffBanner = !wifiRadioEnabled && !state.pcBridgeConnected
            if (showWifiClientOffBanner) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFFF3CD),
                ) {
                    Text(
                        text = "Wi-Fi client is off. If you're using mobile hotspot, this can be normal.",
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        color = TextDark,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Show LAN connect instructions whenever we have a Wi-Fi/hotspot IPv4 and the PC
                // is not yet linked (Wi-Fi client may be off while mobile hotspot is on).
                val wifiIp =
                    connectHintIpv4
                        ?: state.phoneWifiIpv4
                        ?: wifiIpv4String(LocalContext.current)
                if (wifiIp != null && !state.pcBridgeConnected) {
                    Text(
                        text = "On your PC, open a J. Kobe host app and connect to $wifiIp:${state.bridgePort}",
                        color = TextDark.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        style =
                            TextStyle(
                                lineHeightStyle =
                                    LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both,
                                    ),
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                    )
                }
                if (state.foregroundServiceActive) {
                    Text(
                        text = "Background keep-alive is active (foreground notification shown).",
                        color = TextDark.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                    )
                }

                BridgeFlowDiagram(
                    sourceKind = state.selectedSourceKind,
                    sourceLinked = state.diagramSourceActive(),
                    inProgressLine = state.featherInProgressLine(),
                    phoneIpv4 =
                        connectHintIpv4
                            ?: state.phoneWifiIpv4
                            ?: wifiIpv4String(LocalContext.current),
                    pcBridgeConnected = state.pcBridgeConnected,
                    pcBridgeIp = state.pcBridgeIp,
                    pcBridgeUserName = state.pcBridgeUserName,
                    pcClientApp = state.pcClientApp,
                    onFindSource = {
                        if (state.diagramSourceActive()) {
                            showConnectedSensorActions = true
                        } else if (
                            state.selectedSourceKind == SourceKind.Simulate &&
                                (state.sensorConnected || state.featherBleConnected)
                        ) {
                            showSimBlocked = true
                        } else {
                            onFindSource()
                        }
                    },
                    onChangeSource = { showSourcePicker = true },
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                if (state.anySourceLinked()) {
                    val linkedNameTransition =
                        rememberInfiniteTransition(label = "linkedNamePulse")
                    val namePulse by linkedNameTransition.animateFloat(
                        initialValue = 0.72f,
                        targetValue = 1f,
                        animationSpec =
                            infiniteRepeatable(
                                animation = tween(1200, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse,
                            ),
                        label = "linkedNameAlpha",
                    )
                    Text(
                        text = "Connected to:",
                        color = TextDark,
                        fontSize = 13.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Medium,
                        style =
                            TextStyle(
                                lineHeightStyle =
                                    LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both,
                                    ),
                            ),
                        modifier = Modifier.align(Alignment.Start),
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = buildString {
                            append(
                                connectedSensorSingleLine(
                                    state.connectedSensorName.ifBlank {
                                        if (state.featherBleConnected) {
                                            com.example.polarh10bridge.feather.FeatherBleContract
                                                .ADVERTISED_NAME_PRIMARY
                                        } else {
                                            state.selectedSourceKind.displayName()
                                        }
                                    },
                                    state.connectedSensorId,
                                ),
                            )
                            state.sensorContact.displayLabel()?.let { label ->
                                append(" · ")
                                append(label)
                            }
                            if (state.sensorConnected) {
                                state.connectedSensorRssi?.let { rssi ->
                                    append(" · ")
                                    append(rssi)
                                    append(" dBm link")
                                }
                            } else if (state.featherBleConnected) {
                                val phase = featherHumanPhase(
                                    state.featherBlePhase,
                                    state.featherBleDetail,
                                )
                                if (phase.isNotBlank()) {
                                    append(" · ")
                                    append(phase)
                                }
                            }
                        },
                        color =
                            if (state.sensorContact == SensorContactState.NoContact) {
                                Color(0xFFB3261E)
                            } else {
                                TextDark.copy(alpha = namePulse)
                            },
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        style =
                            TextStyle(
                                lineHeightStyle =
                                    LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both,
                                    ),
                            ),
                        modifier = Modifier.align(Alignment.Start),
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                if (state.techView) {
                    LaunchedEffect(state.techView, state.sessionActive) {
                        while (true) {
                            onRefreshTechMeters()
                            delay(2000)
                        }
                    }
                    BridgeSessionPanel(
                        mode = state.sessionMode,
                        kind = state.sessionKind,
                        active = state.sessionActive,
                        sessionId = state.sessionId,
                        ibiCount = state.sessionIbiCount,
                        lastRmssdMs = state.lastRmssdMs,
                        sensorConnected = state.sensorConnected,
                        featherSimActive = state.featherSimActive,
                        featherBleConnected = state.featherBleConnected,
                        lastRitualSessionId = state.lastRitualSessionId,
                        lastRitualAcked = state.lastRitualAcked,
                        lastRitualRmssdMs = state.lastRitualRmssdMs,
                        lastRitualEmittedAt = state.lastRitualEmittedAt,
                        onModeSelected = onSessionModeSelected,
                        onStart = onStartSession,
                        onStop = onStopSession,
                        onSendLastRitual = onSendLastRitual,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    TechSessionMeters(
                        sessionActive = state.sessionActive,
                        sessionMode = state.sessionMode,
                        sessionStartedElapsedMs = state.sessionStartedElapsedMs,
                        settleTrimSec = state.settleTrimSec,
                        sessionTargetSec = state.sessionTargetSec,
                        ibiCount = state.sessionIbiCount,
                        recentHrBpm = state.recentHrBpm,
                        displayRmssdMs =
                            if (state.sessionActive && state.sessionMode == BridgeSessionMode.Record) {
                                null
                            } else {
                                state.lastRmssdMs
                            },
                        acceptedBeats = state.lastAcceptedBeats,
                        qualityFlags = state.lastQualityFlags,
                        sensorContact = state.sensorContact,
                        connectedSensorRssi = state.connectedSensorRssi,
                        featherSimActive = state.featherSimActive,
                        featherBlePhase = state.featherBlePhase,
                        featherBleDetail = state.featherBleDetail,
                        featherBleLastIbiMs = state.featherBleLastIbiMs,
                        featherBleConnected = state.featherBleConnected,
                        featherLeadsOff = state.featherLeadsOff,
                        featherUseLeadsOff = state.featherUseLeadsOff,
                        featherEcgTraceMv = state.featherEcgTraceMv,
                        featherEcgTracePeaks = state.featherEcgTracePeaks,
                        featherEcgSampleHz = state.featherEcgSampleHz,
                        featherEcgPacketCount = state.featherEcgPacketCount,
                        featherProfiles = state.featherProfiles,
                        featherActiveProfileId = state.featherActiveProfileId,
                        featherActiveDisplayName = state.featherActiveDisplayName,
                        featherProfileStatus = state.featherProfileStatus,
                        featherActiveCoeffs = state.featherActiveCoeffs,
                        featherCoeffsEpoch = state.featherCoeffsEpoch,
                        tunerLinked =
                            state.pcClientApp.equals("ecg_box_tuner", ignoreCase = true),
                        onSelectFeatherProfile = onSelectFeatherProfile,
                        onAddFeatherPatient = onAddFeatherPatient,
                        onRenameFeatherPatient = onRenameFeatherPatient,
                        onDeleteFeatherProfile = onDeleteFeatherProfile,
                        onSaveFeatherProfileCoeffs = onSaveFeatherProfileCoeffs,
                        onGetFeatherOffline = onGetFeatherOffline,
                        onSendFeatherOffline = onSendFeatherOffline,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                } else if (state.sessionActive) {
                    Text(
                        text = "Session in progress",
                        color = TextDark.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                val pacerPrefs =
                    remember(context) {
                        context.getSharedPreferences(BRIDGE_PREFS_NAME, Context.MODE_PRIVATE)
                    }
                val initialPacerPreset =
                    remember(pacerPrefs) {
                        val saved = pacerPrefs.getString(BRIDGE_PACER_PRESET_PREF_KEY, null)
                        BreathPacePreset.entries.firstOrNull { it.name == saved }
                            ?: BreathPacePreset.COHERENCE
                    }
                PatientBreathingPacer(
                    initialPreset = initialPacerPreset,
                    onPresetChanged = { chosen ->
                        pacerPrefs.edit().putString(BRIDGE_PACER_PRESET_PREF_KEY, chosen.name).apply()
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        if (versionName.isNotEmpty()) {
            Text(
                text = "Version $versionName",
                color = TextDark.copy(alpha = 0.45f),
                fontSize = 11.sp,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                textAlign = TextAlign.Center,
            )
        }
    }

    if (showConnectionSettings) {
        ConnectionSettingsDialog(
            bridgePort = state.bridgePort,
            pcBridgeConnected = state.pcBridgeConnected,
            wifiIp = state.phoneWifiIpv4 ?: "Unavailable",
            subnetMask = state.phoneWifiSubnetMask ?: "Unavailable",
            keepAliveInBackground = state.keepAliveInBackground,
            onDismissRequest = { showConnectionSettings = false },
            onSavePort = { port ->
                onSaveBridgePort(port)
                showConnectionSettings = false
            },
            onSaveKeepAliveInBackground = onSaveKeepAliveInBackground,
        )
    }
    if (showAbout) {
        AboutDialog(
            versionName = versionName,
            availableUpdate = availableUpdate,
            onOpenUpdate = { update -> uriHandler.openUri(update.releaseUrl) },
            onDismissRequest = { showAbout = false },
        )
    }
    if (showSourcePicker) {
        SourcePickerDialog(
            selected = state.selectedSourceKind,
            techView = state.techView,
            onSelect = { kind ->
                if (kind == SourceKind.Simulate &&
                    !state.featherSimActive &&
                    (state.sensorConnected || state.featherBleConnected)
                ) {
                    showSourcePicker = false
                    showSimBlocked = true
                } else {
                    onChangeSourceKind(kind)
                    showSourcePicker = false
                }
            },
            onDismissRequest = { showSourcePicker = false },
        )
    }
    if (showSimBlocked) {
        FeatherSimBlockedDialog(
            sensorConnected = state.sensorConnected,
            featherBleConnected = state.featherBleConnected,
            onDismissRequest = { showSimBlocked = false },
        )
    }
    val hintPending = state.featherProfileHintPending
    val featherishSource =
        state.selectedSourceKind == SourceKind.Feather ||
            state.selectedSourceKind == SourceKind.Simulate
    if (state.techView && featherishSource && hintPending != null) {
        val appLabel =
            com.example.polarh10bridge.feather.FeatherProfileHintMatcher.clientAppLabel(
                hintPending.clientApp,
            )
        AlertDialog(
            onDismissRequest = onDismissFeatherProfileHint,
            containerColor = UiWhite,
            titleContentColor = TextDark,
            textContentColor = TextDark,
            title = { Text("Switch Feather profile?", color = TextDark) },
            text = {
                Text(
                    "$appLabel selected ${hintPending.matchedDisplayName}. " +
                        "Switch Feather profile from ${hintPending.currentDisplayName} → " +
                        "${hintPending.matchedDisplayName}?",
                    color = TextDark,
                )
            },
            confirmButton = {
                TextButton(onClick = onAcceptFeatherProfileHint) {
                    Text("Switch", color = BannerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissFeatherProfileHint) {
                    Text("Keep", color = BannerRed)
                }
            },
        )
    }
    if (showConnectedSensorActions) {
        val linkedLabel =
            when {
                state.sensorConnected ->
                    connectedSensorSingleLine(
                        state.connectedSensorName,
                        state.connectedSensorId,
                    )
                state.featherBleConnected ->
                    state.connectedSensorName.ifBlank {
                        com.example.polarh10bridge.feather.FeatherBleContract.ADVERTISED_NAME_PRIMARY
                    }
                state.featherSimActive -> "Feather sim"
                else -> state.selectedSourceKind.displayName()
            }
        ConnectedSensorActionsDialog(
            sourceLabel = linkedLabel,
            onDisconnect = {
                showConnectedSensorActions = false
                onDisconnectSensor()
            },
            onRescan = {
                showConnectedSensorActions = false
                onDisconnectSensor()
                onFindSource()
            },
            onDismissRequest = { showConnectedSensorActions = false },
        )
    }
}

@Composable
private fun ConnectionSettingsDialog(
    bridgePort: Int,
    pcBridgeConnected: Boolean,
    wifiIp: String,
    subnetMask: String,
    keepAliveInBackground: Boolean,
    onDismissRequest: () -> Unit,
    onSavePort: (Int) -> Unit,
    onSaveKeepAliveInBackground: (Boolean) -> Unit,
) {
    var portText by remember(bridgePort) {
        mutableStateOf(bridgePort.coerceIn(BRIDGE_PORT_MIN, BRIDGE_PORT_MAX).toString())
    }
    var showPortWarning by remember { mutableStateOf(false) }
    var showDisconnectPcWarning by remember { mutableStateOf(false) }
    var pendingPort by remember { mutableStateOf<Int?>(null) }
    var portMenuExpanded by remember { mutableStateOf(false) }
    val parsedPort = portText.toIntOrNull()
    val portValid = parsedPort != null && parsedPort in BRIDGE_PORT_MIN..BRIDGE_PORT_MAX
    val portDirty = parsedPort != null && parsedPort != bridgePort
    val commonPorts = listOf(8765, 7777, 5000, 8080, 9000)
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(10.dp), color = UiWhite) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Connection settings",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = TextDark,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Bridge port", color = TextDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = portText,
                        onValueChange = { input -> portText = input.filter { it.isDigit() }.take(5) },
                        singleLine = true,
                        textStyle = TextStyle(color = TextDark, fontSize = 16.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier =
                            Modifier
                                .weight(1f)
                                .background(Color.White, RoundedCornerShape(6.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (portValid) TextDark.copy(alpha = 0.55f) else BannerRed,
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                    Box(modifier = Modifier.padding(start = 8.dp)) {
                        TextButton(onClick = { portMenuExpanded = true }) {
                            Text("Select", color = BannerRed, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = portMenuExpanded,
                            onDismissRequest = { portMenuExpanded = false },
                        ) {
                            for (port in commonPorts) {
                                DropdownMenuItem(
                                    text = { Text(port.toString()) },
                                    onClick = {
                                        portText = port.toString()
                                        portMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (!portValid) {
                    Text(
                        text = "Enter a port from $BRIDGE_PORT_MIN to $BRIDGE_PORT_MAX.",
                        color = BannerRed,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "IP address: $wifiIp", color = TextDark, fontSize = 13.sp)
                Text(text = "Subnet mask: $subnetMask", color = TextDark, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Keep bridge active in background",
                        color = TextDark,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = keepAliveInBackground,
                        onCheckedChange = { enabled -> onSaveKeepAliveInBackground(enabled) },
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
                Text(
                    text = "When enabled, a persistent notification keeps bridge streaming stable while using other apps.",
                    color = TextDark.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) { Text("Cancel", color = BannerRed) }
                    TextButton(
                        onClick = {
                            val target = parsedPort ?: return@TextButton
                            if (target != bridgePort) {
                                pendingPort = target.coerceIn(BRIDGE_PORT_MIN, BRIDGE_PORT_MAX)
                                if (pcBridgeConnected) {
                                    showDisconnectPcWarning = true
                                } else {
                                    showPortWarning = true
                                }
                            }
                        },
                        enabled = portValid && portDirty,
                    ) {
                        Text(
                            "Save",
                            color =
                                if (portValid && portDirty) {
                                    BannerRed
                                } else {
                                    TextDark.copy(alpha = 0.35f)
                                },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
    if (showDisconnectPcWarning) {
        AlertDialog(
            onDismissRequest = {
                showDisconnectPcWarning = false
                pendingPort = null
            },
            containerColor = UiWhite,
            titleContentColor = TextDark,
            textContentColor = TextDark,
            title = { Text("Disconnect PC?", color = TextDark) },
            text = {
                Text(
                    "Changing the bridge port will disconnect Hertz & Hearts on your PC. You can reconnect after updating the port on the PC.",
                    color = TextDark,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPort?.let { onSavePort(it) }
                        pendingPort = null
                        showDisconnectPcWarning = false
                    },
                ) {
                    Text("Change port", color = BannerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDisconnectPcWarning = false
                        pendingPort = null
                    },
                ) {
                    Text("Cancel", color = BannerRed)
                }
            },
        )
    }
    if (showPortWarning) {
        AlertDialog(
            onDismissRequest = {
                showPortWarning = false
                pendingPort = null
            },
            containerColor = UiWhite,
            titleContentColor = TextDark,
            textContentColor = TextDark,
            title = { Text("Change bridge port?", color = TextDark) },
            text = {
                Text(
                    "Changing the bridge port can break connection if Hertz & Hearts on your PC is not set to the same port.",
                    color = TextDark,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingPort?.let { onSavePort(it) }
                        pendingPort = null
                        showPortWarning = false
                    },
                ) {
                    Text("Change port", color = BannerRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPortWarning = false
                        pendingPort = null
                    },
                ) {
                    Text("Cancel", color = BannerRed)
                }
            },
        )
    }
}

@Composable
internal fun AppUpdateBanner(
    update: AvailableAppUpdate,
    onGetUpdate: () -> Unit,
    onLater: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFDCEBFF),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Update available: ${update.versionLabel}",
                modifier = Modifier.weight(1f),
                color = TextDark,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            TextButton(onClick = onGetUpdate) {
                Text("Get update", color = Color(0xFF0B57D0), fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onLater) {
                Text("Later", color = TextDark.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun AboutDialog(
    versionName: String,
    availableUpdate: AvailableAppUpdate?,
    onOpenUpdate: (AvailableAppUpdate) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val today =
        remember(context) {
            android.text.format.DateFormat.getMediumDateFormat(context).format(java.util.Date())
        }
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = RoundedCornerShape(10.dp), color = UiWhite) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("About", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextDark)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "For use with Hertz & Hearts, VNS-TA, and FlareTracker",
                    color = TextDark,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                )
                Text(
                    "Developed by J. Kobe Labs",
                    color = Color(0xFF0B57D0),
                    fontSize = 13.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier =
                        Modifier.clickable {
                            uriHandler.openUri("https://jkobelabs.com/")
                        },
                )
                Text("Date: $today", color = TextDark, fontSize = 13.sp)
                if (versionName.isNotBlank()) {
                    Text("Version: $versionName", color = TextDark, fontSize = 13.sp)
                }
                if (availableUpdate != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Update available: ${availableUpdate.versionLabel}",
                        color = Color(0xFF0B57D0),
                        fontSize = 13.sp,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable { onOpenUpdate(availableUpdate) },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Close", color = BannerRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorListDialog(
    scanning: Boolean,
    connecting: Boolean,
    rows: List<BleDeviceRow>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismissRequest: () -> Unit,
    onCancel: () -> Unit,
    onOk: () -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = UiWhite,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Devices found:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = TextDark,
                )
                Spacer(modifier = Modifier.height(12.dp))
                when {
                    scanning && rows.isEmpty() -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.size(12.dp))
                            Text(text = "Scanning for sensors…", color = TextDark, fontSize = 14.sp)
                        }
                    }

                    rows.isEmpty() && !scanning -> {
                        Text(text = "No devices found.", color = TextDark, fontSize = 14.sp)
                    }

                    else -> {
                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(rows, key = { it.deviceId }) { row ->
                                val sel = selectedId == row.deviceId
                                val line1 =
                                    connectedSensorSingleLine(row.displayName, row.deviceId)
                                val line2 =
                                    if (line1.contains(row.deviceId, ignoreCase = true)) {
                                        "${row.rssi} dBm"
                                    } else {
                                        "ID ${row.deviceId} · ${row.rssi} dBm"
                                    }
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .selectable(
                                                selected = sel,
                                                onClick = { onSelect(row.deviceId) },
                                                role = Role.RadioButton,
                                            )
                                            .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = sel,
                                        onClick = null,
                                        colors =
                                            RadioButtonDefaults.colors(
                                                selectedColor = BannerRed,
                                            ),
                                    )
                                    Column(modifier = Modifier.padding(start = 4.dp)) {
                                        Text(
                                            text = line1,
                                            fontSize = 14.sp,
                                            color = TextDark,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = line2,
                                            fontSize = 12.sp,
                                            color = TextDark,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (connecting) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Connecting…", fontSize = 14.sp, color = TextDark)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = BannerRed)
                    }
                    TextButton(
                        onClick = onOk,
                        enabled = !connecting && selectedId != null && rows.isNotEmpty(),
                    ) {
                        Text("Connect", color = BannerRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun Context.appVersionName(): String =
    try {
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    } catch (_: Exception) {
        ""
    }

@Suppress("DEPRECATION")
private fun isWifiRadioOn(context: Context): Boolean {
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: return true
    return wm.isWifiEnabled
}

internal fun isLikelyTenSlashEightLanIpv4String(host: String): Boolean {
    val parts = host.split('.').mapNotNull { it.toIntOrNull() }
    return parts.size == 4 && parts[0] == 10
}

/** When two readings disagree, prefer the address that is not in 10.0.0.0/8 if the other is. */
internal fun preferMoreLikelyLanDisplayIp(current: String?, newer: String?): String? {
    when {
        newer.isNullOrBlank() -> return current
        current.isNullOrBlank() -> return newer
        isLikelyTenSlashEightLanIpv4String(current) &&
            !isLikelyTenSlashEightLanIpv4String(newer) -> return newer
        !isLikelyTenSlashEightLanIpv4String(current) &&
            isLikelyTenSlashEightLanIpv4String(newer) -> return current
        else -> return newer
    }
}

/**
 * Typical home/offices use 192.168/16 or 172.16/12. 10/8 also appears on LANs but matches many
 * carrier CGNAT ranges and Wi-Fi Direct-style subnets; score it lower when multiple Wi-Fi networks exist.
 * 100.64/10 is CGNAT (RFC 6598); deprioritize for "your Wi-Fi IP" display.
 */
private fun ipv4LanRangeScore(addr: Inet4Address): Int {
    if (addr.isLinkLocalAddress) return -1_000_000
    val h = addr.hostAddress ?: return 0
    val parts = h.split('.').mapNotNull { it.toIntOrNull() }
    if (parts.size != 4) return 0
    val a = parts[0]
    val b = parts[1]
    return when {
        a == 192 && b == 168 -> 400_000
        a == 172 && b in 16..31 -> 350_000
        a == 10 -> 150_000
        a == 100 && b in 64..127 -> 20_000
        else -> 50_000
    }
}

private fun networkQualityScoreForWifiDisplay(caps: NetworkCapabilities): Int {
    var s = 0
    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) s += 80
    if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) s += 120
    return s
}

private data class WifiIpv4Pick(
    val host: String,
    val prefixLength: Int,
    val score: Int,
)

@SuppressLint("MissingPermission")
private fun pickBestWifiLanIpv4(cm: ConnectivityManager): WifiIpv4Pick? {
    val candidates = mutableListOf<WifiIpv4Pick>()
    for (network in cm.allNetworks) {
        val caps = cm.getNetworkCapabilities(network) ?: continue
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue

        val netScore = networkQualityScoreForWifiDisplay(caps)
        val lp = cm.getLinkProperties(network) ?: continue
        for (la in lp.linkAddresses) {
            val addr = la.address
            if (addr !is Inet4Address || addr.isLoopbackAddress) continue
            val host = addr.hostAddress ?: continue
            val rangeScore = ipv4LanRangeScore(addr)
            if (rangeScore < -500_000) continue
            val combined = netScore * 10_000 + rangeScore
            candidates.add(WifiIpv4Pick(host = host, prefixLength = la.prefixLength, score = combined))
        }
    }
    if (candidates.isEmpty()) return null
    val withoutTen =
        candidates.filter { !isLikelyTenSlashEightLanIpv4String(it.host) }
    val pool = if (withoutTen.isNotEmpty()) withoutTen else candidates
    return pool.maxByOrNull { it.score }
}

@SuppressLint("MissingPermission")
internal fun wifiIpv4String(context: Context): String? {
    val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return null
    // Only Wi-Fi transports; never fall back to cellular/VPN/other (misleading for LAN instructions).
    return pickBestWifiLanIpv4(cm)?.host
}

private data class WifiNetworkInfo(
    val ipv4: String?,
    val subnetMask: String?,
)

@SuppressLint("MissingPermission")
private fun wifiNetworkInfo(context: Context): WifiNetworkInfo {
    val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return WifiNetworkInfo(ipv4 = null, subnetMask = null)
    val pick = pickBestWifiLanIpv4(cm) ?: return WifiNetworkInfo(ipv4 = null, subnetMask = null)
    return WifiNetworkInfo(
        ipv4 = pick.host,
        subnetMask = prefixLengthToSubnetMask(pick.prefixLength),
    )
}

private fun prefixLengthToSubnetMask(prefixLength: Int): String {
    val p = prefixLength.coerceIn(0, 32)
    val mask = if (p == 0) 0 else (-0x1 shl (32 - p))
    val b1 = (mask ushr 24) and 0xFF
    val b2 = (mask ushr 16) and 0xFF
    val b3 = (mask ushr 8) and 0xFF
    val b4 = mask and 0xFF
    return "$b1.$b2.$b3.$b4"
}
