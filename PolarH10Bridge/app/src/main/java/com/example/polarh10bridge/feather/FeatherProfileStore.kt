package com.example.polarh10bridge.feather

import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * Phone-local patient profile (V1). Shape matches docs/FEATHER_PROFILE_SCHEMA.md.
 * Pure JVM — [rootDir] is typically `context.filesDir.resolve("feather_profiles")`.
 *
 * Tuner-safe: same JSON files; phone remains system of record. Explicit [save] only.
 */
data class FeatherPatientProfile(
    val schemaVersion: Int = 1,
    val profileId: String,
    val displayName: String,
    val createdAt: String = Instant.now().toString(),
    val updatedAt: String = Instant.now().toString(),
    val coeffs: Map<String, Any?> = emptyMap(),
    val hardware: Map<String, Any?> = emptyMap(),
    /** Stored for Tuner co-edit; phone RMSSD still uses hardcoded defaults until wired. */
    val sessionTiming: Map<String, Any?> = defaultSessionTiming(),
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
        if (sessionTiming.isNotEmpty()) {
            obj.put(
                "session_timing",
                JSONObject(sessionTiming.mapValues { (_, v) -> v ?: JSONObject.NULL }),
            )
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
            val timingObj = obj.optJSONObject("session_timing")
            return FeatherPatientProfile(
                schemaVersion = obj.optInt("schema_version", 1),
                profileId = obj.getString("profile_id"),
                displayName = obj.optString("display_name", obj.getString("profile_id")),
                createdAt = obj.optString("created_at", Instant.now().toString()),
                updatedAt = obj.optString("updated_at", Instant.now().toString()),
                coeffs = jsonObjectToMap(coeffsObj),
                hardware = jsonObjectToMap(hardwareObj),
                sessionTiming =
                    if (timingObj != null) {
                        jsonObjectToMap(timingObj)
                    } else {
                        defaultSessionTiming()
                    },
            )
        }

        fun defaultSessionTiming(): Map<String, Any?> =
            linkedMapOf(
                "settle_trim_s" to 45,
                "analysis_window_s" to 60,
                "final_trim_s" to 15,
            )

        /** Tech calibrate MVP — keys shown for edit; others preserved on Save. */
        val EDITABLE_COEFF_KEYS: List<String> =
            listOf(
                "refractory_ms",
                "ibi_min_ms",
                "ibi_max_ms",
                "peak_search_ms",
                "peak_search_min_ms",
                "peak_end_frac",
                "r_peak_refine_ms",
                "fiducial_delay_ms",
                "ibi_outlier_lo",
                "ibi_outlier_hi",
                "ibi_rmssd_max_ms",
            )

        private val DOUBLE_COEFF_KEYS =
            setOf("peak_end_frac", "ibi_outlier_lo", "ibi_outlier_hi")

        /**
         * Merge Tech draft strings into [base] coeffs. Returns null [error] on success.
         */
        fun mergeEditableCoeffDraft(
            base: Map<String, Any?>,
            draft: Map<String, String>,
        ): Pair<Map<String, Any?>, String?> {
            val out = linkedMapOf<String, Any?>().apply { putAll(base) }
            for (key in EDITABLE_COEFF_KEYS) {
                val raw = draft[key]?.trim().orEmpty()
                if (raw.isEmpty()) {
                    return base to "Empty value for $key"
                }
                val parsed =
                    try {
                        if (key in DOUBLE_COEFF_KEYS) {
                            raw.toDouble()
                        } else {
                            raw.toInt()
                        }
                    } catch (_: NumberFormatException) {
                        return base to "Bad number for $key"
                    }
                out[key] = parsed
            }
            return out to null
        }

        fun coeffDraftFrom(profile: FeatherPatientProfile): Map<String, String> =
            EDITABLE_COEFF_KEYS.associateWith { key ->
                profile.coeffs[key]?.toString().orEmpty()
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
         * Generic patch/torso known-good (`docs/FEATHER_BLE.md`). Bump [DEMO_SEED]
         * when those values change so [FeatherProfileStore.ensureDemoProfile]
         * rewrites an already-saved demo file. Display name: "Typical patch torso".
         */
        const val DEMO_SEED = 2

        fun defaultTypicalPatchTorso(
            profileId: String = "demo",
            displayName: String = "Typical patch torso",
        ): FeatherPatientProfile =
            FeatherPatientProfile(
                profileId = profileId,
                displayName = displayName,
                coeffs = patchTorsoCoeffs(),
                hardware =
                    mapOf(
                        "board" to "feather_huzzah32",
                        "frontend" to "sparkfun_ad8232",
                        "electrode_setup" to "patch_torso",
                        "sample_hz" to 250,
                        "demo_seed" to DEMO_SEED,
                        "notes" to "Factory patch/torso seed (not a Tuning Data capture file)",
                    ),
                sessionTiming = defaultSessionTiming(),
            )

        /** @deprecated Use [defaultTypicalPatchTorso]. */
        fun defaultDemo(profileId: String = "demo", displayName: String = "Typical patch torso"): FeatherPatientProfile =
            defaultTypicalPatchTorso(profileId, displayName)

        /**
         * Patient 1 — Payton torso bring-up (shoulders + lower-right abdomen).
         * Same detector knobs as patch/torso known-good; Payton Test 1 used
         * fiducial 40 / refractory 400 / ibi_min 400 (outlier caps now known-good).
         */
        fun defaultPatient1(): FeatherPatientProfile =
            FeatherPatientProfile(
                profileId = "patient-1",
                displayName = "Patient 1",
                coeffs = patchTorsoCoeffs(),
                hardware =
                    mapOf(
                        "board" to "feather_huzzah32",
                        "frontend" to "sparkfun_ad8232",
                        "electrode_setup" to "patch_torso",
                        "sample_hz" to 250,
                        "notes" to
                            "Payton — shoulders + lower right abdomen; coeffs from patch/torso " +
                                "known-good (aligned with 2026-09-03 bring-up)",
                    ),
                sessionTiming = defaultSessionTiming(),
            )

        /**
         * Patient 2 — Joel handgrip Lead I (Aug 31 Polar-agreeing knobs:
         * `fiducial_delay_ms` 90, `refractory_ms` 420). Starting coeffs only.
         */
        fun defaultPatient2(): FeatherPatientProfile =
            FeatherPatientProfile(
                profileId = "patient-2",
                displayName = "Patient 2",
                coeffs =
                    mapOf(
                        "refractory_ms" to 420,
                        "ibi_min_ms" to 400,
                        "ibi_max_ms" to 1500,
                        "peak_search_ms" to 220,
                        "peak_search_min_ms" to 60,
                        "peak_end_frac" to 0.55,
                        "r_peak_refine_ms" to 200,
                        "fiducial_delay_ms" to 90,
                        "ibi_outlier_lo" to 0.75,
                        "ibi_outlier_hi" to 1.30,
                        "ibi_rmssd_max_ms" to 1200,
                    ),
                hardware =
                    mapOf(
                        "board" to "feather_huzzah32",
                        "frontend" to "sparkfun_ad8232",
                        "electrode_setup" to "handgrip",
                        "sample_hz" to 250,
                        "notes" to
                            "Joel — exaggerated R / handgrip Lead I; seed from Aug 31 " +
                                "Polar-agreeing handgrip knobs (not the .txt capture itself)",
                    ),
                sessionTiming = defaultSessionTiming(),
            )

        private fun patchTorsoCoeffs(): Map<String, Any?> =
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

        fun sanitizeProfileId(raw: String): String =
            raw
                .trim()
                .lowercase()
                .replace(Regex("[^a-z0-9._-]+"), "_")
                .trim('_')
                .ifEmpty { "patient" }
    }
}

