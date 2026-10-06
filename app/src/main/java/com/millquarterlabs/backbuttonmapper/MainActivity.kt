package com.millquarterlabs.backbuttonmapper

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView

/** Shows whether the service is on and links to the system setting that turns it on. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.open_settings).setOnClickListener { openAccessibilitySettings() }
        findViewById<Button>(R.id.test_wallet).setOnClickListener { WalletLauncher.launch(this) }
        findViewById<Button>(R.id.refresh_log).setOnClickListener { showLog() }
        findViewById<Button>(R.id.battery).setOnClickListener { requestBatteryExemption() }
    }

    override fun onResume() {
        super.onResume()
        if (isServiceEnabled()) KeepAliveService.start(this)
        val status = getString(if (isServiceEnabled()) R.string.status_on else R.string.status_off)
        val mode = getString(
            if (SamsungWallet.isActive(this)) R.string.mode_redirect else R.string.mode_direct
        )
        val keepAlive = getString(
            if (KeepAliveService.running) R.string.keep_alive_on else R.string.keep_alive_off
        )
        val unrestricted = isIgnoringBatteryOptimizations()
        val battery = getString(
            if (unrestricted) R.string.battery_unrestricted else R.string.battery_restricted
        )
        findViewById<TextView>(R.id.status).text = "$status\n\n$mode\n\n$keepAlive\n$battery"
        findViewById<Button>(R.id.battery).visibility =
            if (unrestricted) android.view.View.GONE else android.view.View.VISIBLE
        showLog()
    }

    private fun showLog() {
        findViewById<TextView>(R.id.log).text = EventLog.dump()
    }

    private fun isIgnoringBatteryOptimizations(): Boolean =
        getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

    private fun requestBatteryExemption() {
        // Exempting the app from battery optimization keeps the watch from pausing it in the
        // background. Some watch builds have no dialog for this; then use the adb command
        // from the README.
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:$packageName"),
        )
        try {
            startActivity(intent)
        } catch (e: RuntimeException) {
            EventLog.add("battery dialog unavailable: ${e.javaClass.simpleName}")
            showLog()
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        // Some watch builds don't expose the accessibility screen directly; fall back to Settings.
        startActivity(
            if (intent.resolveActivity(packageManager) != null) intent
            else Intent(Settings.ACTION_SETTINGS)
        )
    }

    private fun isServiceEnabled(): Boolean {
        val me = ComponentName(this, BackButtonService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }
        return splitter.any { ComponentName.unflattenFromString(it) == me }
    }
}
