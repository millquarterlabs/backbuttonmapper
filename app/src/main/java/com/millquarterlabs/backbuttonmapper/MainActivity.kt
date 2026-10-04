package com.millquarterlabs.backbuttonmapper

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
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
    }

    override fun onResume() {
        super.onResume()
        findViewById<TextView>(R.id.status).setText(
            if (isServiceEnabled()) R.string.status_on else R.string.status_off
        )
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
