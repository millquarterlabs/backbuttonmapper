package com.millquarterlabs.backbuttonmapper

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Sends a long press of the Back (lower) key to Google Wallet.
 *
 * On the Galaxy Watch Ultra 2 the lower key arrives as KEYCODE_STEM_PRIMARY, and the firmware acts
 * on its long press before accessibility services can stop it: it always opens something, either
 * Samsung Wallet (often several windows in a row) or Android's "Default wallet app" picker. So the
 * service doesn't touch the key at all; it waits for that burst of windows to settle and then
 * brings Google Wallet to the front, once.
 */
class BackButtonService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private var keyDownAt = 0L
    private var lastFirmwareScreenAt = 0L
    private var pickerSeen = false

    private val redirect = Runnable {
        if (pickerSeen) {
            // The picker is a plain settings screen; close it so it isn't left behind Wallet.
            pickerSeen = false
            EventLog.add("-> close picker, Google Wallet")
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed(launchWallet, AFTER_BACK_DELAY_MS)
        } else {
            EventLog.add("-> Google Wallet")
            WalletLauncher.launch(this)
        }
    }

    private val launchWallet = Runnable { WalletLauncher.launch(this) }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode !in BACK_KEYS || event.repeatCount > 0) return false
        val down = event.action == KeyEvent.ACTION_DOWN
        EventLog.add("key ${KeyEvent.keyCodeToString(event.keyCode)} ${if (down) "down" else "up"}")
        val now = SystemClock.uptimeMillis()
        if (down) {
            keyDownAt = now
        } else if (now - keyDownAt >= LONG_PRESS_MS && lastFirmwareScreenAt < keyDownAt) {
            // Long press but the firmware showed nothing (yet): open Wallet ourselves.
            EventLog.add("long press, no firmware screen -> Google Wallet")
            WalletLauncher.launch(this)
        }
        return false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        EventLog.add("window $pkg / ${event.className}")

        val now = SystemClock.uptimeMillis()
        val isPicker = pkg in PICKER_PACKAGES && now - keyDownAt < FIRMWARE_WINDOW_MS
        if (!isSamsungWallet(pkg) && !isPicker) return

        lastFirmwareScreenAt = now
        if (isPicker) pickerSeen = true
        // Samsung Wallet opens several windows in a row; restart the timer on each one so we only
        // switch to Google Wallet after the last of them.
        handler.removeCallbacks(redirect)
        handler.removeCallbacks(launchWallet)
        handler.postDelayed(redirect, SETTLE_MS)
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

    private companion object {
        val BACK_KEYS = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_STEM_PRIMARY)
        val PICKER_PACKAGES = setOf(
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
        const val LONG_PRESS_MS = 500L
        const val FIRMWARE_WINDOW_MS = 3000L
        const val SETTLE_MS = 250L
        const val AFTER_BACK_DELAY_MS = 200L
    }
}
