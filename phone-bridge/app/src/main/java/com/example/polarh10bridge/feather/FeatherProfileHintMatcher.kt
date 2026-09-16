package com.example.polarh10bridge.feather

/**
 * Resolve a PC `client_info.pc_user` hint to a phone-local Feather profile.
 *
 * Match order (unique hit only): exact [FeatherProfileSummary.profileId] →
 * exact [FeatherProfileSummary.displayName] → [FeatherPatientProfile.sanitizeProfileId]
 * of the hint vs `profileId`. Case-insensitive; trim. No fuzzy match.
 */
object FeatherProfileHintMatcher {
    private const val KEEP_DEBOUNCE_MS = 30_000L

    sealed class Result {
        data class Match(
            val profileId: String,
            val displayName: String,
            val via: String,
        ) : Result()

        data object None : Result()

        data class Ambiguous(
            val candidateIds: List<String>,
        ) : Result()
    }

    data class PendingConfirm(
        val pcUser: String,
        val clientApp: String?,
        val matchedProfileId: String,
        val matchedDisplayName: String,
        val currentProfileId: String,
        val currentDisplayName: String,
    )

    fun resolve(
        hint: String?,
        profiles: List<FeatherProfileSummary>,
    ): Result {
        val trimmed = hint?.trim().orEmpty()
        if (trimmed.isEmpty() || profiles.isEmpty()) return Result.None

        val byId =
            profiles.filter { it.profileId.equals(trimmed, ignoreCase = true) }
        when (byId.size) {
            1 ->
                return Result.Match(
                    profileId = byId[0].profileId,
                    displayName = byId[0].displayName,
                    via = "profile_id",
                )
            in 2..Int.MAX_VALUE ->
                return Result.Ambiguous(byId.map { it.profileId })
        }

        val byName =
            profiles.filter { it.displayName.equals(trimmed, ignoreCase = true) }
        when (byName.size) {
            1 ->
                return Result.Match(
                    profileId = byName[0].profileId,
                    displayName = byName[0].displayName,
                    via = "display_name",
                )
            in 2..Int.MAX_VALUE ->
                return Result.Ambiguous(byName.map { it.profileId })
        }

        val slug = FeatherPatientProfile.sanitizeProfileId(trimmed)
        val bySlug =
            profiles.filter { it.profileId.equals(slug, ignoreCase = true) }
        return when (bySlug.size) {
            1 ->
                Result.Match(
                    profileId = bySlug[0].profileId,
                    displayName = bySlug[0].displayName,
                    via = "slug",
                )
            in 2..Int.MAX_VALUE -> Result.Ambiguous(bySlug.map { it.profileId })
            else -> Result.None
        }
    }

    /** Host label for confirm copy / status lines. */
    fun clientAppLabel(clientApp: String?): String =
        when (clientApp?.trim()?.lowercase()) {
            "hertz_and_hearts", "hnh", null, "" -> "Hertz & Hearts"
            "vns_ta" -> "VNS-TA"
            "flaretracker" -> "FlareTracker"
            "ecg_box_tuner" -> "ECG-Box Tuner"
            else -> "PC app"
        }

    fun keepDebounceMs(): Long = KEEP_DEBOUNCE_MS

    fun debounceKey(pcUser: String): String = pcUser.trim().lowercase()
}
