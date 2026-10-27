package com.ember.tv

import kotlin.math.min

/** Turns a color temperature into the overlay color that best fakes it. See tools/table.py. */
object ColorMath {

    const val MIN_K = 1000
    const val MAX_K = 6500

    /** Overlay layer: alpha and *premultiplied* color, all 0..1. */
    data class Layer(val alpha: Double, val r: Double, val g: Double, val b: Double) {
        fun toArgb(): Int {
            if (alpha <= 0.0) return 0
            fun ch(v: Double) = (v / alpha * 255).toInt().coerceIn(0, 255)
            return ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or
                (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
        }
    }

    /**
     * @param kelvin target color temperature (6500 = neutral)
     * @param dim    0..1 extra darkening (0 = none)
     */
    fun layerFor(kelvin: Double, dim: Double = 0.0): Layer {
        val k = kelvin.coerceIn(MIN_K.toDouble(), MAX_K.toDouble())
        val pos = (k - MIN_K) / 100.0
        val i = min(pos.toInt(), TABLE.size - 2)
        val f = pos - i
        val lo = TABLE[i]
        val hi = TABLE[i + 1]
        // Interpolate in premultiplied space so blending stays correct.
        fun pm(row: DoubleArray, c: Int) = row[1] * row[c]
        val a = lerp(lo[1], hi[1], f)
        val r = lerp(pm(lo, 2), pm(hi, 2), f)
        val g = lerp(pm(lo, 3), pm(hi, 3), f)
        val b = lerp(pm(lo, 4), pm(hi, 4), f)
        // Fold a black dimming layer on top: out = (src*(1-a) + c) * (1-d)
        val d = dim.coerceIn(0.0, 0.9)
        val keep = 1 - d
        return Layer(1 - (1 - a) * keep, r * keep, g * keep, b * keep)
    }

    /** Ideal blackbody color of white light at this temperature, for UI swatches. */
    fun swatch(kelvin: Double): Int {
        val t = kelvin.coerceIn(1000.0, 6500.0) / 100
        val r = if (t <= 66) 255.0 else 329.698727446 * Math.pow(t - 60, -0.1332047592)
        val g = if (t <= 66) 99.4708025861 * Math.log(t) - 161.1195681661
        else 288.1221695283 * Math.pow(t - 60, -0.0755148492)
        val b = when { t >= 66 -> 255.0; t <= 19 -> 0.0; else -> 138.5177312231 * Math.log(t - 10) - 305.0447927307 }
        fun ch(v: Double, white: Double) = (v / white * 255).coerceIn(0.0, 255.0).toInt()
        return (0xFF shl 24) or (ch(r, 255.0) shl 16) or (ch(g, 254.1) shl 8) or ch(b, 250.0)
    }

    /** f.lux's named lighting presets. */
    fun name(kelvin: Int): String = when {
        kelvin <= 1300 -> "Ember"
        kelvin <= 2000 -> "Candle"
        kelvin <= 2450 -> "Warm Incandescent"
        kelvin <= 2900 -> "Incandescent"
        kelvin <= 3700 -> "Halogen"
        kelvin <= 4600 -> "Fluorescent"
        kelvin <= 6100 -> "Daylight"
        else -> "No filter"
    }

    fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t

    /** Blend two temperatures in mired space, which is perceptually even. */
    fun lerpKelvin(from: Double, to: Double, t: Double): Double {
        val m = lerp(1e6 / from, 1e6 / to, t.coerceIn(0.0, 1.0))
        return 1e6 / m
    }

