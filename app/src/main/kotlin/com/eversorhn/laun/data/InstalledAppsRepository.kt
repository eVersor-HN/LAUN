package com.eversorhn.laun.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 28dp tile icon at up to xxxhdpi (~4x) plus headroom — plenty for a sharp render. */
private const val ICON_SIZE_PX = 128

/**
 * Real installed-app list, replacing demo.html's placeholder APPS array.
 */
class InstalledAppsRepository(private val context: Context) {

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        resolveInfos
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                AppInfo(
                    packageName = info.activityInfo.packageName,
                    label = info.loadLabel(pm).toString(),
                    // Tiles only ever render this at 28dp — decoding at native adaptive-icon
                    // resolution (often 300px+) wastes memory across every installed app, not
                    // just the ones assigned to a slot.
                    icon = info.loadIcon(pm).toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX).asImageBitmap()
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
