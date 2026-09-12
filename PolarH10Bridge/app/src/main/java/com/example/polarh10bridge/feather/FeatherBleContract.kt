package com.example.polarh10bridge.feather

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import org.json.JSONObject

/** Draft v1 UUIDs — keep in sync with docs/FEATHER_BLE_GATT.md */
object FeatherBleContract {
    const val ADVERTISED_NAME_PRIMARY = "HnH-Feather"
    const val ADVERTISED_NAME_ALT = "ECG-Box"

    val SERVICE_UUID: UUID = UUID.fromString("c3f0a000-7a1e-4f3b-9c2d-8e5f6a7b8c9d")
    val IBI_NOTIFY_UUID: UUID = UUID.fromString("c3f0a001-7a1e-4f3b-9c2d-8e5f6a7b8c9d")
    val ECG_NOTIFY_UUID: UUID = UUID.fromString("c3f0a002-7a1e-4f3b-9c2d-8e5f6a7b8c9d")
    val COEFFS_WRITE_UUID: UUID = UUID.fromString("c3f0a003-7a1e-4f3b-9c2d-8e5f6a7b8c9d")
    val CONTROL_WRITE_UUID: UUID = UUID.fromString("c3f0a004-7a1e-4f3b-9c2d-8e5f6a7b8c9d")
    val STATUS_NOTIFY_UUID: UUID = UUID.fromString("c3f0a005-7a1e-4f3b-9c2d-8e5f6a7b8c9d")

    const val PACKET_VERSION: Int = 1
    const val SOURCE_DEVICE_WIRE: String = "FEATHER"

    fun matchesAdvertisedName(name: String?): Boolean {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return false
        return n.equals(ADVERTISED_NAME_PRIMARY, ignoreCase = true) ||
            n.equals(ADVERTISED_NAME_ALT, ignoreCase = true) ||
            n.startsWith("HnH-Feather", ignoreCase = true) ||
            n.startsWith("ECG-Box", ignoreCase = true)
    }
}

data class FeatherIbiPacket(
    val version: Int,
    val timestampMs: Long,
    val ibiMs: List<Int>,
)

data class FeatherEcgPacket(
    val version: Int,
    val timestampMs: Long,
    val sampleHz: Int,
    /** Microvolts. */
    val samplesUv: List<Int>,
)

object FeatherPacketCodec {
    fun encodeIbi(packet: FeatherIbiPacket): ByteArray {
        require(packet.ibiMs.size <= 255)
        val buf =
            ByteBuffer.allocate(7 + packet.ibiMs.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(packet.version.toShort())
        buf.putInt(packet.timestampMs.toInt())
        buf.put(packet.ibiMs.size.toByte())
        for (ibi in packet.ibiMs) {
            buf.putShort(ibi.toShort())
        }
        return buf.array()
    }

    fun decodeIbi(bytes: ByteArray): FeatherIbiPacket? {
        if (bytes.size < 7) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = buf.short.toInt() and 0xFFFF
        if (version != FeatherBleContract.PACKET_VERSION) return null
        val timestampMs = buf.int.toLong() and 0xFFFFFFFFL
        val count = buf.get().toInt() and 0xFF
        if (bytes.size < 7 + count * 2) return null
        val ibis = ArrayList<Int>(count)
        repeat(count) {
            ibis.add(buf.short.toInt() and 0xFFFF)
        }
        return FeatherIbiPacket(version, timestampMs, ibis)
    }

    fun encodeEcg(packet: FeatherEcgPacket): ByteArray {
        require(packet.samplesUv.size <= 255)
        val buf =
            ByteBuffer.allocate(9 + packet.samplesUv.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(packet.version.toShort())
        buf.putInt(packet.timestampMs.toInt())
        buf.putShort(packet.sampleHz.toShort())
        buf.put(packet.samplesUv.size.toByte())
        for (s in packet.samplesUv) {
            buf.putShort(s.toShort())
        }
        return buf.array()
    }

    fun decodeEcg(bytes: ByteArray): FeatherEcgPacket? {
        if (bytes.size < 9) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val version = buf.short.toInt() and 0xFFFF
        if (version != FeatherBleContract.PACKET_VERSION) return null
        val timestampMs = buf.int.toLong() and 0xFFFFFFFFL
        val sampleHz = buf.short.toInt() and 0xFFFF
        val count = buf.get().toInt() and 0xFF
        if (bytes.size < 9 + count * 2) return null
        val samples = ArrayList<Int>(count)
        repeat(count) {
            samples.add(buf.short.toInt())
        }
        return FeatherEcgPacket(version, timestampMs, sampleHz, samples)
    }

    fun samplesUvToMv(samplesUv: List<Int>): List<Double> =
        samplesUv.map { it / 1000.0 }

    fun encodeCoeffsJson(coeffs: Map<String, Any?>): ByteArray {
        val obj = JSONObject()
        obj.put("schema_version", 1)
        for ((k, v) in coeffs) {
            when (v) {
                null -> obj.put(k, JSONObject.NULL)
                is Number, is String, is Boolean -> obj.put(k, v)
                else -> obj.put(k, v.toString())
            }
        }
        return obj.toString().toByteArray(Charsets.UTF_8)
    }

    fun decodeCoeffsJson(bytes: ByteArray): Map<String, Any?>? {
        return try {
            val obj = JSONObject(String(bytes, Charsets.UTF_8))
            val out = linkedMapOf<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.get(k)
                out[k] = if (v == JSONObject.NULL) null else v
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    fun encodeControl(cmd: String): ByteArray =
        JSONObject().put("cmd", cmd).toString().toByteArray(Charsets.UTF_8)

    fun decodeControl(bytes: ByteArray): String? {
        return try {
            JSONObject(String(bytes, Charsets.UTF_8)).optString("cmd").ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}
