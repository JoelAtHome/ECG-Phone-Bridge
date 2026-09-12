package com.example.polarh10bridge.feather

import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Synthetic IBIs for Tech "Simulate Feather IBI + ECG" — RSA + slow wander so HR and
 * rolling RMSSD look alive after settle (not a frozen flatline).
 */
object FeatherSimIbi {
    /** Mean IBI ≈ 75 bpm. */
    const val MEAN_MS: Double = 800.0

    /** ~12 breaths/min. */
    const val BREATH_PERIOD_MS: Double = 5_000.0

    /** Peak RSA swing (±ms) around the mean. */
    const val RSA_AMP_MS: Double = 48.0

    /** Slow mean wander period so successive RMSSD windows differ. */
    const val WANDER_PERIOD_MS: Double = 42_000.0

    const val WANDER_AMP_MS: Double = 22.0

    /**
     * @param elapsedSimMs time since sim start (sum of prior IBIs or wall clock)
     * @param beatIndex 0-based beat count (drives tiny deterministic jitter)
     */
    fun nextIbiMs(elapsedSimMs: Long, beatIndex: Int): Int {
        val t = elapsedSimMs.coerceAtLeast(0L).toDouble()
        val rsa = RSA_AMP_MS * sin(2.0 * Math.PI * t / BREATH_PERIOD_MS)
        val wander = WANDER_AMP_MS * sin(2.0 * Math.PI * t / WANDER_PERIOD_MS)
        // Small deterministic jitter so beats are not a perfect sine (±6 ms).
        val jitter = ((beatIndex * 47) % 13) - 6.0
        return (MEAN_MS + rsa + wander + jitter).roundToInt().coerceIn(560, 1_080)
    }
}
