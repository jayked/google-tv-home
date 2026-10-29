package com.jayked.tvhome

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Makes TV Home the home screen without disabling Google TV's launcher (which on Chromecast
 * leaves Home broken): Home presses are consumed and open TV Home, and whenever the stock home
 * screen comes up anyway (e.g. after Back out of an app) TV Home is opened over it.
 */
class HomeButtonService : AccessibilityService() {
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) openTvHome()
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.className == STOCK_HOME) {
            openTvHome()
        }
    }

    override fun onInterrupt() = Unit

    private fun openTvHome() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val STOCK_HOME = "com.google.android.apps.tv.launcherx.home.HomeActivity"
    }
}
