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

/** Sane upper bound on tile count — a full 5-ring hex-of-hexes, more than fits any phone screen. */
const val MAX_HEX_COUNT = 91

data class LauncherSettings(
    val hexSizeDp: Int = 100,
    /** Exact tile count, not snapped to symmetric ring totals — how many actually fit at the
     *  current size is the user's call; HexGrid's own capacity math + auto-shrink is the only
     *  backstop against a combination that doesn't fit the screen. */
    val hexCount: Int = 37,
    val hudVisible: Boolean = true,
    val hudShowStatus: Boolean = true,
    val hudShowClock: Boolean = true,
    val hudShowClockMillis: Boolean = false,
    val hudShowBattery: Boolean = true,
    val hudShowBatteryPercent: Boolean = false,
    val hudShowSignal: Boolean = true,
    val hudShowWifi: Boolean = false,
    val hudShowBluetooth: Boolean = false,
    val hudShowCursor: Boolean = true,
    val immersiveEnabled: Boolean = true,
    val showAppIcons: Boolean = false,
    /** Icon size as a percent of tile width — clamped to 20..75 wherever it's actually drawn, so
     *  there's always a margin inside the tile the icon can't cross. */
    val iconSizePercent: Int = 55,
    /** -1 = no reveal animation (tiles snap instantly); otherwise an index into [REVEAL_ANIMATIONS]. */
    val revealAnimation: Int = 0,
    val animationSpeed: Int = 50,
    val showWallpaper: Boolean = false,
    /** -1 = no animated background; otherwise an index into [BACKGROUND_ANIMATIONS]. Mutually
     *  exclusive with [showWallpaper] — LauncherScreen prefers this over the real wallpaper. */
    val backgroundAnimation: Int = -1,
    /** 0..100 — applies to whichever background is active (animated concept or real wallpaper). */
    val backgroundOpacity: Int = 100,
    val hasShownDefaultLauncherHint: Boolean = false,
    val hasShownGestureHint: Boolean = false,
    val tileColors: Map<String, String> = emptyMap(),
    /** One or more packages per slot — one is a normal tile, more than one is a folder tile. */
    val slotApps: Map<Int, List<String>> = emptyMap()
)

/** Selectable tile reveal/close animations — index into this list is what's persisted. */
val REVEAL_ANIMATIONS = listOf("VOLTAGE SURGE", "SIGNAL LOCK-ON", "DATA PACKET PING", "SERVO LOCK ROTATE", "QUANTUM FLICKER")

