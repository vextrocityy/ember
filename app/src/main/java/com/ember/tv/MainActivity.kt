package com.ember.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.roundToInt

/** Remote-friendly settings: ◀ ▶ changes a value, OK toggles or runs an action. */
class MainActivity : Activity() {

    private lateinit var s: Settings
    private val handler = Handler(Looper.getMainLooper())
    private val dp by lazy { resources.displayMetrics.density }

    private lateinit var bigTemp: TextView
    private lateinit var phase: TextView
    private lateinit var sunInfo: TextView
    private lateinit var setupCard: TextView
    private lateinit var graph: DayGraph
    private val rows = mutableListOf<Row>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        s = Prefs.load(this)
        setContentView(buildUi())
        rows.first().view.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        s = Prefs.load(this)
        refreshAll()
        OverlayService.refresh(this)
        handler.post(clock)
    }

    override fun onPause() {
        handler.removeCallbacks(clock)
        super.onPause()
    }

    private val clock = object : Runnable {
        override fun run() { refreshStatus(); handler.postDelayed(this, 30_000) }
    }

    // ---- rows --------------------------------------------------------------------

    private inner class Row(
        val label: String,
        val value: () -> String,
        val swatch: (() -> Int)? = null,
        val onStep: ((dir: Int, repeat: Int) -> Unit)? = null,
        val onClick: (() -> Unit)? = null,
    ) {
        lateinit var view: LinearLayout
        lateinit var valueView: TextView
        lateinit var dot: View
        fun render() {
            val v = value()
            valueView.text = if (onStep != null && view.isFocused) "‹  $v  ›" else v
            swatch?.let { dot.visibility = View.VISIBLE; (dot.background as GradientDrawable).setColor(it()) }
        }
    }

    private fun buildRows(): List<Row> = listOf(
        Row("Ember", { if (s.enabled) "On" else "Off" },
            onStep = { _, _ -> update(s.copy(enabled = !s.enabled)) },
            onClick = { update(s.copy(enabled = !s.enabled)) }),
        tempRow("Daytime", { s.dayK }) { s.copy(dayK = it) },
        tempRow("Sunset", { s.sunsetK }) { s.copy(sunsetK = it) },
        tempRow("Bedtime", { s.bedK }) { s.copy(bedK = it) },
        timeRow("Wake time", { s.wakeMin }) { s.copy(wakeMin = it) },
        timeRow("Bedtime starts", { s.bedMin }) { s.copy(bedMin = it) },
        cycleRow("Transition speed", TRANSITIONS, { s.transitionSec }, ::transitionName) {
            s.copy(transitionSec = it)
        },
        cycleRow("Dim at bedtime", listOf(0, 15, 30, 45), { s.bedDimPct },
            { if (it == 0) "Off" else "$it%" }) { s.copy(bedDimPct = it) },
        coordRow("Latitude", { s.lat }, 90.0) { s.copy(lat = it) },
        coordRow("Longitude", { s.lon }, 180.0) { s.copy(lon = it) },
        Row("Disable for an hour", {
            if (System.currentTimeMillis() < s.pauseUntil) "Paused until ${clockTime(s.pauseUntil)}  ·  OK to resume"
            else "OK"
        }, onClick = {
            val paused = System.currentTimeMillis() < s.pauseUntil
            update(s.copy(pauseUntil = if (paused) 0 else System.currentTimeMillis() + 3_600_000, movieUntil = 0))
        }),
        Row("Movie mode", {
            if (System.currentTimeMillis() < s.movieUntil) "On until ${clockTime(s.movieUntil)}  ·  OK to end"
            else "OK  ·  2.5 h, keeps colors natural"
        }, onClick = {
            val on = System.currentTimeMillis() < s.movieUntil
            update(s.copy(movieUntil = if (on) 0 else System.currentTimeMillis() + 9_000_000, pauseUntil = 0))
        }),
        Row("Preview the day", { "OK" }, onClick = {
            if (s.enabled) OverlayService.previewDay(this) else toast("Turn Ember on first")
        }),
    )

    private fun tempRow(label: String, get: () -> Int, set: (Int) -> Settings) = Row(
        label,
        { "${get()}K  ·  ${ColorMath.name(get())}" },
        swatch = { ColorMath.swatch(get().toDouble()) },
        onStep = { dir, rep ->
            val step = if (rep > 8) 300 else 100
            val k = (get() + dir * step).coerceIn(1200, 6500)
            update(set(k), refresh = false)
            if (s.enabled) OverlayService.previewKelvin(this, k.toDouble())
        },
    )

    private fun timeRow(label: String, get: () -> Int, set: (Int) -> Settings) = Row(
        label, { minutesName(get()) },
        onStep = { dir, _ -> update(set(Math.floorMod(get() + dir * 15, 1440))) },
    )

    private fun cycleRow(
        label: String, options: List<Int>, get: () -> Int, name: (Int) -> String, set: (Int) -> Settings,
    ) = Row(
        label, { name(get()) },
        onStep = { dir, _ ->
            val i = options.indexOf(get()).coerceAtLeast(0)
            update(set(options[Math.floorMod(i + dir, options.size)]))
        },
    )

    private fun coordRow(label: String, get: () -> Double, limit: Double, set: (Double) -> Settings) = Row(
        label, { String.format(Locale.US, "%.2f°", get()) },
        onStep = { dir, rep ->
            val step = if (rep > 10) 1.0 else 0.1
            val v = ((get() + dir * step) * 100).roundToInt() / 100.0
            update(set(v.coerceIn(-limit, limit)))
        },
    )

    private fun update(next: Settings, refresh: Boolean = true) {
        s = next
        Prefs.save(this, s)
        refreshAll()
        if (refresh) OverlayService.refresh(this)
    }

    // ---- layout ------------------------------------------------------------------

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(BG) }
        val cols = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(px(48), px(28), px(48), px(20))
        }
        root.addView(cols, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        // Left: settings list
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(text("Ember", 30f, TEXT, bold = true))
        left.addView(text("Warm light after dark — f.lux for your TV", 14f, MUTED).apply {
            setPadding(0, px(2), 0, px(14))
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.clear(); rows.addAll(buildRows())
        rows.forEach { list.addView(makeRowView(it)) }
        left.addView(ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, px(4), 0, px(4))
            addView(list)
        }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        cols.addView(left, LinearLayout.LayoutParams(0, MATCH_PARENT, 1.15f))

        // Right: live status + day graph
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(36), px(56), 0, 0)
        }
        bigTemp = text("", 44f, TEXT, bold = true)
        phase = text("", 16f, ACCENT)
        sunInfo = text("", 14f, MUTED).apply { setPadding(0, px(4), 0, px(16)) }
        graph = DayGraph(this)
        setupCard = text("", 14f, TEXT).apply {
            background = rounded(SURFACE, 14f)
            setPadding(px(16), px(14), px(16), px(14))
            visibility = View.GONE
        }
        right.addView(bigTemp); right.addView(phase); right.addView(sunInfo)
        right.addView(graph, LinearLayout.LayoutParams(MATCH_PARENT, px(170)))
        right.addView(setupCard, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = px(16)
        })
        cols.addView(right, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
        return root
    }

    private fun makeRowView(row: Row): View {
        val v = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(px(16), px(10), px(16), px(10))
            background = rounded(Color.TRANSPARENT, 12f)
        }
        val label = text(row.label, 16f, TEXT)
        row.dot = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            visibility = View.GONE
        }
        row.valueView = text("", 15f, MUTED).apply { gravity = Gravity.END }
        v.addView(label, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        v.addView(row.dot, LinearLayout.LayoutParams(px(12), px(12)).apply { marginEnd = px(10) })
        v.addView(row.valueView)
        row.view = v

        v.setOnFocusChangeListener { _, focused ->
            (v.background as GradientDrawable).setColor(if (focused) SURFACE_FOCUS else Color.TRANSPARENT)
            (v.background as GradientDrawable).setStroke(px(2), if (focused) ACCENT else Color.TRANSPARENT)
            row.valueView.setTextColor(if (focused) TEXT else MUTED)
            row.render()
        }
        v.setOnKeyListener { _, code, e ->
            if (e.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (code) {
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    val step = row.onStep ?: return@setOnKeyListener true
                    step(if (code == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1, e.repeatCount)
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    row.onClick?.invoke()
                    row.onClick != null
                }
                else -> false
            }
        }
        v.setOnClickListener { row.onClick?.invoke() }
        row.render()
        return v
    }

    // ---- status ------------------------------------------------------------------

    private fun refreshAll() {
        rows.forEach { it.render() }
        refreshStatus()
    }

    private fun refreshStatus() {
        val now = ZonedDateTime.now()
        val st = Schedule.compute(s, now)
        val wall = System.currentTimeMillis()
        val (k, label) = when {
            !s.enabled -> 6500.0 to "Off"
            wall < s.pauseUntil -> 6500.0 to "Paused until ${clockTime(s.pauseUntil)}"
            wall < s.movieUntil -> maxOf(st.kelvin, 4500.0) to "Movie mode"
            else -> st.kelvin to st.phase
        }
        val kr = ((k / 50).roundToInt() * 50)
        bigTemp.text = "${kr}K"
        bigTemp.setTextColor(ColorMath.swatch(k))
        phase.text = "$label  ·  ${ColorMath.name(kr)}"
        sunInfo.text = buildString {
            append("Sunrise ${st.sunrise?.let { minutesName(it.roundToInt()) } ?: "—"}")
            append("   ·   Sunset ${st.sunset?.let { minutesName(it.roundToInt()) } ?: "—"}")
        }
        graph.settings = s

        val granted = AndroidSettings.canDrawOverlays(this)
        setupCard.visibility = if (granted) View.GONE else View.VISIBLE
        if (!granted) {
            setupCard.text = "One-time setup: Ember needs permission to draw over other apps.\n\n" +
                "From your PC run:\nadb shell appops set $packageName SYSTEM_ALERT_WINDOW allow\n\n" +
                "Then reopen Ember."
            tryOpenOverlaySettings()
        }
    }

    private var askedForPermission = false
    private fun tryOpenOverlaySettings() {
        if (askedForPermission) return
        askedForPermission = true
        runCatching {
            startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } // Google TV usually hides this screen; the adb command above always works.
    }

    // ---- helpers -----------------------------------------------------------------

    private fun text(t: String, sp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = t
        textSize = sp
        setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun rounded(color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = radiusDp * dp
    }

    private fun px(v: Int) = (v * dp).roundToInt()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun clockTime(millis: Long): String {
        val t = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        return minutesName(t.hour * 60 + t.minute)
    }

    companion object {
        private val BG = 0xFF120D0A.toInt()
        private val SURFACE = 0xFF1E1712.toInt()
        private val SURFACE_FOCUS = 0xFF2E2118.toInt()
        private val TEXT = 0xFFF3E9DF.toInt()
        private val MUTED = 0xFFA8968A.toInt()
        private val ACCENT = 0xFFFF9E3D.toInt()

        private val TRANSITIONS = listOf(20, 20 * 60, 60 * 60)

        fun transitionName(sec: Int) = when (sec) {
            20 -> "Fast · 20 sec"
            20 * 60 -> "Medium · 20 min"
            else -> "Slow · 60 min"
        }

        fun minutesName(min: Int): String {
            val m = Math.floorMod(min, 1440)
            val h = m / 60
            val h12 = if (h % 12 == 0) 12 else h % 12
            return String.format(Locale.US, "%d:%02d %s", h12, m % 60, if (h < 12) "AM" else "PM")
        }
    }
}
