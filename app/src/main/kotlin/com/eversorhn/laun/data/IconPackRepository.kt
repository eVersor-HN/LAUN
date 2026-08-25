package com.eversorhn.laun.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

/** Same render size as [InstalledAppsRepository]'s own icons — both end up on the same tile. */
private const val ICON_SIZE_PX = 128

data class IconPackInfo(val packageName: String, val label: String)

data class IconPackEntry(val drawableName: String, val category: String)

/**
 * Reads icon packs installed as separate apps — the same "org.adw.launcher.THEMES" convention
 * every icon-pack app on the Play Store already registers for (Nova, Lawnchair, and most other
 * launchers all discover packs the same way), so this works with whatever the user already has
 * installed, no pack-specific code needed. Each pack ships two plain-text XML assets:
 * `assets/appfilter.xml` (which drawable a known app maps to) and `assets/drawable.xml` (the
 * pack's full browsable catalogue, grouped into categories) — both readable directly out of the
 * pack's own `assets/` via a cross-app [Context], no binary-XML-resource parsing needed.
 */
class IconPackRepository(private val context: Context) {

    fun listInstalledPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val intent = Intent("org.adw.launcher.THEMES").addCategory(Intent.CATEGORY_DEFAULT)
        val infos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return infos
            .distinctBy { it.activityInfo.packageName }
            .map { IconPackInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    /** The pack's full browsable catalogue — every icon it ships, not just ones matched to an
     *  installed app. Falls back to the (unique, uncategorized) drawable names out of appfilter.xml
     *  if a pack has no separate drawable.xml, which some smaller packs skip. */
    suspend fun loadCatalog(iconPackPackage: String): List<IconPackEntry> = withContext(Dispatchers.IO) {
        val fromDrawableXml = runCatching {
            parseDrawableXml(openPackAsset(iconPackPackage, "drawable.xml") ?: return@runCatching emptyList())
        }.getOrDefault(emptyList())
        if (fromDrawableXml.isNotEmpty()) return@withContext fromDrawableXml

        runCatching {
            parseAppFilterDrawables(openPackAsset(iconPackPackage, "appfilter.xml") ?: return@runCatching emptyList())
        }.getOrDefault(emptyList())
    }

    /** Maps an installed app's package name to the drawable the pack itself wants for it, per its
     *  own appfilter.xml — used to suggest a match before the user browses the full catalogue. */
    suspend fun loadAppFilterMap(iconPackPackage: String): Map<String, String> = withContext(Dispatchers.IO) {
        runCatching {
            parseAppFilterMap(openPackAsset(iconPackPackage, "appfilter.xml") ?: return@runCatching emptyMap())
        }.getOrDefault(emptyMap())
    }

    suspend fun loadIcon(iconPackPackage: String, drawableName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val pkgContext = context.createPackageContext(iconPackPackage, 0)
            val id = pkgContext.resources.getIdentifier(drawableName, "drawable", iconPackPackage)
            if (id == 0) return@runCatching null
            androidx.core.content.ContextCompat.getDrawable(pkgContext, id)
                ?.toBitmap(width = ICON_SIZE_PX, height = ICON_SIZE_PX)
                ?.asImageBitmap()
        }.getOrNull()
    }

    private fun openPackAsset(iconPackPackage: String, assetName: String): java.io.InputStream? =
        runCatching { context.createPackageContext(iconPackPackage, 0).assets.open(assetName) }.getOrNull()

    private fun parseDrawableXml(input: java.io.InputStream): List<IconPackEntry> = input.use { stream ->
        val entries = mutableListOf<IconPackEntry>()
        var currentCategory = ""
        val parser = android.util.Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "category" -> currentCategory = parser.getAttributeValue(null, "title") ?: ""
                    "item" -> {
                        val drawable = parser.getAttributeValue(null, "drawable")
                        if (!drawable.isNullOrBlank()) entries += IconPackEntry(drawable, currentCategory)
                    }
                }
            }
            event = parser.next()
        }
        entries
    }

    private fun parseAppFilterDrawables(input: java.io.InputStream): List<IconPackEntry> = input.use { stream ->
        val seen = LinkedHashSet<String>()
        val parser = android.util.Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val drawable = parser.getAttributeValue(null, "drawable")
                if (!drawable.isNullOrBlank()) seen += drawable
            }
            event = parser.next()
        }
        seen.map { IconPackEntry(it, "") }
    }

    /** component="ComponentInfo{pkg/activity}" — only the package name before the slash matters
     *  for matching against an installed app; the first entry for a package wins. */
    private fun parseAppFilterMap(input: java.io.InputStream): Map<String, String> = input.use { stream ->
        val map = LinkedHashMap<String, String>()
        val parser = android.util.Xml.newPullParser()
        parser.setInput(stream, "UTF-8")
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                val pkg = component?.substringAfter("ComponentInfo{")?.substringBefore("/")
                if (!pkg.isNullOrBlank() && !drawable.isNullOrBlank() && pkg !in map) map[pkg] = drawable
            }
            event = parser.next()
        }
        map
    }
}
