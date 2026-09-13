package com.eversorhn.laun.data

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

        val unique = resolveInfos.distinctBy { it.activityInfo.packageName }
        // Label + icon loading is the slow part of cold start (a PackageManager round-trip plus an
        // adaptive-icon rasterize per app, ~150+ apps on a typical phone) and the grid stays blank
        // until it finishes. The per-app work is independent, so it's fanned out across
        // Dispatchers.Default's worker threads in chunks instead of run strictly one app at a time
        // on a single coroutine — the wall-clock time drops roughly by the core count.
        val chunkSize = maxOf(8, unique.size / (Runtime.getRuntime().availableProcessors() * 2).coerceAtLeast(1))
        coroutineScope {
            unique.chunked(chunkSize).map { chunk ->
                async {
                    chunk.map { info ->
                        AppInfo(
                            packageName = info.activityInfo.packageName,
                            label = info.loadLabel(pm).toString(),
                            // Tiles only ever render this at 28dp — decoding at native adaptive-icon
                            // resolution (often 300px+) wastes memory across every installed app, not
                            // just the ones assigned to a slot.
                            icon = info.loadIcon(pm).toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX).asImageBitmap(),
                            activityName = info.activityInfo.name
                        )
                    }
                }
            }.awaitAll()
        }
            .flatten()
            .sortedBy { it.label.lowercase() }
    }

    /** Builds the launch Intent directly from the already-resolved component instead of asking
     *  PackageManager to re-resolve it (getLaunchIntentForPackage) on every single tap — that's a
     *  synchronous binder call to system_server sitting right in front of startActivity(), on the
     *  exact path where launch latency is most noticeable. Falls back to the PM lookup only if
     *  activityName wasn't captured (shouldn't happen for anything loaded via loadApps()). */
    fun launch(app: AppInfo) {
        val intent = if (app.activityName.isNotEmpty()) {
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = android.content.ComponentName(app.packageName, app.activityName)
            }
        } else {
            context.packageManager.getLaunchIntentForPackage(app.packageName) ?: return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Skips Android's default ~300ms cross-fade/zoom open-app transition — on top of the
        // target app's own cold-start time, that's pure added latency between the tap and
        // something useful appearing, and this launcher has no "old screen" worth animating away
        // from anyway.
        val options = ActivityOptions.makeCustomAnimation(context, 0, 0)
        context.startActivity(intent, options.toBundle())
    }
}
