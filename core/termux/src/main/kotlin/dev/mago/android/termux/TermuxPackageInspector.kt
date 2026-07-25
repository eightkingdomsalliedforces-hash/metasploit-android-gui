package dev.mago.android.termux

import android.content.Context
import android.content.pm.PackageManager

class TermuxPackageInspector(private val context: Context) {
    @Suppress("DEPRECATION")
    fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(TermuxRunCommandContract.PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
