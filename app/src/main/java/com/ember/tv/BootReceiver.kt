package com.ember.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the filter after the Chromecast reboots or the app updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> runCatching { OverlayService.refresh(context) }
        }
    }
}
