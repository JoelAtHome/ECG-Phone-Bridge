package com.example.polarh10bridge.feather

import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * Phone-local patient profile (V1). Shape matches docs/FEATHER_PROFILE_SCHEMA.md.
 * Pure JVM — [rootDir] is typically `context.filesDir.resolve("feather_profiles")`.
 */
data class FeatherPatientProfile(
    val schemaVersion: Int = 1,
    val profileId: String,
    val displayName: String,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val coeffs: Map<String, Any?> = emptyMap(),
    val hardware: Map<String, Any?> = emptyMap(),
) {
    fun toJsonObject(): JSONObject {
        val obj =
            JSONObject()
                .put("schema_version", schemaVersion)
                .put("profile_id", profileId)
                .put("display_name", displayName)
                .put("created_at", createdAt)
                .put("updated_at", updatedAt)
                .put("coeffs", JSONObject(coeffs.mapValues { (_, v) -> v ?: JSONObject.NULL }))
        if (hardware.isNotEmpty()) {
            obj.put("hardware", JSONObject(hardware.mapValues { (_, v) -> v ?: JSONObject.NULL }))
        }
        return obj
    }

    fun coeffsForBleWrite(): Map<String, Any?> =
        linkedMapOf<String, Any?>("schema_version" to schemaVersion).apply {
            putAll(coeffs)
        }

    companion object {
        fun fromJsonObject(obj: JSONObject): FeatherPatientProfile {
            val coeffsObj = obj.optJSONObject("coeffs") ?: JSONObject()
            val hardwareObj = obj.optJSONObject("hardware") ?: JSONObject()
            return FeatherPatientProfile(
                schemaVersion = obj.optInt("schema_version", 1),
                profileId = obj.getString("profile_id"),
                displayName = obj.optString("display_name", obj.getString("profile_id")),
                createdAt = obj.optString("created_at", Instant.now().toString()),
                updatedAt = obj.optString("updated_at", Instant.now().toString()),
                coeffs = jsonObjectToMap(coeffsObj),
                hardware = jsonObjectToMap(hardwareObj),
            )
        }

        private fun jsonObjectToMap(obj: JSONObject): Map<String, Any?> {
            val out = linkedMapOf<String, Any?>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.get(k)
                out[k] = if (v == JSONObject.NULL) null else v
            }
            return out
        }

        /** Defaults aligned with ecg-box firmware/hframe_ecg_hrv/config.h */
        fun defaultDemo(profileId: String = "demo", displayName: String = "Demo"): FeatherPatientProfile =
            FeatherPatientProfile(
                profileId = profileId,
                displayName = displayName,
                coeffs =
                    mapOf(
                        "refractory_ms" to 400,
                        "ibi_min_ms" to 400,
                        "ibi_max_ms" to 1500,
                        "peak_search_ms" to 220,
                        "peak_search_min_ms" to 60,
                        "peak_end_frac" to 0.55,
                        "r_peak_refine_ms" to 200,
                        "fiducial_delay_ms" to 40,
                        "ibi_outlier_lo" to 0.65,
                        "ibi_outlier_hi" to 1.4,
                        "ibi_rmssd_max_ms" to 1000,
                    ),
                hardware =
                    mapOf(
                        "board" to "feather_huzzah32",
                        "frontend" to "sparkfun_ad8232",
                        "electrode_setup" to "patch_torso",
                        "sample_hz" to 250,
                    ),
            )
    }
}

class FeatherProfileStore(
    private val rootDir: File,
) {
    init {
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
    }

    fun listProfiles(): List<FeatherPatientProfile> =
        rootDir
            .listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { file ->
                try {
                    FeatherPatientProfile.fromJsonObject(JSONObject(file.readText()))
                } catch (_: Exception) {
                    null
                }
            }
            ?.sortedBy { it.displayName.lowercase() }
            .orEmpty()

    fun load(profileId: String): FeatherPatientProfile? {
        val file = fileFor(profileId)
        if (!file.exists()) return null
        return try {
            FeatherPatientProfile.fromJsonObject(JSONObject(file.readText()))
        } catch (_: Exception) {
            null
        }
    }

    /** Explicit save only — never silent overwrite from callers that skip this API. */
    fun save(profile: FeatherPatientProfile): FeatherPatientProfile {
        val stamped =
            profile.copy(updatedAt = Instant.now().toString())
        val file = fileFor(stamped.profileId)
        file.writeText(stamped.toJsonObject().toString(2))
        return stamped
    }

    fun ensureDemoProfile(): FeatherPatientProfile {
        load("demo")?.let { return it }
        return save(FeatherPatientProfile.defaultDemo())
    }

    private fun fileFor(profileId: String): File {
        val safe =
            profileId.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        require(safe.isNotEmpty()) { "profileId empty" }
        return File(rootDir, "$safe.json")
    }
}