/** Selectable animated backgrounds, rendered behind the grid — index into this list is persisted. */
val BACKGROUND_ANIMATIONS = listOf(
    "NEURO LINKS", "CIRCUIT TRACE", "WARP TUNNEL", "SERVER GRID",
    "THREAT PING MAP", "SHARD DRIFT", "CIPHER SCROLL", "STARFIELD DRIFT"
)

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
        val HEX_COUNT = intPreferencesKey("hex_count")
        val HUD_VISIBLE = booleanPreferencesKey("hud_visible")
        val HUD_SHOW_STATUS = booleanPreferencesKey("hud_show_status")
        val HUD_SHOW_CLOCK = booleanPreferencesKey("hud_show_clock")
        val HUD_SHOW_CLOCK_MILLIS = booleanPreferencesKey("hud_show_clock_millis")
        val HUD_SHOW_BATTERY = booleanPreferencesKey("hud_show_battery")
        val HUD_SHOW_BATTERY_PERCENT = booleanPreferencesKey("hud_show_battery_percent")
        val HUD_SHOW_SIGNAL = booleanPreferencesKey("hud_show_signal")
        val HUD_SHOW_WIFI = booleanPreferencesKey("hud_show_wifi")
        val HUD_SHOW_BLUETOOTH = booleanPreferencesKey("hud_show_bluetooth")
        val HUD_SHOW_CURSOR = booleanPreferencesKey("hud_show_cursor")
        val IMMERSIVE_ENABLED = booleanPreferencesKey("immersive_enabled")
        val SHOW_APP_ICONS = booleanPreferencesKey("show_app_icons")
        val ICON_SIZE_PERCENT = intPreferencesKey("icon_size_percent")
        val REVEAL_ANIMATION = intPreferencesKey("reveal_animation")
        val ANIMATION_SPEED = intPreferencesKey("animation_speed")
        val SHOW_WALLPAPER = booleanPreferencesKey("show_wallpaper")
        val BACKGROUND_ANIMATION = intPreferencesKey("background_animation")
        val BACKGROUND_OPACITY = intPreferencesKey("background_opacity")
        val HAS_SHOWN_DEFAULT_LAUNCHER_HINT = booleanPreferencesKey("has_shown_default_launcher_hint")
        val HAS_SHOWN_GESTURE_HINT = booleanPreferencesKey("has_shown_gesture_hint")
        val TILE_COLORS = stringSetPreferencesKey("tile_colors")
        val SLOT_APPS = stringSetPreferencesKey("slot_apps")
    }

    val settings: Flow<LauncherSettings> = context.dataStore.data.map { prefs ->
        LauncherSettings(
            hexSizeDp = prefs[Keys.HEX_SIZE] ?: 100,
            hexCount = (prefs[Keys.HEX_COUNT] ?: 37).coerceIn(1, MAX_HEX_COUNT),
            hudVisible = prefs[Keys.HUD_VISIBLE] ?: true,
            hudShowStatus = prefs[Keys.HUD_SHOW_STATUS] ?: true,
            hudShowClock = prefs[Keys.HUD_SHOW_CLOCK] ?: true,
            hudShowClockMillis = prefs[Keys.HUD_SHOW_CLOCK_MILLIS] ?: false,
            hudShowBattery = prefs[Keys.HUD_SHOW_BATTERY] ?: true,
            hudShowBatteryPercent = prefs[Keys.HUD_SHOW_BATTERY_PERCENT] ?: false,
            hudShowSignal = prefs[Keys.HUD_SHOW_SIGNAL] ?: true,
            hudShowWifi = prefs[Keys.HUD_SHOW_WIFI] ?: false,
            hudShowBluetooth = prefs[Keys.HUD_SHOW_BLUETOOTH] ?: false,
            hudShowCursor = prefs[Keys.HUD_SHOW_CURSOR] ?: true,
            immersiveEnabled = prefs[Keys.IMMERSIVE_ENABLED] ?: true,
            showAppIcons = prefs[Keys.SHOW_APP_ICONS] ?: false,
            iconSizePercent = (prefs[Keys.ICON_SIZE_PERCENT] ?: 55).coerceIn(20, 75),
            revealAnimation = (prefs[Keys.REVEAL_ANIMATION] ?: 0).let {
                if (it == -1 || it in REVEAL_ANIMATIONS.indices) it else 0
            },
            animationSpeed = (prefs[Keys.ANIMATION_SPEED] ?: 50).coerceIn(0, 100),
            showWallpaper = prefs[Keys.SHOW_WALLPAPER] ?: false,
            backgroundAnimation = (prefs[Keys.BACKGROUND_ANIMATION] ?: -1).let {
                if (it in BACKGROUND_ANIMATIONS.indices) it else -1
            },
            backgroundOpacity = (prefs[Keys.BACKGROUND_OPACITY] ?: 100).coerceIn(0, 100),
            hasShownDefaultLauncherHint = prefs[Keys.HAS_SHOWN_DEFAULT_LAUNCHER_HINT] ?: false,
            hasShownGestureHint = prefs[Keys.HAS_SHOWN_GESTURE_HINT] ?: false,
            tileColors = (prefs[Keys.TILE_COLORS] ?: emptySet())
                .mapNotNull { entry ->
                    val i = entry.indexOf('|')
                    if (i < 0) null else entry.substring(0, i) to entry.substring(i + 1)
                }
                .toMap(),
            // Multiple entries sharing the same slot index naturally become that slot's folder —
            // no separate storage format needed for "more than one app in a slot".
            slotApps = (prefs[Keys.SLOT_APPS] ?: emptySet())
                .mapNotNull { entry ->
                    val i = entry.indexOf('|')
                    if (i < 0) null else entry.substring(0, i).toIntOrNull()?.let { it to entry.substring(i + 1) }
                }
                .groupBy({ it.first }, { it.second })
        )
    }

    suspend fun setHexSize(dp: Int) {
        context.dataStore.edit { it[Keys.HEX_SIZE] = dp }
    }

    suspend fun setHexCount(count: Int) {
        context.dataStore.edit { it[Keys.HEX_COUNT] = count.coerceIn(1, MAX_HEX_COUNT) }
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

    suspend fun setHudShowClockMillis(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_SHOW_CLOCK_MILLIS] = visible }
    }

    suspend fun setHudShowBattery(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_SHOW_BATTERY] = visible }
    }

    suspend fun setHudShowBatteryPercent(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_SHOW_BATTERY_PERCENT] = visible }
    }

    suspend fun setHudShowSignal(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_SHOW_SIGNAL] = visible }
    }

    suspend fun setHudShowWifi(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_SHOW_WIFI] = visible }
    }

    suspend fun setHudShowBluetooth(visible: Boolean) {
        context.dataStore.edit { it[Keys.HUD_SHOW_BLUETOOTH] = visible }
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

    suspend fun setIconSizePercent(percent: Int) {
        context.dataStore.edit { it[Keys.ICON_SIZE_PERCENT] = percent.coerceIn(20, 75) }
    }

    suspend fun setRevealAnimation(index: Int) {
        context.dataStore.edit { it[Keys.REVEAL_ANIMATION] = index.coerceIn(-1, REVEAL_ANIMATIONS.size - 1) }
    }

    suspend fun setAnimationSpeed(speed: Int) {
        context.dataStore.edit { it[Keys.ANIMATION_SPEED] = speed.coerceIn(0, 100) }
    }

    suspend fun setShowWallpaper(show: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SHOW_WALLPAPER] = show
            if (show) prefs[Keys.BACKGROUND_ANIMATION] = -1
        }
    }

    /** -1 clears the animated background; also turns off the real wallpaper (mutually exclusive). */
    suspend fun setBackgroundAnimation(index: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BACKGROUND_ANIMATION] = index.coerceIn(-1, BACKGROUND_ANIMATIONS.size - 1)
            if (index >= 0) prefs[Keys.SHOW_WALLPAPER] = false
        }
    }

    suspend fun setBackgroundOpacity(percent: Int) {
        context.dataStore.edit { it[Keys.BACKGROUND_OPACITY] = percent.coerceIn(0, 100) }
    }

    suspend fun setHasShownDefaultLauncherHint(shown: Boolean) {
        context.dataStore.edit { it[Keys.HAS_SHOWN_DEFAULT_LAUNCHER_HINT] = shown }
    }

    suspend fun setHasShownGestureHint(shown: Boolean) {
        context.dataStore.edit { it[Keys.HAS_SHOWN_GESTURE_HINT] = shown }
    }

    /**
     * Resets the settings-panel controls (grid, appearance, animation, HUD, system toggles) back
     * to their defaults. Deliberately leaves slotApps/tileColors/onboarding-hint flags alone —
     * this is "undo my settings fiddling," not "wipe my home screen."
     */
    suspend fun resetSettings() {
        val d = LauncherSettings()
        context.dataStore.edit { prefs ->
            prefs[Keys.HEX_SIZE] = d.hexSizeDp
            prefs[Keys.HEX_COUNT] = d.hexCount
            prefs[Keys.HUD_VISIBLE] = d.hudVisible
            prefs[Keys.HUD_SHOW_STATUS] = d.hudShowStatus
            prefs[Keys.HUD_SHOW_CLOCK] = d.hudShowClock
            prefs[Keys.HUD_SHOW_CLOCK_MILLIS] = d.hudShowClockMillis
            prefs[Keys.HUD_SHOW_BATTERY] = d.hudShowBattery
            prefs[Keys.HUD_SHOW_BATTERY_PERCENT] = d.hudShowBatteryPercent
            prefs[Keys.HUD_SHOW_SIGNAL] = d.hudShowSignal
            prefs[Keys.HUD_SHOW_WIFI] = d.hudShowWifi
            prefs[Keys.HUD_SHOW_BLUETOOTH] = d.hudShowBluetooth
            prefs[Keys.HUD_SHOW_CURSOR] = d.hudShowCursor
            prefs[Keys.IMMERSIVE_ENABLED] = d.immersiveEnabled
            prefs[Keys.SHOW_APP_ICONS] = d.showAppIcons
            prefs[Keys.ICON_SIZE_PERCENT] = d.iconSizePercent
            prefs[Keys.REVEAL_ANIMATION] = d.revealAnimation
            prefs[Keys.ANIMATION_SPEED] = d.animationSpeed
            prefs[Keys.SHOW_WALLPAPER] = d.showWallpaper
            prefs[Keys.BACKGROUND_ANIMATION] = d.backgroundAnimation
            prefs[Keys.BACKGROUND_OPACITY] = d.backgroundOpacity
        }
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

    /** Replaces the full set of apps occupying a grid slot — an empty list clears it. */
    suspend fun setSlotApps(slotIndex: Int, packageNames: List<String>) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.SLOT_APPS] ?: emptySet())
                .filterNot { it.startsWith("$slotIndex|") }
                .toMutableSet()
            packageNames.forEach { pkg -> current += "$slotIndex|$pkg" }
            prefs[Keys.SLOT_APPS] = current
        }
    }
}
