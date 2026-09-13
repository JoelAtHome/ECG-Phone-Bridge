package com.example.polarh10bridge.rmssd

import kotlin.math.sqrt

/**
 * Official session RMSSD from an IBI series (phone-bridge policy).
 *
 * Pure JVM logic — no Android dependencies. Peak detection stays on Polar/Feather;
 * this module only turns accepted IBIs into RMSSD + window/quality metadata.
 *
 * See docs/SYSTEM_ARCHITECTURE.md §6 and docs/PROTOCOL.md §6.
 */
object RmssdCalculator {

    data class Config(
        /** Seconds of IBI timeline to skip after t=0 before analysis. */
        val settleTrimSec: Double = 45.0,
        /** Length of each rolling analysis window (seconds of IBI time). */
        val analysisWindowSec: Double = 60.0,
        /** Drop this many seconds at the end when the session is long enough. */
        val finalTrimSec: Double = 15.0,
        /** Step between rolling window starts (seconds). */
        val rollStepSec: Double = 10.0,
        /** Minimum accepted IBIs inside a candidate window. */
        val minBeatsPerWindow: Int = 20,
        /**
         * If the last post-settle window's RMSSD is below this fraction of the
         * selected plateau, set [QualityFlags.END_DIVERGENCE] (informational).
         */
        val endDivergenceRatio: Double = 0.5,
    )

    data class IbiSample(
        /** Inter-beat interval in milliseconds. */
        val ibiMs: Double,
        /**
         * Optional wall or monotonic timestamp (ms). If null for all samples,
         * timeline is the cumulative sum of [ibiMs].
         */
        val tMs: Double? = null,
    )

    data class Window(
        val settleTrimS: Double,
        val analysisStartS: Double,
        val analysisEndS: Double,
        val method: String,
    )

    data class Quality(
        val acceptedBeats: Int,
        val skippedBeatPct: Double?,
        val flags: List<String>,
    )

    data class Result(
        val rmssdMs: Double?,
        val window: Window?,
        val quality: Quality,
        val rollingCandidates: List<RollingEstimate> = emptyList(),
    )

    data class RollingEstimate(
        val startS: Double,
        val endS: Double,
        val beatCount: Int,
        val rmssdMs: Double,
    )

    object QualityFlags {
        const val INSUFFICIENT_BEATS = "insufficient_beats"
        const val NO_STABLE_WINDOW = "no_stable_window"
        const val END_DIVERGENCE = "end_divergence"
        const val SHORT_SESSION = "short_session"
        const val HIGH_SKIP_RATE = "high_skip_rate"
    }

    object Methods {
        const val MEDIAN_ROLLING_QUALITY = "median_rolling_quality"
        const val FULL_SPAN = "full_span_after_trims"
    }

