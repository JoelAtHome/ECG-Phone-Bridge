package com.example.polarh10bridge.ritual

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** Build PROTOCOL §7 NDJSON lines for a ritual package transfer. */
object RitualWireCodec {
    /** Target ~96 KiB of base64 payload per ECG chunk (~72 KiB raw shorts). */
    const val ECG_SAMPLES_PER_CHUNK = 36_000

    fun sessionSummary(
        pkg: RitualPackage,
        reason: RitualTransferReason,
    ): JSONObject =
        JSONObject()
            .put("type", "session_summary")
            .put("session_id", pkg.sessionId)
            .put("mode", pkg.mode)
            .put("kind", pkg.kind)
            .put("duration_s", pkg.durationS)
            .put("ibi_count", pkg.ibiCount)
            .put("has_ecg", pkg.hasEcg)
            .put("ecg_sample_hz", pkg.ecgSampleHz)
            .put("ecg_truncated", pkg.ecgTruncated)
            .put("source_device", pkg.sourceDevice)
            .put("emitted_at", pkg.emittedAt)
            .put("transfer_reason", reason.wire)
            .apply {
                pkg.rmssdMs?.let { put("rmssd_ms", it) }
                pkg.profileId?.takeIf { it.isNotBlank() }?.let { put("profile_id", it) }
                pkg.profileDisplayName?.takeIf { it.isNotBlank() }?.let {
                    put("profile_display_name", it)
                }
            }

    /** Host `ritual_list` reply. Order is newest first. Does not transfer IBI or ECG. */
    fun ritualList(packages: List<RitualPackage>): JSONObject {
        val recordings = JSONArray()
        for (pkg in packages) {
            recordings.put(catalogEntry(pkg))
        }
        return JSONObject()
            .put("type", "ritual_list")
            .put("recordings", recordings)
    }

    /**
     * Reply when the host named a `session_id` and that package is not on the phone.
     * A request with no id does not use this; an empty ring stays silent.
     */
    fun ritualUnavailable(sessionId: String): JSONObject =
        JSONObject()
            .put("type", "ritual_unavailable")
            .put("session_id", sessionId)
            .put("reason", "not_found")

    fun catalogEntry(pkg: RitualPackage): JSONObject =
        JSONObject()
            .put("session_id", pkg.sessionId)
            .put("emitted_at", pkg.emittedAt)
            .put("duration_s", pkg.durationS)
            .put("acked", pkg.acked)
            .apply {
                pkg.rmssdMs?.let { put("rmssd_ms", it) }
                pkg.profileId?.takeIf { it.isNotBlank() }?.let { put("profile_id", it) }
                pkg.profileDisplayName?.takeIf { it.isNotBlank() }?.let {
                    put("profile_display_name", it)
                }
            }

    /**
     * Ordered wire lines: summary, optional rmssd, optional session_state, IBI chunk(s), ECG chunk(s).
     */
    fun buildTransferLines(
        pkg: RitualPackage,
        reason: RitualTransferReason,
    ): List<String> {
        val lines = ArrayList<String>()
        lines.add(sessionSummary(pkg, reason).toString())
        pkg.rmssdJson?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        pkg.sessionStateJson?.takeIf { it.isNotBlank() }?.let { lines.add(it) }
        lines.addAll(ibiChunks(pkg))
        lines.addAll(ecgChunks(pkg))
        return lines
    }

    fun ibiChunks(pkg: RitualPackage): List<String> {
        if (pkg.ibiMs.isEmpty()) {
            return listOf(
                JSONObject()
                    .put("type", "ritual_chunk")
                    .put("session_id", pkg.sessionId)
                    .put("seq", 1)
                    .put("of", 1)
                    .put("content", "ibi")
                    .put("encoding", "int_ms_json")
                    .put("samples", JSONArray())
                    .toString(),
            )
        }
        // One chunk is fine for typical rituals; split only if huge.
        val maxPerChunk = 20_000
        val parts = pkg.ibiMs.chunked(maxPerChunk)
        return parts.mapIndexed { index, slice ->
            JSONObject()
                .put("type", "ritual_chunk")
                .put("session_id", pkg.sessionId)
                .put("seq", index + 1)
                .put("of", parts.size)
                .put("content", "ibi")
                .put("encoding", "int_ms_json")
                .put("samples", JSONArray(slice))
                .toString()
        }
    }

    fun ecgChunks(pkg: RitualPackage): List<String> {
        if (pkg.ecgUv.isEmpty()) return emptyList()
        val total = (pkg.ecgUv.size + ECG_SAMPLES_PER_CHUNK - 1) / ECG_SAMPLES_PER_CHUNK
        val out = ArrayList<String>(total)
        var offset = 0
        var seq = 1
        while (offset < pkg.ecgUv.size) {
            val end = minOf(offset + ECG_SAMPLES_PER_CHUNK, pkg.ecgUv.size)
            val sliceLen = end - offset
            val buf = ByteBuffer.allocate(sliceLen * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in offset until end) {
                buf.putShort(pkg.ecgUv[i])
            }
            out.add(
                JSONObject()
                    .put("type", "ritual_chunk")
                    .put("session_id", pkg.sessionId)
                    .put("seq", seq)
                    .put("of", total)
                    .put("content", "ecg")
                    .put("encoding", "int16_uv_b64")
                    .put("sample_rate_hz", pkg.ecgSampleHz)
                    .put("scale_uv_per_lsb", pkg.ecgScaleUvPerLsb)
                    .put("data", Base64.getEncoder().encodeToString(buf.array()))
                    .toString(),
            )
            offset = end
            seq++
        }
        return out
    }

    /** Whether this client_app should receive auto delayed_push. */
    fun shouldAutoPush(clientApp: String?): Boolean {
        val app = clientApp?.trim()?.lowercase().orEmpty()
        return when (app) {
            "", "hertz_and_hearts", "flaretracker" -> true
            "vns_ta", "ecg_box_tuner" -> false
            else -> true
        }
    }
}
