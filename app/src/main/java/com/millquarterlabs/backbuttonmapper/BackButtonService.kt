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
 * Fallback: on some firmware Samsung handles the long press before accessibility services get
 * the key, so we never see it. Whenever a Samsung Wallet window appears, we open Google Wallet on
 * top of it.
 */
class BackButtonService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    /** True while we are holding a Back press that we swallowed. */
    private var tracking = false
    private var longPressFired = false

    /** True while a Back press is passing through untouched (our own replayed short press). */
    private var passingThrough = false
    private var replayedAt = 0L


    private val longPress = Runnable {
        if (tracking) {
            longPressFired = true
            EventLog.add("long press -> Google Wallet")
            buzz()
            WalletLauncher.launch(this)
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        EventLog.add(
            "key ${KeyEvent.keyCodeToString(event.keyCode)} " +
                (if (event.action == KeyEvent.ACTION_DOWN) "down" else "up") +
                if (event.repeatCount > 0) " repeat=${event.repeatCount}" else ""
        )
        if (event.keyCode != KeyEvent.KEYCODE_BACK) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return tracking
                val now = SystemClock.uptimeMillis()
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
        EventLog.add("window $pkg / ${event.className}")
        if (!isSamsungWallet(pkg)) return

        // Bringing Google Wallet to the front again is harmless if we already opened it.
        EventLog.add("Samsung Wallet seen -> Google Wallet")
        WalletLauncher.launch(this)
    }

    override fun onServiceConnected() {
        EventLog.add("service connected")
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    private fun isSamsungWallet(pkg: String): Boolean =
        pkg.startsWith("com.samsung.") && (pkg.contains("pay") || pkg.contains("wallet"))

    private fun buzz() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    private companion object {
        const val LONG_PRESS_MS = 500L
        const val REPLAY_WINDOW_MS = 150L
    }
}
