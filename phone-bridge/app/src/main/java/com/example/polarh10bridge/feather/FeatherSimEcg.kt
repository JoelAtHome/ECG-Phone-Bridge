package com.example.polarh10bridge.feather

import kotlin.math.exp

/**
 * Textbook-ish Lead-I PQRST for Tech "Simulate Feather IBI + ECG" — enough for Tech strip
 * and host `ecg` bring-up (not a physiological model).
 */
object FeatherSimEcg {
    const val SAMPLE_HZ: Int = 250

    /**
     * @param phase01 position within the current beat, 0 = R-aligned start, 1 = next R
     * @return millivolts (rough visual scale)
     */
    fun sampleMv(phase01: Double): Double {
        val p = phase01.mod(1.0)
        // Sum of Gaussians: P, Q, R, S, T (relative amplitudes for a clean strip).
        return gauss(p, 0.12, 0.025, 0.12) +
            gauss(p, 0.26, 0.012, -0.10) +
            gauss(p, 0.30, 0.016, 1.15) +
            gauss(p, 0.34, 0.014, -0.28) +
            gauss(p, 0.52, 0.055, 0.32)
    }

    /** Fill [count] samples starting at [phase01], advancing by 1/sampleHz of [ibiMs]. */
    fun fillBatchMv(
        phase01: Double,
        ibiMs: Double,
        count: Int,
        sampleHz: Int = SAMPLE_HZ,
    ): Pair<Double, FloatArray> {
        val ibi = ibiMs.coerceAtLeast(400.0)
        val step = 1000.0 / sampleHz.coerceAtLeast(1) / ibi
        var phase = phase01.mod(1.0)
        val out = FloatArray(count.coerceAtLeast(0))
        for (i in out.indices) {
            out[i] = sampleMv(phase).toFloat()
            phase = (phase + step).mod(1.0)
        }
        return phase to out
    }

    private fun gauss(x: Double, center: Double, width: Double, amp: Double): Double {
        val z = (x - center) / width
        return amp * exp(-z * z)
    }
}
