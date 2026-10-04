package com.millquarterlabs.backbuttonmapper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Remaps a long press of the Back (lower) key to Google Wallet.
 *
 * The service swallows every Back key press so the system never sees a long press (and so never
 * opens Samsung Wallet). Short presses are replayed with [GLOBAL_ACTION_BACK]; a press held for
 * [LONG_PRESS_MS] launches Google Wallet instead.
 *
 * Fallback: if the firmware handles the long press before accessibility services get the key,
 * Samsung Wallet still opens. When that happens right after a Back press, we open Google Wallet
 * on top of it.
 */
class BackButtonService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    /** True while we are holding a Back press that we swallowed. */
    private var tracking = false
    private var longPressFired = false

    /** True while a Back press is passing through untouched (our own replayed short press). */
    private var passingThrough = false
    private var replayedAt = 0L

    private var lastBackDownAt = 0L
    private var lastRedirectAt = 0L

    private val longPress = Runnable {
        if (tracking) {
            longPressFired = true
            buzz()
            WalletLauncher.launch(this)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return tracking
                val now = SystemClock.uptimeMillis()
                lastBackDownAt = now
                // GLOBAL_ACTION_BACK may be delivered back to us on some builds; let it through.
                if (now - replayedAt < REPLAY_WINDOW_MS) {
                    passingThrough = true
                    return false
                }
                tracking = true
                longPressFired = false
                handler.postDelayed(longPress, LONG_PRESS_MS)
                return true
            }

            KeyEvent.ACTION_UP -> {
                if (passingThrough) {
                    passingThrough = false
                    return false
                }
                if (!tracking) return false
                tracking = false
                handler.removeCallbacks(longPress)
                if (!longPressFired) {
                    replayedAt = SystemClock.uptimeMillis()
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                return true
            }
        }
        return tracking
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (!isSamsungWallet(pkg)) return

        val now = SystemClock.uptimeMillis()
        val afterBackPress = now - lastBackDownAt < REDIRECT_WINDOW_MS
        val notJustRedirected = now - lastRedirectAt > REDIRECT_WINDOW_MS
        if (afterBackPress && notJustRedirected) {
            lastRedirectAt = now
            WalletLauncher.launch(this)
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    private fun isSamsungWallet(pkg: String): Boolean =
        pkg.startsWith("com.samsung.android.") &&
            (pkg.contains("pay") || pkg.contains("wallet"))

    private fun buzz() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    private companion object {
        const val LONG_PRESS_MS = 500L
        const val REPLAY_WINDOW_MS = 150L
        const val REDIRECT_WINDOW_MS = 3000L
    }
}
