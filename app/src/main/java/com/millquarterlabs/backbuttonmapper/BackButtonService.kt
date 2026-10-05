package com.millquarterlabs.backbuttonmapper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Sends a long press of the Back (lower) key to Google Wallet.
 *
 * On the Galaxy Watch Ultra 2 the lower key arrives as KEYCODE_STEM_PRIMARY, and the firmware acts
 * on its long press before accessibility services can stop it: it always opens something, either
 * Samsung Wallet (often several windows in a row) or Android's "Default wallet app" picker. So the
 * service doesn't touch the key at all; it waits for that burst of windows to settle and then
 * brings Google Wallet to the front, once.
 *
 * To hide Samsung's screens while that happens, a black accessibility overlay covers the display
 * from shortly into the press until Google Wallet is on top.
 *
 * Direct mode: when Samsung Wallet is disabled (adb `pm disable-user`), the firmware has nothing to
 * open. The service still lets every key through untouched (short presses are the watch's own
 * Back) and only adds a timer: a hold opens Google Wallet straight away. No overlay, no window
 * watching. Samsung Health is left alone, since it uses the button during workouts.
 */
class BackButtonService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var keyDownAt = 0L
    private var lastFirmwareScreenAt = 0L
    private var pickerSeen = false
    private var walletLaunchedAt = 0L

    private var cover: View? = null

    // Direct mode state.
    private var foregroundPkg = ""
    private var directPressActive = false
    private var directWalletOpened = false

    private val directLongPress = Runnable {
        if (directPressActive) {
            directWalletOpened = true
            EventLog.add("long press -> Google Wallet")
            openWallet()
        }
    }

    private val redirect = Runnable {
        if (pickerSeen) {
            // The picker is a plain settings screen; close it so it isn't left behind Wallet.
            pickerSeen = false
            EventLog.add("-> close picker, Google Wallet")
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed(launchWallet, AFTER_BACK_DELAY_MS)
        } else {
            EventLog.add("-> Google Wallet")
            openWallet()
        }
    }

    private val launchWallet = Runnable { openWallet() }
    private val showCover = Runnable { showCover() }
    private val hideCover = Runnable { hideCover() }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode !in BACK_KEYS) return false
        if (event.repeatCount > 0) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        val now = SystemClock.uptimeMillis()
        // Key events can reach us late (seen on the watch: several presses arriving within a few
        // ms after it woke up), so time presses by the key's own timestamps, not by arrival.
        val lag = now - event.eventTime
        val held = event.eventTime - event.downTime
        EventLog.add(
            "key ${KeyEvent.keyCodeToString(event.keyCode)} " +
                (if (down) "down" else "up, held $held ms") +
                if (lag > LATE_LOG_MS) " (arrived $lag ms late)" else ""
        )
        if (!directPressActive && foregroundPkg in PASSTHROUGH_PACKAGES) {
            // Samsung Health ends a workout on a long press of this button: leave it alone
            // completely (no Wallet, no cover).
            return false
        }
        if (directPressActive || isDirectMode()) {
            // Direct mode: never consume the key, so short presses stay the watch's own Back.
            // A press held past LONG_PRESS_MS also opens Google Wallet. A new DOWN restarts the
            // timer, so a lost UP can't leave anything stuck.
            handler.removeCallbacks(directLongPress)
            directPressActive = down
            if (down) {
                directWalletOpened = false
                // Runs at once if the DOWN arrived more than LONG_PRESS_MS late; a press that
                // arrives very late is stale, so don't act on it.
                if (lag < MAX_LATE_MS) {
                    handler.postAtTime(directLongPress, event.downTime + LONG_PRESS_MS)
                }
            } else if (!directWalletOpened && held >= LONG_PRESS_MS && lag < MAX_LATE_MS) {
                // The whole hold arrived late, after the timer could have fired.
                directWalletOpened = true
                EventLog.add("long press (late) -> Google Wallet")
                openWallet()
            }
            return false
        }
        if (down) {
            keyDownAt = now
            handler.postDelayed(showCover, COVER_AFTER_MS)
        } else {
            handler.removeCallbacks(showCover)
            if (now - keyDownAt < LONG_PRESS_MS && lastFirmwareScreenAt < keyDownAt) {
                hideCover() // released just after the cover went up: not a long press after all
            } else if (now - keyDownAt >= LONG_PRESS_MS && lastFirmwareScreenAt < keyDownAt) {
                // Long press but the firmware showed nothing (yet): open Wallet ourselves.
                EventLog.add("long press, no firmware screen -> Google Wallet")
                openWallet()
            }
        }
        return false
    }

    private fun isDirectMode(): Boolean = !SamsungWallet.isActive(this)

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return
        foregroundPkg = pkg
        EventLog.add("window $pkg / ${event.className}")

        val now = SystemClock.uptimeMillis()
        if (pkg == WalletLauncher.GOOGLE_WALLET_PACKAGE && walletLaunchedAt >= keyDownAt) {
            // Our Google Wallet is on top; let it draw a frame, then lift the cover.
            handler.removeCallbacks(hideCover)
            handler.postDelayed(hideCover, UNCOVER_DELAY_MS)
            return
        }

        val isPicker = pkg in PICKER_PACKAGES && now - keyDownAt < FIRMWARE_WINDOW_MS
        if (!isSamsungWallet(pkg) && !isPicker) return

        lastFirmwareScreenAt = now
        if (isPicker) pickerSeen = true
        // Samsung Wallet opens several windows in a row; restart the timer on each one so we only
        // switch to Google Wallet after the last of them. Keep (or put up) the cover meanwhile.
        handler.removeCallbacks(hideCover)
        handler.removeCallbacks(redirect)
        handler.removeCallbacks(launchWallet)
        showCover()
        handler.postDelayed(redirect, SETTLE_MS)
    }

    override fun onServiceConnected() {
        EventLog.add(
            "service connected, " +
                if (SamsungWallet.isActive(this)) "redirect mode" else "direct mode"
        )
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        hideCover()
        return super.onUnbind(intent)
    }

    private fun openWallet() {
        walletLaunchedAt = SystemClock.uptimeMillis()
        WalletLauncher.launch(this)
    }

    private fun showCover() {
        // Never leave the screen black: the cover always comes down after a while.
        handler.removeCallbacks(hideCover)
        handler.postDelayed(hideCover, COVER_MAX_MS)
        if (cover != null) return
        val view = View(this).apply { setBackgroundColor(Color.BLACK) }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.OPAQUE,
        )
        try {
            getSystemService(WindowManager::class.java).addView(view, params)
            cover = view
        } catch (e: RuntimeException) {
            EventLog.add("cover failed: ${e.message}")
        }
    }

    private fun hideCover() {
        val view = cover ?: return
        cover = null
        try {
            getSystemService(WindowManager::class.java).removeView(view)
        } catch (e: RuntimeException) {
            EventLog.add("uncover failed: ${e.message}")
        }
    }

    private fun isSamsungWallet(pkg: String): Boolean =
        pkg.startsWith("com.samsung.") && (pkg.contains("pay") || pkg.contains("wallet"))

    private companion object {
        val BACK_KEYS = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_STEM_PRIMARY)
        val PICKER_PACKAGES = setOf(
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
        val PASSTHROUGH_PACKAGES = setOf("com.samsung.android.wear.shealth")
        const val LONG_PRESS_MS = 500L
        const val LATE_LOG_MS = 100L
        const val MAX_LATE_MS = 2000L
        const val COVER_AFTER_MS = 400L
        const val COVER_MAX_MS = 3000L
        const val UNCOVER_DELAY_MS = 150L
        const val FIRMWARE_WINDOW_MS = 3000L
        const val SETTLE_MS = 250L
        const val AFTER_BACK_DELAY_MS = 200L
    }
}
