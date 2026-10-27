package com.ember.tv

import java.time.ZonedDateTime
import kotlin.math.max

/** Everything the user can change. Defaults mirror f.lux's. */
data class Settings(
    val enabled: Boolean = true,
    val dayK: Int = 6500,
    val sunsetK: Int = 3400,     // Halogen
    val bedK: Int = 1900,        // Candle
    val wakeMin: Int = 7 * 60,   // 7:00 AM
    val bedMin: Int = 23 * 60,   // 11:00 PM
    val transitionSec: Int = 20 * 60,
    val bedDimPct: Int = 0,
    val lat: Double = 47.76,     // Bothell, WA
    val lon: Double = -122.21,
    val pauseUntil: Long = 0,
    val movieUntil: Long = 0,
)

/** f.lux's day: daylight → sunset color at sunset → bedtime color at bedtime → daylight at sunrise/wake. */
object Schedule {

    data class State(
        val kelvin: Double,
        val dim: Double,
        val phase: String,
        val sunrise: Double?,   // minutes after midnight
        val sunset: Double?,
        val dayStart: Double,
    )

    fun compute(s: Settings, now: ZonedDateTime): State {
        val sun = Sun.times(now.toLocalDate(), s.lat, s.lon, now.zone)
        val rise = sun.sunrise ?: if (sun.polarDay) 0.0 else 720.0
        val set = sun.sunset ?: if (sun.polarDay) 1440.0 else 720.0
        val m = now.hour * 60 + now.minute + now.second / 60.0
        return at(s, m, rise, set).copy(sunrise = sun.sunrise, sunset = sun.sunset)
    }

    /** Pure function of minutes-after-midnight, so the 24h graph can reuse it. */
    fun at(s: Settings, m: Double, rise: Double, set: Double): State {
        val t = max(s.transitionSec / 60.0, 0.1)
        val dayStart = max(rise, s.wakeMin.toDouble())           // never brighten before you wake
        val bed = if (s.bedMin < 12 * 60) s.bedMin + 1440.0 else s.bedMin.toDouble() // after-midnight bedtimes
        val bedDim = s.bedDimPct / 100.0
        fun ramp(x: Double, start: Double) = smooth((x - start) / t)

        if (m >= dayStart && m < dayStart + t) {                  // morning: night → day
            val p = ramp(m, dayStart)
            return State(
                ColorMath.lerpKelvin(s.bedK.toDouble(), s.dayK.toDouble(), p),
                bedDim * (1 - p), "Sunrise", rise, set, dayStart,
            )
        }
        val mm = if (m < dayStart) m + 1440 else m              // small hours belong to last night
        val f1 = ramp(mm, set)
        val f2 = ramp(mm, bed)
        val k = ColorMath.lerpKelvin(
            ColorMath.lerpKelvin(s.dayK.toDouble(), s.sunsetK.toDouble(), f1),
            s.bedK.toDouble(), f2,
        )
        val phase = when {
            f2 >= 1 -> "Bedtime"
            f2 > 0 -> "Winding down"
            f1 >= 1 -> "Sunset"
            f1 > 0 -> "Sunsetting"
            else -> "Daytime"
        }
        return State(k, bedDim * f2, phase, rise, set, dayStart)
    }

    private fun smooth(x: Double): Double {
        val c = x.coerceIn(0.0, 1.0)
        return c * c * (3 - 2 * c)
    }
}
