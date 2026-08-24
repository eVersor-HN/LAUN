package com.eversorhn.laun.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "launcher_prefs")

/** Ring totals for a symmetric hex-of-hexes: 1 (center), then full rings of 6, 12, 18, 24, 30. */
val RING_COUNTS = listOf(1, 7, 19, 37, 61, 91)

data class LauncherSettings(
    val hexSizeDp: Int = 118,
    val hexCountIndex: Int = 3,
    val hudVisible: Boolean = true,
    val immersiveEnabled: Boolean = true,
    val tileColors: Map<String, String> = emptyMap()
) {
    val hexCount: Int get() = RING_COUNTS[hexCountIndex.coerceIn(RING_COUNTS.indices)]
}

/**
 * Persists everything that was reset on every page reload in the demo.html prototype:
 * tile size/count, HUD/immersive toggles, and per-app tile colors (keyed by package name,
 * not index — an index isn't a stable identity once the app list is the real installed set).
 */
class LauncherPrefs(private val context: Context) {

    private object Keys {
        val HEX_SIZE = intPreferencesKey("hex_size_dp")
        val HEX_COUNT_INDEX = intPreferencesKey("hex_count_index")
        val HUD_VISIBLE = booleanPreferencesKey("hud_visible")
        val IMMERSIVE_ENABLED = booleanPreferencesKey("immersive_enabled")
        val TILE_COLORS = stringSetPreferencesKey("tile_colors")
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
        LauncherSettings(
            hexSizeDp = prefs[Keys.HEX_SIZE] ?: 118,
            hexCountIndex = prefs[Keys.HEX_COUNT_INDEX] ?: 3,
            hudVisible = prefs[Keys.HUD_VISIBLE] ?: true,
            immersiveEnabled = prefs[Keys.IMMERSIVE_ENABLED] ?: true,
            tileColors = (prefs[Keys.TILE_COLORS] ?: emptySet())
                .mapNotNull { entry ->
                    val i = entry.indexOf('|')
                    if (i < 0) null else entry.substring(0, i) to entry.substring(i + 1)
                }
                .toMap()
        )
    }

    suspend fun setHexSize(dp: Int) {
        context.dataStore.edit { it[Keys.HEX_SIZE] = dp }
    }

    suspend fun setHexCountIndex(index: Int) {
        context.dataStore.edit { it[Keys.HEX_COUNT_INDEX] = index.coerceIn(RING_COUNTS.indices) }
    }

    suspend fun setHudVisible(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_VISIBLE] = visible }
    }

    suspend fun setImmersiveEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IMMERSIVE_ENABLED] = enabled }
    }

    suspend fun setTileColor(packageName: String, colorHex: String?) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.TILE_COLORS] ?: emptySet())
                .filterNot { it.startsWith("$packageName|") }
                .toMutableSet()
            if (colorHex != null) current += "$packageName|$colorHex"
            prefs[Keys.TILE_COLORS] = current
        }
    }
}
