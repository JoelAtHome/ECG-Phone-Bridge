package com.example.polarh10bridge.ritual

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/**
 * Durable Record (ritual) package persisted on the phone for delayed FT/HnH transfer.
 * ECG is little-endian int32 µV (`int32_uv_b64`). Older files may still be int16; see docs/PROTOCOL.md §7.
 */
data class RitualPackage(
    val sessionId: String,
    val mode: String,
    val kind: String,
    val sourceDevice: String,
    val emittedAt: String,
    /**
     * Beat-coverage length: Σ `ibi_ms` / 1000 when IBIs exist (else wall clock).
     * Hosts that rebuild trends from the IBI list should treat this as the series span.
     */
    val durationS: Double,
    /**
     * Wall-clock Record start→stop (elapsedRealtime). May exceed [durationS] when the
     * sensor stopped publishing accepted IBIs mid-capture (LOD / BLE backpressure).
     * Absent on older on-disk packages (treated as [durationS]).
     */
    val wallDurationS: Double = durationS,
    val ibiMs: List<Int>,
    val ecgSampleHz: Int,
    /** Little-endian int32 samples in µV (after [ecgScaleUvPerLsb]). */
    val ecgUv: IntArray,
    val ecgScaleUvPerLsb: Double = 1.0,
    val ecgTruncated: Boolean = false,
    val rmssdJson: String?,
    val sessionStateJson: String?,
    val acked: Boolean = false,
    /** Feather patient profile active at Record Stop. Absent on older packages. */
    val profileId: String? = null,
    val profileDisplayName: String? = null,
) {
    val hasEcg: Boolean get() = ecgUv.isNotEmpty()
    val ibiCount: Int get() = ibiMs.size
    /** Σ ibi_ms / 1000 — same basis as [durationS] when the series is non-empty. */
    val ibiSpanS: Double get() = ibiSpanSeconds(ibiMs)
    /**
     * True when wall clock is meaningfully longer than beat coverage (incomplete IBI
     * series relative to the Record attempt). Phone does **not** invent missing beats.
     */
    val ibiIncomplete: Boolean
        get() =
            ibiMs.isNotEmpty() &&
                wallDurationS > 0.0 &&
                ibiSpanS < wallDurationS * IBI_INCOMPLETE_RATIO
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
        val ecgBytes = ByteBuffer.allocate(ecgUv.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (s in ecgUv) {
            ecgBytes.putInt(s)
        }
        return JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("session_id", sessionId)
            .put("mode", mode)
            .put("kind", kind)
            .put("source_device", sourceDevice)
            .put("emitted_at", emittedAt)
            .put("duration_s", durationS)
            .put("wall_duration_s", wallDurationS)
            .put("ibi_span_s", ibiSpanS)
            .put("ibi_incomplete", ibiIncomplete)
            .put("ibi_ms", JSONArray(ibiMs))
            .put("ecg_sample_hz", ecgSampleHz)
            .put("ecg_encoding", ECG_ENCODING_INT32)
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
            .apply {
                profileId?.takeIf { it.isNotBlank() }?.let { put("profile_id", it) }
                profileDisplayName?.takeIf { it.isNotBlank() }?.let { put("profile_display_name", it) }
            }
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
            wallDurationS == other.wallDurationS &&
            ibiMs == other.ibiMs &&
            ecgSampleHz == other.ecgSampleHz &&
            ecgUv.contentEquals(other.ecgUv) &&
            ecgScaleUvPerLsb == other.ecgScaleUvPerLsb &&
            ecgTruncated == other.ecgTruncated &&
            rmssdJson == other.rmssdJson &&
            sessionStateJson == other.sessionStateJson &&
            acked == other.acked &&
            profileId == other.profileId &&
            profileDisplayName == other.profileDisplayName
    }

    override fun hashCode(): Int {
        var result = sessionId.hashCode()
        result = 31 * result + ecgUv.contentHashCode()
        result = 31 * result + acked.hashCode()
        result = 31 * result + (profileId?.hashCode() ?: 0)
        result = 31 * result + (profileDisplayName?.hashCode() ?: 0)
        return result
    }

    companion object {
        const val SCHEMA_VERSION = 1
        const val ECG_ENCODING_INT32 = "int32_uv_b64"
        const val ECG_ENCODING_INT16 = "int16_uv_b64"
        /** Wall longer than this fraction of beat span ⇒ [ibiIncomplete]. */
        const val IBI_INCOMPLETE_RATIO = 0.85

        fun ibiSpanSeconds(ibiMs: List<Int>): Double {
            if (ibiMs.isEmpty()) return 0.0
            var sum = 0L
            for (v in ibiMs) {
                sum += v.coerceAtLeast(0).toLong()
            }
            return sum / 1000.0
        }

        fun fromFileJson(obj: JSONObject): RitualPackage {
            val ibiArr = obj.optJSONArray("ibi_ms") ?: JSONArray()
            val ibi = ArrayList<Int>(ibiArr.length())
            for (i in 0 until ibiArr.length()) {
                ibi.add(ibiArr.optInt(i))
            }
            val durationS = obj.optDouble("duration_s", 0.0)
            val wallDurationS =
                if (obj.has("wall_duration_s")) {
                    obj.optDouble("wall_duration_s", durationS)
                } else {
                    durationS
                }
            val ecgUv = decodeEcgUv(obj)
            val rmssdObj = obj.optJSONObject("rmssd")
            val stateObj = obj.optJSONObject("session_state")
            return RitualPackage(
                sessionId = obj.getString("session_id"),
                mode = obj.optString("mode", "record"),
                kind = obj.optString("kind", "ritual"),
                sourceDevice = obj.optString("source_device", "POLAR_H10"),
                emittedAt = obj.optString("emitted_at", ""),
                durationS = durationS,
                wallDurationS = wallDurationS,
                ibiMs = ibi,
                ecgSampleHz = obj.optInt("ecg_sample_hz", 0),
                ecgUv = ecgUv,
                ecgScaleUvPerLsb = obj.optDouble("ecg_scale_uv_per_lsb", 1.0),
                ecgTruncated = obj.optBoolean("ecg_truncated", false),
                rmssdJson = rmssdObj?.toString(),
                sessionStateJson = stateObj?.toString(),
                acked = obj.optBoolean("acked", false),
                profileId = obj.optString("profile_id", "").trim().ifEmpty { null },
                profileDisplayName = obj.optString("profile_display_name", "").trim().ifEmpty { null },
            )
        }

        /**
         * Convert millivolt samples to int32 µV.
         * Truncates toward zero, same as the previous int16 packer, without the ±32767 clamp.
         */
        fun mvToUvInts(samplesMv: List<Float>): IntArray {
            val out = IntArray(samplesMv.size)
            for (i in samplesMv.indices) {
                out[i] = (samplesMv[i] * 1000.0).toInt()
            }
            return out
        }

        /**
         * Files written before int32 omit `ecg_encoding` and store little-endian int16.
         * Those widen to int32 on load. New files set [ECG_ENCODING_INT32].
         */
        private fun decodeEcgUv(obj: JSONObject): IntArray {
            val b64 = obj.optString("ecg_uv_b64", "")
            if (b64.isBlank()) return IntArray(0)
            val bytes = Base64.getDecoder().decode(b64)
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val encoding = obj.optString("ecg_encoding", ECG_ENCODING_INT16)
            return if (encoding == ECG_ENCODING_INT32) {
                IntArray(bytes.size / 4) { buf.int }
            } else {
                IntArray(bytes.size / 2) { buf.short.toInt() }
            }
        }
    }
}

enum class RitualTransferReason(val wire: String) {
    LiveStop("live_stop"),
    ReconnectReplay("reconnect_replay"),
    DelayedPush("delayed_push"),
    ManualSend("manual_send"),
}
