package dev.mago.android.termux

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class TermuxPermissionInspector(private val context: Context) {
    fun isRunCommandGranted(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            TermuxRunCommandContract.PERMISSION_RUN_COMMAND,
        ) == PackageManager.PERMISSION_GRANTED
}