    /**
     * @param ibis accepted IBIs in order (already peak-detected upstream)
     * @param skippedBeatCount optional count of beats excluded before this list
     */
    fun compute(
        ibis: List<IbiSample>,
        config: Config = Config(),
        skippedBeatCount: Int = 0,
    ): Result {
        val flags = mutableListOf<String>()
        val totalObserved = ibis.size + skippedBeatCount.coerceAtLeast(0)
        val skippedPct =
            if (totalObserved > 0) {
                100.0 * skippedBeatCount.coerceAtLeast(0) / totalObserved
            } else {
                null
            }
        if (skippedPct != null && skippedPct >= 10.0) {
            flags += QualityFlags.HIGH_SKIP_RATE
        }

        if (ibis.size < 2) {
            flags += QualityFlags.INSUFFICIENT_BEATS
            return Result(
                rmssdMs = null,
                window = null,
                quality = Quality(ibis.size, skippedPct, flags.distinct()),
            )
        }

        val timesSec = cumulativeEndTimesSec(ibis)
        val durationS = timesSec.last()
        val usableStart = config.settleTrimSec.coerceAtLeast(0.0)
        var usableEnd = durationS - config.finalTrimSec.coerceAtLeast(0.0)
        if (usableEnd <= usableStart) {
            // Session too short for both trims: keep settle, drop final trim.
            usableEnd = durationS
            flags += QualityFlags.SHORT_SESSION
        }
        if (durationS < config.settleTrimSec + config.analysisWindowSec) {
            if (QualityFlags.SHORT_SESSION !in flags) flags += QualityFlags.SHORT_SESSION
        }

        val rolling = rollingEstimates(
            ibis = ibis,
            timesSec = timesSec,
            usableStart = usableStart,
            usableEnd = usableEnd,
            windowSec = config.analysisWindowSec,
            stepSec = config.rollStepSec,
            minBeats = config.minBeatsPerWindow,
        )

        if (rolling.isEmpty()) {
            // Fallback: single span after settle (and final trim if applied).
            val spanIbis = ibisInHalfOpen(ibis, timesSec, usableStart, usableEnd)
            val rmssd = rmssdMs(spanIbis.map { it.ibiMs })
            if (rmssd == null || spanIbis.size < 2) {
                flags += QualityFlags.INSUFFICIENT_BEATS
                flags += QualityFlags.NO_STABLE_WINDOW
                return Result(
                    rmssdMs = null,
                    window = null,
                    quality = Quality(ibis.size, skippedPct, flags.distinct()),
                    rollingCandidates = emptyList(),
                )
            }
            return Result(
                rmssdMs = rmssd,
                window = Window(
                    settleTrimS = config.settleTrimSec,
                    analysisStartS = usableStart,
                    analysisEndS = usableEnd,
                    method = Methods.FULL_SPAN,
                ),
                quality = Quality(ibis.size, skippedPct, flags.distinct()),
                rollingCandidates = emptyList(),
            )
        }

        val plateau = median(rolling.map { it.rmssdMs })
        // Represent selected window as the median member's bounds (closest to plateau).
        val selected = rolling.minBy { kotlin.math.abs(it.rmssdMs - plateau) }

        val lastWindow = rolling.last()
        if (plateau > 0.0 && lastWindow.rmssdMs < plateau * config.endDivergenceRatio) {
            flags += QualityFlags.END_DIVERGENCE
        }

        return Result(
            rmssdMs = plateau,
            window = Window(
                settleTrimS = config.settleTrimSec,
                analysisStartS = selected.startS,
                analysisEndS = selected.endS,
                method = Methods.MEDIAN_ROLLING_QUALITY,
            ),
            quality = Quality(ibis.size, skippedPct, flags.distinct()),
            rollingCandidates = rolling,
        )
    }

    /** Classic RMSSD (ms) over successive IBIs. */
    fun rmssdMs(ibiMs: List<Double>): Double? {
        if (ibiMs.size < 2) return null
        var sumSq = 0.0
        var n = 0
        for (i in 1 until ibiMs.size) {
            val d = ibiMs[i] - ibiMs[i - 1]
            sumSq += d * d
            n++
        }
        if (n == 0) return null
        return sqrt(sumSq / n)
    }

    internal fun cumulativeEndTimesSec(ibis: List<IbiSample>): List<Double> {
        val allHaveT = ibis.all { it.tMs != null }
        if (allHaveT) {
            val t0 = ibis.first().tMs!!
            return ibis.map { ((it.tMs!! - t0) / 1000.0).coerceAtLeast(0.0) }
        }
        var acc = 0.0
        return ibis.map { sample ->
            acc += sample.ibiMs / 1000.0
            acc
        }
    }

    private fun ibisInHalfOpen(
        ibis: List<IbiSample>,
        timesSec: List<Double>,
        startS: Double,
        endS: Double,
    ): List<IbiSample> {
        val out = ArrayList<IbiSample>()
        for (i in ibis.indices) {
            val t = timesSec[i]
            if (t > startS && t <= endS) out += ibis[i]
        }
        return out
    }

    private fun rollingEstimates(
        ibis: List<IbiSample>,
        timesSec: List<Double>,
        usableStart: Double,
        usableEnd: Double,
        windowSec: Double,
        stepSec: Double,
        minBeats: Int,
    ): List<RollingEstimate> {
        if (usableEnd - usableStart < windowSec * 0.5) return emptyList()
        val out = ArrayList<RollingEstimate>()
        var start = usableStart
        val step = stepSec.coerceAtLeast(1.0)
        while (start + windowSec <= usableEnd + 1e-9) {
            val end = start + windowSec
            val slice = ibisInHalfOpen(ibis, timesSec, start, end)
            val r = rmssdMs(slice.map { it.ibiMs })
            if (r != null && slice.size >= minBeats) {
                out += RollingEstimate(start, end, slice.size, r)
            }
            start += step
        }
        return out
    }

    private fun median(values: List<Double>): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val m = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[m - 1] + sorted[m]) / 2.0
        } else {
            sorted[m]
        }
    }
}
