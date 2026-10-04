package com.millquarterlabs.backbuttonmapper

import android.content.Context
import android.content.Intent
import android.widget.Toast

object WalletLauncher {
    const val GOOGLE_WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"

    /** Starts Google Wallet; returns false (and shows a toast) when it isn't installed. */
    fun launch(context: Context): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(GOOGLE_WALLET_PACKAGE)
        if (intent == null) {
            Toast.makeText(context, R.string.wallet_missing, Toast.LENGTH_SHORT).show()
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
        return true
    }
}
