package com.example.polarh10bridge

import com.example.polarh10bridge.rmssd.RmssdCalculator
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

enum class BridgeSessionMode {
    Stream,
    Record,
    ;

    fun wireValue(): String =
        when (this) {
            Stream -> "stream"
            Record -> "record"
        }

    companion object {
        fun fromWire(raw: String?): BridgeSessionMode? =
            when (raw?.trim()?.lowercase(Locale.US)) {
                "stream" -> Stream
                "record" -> Record
                else -> null
            }
    }
}

enum class BridgeSessionKind {
    Session,
    Ritual,
    ;

    fun wireValue(): String =
        when (this) {
            Session -> "session"
            Ritual -> "ritual"
        }

    companion object {
        fun fromWire(raw: String?): BridgeSessionKind? =
            when (raw?.trim()?.lowercase(Locale.US)) {
                "session" -> Session
                "ritual" -> Ritual
                else -> null
            }
    }
}

enum class BridgeSessionRunState {
    Idle,
    Streaming,
    Recording,
    Finalizing,
    Completed,
    Error,
    ;

    fun wireValue(): String =
        when (this) {
            Idle -> "idle"
            Streaming -> "streaming"
            Recording -> "recording"
            Finalizing -> "finalizing"
            Completed -> "completed"
            Error -> "error"
        }
}

/**
 * Phone-owned capture session: collects IBIs for official bridge RMSSD and
 * builds protocol NDJSON for session_state / rmssd snapshots.
 */
class BridgeSessionController(
    private val rollingIntervalMs: Long = 30_000L,
) {
    @Volatile
    var preferredMode: BridgeSessionMode = BridgeSessionMode.Stream

    @Volatile
    var preferredKind: BridgeSessionKind = BridgeSessionKind.Session

    @Volatile
    var runState: BridgeSessionRunState = BridgeSessionRunState.Idle
        private set

    @Volatile
    var sessionId: String? = null
        private set

    @Volatile
    var activeMode: BridgeSessionMode = BridgeSessionMode.Stream
        private set

    @Volatile
    var activeKind: BridgeSessionKind = BridgeSessionKind.Session
        private set

    private val ibiLock = Any()
    private val ibis = ArrayList<RmssdCalculator.IbiSample>(512)
    private val lastRollingEmitElapsedMs = AtomicLong(0L)

    val ibiCount: Int
        get() = synchronized(ibiLock) { ibis.size }

    fun isActive(): Boolean =
        runState == BridgeSessionRunState.Streaming ||
            runState == BridgeSessionRunState.Recording

    fun start(
        mode: BridgeSessionMode = preferredMode,
        kind: BridgeSessionKind = preferredKind,
        requestedSessionId: String? = null,
        nowElapsedMs: Long,
    ): JSONObject {
        synchronized(ibiLock) {
            ibis.clear()
        }
        activeMode = mode
        activeKind = kind
        preferredMode = mode
        preferredKind = kind
        sessionId = requestedSessionId?.trim()?.takeIf { it.isNotEmpty() } ?: mintSessionId()
        runState =
            when (mode) {
                BridgeSessionMode.Stream -> BridgeSessionRunState.Streaming
                BridgeSessionMode.Record -> BridgeSessionRunState.Recording
            }
        lastRollingEmitElapsedMs.set(nowElapsedMs)
        return sessionStateJson()
    }

    fun onRrMs(rrMs: Int, tElapsedMs: Long) {
        if (!isActive() || rrMs <= 0) return
        synchronized(ibiLock) {
            ibis.add(RmssdCalculator.IbiSample(ibiMs = rrMs.toDouble(), tMs = tElapsedMs.toDouble()))
        }
    }

    /**
     * Stream mode: optional periodic rolling snapshot. Record mode: null until stop.
     */
    fun maybeRollingRmssdJson(nowElapsedMs: Long, sourceDevice: String): JSONObject? {
        if (activeMode != BridgeSessionMode.Stream || !isActive()) return null
        val last = lastRollingEmitElapsedMs.get()
        if (nowElapsedMs - last < rollingIntervalMs) return null
        if (!lastRollingEmitElapsedMs.compareAndSet(last, nowElapsedMs)) return null
        return buildRmssdJson(sourceDevice)
    }

    fun stop(sourceDevice: String): StopResult {
        if (!isActive()) {
            return StopResult(sessionState = null, rmssd = null)
        }
        runState = BridgeSessionRunState.Finalizing
        val rmssd = buildRmssdJson(sourceDevice)
        runState = BridgeSessionRunState.Completed
        val state = sessionStateJson()
        // Return to idle for the next start; keep last sessionId on the completed message.
        runState = BridgeSessionRunState.Idle
        return StopResult(sessionState = state, rmssd = rmssd)
    }

    fun resetOnDisconnect() {
        synchronized(ibiLock) {
            ibis.clear()
        }
        runState = BridgeSessionRunState.Idle
        sessionId = null
        lastRollingEmitElapsedMs.set(0L)
    }

    fun sessionStateJson(): JSONObject {
        val id = sessionId
        return JSONObject()
            .put("type", "session_state")
            .put("session_id", id)
            .put("mode", activeMode.wireValue())
            .put("kind", activeKind.wireValue())
            .put("state", runState.wireValue())
            .put("source_device", "POLAR_H10")
    }

    private fun buildRmssdJson(sourceDevice: String): JSONObject? {
        val snapshot =
            synchronized(ibiLock) {
                ibis.toList()
            }
        val result = RmssdCalculator.compute(snapshot)
        val rmssd = result.rmssdMs ?: return null
        val obj =
            JSONObject()
                .put("type", "rmssd")
                .put("session_id", sessionId)
                .put("rmssd_ms", rmssd)
                .put("rmssd_source", "bridge")
                .put("source_device", sourceDevice)
        result.window?.let { w ->
            obj.put(
                "window",
                JSONObject()
                    .put("settle_trim_s", w.settleTrimS)
                    .put("analysis_start_s", w.analysisStartS)
                    .put("analysis_end_s", w.analysisEndS)
                    .put("method", w.method),
            )
        }
        obj.put(
            "quality",
            JSONObject()
                .put("accepted_beats", result.quality.acceptedBeats)
                .apply {
                    val pct = result.quality.skippedBeatPct
                    if (pct != null) put("skipped_beat_pct", pct)
                }
                .put("flags", JSONArray(result.quality.flags)),
        )
        return obj
    }

    data class StopResult(
        val sessionState: JSONObject?,
        val rmssd: JSONObject?,
    )

    companion object {
        private val idFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)

        fun mintSessionId(): String {
            val stamp = idFormatter.format(Instant.now())
            val suffix = Random.nextInt(0x10000).toString(16).padStart(4, '0')
            return "$stamp-$suffix"
        }
    }
}
