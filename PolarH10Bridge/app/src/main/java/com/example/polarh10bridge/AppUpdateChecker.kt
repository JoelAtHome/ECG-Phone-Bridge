package com.example.polarh10bridge

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class AvailableAppUpdate(
    val tagName: String,
    val versionLabel: String,
    val releaseUrl: String,
)

private const val UPDATE_PREFS_NAME = "bridge_update_prefs"
private const val UPDATE_DISMISSED_TAG_KEY = "dismissed_release_tag"
private const val UPDATE_LAST_CHECK_MS_KEY = "last_check_elapsed_wall_ms"
private const val UPDATE_CACHED_TAG_KEY = "cached_latest_tag"
private const val UPDATE_CACHED_URL_KEY = "cached_latest_url"
private const val UPDATE_CHECK_MIN_INTERVAL_MS = 6L * 60L * 60L * 1000L

private const val GITHUB_LATEST_RELEASE_URL =
    "https://api.github.com/repos/JoelAtHome/ECG-Phone-Bridge/releases/latest"
private const val GITHUB_RELEASES_PAGE_URL =
    "https://github.com/JoelAtHome/ECG-Phone-Bridge/releases"

/**
 * Returns a newer published GitHub release when one exists and the user has not dismissed that tag.
 * Failures are silent (offline / rate limit / parse errors).
 */
internal suspend fun checkForAvailableAppUpdate(
    context: Context,
    currentVersionName: String,
    forceNetwork: Boolean = false,
): AvailableAppUpdate? =
    withContext(Dispatchers.IO) {
        val current = normalizeVersionLabel(currentVersionName)
        if (current.isEmpty()) return@withContext null

        val prefs = context.applicationContext.getSharedPreferences(UPDATE_PREFS_NAME, Context.MODE_PRIVATE)
        val dismissed = prefs.getString(UPDATE_DISMISSED_TAG_KEY, null)
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(UPDATE_LAST_CHECK_MS_KEY, 0L)
        val useCache = !forceNetwork && lastCheck > 0L && (now - lastCheck) < UPDATE_CHECK_MIN_INTERVAL_MS

        val latest =
            if (useCache) {
                val tag = prefs.getString(UPDATE_CACHED_TAG_KEY, null) ?: return@withContext null
                val url = prefs.getString(UPDATE_CACHED_URL_KEY, null) ?: GITHUB_RELEASES_PAGE_URL
                AvailableAppUpdate(
                    tagName = tag,
                    versionLabel = normalizeVersionLabel(tag),
                    releaseUrl = url,
                )
            } else {
                fetchLatestGithubRelease()?.also { fetched ->
                    prefs
                        .edit()
                        .putLong(UPDATE_LAST_CHECK_MS_KEY, now)
                        .putString(UPDATE_CACHED_TAG_KEY, fetched.tagName)
                        .putString(UPDATE_CACHED_URL_KEY, fetched.releaseUrl)
                        .apply()
                } ?: return@withContext null
            }

        if (dismissed != null && dismissed == latest.tagName) return@withContext null
        if (compareAppVersions(latest.versionLabel, current) <= 0) return@withContext null
        latest
    }

internal fun dismissAvailableAppUpdate(context: Context, tagName: String) {
    context.applicationContext
        .getSharedPreferences(UPDATE_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(UPDATE_DISMISSED_TAG_KEY, tagName)
        .apply()
}

internal fun normalizeVersionLabel(raw: String): String =
    raw.trim().removePrefix("v").removePrefix("V").trim()

/**
 * SemVer-ish compare for this app's tags (`1.0.0-beta.19`).
 * Returns >0 if [a] is newer than [b], 0 if equal, <0 if older.
 */
internal fun compareAppVersions(a: String, b: String): Int {
    val left = parseAppVersion(normalizeVersionLabel(a)) ?: return 0
    val right = parseAppVersion(normalizeVersionLabel(b)) ?: return 0
    val coreCmp = compareIntLists(left.core, right.core)
    if (coreCmp != 0) return coreCmp
    // No prerelease ranks higher than any prerelease (1.0.0 > 1.0.0-beta.1).
    return when {
        left.preKind == null && right.preKind == null -> 0
        left.preKind == null -> 1
        right.preKind == null -> -1
        left.preKind != right.preKind -> left.preKind.compareTo(right.preKind)
        else -> left.preNumber.compareTo(right.preNumber)
    }
}

private data class ParsedAppVersion(
    val core: List<Int>,
    val preKind: String?,
    val preNumber: Int,
)

private fun parseAppVersion(label: String): ParsedAppVersion? {
    if (label.isEmpty()) return null
    val parts = label.split("-", limit = 2)
    val core =
        parts[0]
            .split('.')
            .map { it.toIntOrNull() ?: return null }
            .ifEmpty { return null }
    if (parts.size == 1) {
        return ParsedAppVersion(core = core, preKind = null, preNumber = 0)
    }
    val pre = parts[1]
    val preParts = pre.split('.', limit = 2)
    val kind = preParts[0].lowercase()
    val num = preParts.getOrNull(1)?.toIntOrNull() ?: 0
    return ParsedAppVersion(core = core, preKind = kind, preNumber = num)
}

private fun compareIntLists(a: List<Int>, b: List<Int>): Int {
    val n = maxOf(a.size, b.size)
    for (i in 0 until n) {
        val av = a.getOrElse(i) { 0 }
        val bv = b.getOrElse(i) { 0 }
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}

private fun fetchLatestGithubRelease(): AvailableAppUpdate? {
    var conn: HttpURLConnection? = null
    return try {
        conn =
            (URL(GITHUB_LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ECG-Phone-Bridge")
                instanceFollowRedirects = true
            }
        val code = conn.responseCode
        if (code !in 200..299) return null
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val tag = json.optString("tag_name").trim()
        if (tag.isEmpty()) return null
        val url =
            json.optString("html_url").trim().ifEmpty {
                GITHUB_RELEASES_PAGE_URL
            }
        AvailableAppUpdate(
            tagName = tag,
            versionLabel = normalizeVersionLabel(tag),
            releaseUrl = url,
        )
    } catch (_: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}
