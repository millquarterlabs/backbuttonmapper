package com.millquarterlabs.backbuttonmapper

import android.content.Context
import android.content.pm.PackageManager

/** Whether Samsung Wallet's watch app is installed and enabled (it owns the Back long press). */
object SamsungWallet {
    const val PACKAGE = "com.samsung.android.samsungpay.gear"

    fun isActive(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo(PACKAGE, 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
