package com.eversorhn.laun.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * A launcher's whole job is to be available instantly, all the time — unlike an ordinary app, it
 * costs nothing while sitting idle, and getting killed by Doze/OEM background-app management and
 * fully reloaded (a fresh installed-app enumeration) on every return actually costs more battery
 * than just staying resident. Pie Launcher (github.com/markusfisch/PieLauncher) requests the same
 * exemption for the same documented reason — this is that.
 */
object BatteryOptimization {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestDisable(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:${context.packageName}")
        context.startActivity(intent)
    }
}
