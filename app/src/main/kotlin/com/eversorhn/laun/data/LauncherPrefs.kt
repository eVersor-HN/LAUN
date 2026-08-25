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
    val hudShowStatus: Boolean = true,
    val hudShowClock: Boolean = true,
    val hudShowBattery: Boolean = true,
    val hudShowSignal: Boolean = true,
    val hudShowAppCount: Boolean = true,
    val hudShowCursor: Boolean = true,
    val immersiveEnabled: Boolean = true,
    val showAppIcons: Boolean = false,
    val revealAnimation: Int = 0,
    val tileColors: Map<String, String> = emptyMap(),
    val slotApps: Map<Int, String> = emptyMap()
) {
    val hexCount: Int get() = RING_COUNTS[hexCountIndex.coerceIn(RING_COUNTS.indices)]
}

/** Selectable tile reveal/close animations — index into this list is what's persisted. */
val REVEAL_ANIMATIONS = listOf("HEX IRIS", "RADIAL PULSE")

/**
 * Persists everything that was reset on every page reload in the demo.html prototype:
 * tile size/count, HUD/immersive toggles, per-app tile colors (keyed by package name, not
 * index — an index isn't a stable identity once the app list is the real installed set), and
 * which app (if any) is assigned to each grid slot (keyed by slot index — slots are fixed
 * positions in the honeycomb, independent of which app currently occupies them).
 */
class LauncherPrefs(private val context: Context) {

    private object Keys {
        val HEX_SIZE = intPreferencesKey("hex_size_dp")
        val HEX_COUNT_INDEX = intPreferencesKey("hex_count_index")
        val HUD_VISIBLE = booleanPreferencesKey("hud_visible")
        val HUD_SHOW_STATUS = booleanPreferencesKey("hud_show_status")
        val HUD_SHOW_CLOCK = booleanPreferencesKey("hud_show_clock")
        val HUD_SHOW_BATTERY = booleanPreferencesKey("hud_show_battery")
        val HUD_SHOW_SIGNAL = booleanPreferencesKey("hud_show_signal")
        val HUD_SHOW_APP_COUNT = booleanPreferencesKey("hud_show_app_count")
        val HUD_SHOW_CURSOR = booleanPreferencesKey("hud_show_cursor")
        val IMMERSIVE_ENABLED = booleanPreferencesKey("immersive_enabled")
        val SHOW_APP_ICONS = booleanPreferencesKey("show_app_icons")
        val REVEAL_ANIMATION = intPreferencesKey("reveal_animation")
        val TILE_COLORS = stringSetPreferencesKey("tile_colors")
        val SLOT_APPS = stringSetPreferencesKey("slot_apps")
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
        LauncherSettings(
            hexSizeDp = prefs[Keys.HEX_SIZE] ?: 118,
            hexCountIndex = prefs[Keys.HEX_COUNT_INDEX] ?: 3,
            hudVisible = prefs[Keys.HUD_VISIBLE] ?: true,
            hudShowStatus = prefs[Keys.HUD_SHOW_STATUS] ?: true,
            hudShowClock = prefs[Keys.HUD_SHOW_CLOCK] ?: true,
            hudShowBattery = prefs[Keys.HUD_SHOW_BATTERY] ?: true,
            hudShowSignal = prefs[Keys.HUD_SHOW_SIGNAL] ?: true,
            hudShowAppCount = prefs[Keys.HUD_SHOW_APP_COUNT] ?: true,
            hudShowCursor = prefs[Keys.HUD_SHOW_CURSOR] ?: true,
            immersiveEnabled = prefs[Keys.IMMERSIVE_ENABLED] ?: true,
            showAppIcons = prefs[Keys.SHOW_APP_ICONS] ?: false,
            revealAnimation = (prefs[Keys.REVEAL_ANIMATION] ?: 0).coerceIn(REVEAL_ANIMATIONS.indices),
            tileColors = (prefs[Keys.TILE_COLORS] ?: emptySet())
                .mapNotNull { entry ->
                    val i = entry.indexOf('|')
                    if (i < 0) null else entry.substring(0, i) to entry.substring(i + 1)
                }
                .toMap(),
            slotApps = (prefs[Keys.SLOT_APPS] ?: emptySet())
                .mapNotNull { entry ->
                    val i = entry.indexOf('|')
                    if (i < 0) null else entry.substring(0, i).toIntOrNull()?.let { it to entry.substring(i + 1) }
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

    suspend fun setHudShowStatus(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_SHOW_STATUS] = visible }
    }

    suspend fun setHudShowClock(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_SHOW_CLOCK] = visible }
    }

    suspend fun setHudShowBattery(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_SHOW_BATTERY] = visible }
    }

    suspend fun setHudShowSignal(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_SHOW_SIGNAL] = visible }
    }

    suspend fun setHudShowAppCount(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_SHOW_APP_COUNT] = visible }
    }

    suspend fun setHudShowCursor(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_SHOW_CURSOR] = visible }
    }

    suspend fun setImmersiveEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IMMERSIVE_ENABLED] = enabled }
    }

    suspend fun setShowAppIcons(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_APP_ICONS] = show }
    }

    suspend fun setRevealAnimation(index: Int) {
        context.dataStore.edit { it[Keys.REVEAL_ANIMATION] = index.coerceIn(REVEAL_ANIMATIONS.indices) }
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

    /** Assigns (or, with packageName = null, clears) the app occupying a grid slot. */
    suspend fun setSlotApp(slotIndex: Int, packageName: String?) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.SLOT_APPS] ?: emptySet())
                .filterNot { it.startsWith("$slotIndex|") }
                .toMutableSet()
            if (packageName != null) current += "$slotIndex|$packageName"
            prefs[Keys.SLOT_APPS] = current
        }
    }
}
