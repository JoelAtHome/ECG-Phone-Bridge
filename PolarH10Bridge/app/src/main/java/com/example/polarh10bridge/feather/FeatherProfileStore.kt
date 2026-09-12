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

        /**
         * Demo seed aligned with ecg-box patch/torso known-good
         * (`docs/FEATHER_BLE.md` § Known-good coeff sets). Bump [DEMO_SEED]
         * when those values change so [FeatherProfileStore.ensureDemoProfile]
         * rewrites an already-saved demo file.
         */
        const val DEMO_SEED = 2

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
                        "ibi_outlier_lo" to 0.75,
                        "ibi_outlier_hi" to 1.30,
                        "ibi_rmssd_max_ms" to 1200,
                    ),
                hardware =
                    mapOf(
                        "board" to "feather_huzzah32",
                        "frontend" to "sparkfun_ad8232",
                        "electrode_setup" to "patch_torso",
                        "sample_hz" to 250,
                        "demo_seed" to DEMO_SEED,
                    ),
            )

        /** True when a stored demo still has pre-seed-2 outlier / RMSSD-cap values. */
        fun demoNeedsKnownGoodMigration(profile: FeatherPatientProfile): Boolean {
            if (profile.profileId != "demo") return false
            val seed = (profile.hardware["demo_seed"] as? Number)?.toInt()
            if (seed != null && seed >= DEMO_SEED) return false
            val lo = (profile.coeffs["ibi_outlier_lo"] as? Number)?.toDouble()
            val hi = (profile.coeffs["ibi_outlier_hi"] as? Number)?.toDouble()
            val maxMs = (profile.coeffs["ibi_rmssd_max_ms"] as? Number)?.toInt()
            // Legacy seed used 0.65 / 1.4 / 1000.
            if (lo == 0.75 && hi == 1.30 && maxMs == 1200) return false
            return true
        }
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
        val existing = load("demo")
        if (existing == null) {
            return save(FeatherPatientProfile.defaultDemo())
        }
        if (FeatherPatientProfile.demoNeedsKnownGoodMigration(existing)) {
            val refreshed =
                FeatherPatientProfile.defaultDemo().copy(
                    createdAt = existing.createdAt,
                    displayName = existing.displayName.ifBlank { "Demo" },
                )
            return save(refreshed)
        }
        return existing
    }

    private fun fileFor(profileId: String): File {
        val safe =
            profileId.trim().lowercase().replace(Regex("[^a-z0-9._-]"), "_")
        require(safe.isNotEmpty()) { "profileId empty" }
        return File(rootDir, "$safe.json")
    }
}
