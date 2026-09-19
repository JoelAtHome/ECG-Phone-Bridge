package com.example.polarh10bridge.feather

import org.json.JSONObject

/**
 * MCU open-lead (LO+/LO−) from Feather status / QC notify.
 *
 * Hosts must use NDJSON `status` fields [use_leads_off] / [leads_off] — not
 * [sensor_quality.contact_state] (Polar skin bit) and not ECG SNR on the phone.
 */
data class FeatherLeadOffSnapshot(
    val useLeadsOff: Boolean,
    val leadsOff: Boolean,
) {
    fun hostsShouldWarn(): Boolean = useLeadsOff && leadsOff

    fun statusMessage(): String =
        when {
            !useLeadsOff -> "Feather lead-off disabled"
            leadsOff -> "Feather lead-off"
            else -> "Feather leads OK"
        }

    /** NDJSON phone → PC `type:status` body (caller wraps send). */
    fun toStatusJson(connected: Boolean = true): String =
        JSONObject()
            .put("type", "status")
            .put("message", statusMessage())
            .put("connected", connected)
            .put("use_leads_off", useLeadsOff)
            .put("leads_off", leadsOff)
            .put("source_device", FeatherBleContract.SOURCE_DEVICE_WIRE)
            .toString()
}

/**
 * Parses LOD from bare status or nested `qc` objects. Returns null when neither
 * field is present (heartbeat / coeffs / unrelated JSON).
 */
object FeatherLeadOffParser {
    fun parseStatusJson(json: String): FeatherLeadOffSnapshot? {
        return try {
            parseStatusObject(JSONObject(json))
        } catch (_: Exception) {
            null
        }
    }

    fun parseStatusObject(obj: JSONObject): FeatherLeadOffSnapshot? {
        val fromRoot = snapshotFrom(obj)
        if (fromRoot != null) return fromRoot
        val qc = obj.optJSONObject("qc") ?: return null
        return snapshotFrom(qc)
    }

    private fun snapshotFrom(obj: JSONObject): FeatherLeadOffSnapshot? {
        val hasUse = obj.has("use_leads_off")
        val hasLod = obj.has("leads_off")
        if (!hasUse && !hasLod) return null
        return FeatherLeadOffSnapshot(
            useLeadsOff = if (hasUse) obj.optBoolean("use_leads_off", true) else true,
            leadsOff = if (hasLod) obj.optBoolean("leads_off", false) else false,
        )
    }
}

/**
 * Emits only when the parsed snapshot differs from the last published one
 * (including the first observation).
 */
class FeatherLeadOffTracker {
    private var lastPublished: FeatherLeadOffSnapshot? = null

    fun last(): FeatherLeadOffSnapshot? = lastPublished

    fun reset() {
        lastPublished = null
    }

    /**
     * @return snapshot to publish on the wire / UI, or null if unchanged / unparsable.
     */
    fun observe(json: String): FeatherLeadOffSnapshot? {
        val next = FeatherLeadOffParser.parseStatusJson(json) ?: return null
        if (next == lastPublished) return null
        lastPublished = next
        return next
    }
}

/**
 * Whether Feather ECG batches should go to NDJSON hosts / ritual buffer.
 * Tech strip always updates independently. Null snapshot (LOD never seen) → forward.
 */
object FeatherEcgExportGate {
    fun shouldForwardToHosts(snap: FeatherLeadOffSnapshot?): Boolean =
        snap?.hostsShouldWarn() != true
}
