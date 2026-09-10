package com.example.polarh10bridge.rmssd

import com.example.polarh10bridge.rmssd.RmssdCalculator.Config
import com.example.polarh10bridge.rmssd.RmssdCalculator.IbiSample
import com.example.polarh10bridge.rmssd.RmssdCalculator.QualityFlags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class RmssdCalculatorTest {

    @Test
    fun rmssdMs_knownSeries() {
        // IBIs: 800, 820, 780 → diffs +20, -40 → sqrt((400+1600)/2) = sqrt(1000)
        val expected = sqrt(1000.0)
        val got = RmssdCalculator.rmssdMs(listOf(800.0, 820.0, 780.0))
        assertNotNull(got)
        assertEquals(expected, got!!, 1e-9)
    }

    @Test
    fun rmssdMs_tooFewReturnsNull() {
        assertNull(RmssdCalculator.rmssdMs(emptyList()))
        assertNull(RmssdCalculator.rmssdMs(listOf(800.0)))
    }

    @Test
    fun compute_insufficientBeats() {
        val result = RmssdCalculator.compute(listOf(IbiSample(800.0)))
        assertNull(result.rmssdMs)
        assertTrue(QualityFlags.INSUFFICIENT_BEATS in result.quality.flags)
    }

    @Test
    fun compute_stableSeries_selectsPlateau() {
        // ~3 minutes of ~500 ms IBIs with modest variation (not metronomic).
        val ibis = mutableListOf<IbiSample>()
        val pattern = listOf(500.0, 512.0, 496.0, 508.0, 504.0, 520.0, 492.0, 516.0)
        repeat(360) { i ->
            ibis += IbiSample(pattern[i % pattern.size])
        }
        val cfg = Config(
            settleTrimSec = 30.0,
            analysisWindowSec = 60.0,
            finalTrimSec = 15.0,
            rollStepSec = 10.0,
            minBeatsPerWindow = 20,
        )
        val result = RmssdCalculator.compute(ibis, cfg)
        assertNotNull(result.rmssdMs)
        assertNotNull(result.window)
        assertEquals(RmssdCalculator.Methods.MEDIAN_ROLLING_QUALITY, result.window!!.method)
        assertTrue(result.rollingCandidates.isNotEmpty())
        // Modest variation → RMSSD should be tens of ms, not ~0 and not hundreds.
        assertTrue(result.rmssdMs!! in 5.0..80.0)
        assertFalse(QualityFlags.INSUFFICIENT_BEATS in result.quality.flags)
    }

    @Test
    fun compute_endCollapse_setsEndDivergenceFlag_butKeepsMidPlateau() {
        // Early/mid: variable IBIs; final stretch: nearly constant (Payton-like).
        val ibis = mutableListOf<IbiSample>()
        val midPattern = listOf(500.0, 540.0, 480.0, 520.0, 460.0, 530.0)
        // ~120 s mid-ish content
        repeat(240) { i ->
            ibis += IbiSample(midPattern[i % midPattern.size])
        }
        // ~40 s metronomic end
        repeat(80) {
            ibis += IbiSample(500.0)
        }

        val cfg = Config(
            settleTrimSec = 20.0,
            analysisWindowSec = 30.0,
            finalTrimSec = 10.0,
            rollStepSec = 10.0,
            minBeatsPerWindow = 15,
            endDivergenceRatio = 0.5,
        )
        val result = RmssdCalculator.compute(ibis, cfg)
        assertNotNull(result.rmssdMs)
        assertTrue(
            "expected end_divergence, flags=${result.quality.flags}",
            QualityFlags.END_DIVERGENCE in result.quality.flags,
        )
        // Plateau should stay well above the collapsed end (~0).
        assertTrue(result.rmssdMs!! > 15.0)
    }

    @Test
    fun compute_lowButStableRmssd_notRejected() {
        // Tiny successive diffs (~4–8 ms) → low RMSSD, still valid (RBBB/Polar-like).
        val ibis = mutableListOf<IbiSample>()
        val pattern = listOf(800.0, 804.0, 796.0, 802.0, 798.0, 806.0, 800.0, 804.0)
        repeat(300) { i ->
            ibis += IbiSample(pattern[i % pattern.size])
        }
        val cfg = Config(
            settleTrimSec = 30.0,
            analysisWindowSec = 60.0,
            finalTrimSec = 15.0,
            minBeatsPerWindow = 20,
        )
        val result = RmssdCalculator.compute(ibis, cfg)
        assertNotNull(result.rmssdMs)
        assertTrue(result.rmssdMs!! < 20.0)
        assertFalse(QualityFlags.NO_STABLE_WINDOW in result.quality.flags)
        assertFalse(QualityFlags.INSUFFICIENT_BEATS in result.quality.flags)
    }

    @Test
    fun compute_shortSession_fallsBackOrFlags() {
        // Only ~40 s of data with 45 s settle → short_session / insufficient path.
        val ibis = List(80) { IbiSample(500.0 + (it % 3) * 4.0) }
        val cfg = Config(
            settleTrimSec = 45.0,
            analysisWindowSec = 60.0,
            finalTrimSec = 15.0,
            minBeatsPerWindow = 20,
        )
        val result = RmssdCalculator.compute(ibis, cfg)
        assertTrue(
            result.quality.flags.any {
                it == QualityFlags.SHORT_SESSION ||
                    it == QualityFlags.INSUFFICIENT_BEATS ||
                    it == QualityFlags.NO_STABLE_WINDOW
            },
        )
    }

    @Test
    fun compute_highSkipRate_flag() {
        val ibis = List(100) { IbiSample(500.0 + (it % 5) * 8.0) }
        val result = RmssdCalculator.compute(
            ibis,
            Config(settleTrimSec = 5.0, analysisWindowSec = 20.0, finalTrimSec = 0.0, minBeatsPerWindow = 10),
            skippedBeatCount = 20,
        )
        assertTrue(QualityFlags.HIGH_SKIP_RATE in result.quality.flags)
        assertNotNull(result.quality.skippedBeatPct)
        assertEquals(100.0 * 20 / 120, result.quality.skippedBeatPct!!, 1e-6)
    }

    @Test
    fun cumulativeTimes_fromExplicitTimestamps() {
        val ibis = listOf(
            IbiSample(800.0, tMs = 1_000.0),
            IbiSample(800.0, tMs = 1_800.0),
            IbiSample(800.0, tMs = 2_600.0),
        )
        val times = RmssdCalculator.cumulativeEndTimesSec(ibis)
        assertEquals(listOf(0.0, 0.8, 1.6), times)
    }

    @Test
    fun settleTrim_excludesEarlyChaosFromCandidates() {
        val ibis = mutableListOf<IbiSample>()
        // 30 s of huge variation
        repeat(60) { ibis += IbiSample(if (it % 2 == 0) 400.0 else 900.0) }
        // then stable modest variation
        val steady = listOf(600.0, 610.0, 590.0, 605.0)
        repeat(240) { i -> ibis += IbiSample(steady[i % steady.size]) }

        val settleSec = 30.0
        val withSettle = RmssdCalculator.compute(
            ibis,
            Config(
                settleTrimSec = settleSec,
                analysisWindowSec = 40.0,
                finalTrimSec = 0.0,
                rollStepSec = 10.0,
                minBeatsPerWindow = 20,
            ),
        )
        val noSettle = RmssdCalculator.compute(
            ibis,
            Config(
                settleTrimSec = 0.0,
                analysisWindowSec = 40.0,
                finalTrimSec = 0.0,
                rollStepSec = 10.0,
                minBeatsPerWindow = 20,
            ),
        )
        assertNotNull(withSettle.rmssdMs)
        assertNotNull(noSettle.rmssdMs)
        assertNotNull(withSettle.window)

        // Settled run must not use rolling windows that start in the chaos band.
        assertTrue(withSettle.rollingCandidates.isNotEmpty())
        assertTrue(
            withSettle.rollingCandidates.all { it.startS >= settleSec - 1e-6 },
        )
        assertTrue(withSettle.window!!.analysisStartS >= settleSec - 1e-6)

        // Unsettled run should still see at least one early high-RMSSD candidate.
        val earlyChaos = noSettle.rollingCandidates.filter { it.startS < settleSec }
        assertTrue(earlyChaos.isNotEmpty())
        assertTrue(earlyChaos.maxOf { it.rmssdMs } > withSettle.rmssdMs!! + 50.0)
    }
}
