package com.ember.tv

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/** Sunrise / sunset from the standard sunrise equation (accurate to a minute or two). */
object Sun {

    /** Minutes after local midnight. Null when the sun doesn't rise or set that day. */
    data class Times(val sunrise: Double?, val sunset: Double?, val polarDay: Boolean = false)

    fun times(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): Times {
        val rad = Math.PI / 180
        // Days from J2000.0 (2000-01-01 12:00 UTC) to noon UTC of this date.
        val n = date.toEpochDay() - 10957.0
        val jStar = n - lon / 360.0
        val m = norm(357.5291 + 0.98560028 * jStar)
        val c = 1.9148 * sin(m * rad) + 0.02 * sin(2 * m * rad) + 0.0003 * sin(3 * m * rad)
        val lambda = norm(m + c + 180 + 102.9372)
        val transit = 2451545.0 + jStar + 0.0053 * sin(m * rad) - 0.0069 * sin(2 * lambda * rad)
        val decl = asin(sin(lambda * rad) * sin(23.4397 * rad))
        val cosW = (sin(-0.833 * rad) - sin(lat * rad) * sin(decl)) / (cos(lat * rad) * cos(decl))
        if (cosW > 1) return Times(null, null, polarDay = false) // sun never rises
        if (cosW < -1) return Times(null, null, polarDay = true) // sun never sets
        val w = acos(cosW) / rad
        val midnight = date.atStartOfDay(zone).toInstant()
        return Times(
            sunrise = minutesAfter(midnight, julianToInstant(transit - w / 360)),
            sunset = minutesAfter(midnight, julianToInstant(transit + w / 360)),
        )
    }

    private fun norm(deg: Double) = ((deg % 360) + 360) % 360

    private fun julianToInstant(j: Double): Instant =
        Instant.ofEpochMilli(((j - 2440587.5) * 86_400_000).toLong())

    private fun minutesAfter(start: Instant, t: Instant) =
        ChronoUnit.SECONDS.between(start, t) / 60.0
}
