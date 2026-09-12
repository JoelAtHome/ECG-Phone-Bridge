package com.example.polarh10bridge.feather

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

/**
 * Minimal Tech bring-up GATT client for ECG-Box-Feather.
 * Scan → connect → CCCD IBI (+ status) → optional coeffs → start_stream.
 */
class FeatherBleClient(
    context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val listener: Listener,
) {
    interface Listener {
        fun onPhase(phase: Phase, detail: String)

        fun onIbiMs(values: List<Int>)

        fun onStatusJson(json: String)

        fun onEcgSamplesUv(sampleHz: Int, samplesUv: List<Int>)
    }

    enum class Phase {
        Idle,
        Scanning,
        Connecting,
        Discovering,
        Ready,
        Streaming,
        Error,
    }

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    @Volatile
    private var gatt: BluetoothGatt? = null

    @Volatile
    private var scanning = false

    @Volatile
    private var phase: Phase = Phase.Idle

    private var bestDevice: BluetoothDevice? = null
    private var bestRssi = Int.MIN_VALUE
    private var pendingCoeffs: ByteArray? = null
    private var autoStartStream = true

    private val cccdUuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val scanTimeoutRunnable =
        Runnable {
            stopScanInternal(connectBest = true)
        }

    private val scanCallback =
        object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                if (result == null) return
                val name = result.device?.name ?: result.scanRecord?.deviceName
                val hasService =
                    result.scanRecord?.serviceUuids?.any {
                        it.uuid == FeatherBleContract.SERVICE_UUID
                    } == true
                if (!hasService && !FeatherBleContract.matchesAdvertisedName(name)) {
                    return
                }
                val rssi = result.rssi
                if (rssi >= bestRssi) {
                    bestRssi = rssi
                    bestDevice = result.device
                    emit(Phase.Scanning, "seen ${name ?: result.device.address} rssi=$rssi")
                }
            }

            override fun onScanFailed(errorCode: Int) {
                scanning = false
                emit(Phase.Error, "scan failed code=$errorCode")
            }
        }

    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    emit(Phase.Error, "connect status=$status")
                    closeGatt()
                    return
                }
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        emit(Phase.Discovering, "connected; discovering")
                        g.requestMtu(185)
                        g.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        emit(Phase.Idle, "disconnected")
                        closeGatt()
                    }
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "MTU=$mtu status=$status")
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    emit(Phase.Error, "discover status=$status")
                    return
                }
                val service = g.getService(FeatherBleContract.SERVICE_UUID)
                if (service == null) {
                    emit(Phase.Error, "service ${FeatherBleContract.SERVICE_UUID} missing")
                    return
                }
                val ibi = service.getCharacteristic(FeatherBleContract.IBI_NOTIFY_UUID)
                if (ibi == null) {
                    emit(Phase.Error, "IBI characteristic missing")
                    return
                }
                enableNotify(g, ibi) {
                    val ecgChar =
                        service.getCharacteristic(FeatherBleContract.ECG_NOTIFY_UUID)
                    val afterEcg = {
                        val statusChar =
                            service.getCharacteristic(FeatherBleContract.STATUS_NOTIFY_UUID)
                        if (statusChar != null) {
                            enableNotify(g, statusChar) {
                                afterNotifiesEnabled(g, service)
                            }
                        } else {
                            afterNotifiesEnabled(g, service)
                        }
                    }
                    if (ecgChar != null) {
                        enableNotify(g, ecgChar, afterEcg)
                    } else {
                        afterEcg()
                    }
                }
            }

            override fun onDescriptorWrite(
                g: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int,
            ) {
                val cont = pendingAfterDescriptorWrite
                pendingAfterDescriptorWrite = null
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    emit(Phase.Error, "CCCD write status=$status")
                    return
                }
                cont?.invoke()
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                val cont = pendingAfterCharWrite
                pendingAfterCharWrite = null
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    emit(Phase.Error, "write ${characteristic.uuid} status=$status")
                    return
                }
                cont?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                handleNotify(characteristic.uuid, characteristic.value ?: return)
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                handleNotify(characteristic.uuid, value)
            }
        }

    private var pendingAfterDescriptorWrite: (() -> Unit)? = null
    private var pendingAfterCharWrite: (() -> Unit)? = null

    fun currentPhase(): Phase = phase

    /** Scan briefly, connect strongest ECG-Box / service match, enable notifies, start_stream. */
    @SuppressLint("MissingPermission")
    fun connectForTest(
        coeffsJsonUtf8: ByteArray? = null,
        scanMs: Long = 12_000L,
        startStream: Boolean = true,
    ) {
        if (adapter == null || !adapter.isEnabled) {
            emit(Phase.Error, "Bluetooth off or missing")
            return
        }
        disconnect()
        pendingCoeffs = coeffsJsonUtf8
        autoStartStream = startStream
        bestDevice = null
        bestRssi = Int.MIN_VALUE
        emit(Phase.Scanning, "scanning for ECG-Box-Feather…")
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            emit(Phase.Error, "no LE scanner")
            return
        }
        scanning = true
        val settings =
            ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (e: Exception) {
            scanning = false
            emit(Phase.Error, "startScan: ${e.message}")
            return
        }
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        mainHandler.postDelayed(scanTimeoutRunnable, scanMs)
    }

    @SuppressLint("MissingPermission")
    fun startStream() {
        writeControl("""{"cmd":"start_stream"}""") {
            emit(Phase.Streaming, "start_stream sent")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopStream() {
        writeControl("""{"cmd":"stop_stream"}""") {
            emit(Phase.Ready, "stop_stream sent")
        }
    }

    @SuppressLint("MissingPermission")
    fun ping() {
        writeControl("""{"cmd":"ping"}""")
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        stopScanInternal(connectBest = false)
        closeGatt()
        emit(Phase.Idle, "idle")
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal(connectBest: Boolean) {
        if (!scanning) {
            if (connectBest) {
                connectBestOrFail()
            }
            return
        }
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        if (connectBest) {
            connectBestOrFail()
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectBestOrFail() {
        val device = bestDevice
        if (device == null) {
            emit(Phase.Error, "no ECG-Box-Feather found")
            return
        }
        emit(Phase.Connecting, "connecting ${device.name ?: device.address}")
        gatt =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(appContext, false, gattCallback)
            }
    }

    @SuppressLint("MissingPermission")
    private fun afterNotifiesEnabled(
        g: BluetoothGatt,
        service: android.bluetooth.BluetoothGattService,
    ) {
        val coeffs = pendingCoeffs
        pendingCoeffs = null
        val afterCoeffs = {
            emit(Phase.Ready, "GATT ready")
            if (autoStartStream) {
                startStream()
            }
        }
        if (coeffs != null && coeffs.isNotEmpty()) {
            val ch = service.getCharacteristic(FeatherBleContract.COEFFS_WRITE_UUID)
            if (ch != null) {
                writeCharacteristic(g, ch, coeffs, afterCoeffs)
                return
            }
        }
        afterCoeffs()
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        then: () -> Unit,
    ) {
        if (!g.setCharacteristicNotification(characteristic, true)) {
            emit(Phase.Error, "setCharacteristicNotification failed")
            return
        }
        val cccd = characteristic.getDescriptor(cccdUuid)
        if (cccd == null) {
            emit(Phase.Error, "CCCD missing on ${characteristic.uuid}")
            return
        }
        pendingAfterDescriptorWrite = then
        val ok =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        if (!ok) {
            pendingAfterDescriptorWrite = null
            emit(Phase.Error, "writeDescriptor failed")
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeControl(json: String, then: (() -> Unit)? = null) {
        val g = gatt
        if (g == null) {
            emit(Phase.Error, "not connected")
            return
        }
        val ch =
            g.getService(FeatherBleContract.SERVICE_UUID)
                ?.getCharacteristic(FeatherBleContract.CONTROL_WRITE_UUID)
        if (ch == null) {
            emit(Phase.Error, "control characteristic missing")
            return
        }
        writeCharacteristic(g, ch, json.toByteArray(Charsets.UTF_8), then)
    }

    @SuppressLint("MissingPermission")
    private fun writeCharacteristic(
        g: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
        value: ByteArray,
        then: (() -> Unit)?,
    ) {
        pendingAfterCharWrite = then
        val ok =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeCharacteristic(
                    ch,
                    value,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
                ) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                ch.value = value
                @Suppress("DEPRECATION")
                g.writeCharacteristic(ch)
            }
        if (!ok) {
            pendingAfterCharWrite = null
            emit(Phase.Error, "writeCharacteristic failed")
        }
    }

    private fun handleNotify(uuid: UUID, value: ByteArray) {
        when (uuid) {
            FeatherBleContract.IBI_NOTIFY_UUID -> {
                val pkt = FeatherPacketCodec.decodeIbi(value) ?: return
                if (pkt.ibiMs.isNotEmpty()) {
                    mainHandler.post { listener.onIbiMs(pkt.ibiMs) }
                }
            }
            FeatherBleContract.ECG_NOTIFY_UUID -> {
                val pkt = FeatherPacketCodec.decodeEcg(value) ?: return
                mainHandler.post {
                    listener.onEcgSamplesUv(pkt.sampleHz, pkt.samplesUv)
                }
            }
            FeatherBleContract.STATUS_NOTIFY_UUID -> {
                val json = value.toString(Charsets.UTF_8)
                mainHandler.post { listener.onStatusJson(json) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        gatt = null
    }

    private fun emit(p: Phase, detail: String) {
        phase = p
        Log.d(TAG, "$p $detail")
        mainHandler.post { listener.onPhase(p, detail) }
    }

    companion object {
        private const val TAG = "FeatherBle"
    }
}
