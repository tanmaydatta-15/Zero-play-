package com.example.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * Handles PackageInstaller session commit callbacks so MODE_FULL_INSTALL can smoothly
 * launch the system confirmation sheet when required and log install status.
 */
class PackageInstallResultReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL_RESULT = "com.example.updater.ACTION_PACKAGE_INSTALL_RESULT"
        private const val TAG = "ZeroPlayInstaller"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirmIntent) }
                        .onFailure { Log.e(TAG, "Failed to launch PackageInstaller confirmation", it) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Zero Play OTA update installed successfully")
            }
            else -> {
                Log.w(TAG, "PackageInstaller status=$status message=$message")
            }
        }
    }
}
