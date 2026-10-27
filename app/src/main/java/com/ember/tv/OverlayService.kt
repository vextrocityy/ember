package com.ember.tv

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.view.View
import android.view.WindowManager
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.max

/**
 * Keeps a full-screen, click-through tint window above everything and steers its
 * color along the schedule. Runs as a foreground service so Google TV won't kill it.
 */
class OverlayService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var view: View? = null

    // What's on screen now, and the animation toward the next target.
    private var curK = 6500.0
    private var curDim = 0.0
    private var fromK = 6500.0
    private var fromDim = 0.0
    private var toK = 6500.0
    private var toDim = 0.0
    private var animStart = 0L
    private var animMs = 0L

    // Temporary overrides from the settings screen.
    private var previewK: Double? = null
    private var previewDim = 0.0
    private var previewUntil = 0L
    private var dayPreviewStart = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREVIEW_K -> {
                previewK = intent.getDoubleExtra(EXTRA_K, 6500.0)
                previewDim = intent.getDoubleExtra(EXTRA_DIM, 0.0)
                previewUntil = SystemClock.uptimeMillis() + 3000
            }
            ACTION_PREVIEW_DAY -> dayPreviewStart = SystemClock.uptimeMillis()
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    // ---- schedule ---------------------------------------------------------------

    private val tick = object : Runnable {
        override fun run() {
            val s = Prefs.load(this@OverlayService)
            if (!s.enabled || !AndroidSettings.canDrawOverlays(this@OverlayService)) {
                stopSelf()
                return
            }
            val now = SystemClock.uptimeMillis()
            val (k, dim) = target(s, now)
            if (abs(1e6 / k - 1e6 / toK) > 0.5 || abs(dim - toDim) > 0.002) {
                val jump = abs(1e6 / k - 1e6 / toK) > 15 || abs(dim - toDim) > 0.05
                fromK = curK; fromDim = curDim; toK = k; toDim = dim
                animStart = now
                animMs = if (jump && dayPreviewStart == 0L) 900 else 0
                handler.post(animate)
            }
            val fast = dayPreviewStart != 0L || previewK != null
            handler.postDelayed(this, if (fast) FRAME_MS else 15_000)
        }
    }

    private fun target(s: Settings, now: Long): Pair<Double, Double> {
        // 24-hour fast-forward, like f.lux's preview.
        if (dayPreviewStart != 0L) {
            val p = (now - dayPreviewStart) / DAY_PREVIEW_MS.toDouble()
            if (p < 1) {
                val today = Schedule.compute(s, ZonedDateTime.now())
                val m = (ZonedDateTime.now().let { it.hour * 60 + it.minute } + p * 1440) % 1440
                val st = Schedule.at(s, m, today.sunrise ?: 720.0, today.sunset ?: 720.0)
                return st.kelvin to st.dim
            }
            dayPreviewStart = 0L
        }
        previewK?.let { if (now < previewUntil) return it to previewDim else previewK = null }

        val wall = System.currentTimeMillis()
        if (wall < s.pauseUntil) return 6500.0 to 0.0
        val st = Schedule.compute(s, ZonedDateTime.now())
        if (wall < s.movieUntil) return max(st.kelvin, MOVIE_K) to 0.0
        return st.kelvin to st.dim
    }

    // ---- drawing ----------------------------------------------------------------

    private val animate = object : Runnable {
        override fun run() {
            val p = if (animMs == 0L) 1.0
            else ((SystemClock.uptimeMillis() - animStart) / animMs.toDouble()).coerceIn(0.0, 1.0)
            curK = ColorMath.lerpKelvin(fromK, toK, p)
            curDim = ColorMath.lerp(fromDim, toDim, p)
            draw()
            if (p < 1) handler.postDelayed(this, FRAME_MS)
        }
    }

    private fun draw() {
        val layer = ColorMath.layerFor(curK, curDim)
        if (layer.alpha < 0.004) { removeOverlay(); return }
        val v = view ?: addOverlay() ?: return
        v.setBackgroundColor(layer.toArgb())
    }

    private fun addOverlay(): View? {
        val v = View(this)
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        )
        lp.title = "Ember"
        return try {
            wm?.addView(v, lp); view = v; v
        } catch (e: Exception) {
            null // permission revoked
        }
    }

    private fun removeOverlay() {
        view?.let { runCatching { wm?.removeView(it) } }
        view = null
    }

    // ---- foreground plumbing ------------------------------------------------------

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Ember filter", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_ember)
            .setContentTitle("Ember is filtering blue light")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    companion object {
        private const val CHANNEL = "ember"
        private const val FRAME_MS = 33L
        private const val DAY_PREVIEW_MS = 12_000L
        private const val MOVIE_K = 4500.0
        private const val ACTION_PREVIEW_K = "com.ember.tv.PREVIEW_K"
        private const val ACTION_PREVIEW_DAY = "com.ember.tv.PREVIEW_DAY"
        private const val EXTRA_K = "k"
        private const val EXTRA_DIM = "dim"

        fun refresh(ctx: Context) = send(ctx, Intent(ctx, OverlayService::class.java))

        fun previewKelvin(ctx: Context, k: Double, dim: Double = 0.0) = send(
            ctx, Intent(ctx, OverlayService::class.java).setAction(ACTION_PREVIEW_K)
                .putExtra(EXTRA_K, k).putExtra(EXTRA_DIM, dim)
        )

        fun previewDay(ctx: Context) =
            send(ctx, Intent(ctx, OverlayService::class.java).setAction(ACTION_PREVIEW_DAY))

        private fun send(ctx: Context, i: Intent) {
            val s = Prefs.load(ctx)
            if (!s.enabled || !AndroidSettings.canDrawOverlays(ctx)) {
                ctx.stopService(Intent(ctx, OverlayService::class.java))
                return
            }
            ctx.startForegroundService(i)
        }
    }
}
