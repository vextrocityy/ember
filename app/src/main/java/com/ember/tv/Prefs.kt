package com.ember.tv

import android.content.Context

object Prefs {
    private const val FILE = "ember"

    fun load(ctx: Context): Settings {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val d = Settings()
        return Settings(
            enabled = p.getBoolean("enabled", d.enabled),
            dayK = p.getInt("dayK", d.dayK),
            sunsetK = p.getInt("sunsetK", d.sunsetK),
            bedK = p.getInt("bedK", d.bedK),
            wakeMin = p.getInt("wakeMin", d.wakeMin),
            bedMin = p.getInt("bedMin", d.bedMin),
            transitionSec = p.getInt("transitionSec", d.transitionSec),
            bedDimPct = p.getInt("bedDimPct", d.bedDimPct),
            lat = p.getFloat("lat", d.lat.toFloat()).toDouble(),
            lon = p.getFloat("lon", d.lon.toFloat()).toDouble(),
            pauseUntil = p.getLong("pauseUntil", d.pauseUntil),
            movieUntil = p.getLong("movieUntil", d.movieUntil),
        )
    }

    fun save(ctx: Context, s: Settings) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("enabled", s.enabled)
            .putInt("dayK", s.dayK)
            .putInt("sunsetK", s.sunsetK)
            .putInt("bedK", s.bedK)
            .putInt("wakeMin", s.wakeMin)
            .putInt("bedMin", s.bedMin)
            .putInt("transitionSec", s.transitionSec)
            .putInt("bedDimPct", s.bedDimPct)
            .putFloat("lat", s.lat.toFloat())
            .putFloat("lon", s.lon.toFloat())
            .putLong("pauseUntil", s.pauseUntil)
            .putLong("movieUntil", s.movieUntil)
            .apply()
    }
}
