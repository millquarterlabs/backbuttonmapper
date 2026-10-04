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
 * On the Galaxy Watch Ultra 2 the lower key arrives as KEYCODE_STEM_PRIMARY, not KEYCODE_BACK.
 * The service swallows every press of it so the system never sees a long press. Short presses are
 * replayed with [GLOBAL_ACTION_BACK]; a press held for [LONG_PRESS_MS] launches Google Wallet.
 *
 * Fallback: if the firmware still reacts to the long press, it shows either a Samsung Wallet
 * window or (once Google Wallet is the default wallet) Android's "Default wallet app" picker.
 * When either appears, we close it and open Google Wallet.
 */
class BackButtonService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    /** True while we are holding a Back press that we swallowed. */
    private var tracking = false
    private var longPressFired = false

    /** True while a Back press is passing through untouched (our own replayed short press). */
    private var passingThrough = false
    private var replayedAt = 0L

    private var lastKeyDownAt = 0L
    private var lastReactionAt = 0L

    private val longPress = Runnable {
        if (tracking) {
            longPressFired = true
            EventLog.add("long press -> Google Wallet")
            buzz()
            WalletLauncher.launch(this)
        }
    }

    private val launchWallet = Runnable { WalletLauncher.launch(this) }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        EventLog.add(
            "key ${KeyEvent.keyCodeToString(event.keyCode)} " +
                (if (event.action == KeyEvent.ACTION_DOWN) "down" else "up") +
                if (event.repeatCount > 0) " repeat=${event.repeatCount}" else ""
        )
        if (event.keyCode !in BACK_KEYS) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount > 0) return tracking
                val now = SystemClock.uptimeMillis()
                // GLOBAL_ACTION_BACK may be delivered back to us on some builds; let it through.
                if (now - replayedAt < REPLAY_WINDOW_MS) {
                    passingThrough = true
                    return false
                }
                lastKeyDownAt = now
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
                if (!longPressFired) goBack()
                return true
            }
        }
        return tracking
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        EventLog.add("window $pkg / ${event.className}")

        // The picker is a normal settings screen too, so only treat it as the firmware's long press
        // reaction when it pops up right after the key went down.
        val firmwareReaction = isSamsungWallet(pkg) ||
            (pkg in PICKER_PACKAGES &&
                SystemClock.uptimeMillis() - lastKeyDownAt < PICKER_WINDOW_MS)
        if (!firmwareReaction) return
        // A screen can report several window changes; react once, or extra backs close Wallet.
        val now = SystemClock.uptimeMillis()
        if (now - lastReactionAt < REACTION_DEBOUNCE_MS) return
        lastReactionAt = now

        EventLog.add("firmware wallet screen -> close, Google Wallet")
        goBack()
        handler.removeCallbacks(launchWallet)
        handler.postDelayed(launchWallet, RELAUNCH_DELAY_MS)
    }

    override fun onServiceConnected() {
        EventLog.add("service connected")
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        return super.onUnbind(intent)
    }

    private fun goBack() {
        replayedAt = SystemClock.uptimeMillis()
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun isSamsungWallet(pkg: String): Boolean =
        pkg.startsWith("com.samsung.") && (pkg.contains("pay") || pkg.contains("wallet"))

    private fun buzz() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    private companion object {
        val BACK_KEYS = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_STEM_PRIMARY)
        val PICKER_PACKAGES = setOf(
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
        const val LONG_PRESS_MS = 500L
        const val REPLAY_WINDOW_MS = 150L
        const val PICKER_WINDOW_MS = 2000L
        const val RELAUNCH_DELAY_MS = 200L
        const val REACTION_DEBOUNCE_MS = 1500L
    }
}
