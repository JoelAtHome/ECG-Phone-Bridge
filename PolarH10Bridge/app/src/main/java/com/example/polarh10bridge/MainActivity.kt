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
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.example.polarh10bridge.ui.theme.PolarH10BridgeTheme
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarHealthThermometerData
import com.polar.sdk.api.model.PolarSensorSetting
import kotlinx.coroutines.delay
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
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

private val BannerRed = Color(0xFFC1121F)
private val UiWhite = Color.White
private val TextDark = Color(0xFF1A1A1A)

/** UDP port: Hertz & Hearts broadcasts here; we reply so the PC can find this phone. */
private const val PHONE_UDP_DISCOVERY_PORT = 45124

private const val PHONE_UDP_DISCOVER_PREFIX = "HnH_PHONE_BRIDGE_DISCOVER_V1"
private const val BRIDGE_PREFS_NAME = "bridge_prefs"
private const val BRIDGE_PORT_PREF_KEY = "bridge_port"
private const val BRIDGE_BG_KEEPALIVE_PREF_KEY = "bridge_bg_keepalive"
private const val BRIDGE_PACER_PRESET_PREF_KEY = "bridge_pacer_preset"
private const val BRIDGE_SESSION_MODE_PREF_KEY = "bridge_session_mode"
private const val BRIDGE_TECH_VIEW_PREF_KEY = "bridge_tech_view"
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
/** Min time between on-screen connected-sensor dBm updates (scan may run faster). */
private const val BLE_RSSI_UI_THROTTLE_MS = 1_500L

