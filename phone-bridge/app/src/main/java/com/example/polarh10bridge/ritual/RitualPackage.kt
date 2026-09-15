package com.example.polarh10bridge.ritual

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * Durable Record (ritual) package persisted on the phone for delayed FT/HnH transfer.
 * ECG is compact int16 µV; see docs/PROTOCOL.md §7.
 */
data class RitualPackage(
    val sessionId: String,
    val mode: String,
    val kind: String,
    val sourceDevice: String,
    val emittedAt: String,
    val durationS: Double,
    val ibiMs: List<Int>,
    val ecgSampleHz: Int,
    /** Little-endian int16 samples in µV (after [ecgScaleUvPerLsb]). */
    val ecgUv: ShortArray,
    val ecgScaleUvPerLsb: Double = 1.0,
    val ecgTruncated: Boolean = false,
    val rmssdJson: String?,
    val sessionStateJson: String?,
    val acked: Boolean = false,
) {
    val hasEcg: Boolean get() = ecgUv.isNotEmpty()
    val ibiCount: Int get() = ibiMs.size
    val rmssdMs: Double?
        get() =
            rmssdJson?.let { raw ->
                try {
                    val v = JSONObject(raw).optDouble("rmssd_ms", Double.NaN)
                    if (v.isNaN()) null else v
                } catch (_: Exception) {
                    null
                }
            }

    fun toIndexEntry(): JSONObject =
        JSONObject()
            .put("session_id", sessionId)
            .put("emitted_at", emittedAt)
            .put("acked", acked)
            .put("rmssd_ms", rmssdMs ?: JSONObject.NULL)
            .put("ibi_count", ibiCount)
            .put("has_ecg", hasEcg)
            .put("source_device", sourceDevice)

    fun toFileJson(): JSONObject {
        val ecgBytes = ByteBuffer.allocate(ecgUv.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in ecgUv) {
            ecgBytes.putShort(s)
        }
        return JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("session_id", sessionId)
            .put("mode", mode)
            .put("kind", kind)
            .put("source_device", sourceDevice)
            .put("emitted_at", emittedAt)
            .put("duration_s", durationS)
            .put("ibi_ms", JSONArray(ibiMs))
            .put("ecg_sample_hz", ecgSampleHz)
            .put("ecg_scale_uv_per_lsb", ecgScaleUvPerLsb)
            .put("ecg_truncated", ecgTruncated)
            .put(
                "ecg_uv_b64",
                if (ecgUv.isEmpty()) {
                    ""
                } else {
                    Base64.getEncoder().encodeToString(ecgBytes.array())
                },
            )
            .put("rmssd", if (rmssdJson.isNullOrBlank()) JSONObject.NULL else JSONObject(rmssdJson))
            .put(
                "session_state",
                if (sessionStateJson.isNullOrBlank()) {
                    JSONObject.NULL
                } else {
                    JSONObject(sessionStateJson)
                },
            )
            .put("acked", acked)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RitualPackage) return false
        return sessionId == other.sessionId &&
            mode == other.mode &&
            kind == other.kind &&
            sourceDevice == other.sourceDevice &&
            emittedAt == other.emittedAt &&
            durationS == other.durationS &&
            ibiMs == other.ibiMs &&
            ecgSampleHz == other.ecgSampleHz &&
            ecgUv.contentEquals(other.ecgUv) &&
            ecgScaleUvPerLsb == other.ecgScaleUvPerLsb &&
            ecgTruncated == other.ecgTruncated &&
            rmssdJson == other.rmssdJson &&
            sessionStateJson == other.sessionStateJson &&
            acked == other.acked
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + ecgUv.contentHashCode()
        result = 31 * result + acked.hashCode()
        return result
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun fromFileJson(obj: JSONObject): RitualPackage {
            val ibiArr = obj.optJSONArray("ibi_ms") ?: JSONArray()
            val ibi = ArrayList<Int>(ibiArr.length())
            for (i in 0 until ibiArr.length()) {
                ibi.add(ibiArr.optInt(i))
            }
            val b64 = obj.optString("ecg_uv_b64", "")
            val ecgUv =
                if (b64.isBlank()) {
                    ShortArray(0)
                } else {
                    val bytes = Base64.getDecoder().decode(b64)
                    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    ShortArray(bytes.size / 2) { buf.short }
                }
            val rmssdObj = obj.optJSONObject("rmssd")
            val stateObj = obj.optJSONObject("session_state")
            return RitualPackage(
                sessionId = obj.getString("session_id"),
                mode = obj.optString("mode", "record"),
                kind = obj.optString("kind", "ritual"),
                sourceDevice = obj.optString("source_device", "POLAR_H10"),
                emittedAt = obj.optString("emitted_at", ""),
                durationS = obj.optDouble("duration_s", 0.0),
                ibiMs = ibi,
                ecgSampleHz = obj.optInt("ecg_sample_hz", 0),
                ecgUv = ecgUv,
                ecgScaleUvPerLsb = obj.optDouble("ecg_scale_uv_per_lsb", 1.0),
                ecgTruncated = obj.optBoolean("ecg_truncated", false),
                rmssdJson = rmssdObj?.toString(),
                sessionStateJson = stateObj?.toString(),
                acked = obj.optBoolean("acked", false),
            )
        }

        /** Convert millivolt samples to int16 µV (clamped). */
        fun mvToUvShorts(samplesMv: List<Float>): ShortArray {
            val out = ShortArray(samplesMv.size)
            for (i in samplesMv.indices) {
                val uv =
                    (samplesMv[i] * 1000.0)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                out[i] = uv.toShort()
            }
            return out
        }
    }
}

enum class RitualTransferReason(val wire: String) {
    LiveStop("live_stop"),
    ReconnectReplay("reconnect_replay"),
    DelayedPush("delayed_push"),
    ManualSend("manual_send"),
}