    private val TABLE = arrayOf(
        doubleArrayOf(1000.0, 0.750, 0.20, 0.01, 0.00),
        doubleArrayOf(1100.0, 0.750, 0.20, 0.03, 0.00),
        doubleArrayOf(1200.0, 0.750, 0.20, 0.05, 0.00),
        doubleArrayOf(1300.0, 0.750, 0.20, 0.06, 0.00),
        doubleArrayOf(1400.0, 0.750, 0.20, 0.08, 0.00),
        doubleArrayOf(1500.0, 0.750, 0.20, 0.10, 0.00),
        doubleArrayOf(1600.0, 0.750, 0.20, 0.11, 0.00),
        doubleArrayOf(1700.0, 0.750, 0.20, 0.13, 0.00),
        doubleArrayOf(1800.0, 0.710, 0.21, 0.12, 0.00),
        doubleArrayOf(1900.0, 0.710, 0.21, 0.14, 0.00),
        doubleArrayOf(2000.0, 0.650, 0.23, 0.12, 0.00),
        doubleArrayOf(2100.0, 0.600, 0.25, 0.10, 0.00),
        doubleArrayOf(2200.0, 0.575, 0.26, 0.10, 0.00),
        doubleArrayOf(2300.0, 0.555, 0.27, 0.11, 0.00),
        doubleArrayOf(2400.0, 0.515, 0.29, 0.09, 0.00),
        doubleArrayOf(2500.0, 0.500, 0.30, 0.10, 0.00),
        doubleArrayOf(2600.0, 0.480, 0.31, 0.10, 0.00),
        doubleArrayOf(2700.0, 0.450, 0.33, 0.09, 0.00),
        doubleArrayOf(2800.0, 0.440, 0.34, 0.09, 0.00),
        doubleArrayOf(2900.0, 0.415, 0.36, 0.09, 0.00),
        doubleArrayOf(3000.0, 0.405, 0.37, 0.09, 0.00),
        doubleArrayOf(3100.0, 0.380, 0.39, 0.09, 0.00),
        doubleArrayOf(3200.0, 0.370, 0.40, 0.09, 0.00),
        doubleArrayOf(3300.0, 0.355, 0.41, 0.09, 0.00),
        doubleArrayOf(3400.0, 0.335, 0.41, 0.09, 0.00),
        doubleArrayOf(3500.0, 0.315, 0.40, 0.08, 0.00),
        doubleArrayOf(3600.0, 0.300, 0.40, 0.08, 0.00),
        doubleArrayOf(3700.0, 0.285, 0.40, 0.08, 0.00),
        doubleArrayOf(3800.0, 0.270, 0.40, 0.08, 0.00),
        doubleArrayOf(3900.0, 0.255, 0.39, 0.08, 0.00),
        doubleArrayOf(4000.0, 0.240, 0.39, 0.07, 0.00),
        doubleArrayOf(4100.0, 0.225, 0.39, 0.07, 0.00),
        doubleArrayOf(4200.0, 0.215, 0.39, 0.07, 0.00),
        doubleArrayOf(4300.0, 0.200, 0.39, 0.07, 0.00),
        doubleArrayOf(4400.0, 0.190, 0.38, 0.07, 0.00),
        doubleArrayOf(4500.0, 0.175, 0.38, 0.06, 0.00),
        doubleArrayOf(4600.0, 0.165, 0.38, 0.06, 0.00),
        doubleArrayOf(4700.0, 0.155, 0.38, 0.06, 0.00),
        doubleArrayOf(4800.0, 0.145, 0.37, 0.06, 0.00),
        doubleArrayOf(4900.0, 0.130, 0.37, 0.05, 0.00),
        doubleArrayOf(5000.0, 0.120, 0.37, 0.05, 0.00),
        doubleArrayOf(5100.0, 0.110, 0.37, 0.04, 0.00),
        doubleArrayOf(5200.0, 0.105, 0.36, 0.05, 0.00),
        doubleArrayOf(5300.0, 0.095, 0.36, 0.05, 0.00),
        doubleArrayOf(5400.0, 0.085, 0.36, 0.04, 0.00),
        doubleArrayOf(5500.0, 0.075, 0.35, 0.04, 0.00),
        doubleArrayOf(5600.0, 0.065, 0.35, 0.03, 0.00),
        doubleArrayOf(5700.0, 0.060, 0.35, 0.04, 0.00),
        doubleArrayOf(5800.0, 0.050, 0.34, 0.03, 0.00),
        doubleArrayOf(5900.0, 0.045, 0.34, 0.05, 0.00),
        doubleArrayOf(6000.0, 0.035, 0.33, 0.03, 0.00),
        doubleArrayOf(6100.0, 0.030, 0.33, 0.05, 0.00),
        doubleArrayOf(6200.0, 0.020, 0.32, 0.02, 0.00),
        doubleArrayOf(6300.0, 0.015, 0.31, 0.05, 0.00),
        doubleArrayOf(6400.0, 0.005, 0.27, 0.00, 0.00),
        doubleArrayOf(6500.0, 0.000, 0.00, 0.00, 0.00),
    )
}
