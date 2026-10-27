package com.ember.tv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import java.time.ZonedDateTime

/** f.lux-style 24-hour strip: the color of each hour, a temperature line, and a "now" marker. */
class DayGraph(ctx: Context) : View(ctx) {

    var settings: Settings = Settings()
        set(value) { field = value; invalidate() }

    private val dp = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f * dp; color = 0xFFF3E9DF.toInt()
    }
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2f * dp; color = 0xFFFF9E3D.toInt()
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFA8968A.toInt(); textSize = 12f * dp; textAlign = Paint.Align.CENTER
    }
    private val clip = Path()
    private val box = RectF()

    override fun onDraw(c: Canvas) {
        val now = ZonedDateTime.now()
        val today = Schedule.compute(settings, now)
        val rise = today.sunrise ?: 720.0
        val set = today.sunset ?: 720.0

        val w = width.toFloat()
        val top = 8 * dp
        val bottom = height - 26 * dp
        val h = bottom - top
        box.set(0f, top, w, bottom)
        clip.reset(); clip.addRoundRect(box, 14 * dp, 14 * dp, Path.Direction.CW)

        // Color band: each column tinted with that minute's swatch.
        c.save(); c.clipPath(clip)
        val cols = 192
        val colW = w / cols
        val linePath = Path()
        for (i in 0..cols) {
            val m = i * 1440.0 / cols
            val k = Schedule.at(settings, m, rise, set).kelvin
            fill.color = ColorMath.swatch(k)
            fill.alpha = 70
            c.drawRect(i * colW, top, (i + 1) * colW + 1, bottom, fill)
            // y: 6500K at the top, 1000K at the bottom (mired scale)
            val frac = ((1e6 / k) - (1e6 / 6500)) / ((1e6 / 1000) - (1e6 / 6500))
            val y = (top + 10 * dp + frac * (h - 20 * dp)).toFloat()
            if (i == 0) linePath.moveTo(0f, y) else linePath.lineTo(i * colW, y)
        }
        c.drawPath(linePath, line)
        c.restore()

        // Now marker
        val nowX = ((now.hour * 60 + now.minute) / 1440.0 * w).toFloat()
        c.drawLine(nowX, top, nowX, bottom, nowPaint)

        // Hour labels
        val hours = listOf(0 to "12a", 360 to "6a", 720 to "12p", 1080 to "6p")
        for ((m, t) in hours) {
            val x = (m / 1440.0 * w).toFloat().coerceIn(14 * dp, w - 14 * dp)
            c.drawText(t, x, height - 6 * dp, label)
        }
    }
}
