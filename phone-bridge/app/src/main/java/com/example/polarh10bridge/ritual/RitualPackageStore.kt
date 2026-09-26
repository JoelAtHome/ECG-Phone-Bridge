package com.example.polarh10bridge.ritual

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Phone-local ritual package ring (last [MAX_PACKAGES] Record sessions).
 * Root is typically `context.filesDir.resolve("ritual_packages")`.
 */
class RitualPackageStore(
    private val rootDir: File,
    private val maxPackages: Int = MAX_PACKAGES,
) {
    private val lock = ReentrantLock()
    private val indexFile get() = File(rootDir, INDEX_NAME)

    init {
        rootDir.mkdirs()
    }

    fun save(pkg: RitualPackage): RitualPackage =
        lock.withLock {
            rootDir.mkdirs()
            val file = packageFile(pkg.sessionId)
            file.writeText(pkg.toFileJson().toString())
            val ids = loadIndexIds().toMutableList()
            ids.remove(pkg.sessionId)
            ids.add(0, pkg.sessionId)
            while (ids.size > maxPackages) {
                val drop = ids.removeAt(ids.lastIndex)
                packageFile(drop).delete()
            }
            writeIndexIds(ids)
            pkg
        }

    fun get(sessionId: String): RitualPackage? =
        lock.withLock {
            val file = packageFile(sessionId)
            if (!file.isFile) return@withLock null
            try {
                RitualPackage.fromFileJson(JSONObject(file.readText()))
            } catch (_: Exception) {
                null
            }
        }

    fun list(): List<RitualPackage> =
        lock.withLock {
            loadIndexIds().mapNotNull { id ->
                val file = packageFile(id)
                if (!file.isFile) return@mapNotNull null
                try {
                    RitualPackage.fromFileJson(JSONObject(file.readText()))
                } catch (_: Exception) {
                    null
                }
            }
        }

    fun count(): Int = list().size

    fun latest(): RitualPackage? = list().firstOrNull()

    fun latestUnacked(): RitualPackage? = list().firstOrNull { !it.acked }

    fun markAcked(sessionId: String): RitualPackage? =
        lock.withLock {
            val existing = getUnlocked(sessionId) ?: return@withLock null
            if (existing.acked) return@withLock existing
            val updated = existing.copy(acked = true)
            packageFile(sessionId).writeText(updated.toFileJson().toString())
            updated
        }

    fun summaryUi(): RitualUiSummary? {
        val pkg = latest() ?: return null
        return RitualUiSummary(
            sessionId = pkg.sessionId,
            acked = pkg.acked,
            rmssdMs = pkg.rmssdMs,
            emittedAt = pkg.emittedAt,
            ibiCount = pkg.ibiCount,
            hasEcg = pkg.hasEcg,
        )
    }

    private fun getUnlocked(sessionId: String): RitualPackage? {
        val file = packageFile(sessionId)
        if (!file.isFile) return null
        return try {
            RitualPackage.fromFileJson(JSONObject(file.readText()))
        } catch (_: Exception) {
            null
        }
    }

    private fun packageFile(sessionId: String): File {
        val safe = sessionId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(rootDir, "$safe.json")
    }

    private fun loadIndexIds(): List<String> {
        if (!indexFile.isFile) {
            // Recover from loose files (newest first by modified time).
            return rootDir
                .listFiles()
                ?.filter { it.isFile && it.name.endsWith(".json") && it.name != INDEX_NAME }
                ?.sortedByDescending { it.lastModified() }
                ?.map { it.name.removeSuffix(".json") }
                .orEmpty()
        }
        return try {
            val arr = JSONObject(indexFile.readText()).optJSONArray("session_ids") ?: JSONArray()
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeIndexIds(ids: List<String>) {
        indexFile.writeText(
            JSONObject().put("session_ids", JSONArray(ids)).toString(),
        )
    }

    companion object {
        const val MAX_PACKAGES = 5
        private const val INDEX_NAME = "_index.json"
    }
}

data class RitualUiSummary(
    val sessionId: String,
    val acked: Boolean,
    val rmssdMs: Double?,
    val emittedAt: String,
    val ibiCount: Int,
    val hasEcg: Boolean,
)
