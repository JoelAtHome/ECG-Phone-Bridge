package com.example.polarh10bridge.feather

/**
 * Resolve a PC `client_info.pc_user` hint to a phone-local Feather profile.
 *
 * Match order (unique hit only; a tie is [Result.Ambiguous] and stops):
 * 1. Exact [FeatherProfileSummary.profileId]
 * 2. Exact [FeatherProfileSummary.displayName]
 * 3. [FeatherPatientProfile.sanitizeProfileId] of the hint vs `profileId`
 * 4. Same name words, any order (`Koblich, Joel` vs `Joel Koblich`)
 * 5. Hint words contained in the display name (`Joel Koblich` vs `Joel Allen Koblich`)
 * 6. One hint word equals the whole display name (`Joel Koblich` vs `Joel`)
 * 7. Single-word hint equals the display name's first word (`Joel` vs `Joel Koblich`)
 * 8. Single-word hint equals any display-name word (`Koblich` vs `Joel Koblich`)
 * 9. Single-word hint is a profile-id prefix at `_` / `-` / `.` (`joel` vs `joel_biceps`)
 *
 * Case-insensitive; trim. Word match ignores commas, hyphens, and apostrophes.
 * A one-letter hint does not take steps 4–9. Two Joels stay ambiguous.
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
        when (bySlug.size) {
            1 ->
                return Result.Match(
                    profileId = bySlug[0].profileId,
                    displayName = bySlug[0].displayName,
                    via = "slug",
                )
            in 2..Int.MAX_VALUE ->
                return Result.Ambiguous(bySlug.map { it.profileId })
        }

        val hintWords = nameWords(trimmed)
        if (hintWords.isEmpty()) return Result.None

        pick(profiles.filter { nameWords(it.displayName).toSet() == hintWords.toSet() }, "name_tokens")
            ?.let { return it }

        if (hintWords.size >= 2) {
            pick(
                profiles.filter { nameWords(it.displayName).toSet().containsAll(hintWords) },
                "name_contains",
            )?.let { return it }
            val hintSet = hintWords.toSet()
            pick(
                profiles.filter { words ->
                    val displayWords = nameWords(words.displayName)
                    displayWords.size == 1 && displayWords[0] in hintSet
                },
                "name_part",
            )?.let { return it }
        }

        if (hintWords.size == 1) {
            val word = hintWords[0]
            pick(
                profiles.filter { nameWords(it.displayName).firstOrNull() == word },
                "given_name",
            )?.let { return it }
            pick(
                profiles.filter { word in nameWords(it.displayName) },
                "name_word",
            )?.let { return it }
            if (slug.length >= 2 && slug != "patient") {
                pick(
                    profiles.filter { idHasWordPrefix(it.profileId, slug) },
                    "id_prefix",
                )?.let { return it }
            }
        }

        return Result.None
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

    private fun pick(
        hits: List<FeatherProfileSummary>,
        via: String,
    ): Result? =
        when (hits.size) {
            0 -> null
            1 ->
                Result.Match(
                    profileId = hits[0].profileId,
                    displayName = hits[0].displayName,
                    via = via,
                )
            else -> Result.Ambiguous(hits.map { it.profileId })
        }

    /** Letters/digits only, length >= 2. Commas, hyphens, and apostrophes are separators. */
    private fun nameWords(raw: String): List<String> =
        raw
            .lowercase()
            .replace("'", "")
            .replace("\u2019", "")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }

    private fun idHasWordPrefix(profileId: String, slug: String): Boolean {
        val id = profileId.lowercase()
        return id.startsWith("${slug}_") ||
            id.startsWith("${slug}-") ||
            id.startsWith("${slug}.")
    }
}
