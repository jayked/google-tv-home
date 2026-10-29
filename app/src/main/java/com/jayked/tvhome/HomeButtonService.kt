package com.jayked.tvhome

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.jayked.tvhome.data.AppRepository

/**
 * Makes TV Home the home screen without disabling Google TV's launcher (which on Chromecast
 * leaves Home broken). The Home key can't be intercepted (the system handles it before
 * accessibility services), so instead TV Home is opened whenever Google TV's home screen comes up,
 * after Home or after Back out of an app.
 */
class HomeButtonService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.className != STOCK_HOME) return
        // Opened on purpose from TV Home: it reports a few window changes while opening, let those through.
        if (SystemClock.elapsedRealtime() - stockHomeRequestedAt < STOCK_HOME_GRACE_MS) return
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onInterrupt() = Unit

    companion object {
        /** When the user opened Google TV's home from TV Home, so it isn't redirected right back. */
        @Volatile
        var stockHomeRequestedAt = 0L

        private const val STOCK_HOME_GRACE_MS = 3_000L

        private const val STOCK_HOME = "${AppRepository.STOCK_LAUNCHER}.home.HomeActivity"
    }
}