private data class BleDeviceRow(
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

private data class BridgeScreenState(
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
    val lastAcceptedBeats: Int = 0,
    val lastQualityFlags: List<String> = emptyList(),
    /** Tech-only: inject synthetic Feather IBIs into the bridge edge (no BLE box required). */
    val featherSimActive: Boolean = false,
    /** Tech Feather GATT client (live ECG-Box-Feather). */
    val featherBleConnected: Boolean = false,
    val featherBlePhase: String = "Idle",
    val featherBleDetail: String = "",
    val featherBleLastIbiMs: Int? = null,
)

class MainActivity : ComponentActivity() {
    private lateinit var polarApi: PolarBleApi
    private val disposables = CompositeDisposable()
    private val bridgeExecutor = Executors.newSingleThreadExecutor()
    private val discoveryExecutor = Executors.newSingleThreadExecutor()
    /** Socket writes must not run on the UI thread (NetworkOnMainThreadException drops Stop). */
    private val bridgeWriteExecutor = Executors.newSingleThreadExecutor()
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
    private var featherBleClient: com.example.polarh10bridge.feather.FeatherBleClient? = null
    private var featherSimIbiIndex = 0
    private var featherSimElapsedMs = 0L
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
                ingestSourceRrMs(rr, updateHrEveryBeat = true)
                mainHandler.postDelayed(this, rr.toLong())
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

    @Volatile
    private var bridgeWriter: java.io.PrintWriter? = null
    private var bridgeClient: Socket? = null

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
        updateScreen { it.copy(techView = enabled) }
    }

    private fun sourceDeviceWire(): String =
        if (screenState.value.featherSimActive || screenState.value.featherBleConnected) {
            com.example.polarh10bridge.feather.FeatherBleContract.SOURCE_DEVICE_WIRE
        } else {
            "POLAR_H10"
        }

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
                                    sensorContact =
                                        if (connected) {
                                            SensorContactState.InContact
                                        } else if (
                                            phase ==
                                                com.example.polarh10bridge.feather.FeatherBleClient
                                                    .Phase.Idle ||
                                                phase ==
                                                com.example.polarh10bridge.feather.FeatherBleClient
                                                    .Phase.Error
                                        ) {
                                            if (it.featherSimActive) {
                                                SensorContactState.InContact
                                            } else {
                                                SensorContactState.Unknown
                                            }
                                        } else {
                                            it.sensorContact
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
                                )
                            }
                            if (phase ==
                                com.example.polarh10bridge.feather.FeatherBleClient.Phase.Idle ||
                                phase ==
                                com.example.polarh10bridge.feather.FeatherBleClient.Phase.Error
                            ) {
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
                            updateScreen {
                                it.copy(featherBleDetail = json.take(120))
                            }
                        }

                        override fun onEcgSamplesUv(sampleHz: Int, samplesUv: List<Int>) {
                            if (samplesUv.isEmpty()) return
                            val mv =
                                com.example.polarh10bridge.feather.FeatherPacketCodec
                                    .samplesUvToMv(samplesUv)
                            // Host path: same shape Polar ECG uses when session active.
                            if (!screenState.value.sessionActive) return
                            val samplesJson = mv.joinToString(prefix = "[", postfix = "]")
                            sendBridgeJsonLine(
                                """{"type":"ecg","sample_rate_hz":$sampleHz,"samples_mv":$samplesJson}""",
                            )
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
        featherProfileStore?.ensureDemoProfile()
        val coeffsBytes =
            featherProfileStore
                ?.load("demo")
                ?.let {
                    com.example.polarh10bridge.feather.FeatherPacketCodec.encodeCoeffsJson(
                        it.coeffsForBleWrite(),
                    )
                }
        ensureFeatherBleClient().connectForTest(coeffsJsonUtf8 = coeffsBytes)
    }

    private fun disconnectFeatherBle() {
        featherBleClient?.disconnect()
        updateScreen {
            it.copy(
                featherBleConnected = false,
                featherBlePhase = "Idle",
                featherBleDetail = "idle",
                featherBleLastIbiMs = null,
                connectedSensorName =
                    if (it.sensorConnected) it.connectedSensorName else "",
                sensorContact =
                    if (it.featherSimActive) {
                        SensorContactState.InContact
                    } else if (it.sensorConnected) {
                        it.sensorContact
                    } else {
                        SensorContactState.Unknown
                    },
            )
        }
    }

    private fun ingestSourceRrMs(rr: Int, updateHrEveryBeat: Boolean = false) {
        if (rr <= 0) return
        val now = SystemClock.elapsedRealtime()
        sessionController.onRrMs(rr, now)
        sendBridgeJsonLine("""{"type":"rr","rr_ms":$rr}""")
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
        if (!enabled) {
            updateScreen { it.copy(featherSimActive = false) }
            return
        }
        featherSimIbiIndex = 0
        featherSimElapsedMs = 0L
        featherProfileStore?.ensureDemoProfile()
        updateScreen {
            it.copy(
                featherSimActive = true,
                sensorContact = SensorContactState.InContact,
            )
        }
        mainHandler.post(featherSimRunnable)
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
        val result = sessionController.stop(sourceDeviceWire())
        val rmssdValue =
            result.rmssd?.optDouble("rmssd_ms", Double.NaN)?.takeIf { !it.isNaN() }
        result.rmssd?.let { sendBridgeJsonLine(it.toString()) }
        result.sessionState?.let { sendBridgeJsonLine(it.toString()) }
        if (rmssdValue != null) {
            syncSessionUiFromController(lastRmssdMs = rmssdValue)
        } else {
            syncSessionUiFromController(clearRmssd = true)
        }
    }

    private fun restartBridgeServerIfNeeded() {
        synchronized(bridgeServerSocketLock) {
            try {
                bridgeClient?.close()
            } catch (_: Exception) {
            }
            bridgeClient = null
            bridgeWriter = null
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
            )
        }
    }

    private fun shouldKeepBridgeAliveInBackground(): Boolean {
        val s = screenState.value
        return s.keepAliveInBackground && (s.sensorConnected || s.pcBridgeConnected)
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
        applyPhoneWifiLinkFromNetworkToScreenState()
        mainHandler.postDelayed(wifiIpLinkRetry500Ms, 500L)
        mainHandler.postDelayed(wifiIpLinkRetry1500Ms, 1500L)
    }

    private fun cancelScheduledPhoneWifiLinkRefresh() {
        mainHandler.removeCallbacks(wifiIpLinkRetry500Ms)
        mainHandler.removeCallbacks(wifiIpLinkRetry1500Ms)
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
        if (bridgeClient !== client) return
        Log.d("HnHBridge", "closing PC socket: $reason")
        bridgeWriter = null
        bridgeClient = null
        try {
            client.close()
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
                else -> {
                    // Ignore unknown types for forward compatibility.
                }
            }
        } catch (_: Exception) {
            // Keep stream compatibility with older/newer clients.
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
        if (!screenState.value.sensorConnected && id.isEmpty() && !featherUp) return
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
        bridgePort = loadBridgePortPref()
        val keepAlivePref = loadKeepAliveInBackgroundPref()
        val sessionModePref = loadSessionModePref()
        featherProfileStore =
            com.example.polarh10bridge.feather.FeatherProfileStore(
                File(filesDir, "feather_profiles"),
            )
        featherProfileStore?.ensureDemoProfile()
        sessionController.preferredMode = sessionModePref
        sessionController.preferredKind =
            when (sessionModePref) {
                BridgeSessionMode.Record -> BridgeSessionKind.Ritual
                BridgeSessionMode.Stream -> BridgeSessionKind.Session
            }
        val techViewPref = loadTechViewPref()
        screenState.value =
            screenState.value.copy(
                bridgePort = bridgePort,
                keepAliveInBackground = keepAlivePref,
                foregroundServiceActive = BridgeForegroundService.isRunning,
                sessionMode = sessionModePref,
                sessionKind = sessionController.preferredKind,
                techView = techViewPref,
                settleTrimSec = sessionController.settleTrimSec,
                sessionTargetSec = sessionController.sessionTargetSec,
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
                        val nameFromRow =
                            screenState.value.bleRows.find {
                                it.deviceId == polarDeviceInfo.deviceId ||
                                    normBleAddr(it.address) == normBleAddr(polarDeviceInfo.address)
                            }?.displayName
                        screenState.value =
                            screenState.value.copy(
                                bleDialogVisible = false,
                                bleConnecting = false,
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
                                                sendBridgeJsonLine("""{"type":"rr","rr_ms":$rr}""")
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
                                val json = """{"type":"ecg","sample_rate_hz":130,"samples_mv":$batch}"""
                                sendBridgeJsonLine(json)
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
                                .put("app", "PolarH10Bridge")
                                .put("role", "phone_bridge")
                                .put("hostname", hostLabel)
                                .put("port", bridgePort)
                                .put("protocol", BRIDGE_PROTOCOL_ID)
                                .put("bridge_version", applicationContext.appVersionName())
                                .put(
                                    "features",
                                    org.json.JSONArray(
                                        listOf("stream", "record", "rmssd_snapshot"),
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
                            server.accept().use { client ->
                                client.keepAlive = true
                                client.tcpNoDelay = true
                                Log.d("HnHBridge", "PC connected from ${client.inetAddress.hostAddress}")

                                bridgeClient = client
                                val writer = java.io.PrintWriter(
                                    java.io.OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8),
                                    true,
                                )
                                bridgeWriter = writer
                                mainHandler.post {
                                    screenState.value =
                                        screenState.value.copy(
                                            pcBridgeConnected = true,
                                            pcBridgeIp = client.inetAddress?.hostAddress,
                                            pcBridgeUserName = null,
                                            pcClientApp = null,
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
                                // Re-announce contact/link quality so hosts that connected mid-session see it.
                                lastPublishedContactState?.let { sendSensorQualityLine(it) }
                                    ?: run {
                                        val known = screenState.value.sensorContact
                                        if (known != SensorContactState.Unknown) {
                                            lastPublishedContactState = known
                                            sendSensorQualityLine(known)
                                        }
                                    }
                                if (sessionController.isActive()) {
                                    sendBridgeJsonLine(sessionController.sessionStateJson().toString())
                                    mainHandler.removeCallbacks(bridgeWireKeepAliveRunnable)
                                    mainHandler.postDelayed(bridgeWireKeepAliveRunnable, 1_000L)
                                } else {
                                    sessionController.lastWireStopForReplay()?.let { replay ->
                                        replay.rmssd?.let { sendBridgeJsonLine(it.toString()) }
                                        replay.sessionState?.let { sendBridgeJsonLine(it.toString()) }
                                    }
                                }

                                try {
                                    val input = client.getInputStream().bufferedReader(Charsets.UTF_8)
                                    while (true) {
                                        val line = input.readLine()
                                        if (line == null) {
                                            Log.d("HnHBridge", "PC closed TCP (EOF)")
                                            break
                                        }
                                        handlePcBridgeInboundLine(line)
                                    }
                                } catch (e: SocketException) {
                                    Log.d("HnHBridge", "TCP connection lost: ${e.message}")
                                } catch (e: Exception) {
                                    Log.e("HnHBridge", "TCP read error", e)
                                } finally {
                                    bridgeWriter = null
                                    bridgeClient = null
                                    mainHandler.post {
                                        updateScreen {
                                            it.copy(
                                                pcBridgeConnected = false,
                                                pcBridgeIp = null,
                                                pcBridgeUserName = null,
                                                pcClientApp = null,
                                            )
                                        }
                                        syncSessionUiFromController()
                                    }
                                    Log.d("HnHBridge", "PC bridge TCP closed (capture continues on phone until Stop)")
                                }
                            }
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
            PolarH10BridgeTheme {
                val state by screenState
                val ipHintRefreshSession by bridgeIpHintRefreshSession
                BridgeMainScreen(
                    state = state,
                    ipHintRefreshSession = ipHintRefreshSession,
                    readPhoneWifiIpv4 = { screenState.value.phoneWifiIpv4 },
                    onWifiRadioAvailabilityChanged = { _ ->
                        schedulePhoneWifiLinkRefreshWithRetries()
                        bridgeIpHintRefreshSession.value = bridgeIpHintRefreshSession.value + 1
                    },
                    onScanSensors = { beginSensorScan() },
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
                    onToggleTechView = { setTechView(!screenState.value.techView) },
                    onRefreshTechMeters = { refreshTechQualityUi() },
                    onToggleFeatherSim = {
                        setFeatherSimActive(!screenState.value.featherSimActive)
                    },
                    onConnectFeatherBle = { beginFeatherBleTest() },
                    onDisconnectFeatherBle = { disconnectFeatherBle() },
                    onFeatherStartStream = { featherBleClient?.startStream() },
                    onFeatherStopStream = { featherBleClient?.stopStream() },
                )
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
            }
        }
    }

    override fun onResume() {
        super.onResume()
        screenState.value =
            screenState.value.copy(
                foregroundServiceActive = BridgeForegroundService.isRunning,
            )
        // Always refresh — Wi-Fi client off does not mean no LAN IP (mobile hotspot).
        schedulePhoneWifiLinkRefreshWithRetries()
        bridgeIpHintRefreshSession.value = bridgeIpHintRefreshSession.value + 1
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
    onScanSensors: () -> Unit,
    onDisconnectSensor: () -> Unit,
    onSaveBridgePort: (Int) -> Unit,
    onSaveKeepAliveInBackground: (Boolean) -> Unit,
    onSessionModeSelected: (BridgeSessionMode) -> Unit,
    onStartSession: () -> Unit,
    onStopSession: () -> Unit,
    onToggleTechView: () -> Unit,
    onRefreshTechMeters: () -> Unit,
    onToggleFeatherSim: () -> Unit,
    onConnectFeatherBle: () -> Unit,
    onDisconnectFeatherBle: () -> Unit,
    onFeatherStartStream: () -> Unit,
    onFeatherStopStream: () -> Unit,
) {
    val context = LocalContext.current
    var wifiRadioEnabled by remember(context) {
        mutableStateOf(isWifiRadioOn(context))
    }
    val onWifiRadioAvailabilityChangedState by rememberUpdatedState(onWifiRadioAvailabilityChanged)
    val peekPhoneWifiIpv4 by rememberUpdatedState(readPhoneWifiIpv4)
    var connectHintIpv4 by remember { mutableStateOf<String?>(null) }
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
    var availableUpdate by remember { mutableStateOf<AvailableAppUpdate?>(null) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(versionName) {
        if (versionName.isBlank()) return@LaunchedEffect
        // Always hit GitHub on cold start so a release published after the last
        // cached check (6h) is not missed. Cache still applies to non-forced calls.
        availableUpdate = checkForAvailableAppUpdate(context, versionName, forceNetwork = true)
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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
                        TextButton(
                            onClick = { uriHandler.openUri(update.releaseUrl) },
                        ) {
                            Text("Get update", color = Color(0xFF0B57D0), fontWeight = FontWeight.Bold)
                        }
                        TextButton(
                            onClick = {
                                dismissAvailableAppUpdate(context, update.tagName)
                                availableUpdate = null
                            },
                        ) {
                            Text("Later", color = TextDark.copy(alpha = 0.7f))
                        }
                    }
                }
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
                    sensorConnected = state.sensorConnected,
                    pcBridgeConnected = state.pcBridgeConnected,
                    pcBridgeUserName = state.pcBridgeUserName,
                    pcClientApp = state.pcClientApp,
                    onScanSensors = onScanSensors,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                if (state.sensorConnected) {
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
                                    state.connectedSensorName,
                                    state.connectedSensorId,
                                ),
                            )
                            state.sensorContact.displayLabel()?.let { label ->
                                append(" · ")
                                append(label)
                            }
                            state.connectedSensorRssi?.let { rssi ->
                                append(" · ")
                                append(rssi)
                                append(" dBm link")
                            }
                        },
                        color =
                            if (state.sensorContact == SensorContactState.NoContact) {
                                Color(0xFFB3261E)
                            } else {
                                TextDark
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
                    TextButton(
                        onClick = onDisconnectSensor,
                        modifier = Modifier.align(Alignment.Start),
                    ) {
                        Text(
                            text = "Disconnect sensor",
                            color = BannerRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    val phoneIp =
                        if (!wifiRadioEnabled) {
                            "Wi-Fi client off (hotspot may still work)"
                        } else {
                            connectHintIpv4
                                ?: state.phoneWifiIpv4
                                ?: wifiIpv4String(LocalContext.current)
                                ?: "unknown"
                        }
                    val pcIp = state.pcBridgeIp ?: "not connected"
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "Phone: $phoneIp",
                        color = TextDark,
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
                        modifier = Modifier.align(Alignment.Start),
                    )
                    Text(
                        text = "PC: $pcIp",
                        color = TextDark,
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
                        modifier = Modifier.align(Alignment.Start),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
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
                        onModeSelected = onSessionModeSelected,
                        onStart = onStartSession,
                        onStop = onStopSession,
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
                        onToggleFeatherSim = onToggleFeatherSim,
                        featherBlePhase = state.featherBlePhase,
                        featherBleDetail = state.featherBleDetail,
                        featherBleLastIbiMs = state.featherBleLastIbiMs,
                        featherBleConnected = state.featherBleConnected,
                        onConnectFeatherBle = onConnectFeatherBle,
                        onDisconnectFeatherBle = onDisconnectFeatherBle,
                        onFeatherStartStream = onFeatherStartStream,
                        onFeatherStopStream = onFeatherStopStream,
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
                            } else {
                                onSavePort(target)
                            }
                        },
                        enabled = portValid,
                    ) {
                        Text("Save", color = BannerRed, fontWeight = FontWeight.Bold)
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

private fun isLikelyTenSlashEightLanIpv4String(host: String): Boolean {
    val parts = host.split('.').mapNotNull { it.toIntOrNull() }
    return parts.size == 4 && parts[0] == 10
}

/** When two readings disagree, prefer the address that is not in 10.0.0.0/8 if the other is. */
private fun preferMoreLikelyLanDisplayIp(current: String?, newer: String?): String? {
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
private fun wifiIpv4String(context: Context): String? {
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