data class FeatherProfileSummary(
    val profileId: String,
    val displayName: String,
)

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
            .listFiles { f -> f.isFile && f.name.endsWith(".json") && !f.name.startsWith("_") }
            ?.mapNotNull { file ->
                try {
                    FeatherPatientProfile.fromJsonObject(JSONObject(file.readText()))
                } catch (_: Exception) {
                    null
                }
            }
            ?.sortedBy { it.displayName.lowercase() }
            .orEmpty()

    fun listSummaries(): List<FeatherProfileSummary> =
        listProfiles().map { FeatherProfileSummary(it.profileId, it.displayName) }

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
            return save(FeatherPatientProfile.defaultTypicalPatchTorso())
        }
        if (FeatherPatientProfile.demoNeedsKnownGoodMigration(existing)) {
            val refreshed =
                FeatherPatientProfile.defaultTypicalPatchTorso().copy(
                    createdAt = existing.createdAt,
                    displayName =
                        when (existing.displayName) {
                            "", "Demo" -> "Typical patch torso"
                            else -> existing.displayName
                        },
                )
            return save(refreshed)
        }
        if (existing.displayName == "Demo") {
            return save(existing.copy(displayName = "Typical patch torso"))
        }
        return existing
    }

    /**
     * Ensure factory seeds exist. Does **not** overwrite Patient 1/2 coeffs that
     * were already Saved on-device.
     */
    fun ensureFactoryProfiles() {
        ensureDemoProfile()
        migrateLegacyJoelToPatient2()
        if (load("patient-1") == null) {
            save(FeatherPatientProfile.defaultPatient1())
        }
        if (load("patient-2") == null) {
            save(FeatherPatientProfile.defaultPatient2())
        }
    }

    /** β.35 used profile id `joel`; rename to `patient-2` once, keeping coeffs. */
    private fun migrateLegacyJoelToPatient2() {
        if (load("patient-2") != null) {
            // Drop leftover joel file if patient-2 already exists.
            fileFor("joel").takeIf { it.exists() }?.delete()
            return
        }
        val legacy = load("joel") ?: return
        save(
            legacy.copy(
                profileId = "patient-2",
                displayName =
                    when (legacy.displayName) {
                        "", "Joel" -> "Patient 2"
                        else -> legacy.displayName
                    },
            ),
        )
        fileFor("joel").delete()
        if (activeMetaFile().exists() && activeMetaFile().readText().trim() == "joel") {
            activeMetaFile().writeText("patient-2")
        }
    }

    fun activeProfileId(): String {
        ensureFactoryProfiles()
        val meta = activeMetaFile()
        if (meta.exists()) {
            val id = meta.readText().trim()
            if (id.isNotEmpty() && load(id) != null) return id
        }
        return "demo"
    }

    fun setActiveProfileId(profileId: String): FeatherPatientProfile? {
        val profile = load(profileId) ?: return null
        activeMetaFile().writeText(profile.profileId)
        return profile
    }

    fun loadActive(): FeatherPatientProfile {
        ensureFactoryProfiles()
        val id = activeProfileId()
        return load(id) ?: ensureDemoProfile().also { setActiveProfileId("demo") }
    }

    /**
     * Clone [cloneFromId] (default demo) into a new patient. Sets active to the new profile.
     * Strips `demo_seed` from hardware so migrations do not rewrite named patients.
     */
    fun addPatient(
        displayName: String,
        cloneFromId: String = "demo",
    ): FeatherPatientProfile {
        ensureFactoryProfiles()
        val source = load(cloneFromId) ?: ensureDemoProfile()
        val label = displayName.trim().ifBlank { "Patient" }
        val baseId = FeatherPatientProfile.sanitizeProfileId(label)
        var id = baseId
        var n = 2
        while (load(id) != null) {
            id = "$baseId-$n"
            n++
        }
        val hardware =
            source.hardware
                .filterKeys { it != "demo_seed" }
                .toMap()
        val created =
            save(
                source.copy(
                    profileId = id,
                    displayName = label,
                    createdAt = Instant.now().toString(),
                    updatedAt = Instant.now().toString(),
                    hardware = hardware,
                ),
            )
        setActiveProfileId(created.profileId)
        return created
    }

    private fun activeMetaFile(): File = File(rootDir, "_active_profile_id")

    private fun fileFor(profileId: String): File {
        val safe = FeatherPatientProfile.sanitizeProfileId(profileId)
        require(safe.isNotEmpty()) { "profileId empty" }
        return File(rootDir, "$safe.json")
    }
}
