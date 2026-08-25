package com.eversorhn.laun.ui

import android.Manifest
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.data.InstalledAppsRepository
import com.eversorhn.laun.data.LauncherPrefs
import com.eversorhn.laun.ui.theme.LaunColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads the current system wallpaper; requires READ_EXTERNAL_STORAGE to be granted first. */
private suspend fun loadWallpaperBitmap(context: Context): ImageBitmap? =
    withContext(Dispatchers.Default) {
        try {
            WallpaperManager.getInstance(context).drawable?.toBitmap()?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

/**
 * Root screen — direct port of demo.html's #stage: an empty screen you tap to reveal the
 * honeycomb, plus the status bar, settings and color-picker chrome around it.
 *
 * The grid is a fixed set of slots (exactly [com.eversorhn.laun.data.LauncherSettings.hexCount]
 * of them, index-stable). Each slot is either empty (tap it to assign an app) or occupied
 * (tap to launch, long-press to recolor or clear it).
 */
@Composable
fun LauncherScreen(
    prefs: LauncherPrefs,
    onImmersiveEnabledChange: (Boolean) -> Unit,
    resetSignal: Int = 0
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsState by prefs.settings.collectAsState(initial = null)
    val settings = settingsState ?: com.eversorhn.laun.data.LauncherSettings()

    val repository = remember { InstalledAppsRepository(context) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    // Guards against opening the grid before the first loadApps() resolves — without it, a tap
    // in the first tens of milliseconds after cold start could momentarily render every assigned
    // tile as empty (appsByPackage still empty) before flashing to its real app once loaded.
    var appsLoaded by remember { mutableStateOf(false) }
    val reloadScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        apps = repository.loadApps()
        appsLoaded = true
    }

    // Installing/uninstalling an app while LAUN is running (the normal case — you background the
    // launcher to go install something from the Play Store) must update the app-picker and any
    // slot referencing a now-gone package without needing the launcher process to restart.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                reloadScope.launch { apps = repository.loadApps() }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    var isOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var colorPickerSlot by remember { mutableStateOf<Int?>(null) }
    var colorPickerAnchor by remember { mutableStateOf(Offset.Zero) }
    var appPickerSlot by remember { mutableStateOf<Int?>(null) }
    var folderSlot by remember { mutableStateOf<Int?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showFaq by remember { mutableStateOf(false) }
    var showLauncherHint by remember { mutableStateOf(false) }
    var showGestureHint by remember { mutableStateOf(false) }
    // Gated on settingsState (nullable) rather than settings.hasShownDefaultLauncherHint directly —
    // collectAsState's `initial` default (hasShownDefaultLauncherHint = false) would otherwise make
    // this fire on every cold start before the real, already-persisted value loads from DataStore.
    //
    // hintCheckDone guards against this effect re-running when settingsState re-emits after the
    // write below completes — without it, the gesture hint would auto-appear the instant the
    // default-launcher-hint flag finishes writing (milliseconds later), stacking both dialogs at
    // once instead of showing the gesture hint only once the user actually dismisses the first.
    // The gesture hint's own entry into that sequence is handled in DefaultLauncherHintSheet's
    // onDismiss below; this effect only covers first-ever-load and "already saw hint 1, never saw
    // hint 2" (e.g. updating from a build before the gesture hint existed).
    var hintCheckDone by remember { mutableStateOf(false) }
    LaunchedEffect(settingsState) {
        val loaded = settingsState ?: return@LaunchedEffect
        if (hintCheckDone) return@LaunchedEffect
        hintCheckDone = true
        when {
            !loaded.hasShownDefaultLauncherHint -> {
                showLauncherHint = true
                prefs.setHasShownDefaultLauncherHint(true)
            }
            !loaded.hasShownGestureHint -> {
                showGestureHint = true
                prefs.setHasShownGestureHint(true)
            }
        }
    }

    // Local, immediate copies of size/count: the slider must resize the grid live, on every
    // pixel of drag — round-tripping every change through DataStore's async Flow first would
    // lag. These drive the grid directly; the DataStore write underneath is fire-and-forget.
    var liveHexSizeDp by remember { mutableStateOf(settings.hexSizeDp) }
    var liveHexCount by remember { mutableStateOf(settings.hexCount) }
    LaunchedEffect(settings.hexSizeDp) { liveHexSizeDp = settings.hexSizeDp }
    LaunchedEffect(settings.hexCount) { liveHexCount = settings.hexCount }

    // Whether the grid actually had to shrink tiles below the requested size to keep every
    // tile on screen — tiles must never leave the screen, so this can happen at any live
    // combination of size/count; SettingsSheet shows a hint whenever it's true.
    var didShrinkToFit by remember { mutableStateOf(false) }

    BackHandler(enabled = isOpen) { isOpen = false }

    // Leaving the foreground for ANY reason (home-swipe while a sheet is open, recents, screen
    // off) must land back on a clean, closed home screen next time — otherwise a sheet that was
    // mid-gesture when the activity stopped can come back stuck, unresponsive to input.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                isOpen = false
                showSettings = false
                colorPickerSlot = null
                appPickerSlot = null
                folderSlot = null
                showAbout = false
                showFaq = false
                showLauncherHint = false
                showGestureHint = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Covers the case ON_STOP misses entirely: MainActivity is singleTask and already resumed
    // while sitting on the home screen, so a repeated "go home" gesture redelivers the HOME
    // intent via onNewIntent instead of stopping/restarting the activity — see MainActivity.
    LaunchedEffect(resetSignal) {
        if (resetSignal > 0) {
            isOpen = false
            showSettings = false
            colorPickerSlot = null
            appPickerSlot = null
            folderSlot = null
        }
    }

    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }
    val slots = remember(appsByPackage, settings.slotApps, liveHexCount) {
        (0 until liveHexCount).map { i -> (settings.slotApps[i] ?: emptyList()).mapNotNull { appsByPackage[it] } }
    }

    // Off by default — the whole look is built around pure black, so showing the system
    // wallpaper is an opt-in rather than something that silently changes the aesthetic.
    // WallpaperManager.getDrawable() enforces READ_EXTERNAL_STORAGE regardless of the app being
    // the default launcher (confirmed on-device — the documented launcher exemption doesn't
    // hold here), so turning this on requests it the first time, same as any other permission.
    // Both READ_EXTERNAL_STORAGE and READ_MEDIA_IMAGES are needed together: WallpaperManager's
    // own permission check wants the former, but a separate AppOps-level check underneath it
    // wants the latter — confirmed on-device that either one alone isn't enough.
    var wallpaperBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val wallpaperPermissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.READ_MEDIA_IMAGES)
    val wallpaperPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) reloadScope.launch { wallpaperBitmap = loadWallpaperBitmap(context) }
    }
    LaunchedEffect(settings.showWallpaper) {
        if (!settings.showWallpaper) {
            wallpaperBitmap = null
        } else if (wallpaperPermissions.all {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        ) {
            wallpaperBitmap = loadWallpaperBitmap(context)
        } else {
            wallpaperPermissionLauncher.launch(wallpaperPermissions)
        }
    }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (settings.showWallpaper) {
                    reloadScope.launch { wallpaperBitmap = loadWallpaperBitmap(context) }
                }
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { context.unregisterReceiver(receiver) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LaunColors.bg)
    ) {
        val backgroundOpacityFraction = settings.backgroundOpacity / 100f
        if (settings.backgroundAnimation >= 0) {
            AnimatedWallpaper(kind = settings.backgroundAnimation, opacity = backgroundOpacityFraction, modifier = Modifier.fillMaxSize())
        } else {
            val wallpaper = wallpaperBitmap
            if (wallpaper != null) {
                Image(
                    bitmap = wallpaper,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(backgroundOpacityFraction)
                )
                // A dark scrim keeps the terminal text/tiles readable over whatever the wallpaper is.
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
            }
        }

        HexGrid(
            slots = slots,
            hexSizeDp = liveHexSizeDp,
            tileColors = settings.tileColors,
            showIcons = settings.showAppIcons,
            iconSizePercent = settings.iconSizePercent,
            revealAnimation = settings.revealAnimation,
            animationSpeed = settings.animationSpeed,
            isOpen = isOpen,
            onOpen = { if (appsLoaded) isOpen = true },
            onCloseBackground = { isOpen = false },
            onLongPressSlot = { _, slotIndex, pos ->
                colorPickerSlot = slotIndex
                colorPickerAnchor = pos
            },
            onLongPressBackground = { showSettings = true },
            onTapEmptySlot = { slotIndex -> appPickerSlot = slotIndex },
            onLaunch = { app ->
                // Collapse before leaving, not after coming back — otherwise returning via the
                // back gesture (or task switcher) finds the grid still fully open, and its
                // predictive-back preview flashes the open grid over the launched app.
                isOpen = false
                repository.launch(app.packageName)
            },
            onOpenFolder = { slotIndex, _ -> folderSlot = slotIndex },
            onReorderSlots = { from, to ->
                val fromApps = settings.slotApps[from] ?: emptyList()
                val toApps = settings.slotApps[to] ?: emptyList()
                // Dropping onto a spot beyond the current count (one of HexGrid's "fits but not
                // active yet" cells) grows the grid to include it, same as dragging the COUNT
                // slider up by hand — the tile just landed exactly where the user put it.
                if (to >= liveHexCount) {
                    liveHexCount = to + 1
                    scope.launch { prefs.setHexCount(to + 1) }
                }
                scope.launch {
                    prefs.setSlotApps(from, toApps)
                    prefs.setSlotApps(to, fromApps)
                }
            },
            onShrinkToFitChange = { didShrinkToFit = it },
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(vertical = if (settings.hudVisible) 64.dp else 0.dp)
        )

        if (settings.hudVisible) {
            StatusBar(
                isOpen = isOpen,
                showStatus = settings.hudShowStatus,
                showClock = settings.hudShowClock,
                showClockMillis = settings.hudShowClockMillis,
                showBattery = settings.hudShowBattery,
                showBatteryPercent = settings.hudShowBatteryPercent,
                showSignal = settings.hudShowSignal,
                showWifi = settings.hudShowWifi,
                showBluetooth = settings.hudShowBluetooth,
                showCursor = settings.hudShowCursor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            )
        }
    }

    colorPickerSlot?.let { slot ->
        val slotApps = slots.getOrNull(slot).orEmpty()
        val colorKey = slotApps.firstOrNull()?.packageName
        ColorPickerSheet(
            anchor = colorPickerAnchor,
            slotApps = slotApps,
            onPick = { hex ->
                colorKey?.let { pkg -> scope.launch { prefs.setTileColor(pkg, hex) } }
                colorPickerSlot = null
            },
            onReset = {
                colorKey?.let { pkg -> scope.launch { prefs.setTileColor(pkg, null) } }
                colorPickerSlot = null
            },
            onEditApps = {
                appPickerSlot = slot
                colorPickerSlot = null
            },
            onClearTile = {
                scope.launch { prefs.setSlotApps(slot, emptyList()) }
                colorPickerSlot = null
            },
            onDismiss = { colorPickerSlot = null }
        )
    }

    appPickerSlot?.let { slot ->
        // The pool excludes apps assigned to OTHER slots, but keeps this slot's own apps in so
        // they show up pre-checked (and can be unchecked) instead of vanishing from the list.
        val assignedElsewhere = remember(settings.slotApps, slot) {
            settings.slotApps.filterKeys { it != slot }.values.flatten().toSet()
        }
        val pickerPool = remember(apps, assignedElsewhere) { apps.filterNot { it.packageName in assignedElsewhere } }
        val currentSelection = remember(settings.slotApps, slot) { (settings.slotApps[slot] ?: emptyList()).toSet() }
        AppPickerSheet(
            apps = pickerPool,
            initiallySelected = currentSelection,
            immersiveEnabled = settings.immersiveEnabled,
            onConfirm = { selected ->
                scope.launch { prefs.setSlotApps(slot, selected) }
                appPickerSlot = null
            },
            onDismiss = { appPickerSlot = null }
        )
    }

    folderSlot?.let { slot ->
        FolderSheet(
            apps = slots.getOrNull(slot).orEmpty(),
            immersiveEnabled = settings.immersiveEnabled,
            onLaunch = { app -> repository.launch(app.packageName) },
            onDismiss = { folderSlot = null }
        )
    }

    if (showSettings) {
        SettingsSheet(
            hexSizeDp = liveHexSizeDp,
            onHexSizeChange = { liveHexSizeDp = it; scope.launch { prefs.setHexSize(it) } },
            hexCount = liveHexCount,
            onHexCountChange = { liveHexCount = it; scope.launch { prefs.setHexCount(it) } },
            didShrinkToFit = didShrinkToFit,
            hudVisible = settings.hudVisible,
            onHudVisibleChange = { scope.launch { prefs.setHudVisible(it) } },
            hudShowStatus = settings.hudShowStatus,
            onHudShowStatusChange = { scope.launch { prefs.setHudShowStatus(it) } },
            hudShowClock = settings.hudShowClock,
            onHudShowClockChange = { scope.launch { prefs.setHudShowClock(it) } },
            hudShowClockMillis = settings.hudShowClockMillis,
            onHudShowClockMillisChange = { scope.launch { prefs.setHudShowClockMillis(it) } },
            hudShowBattery = settings.hudShowBattery,
            onHudShowBatteryChange = { scope.launch { prefs.setHudShowBattery(it) } },
            hudShowBatteryPercent = settings.hudShowBatteryPercent,
            onHudShowBatteryPercentChange = { scope.launch { prefs.setHudShowBatteryPercent(it) } },
            hudShowSignal = settings.hudShowSignal,
            onHudShowSignalChange = { scope.launch { prefs.setHudShowSignal(it) } },
            hudShowWifi = settings.hudShowWifi,
            onHudShowWifiChange = { scope.launch { prefs.setHudShowWifi(it) } },
            hudShowBluetooth = settings.hudShowBluetooth,
            onHudShowBluetoothChange = { scope.launch { prefs.setHudShowBluetooth(it) } },
            hudShowCursor = settings.hudShowCursor,
            onHudShowCursorChange = { scope.launch { prefs.setHudShowCursor(it) } },
            immersiveEnabled = settings.immersiveEnabled,
            onImmersiveEnabledChange = {
                scope.launch { prefs.setImmersiveEnabled(it) }
                onImmersiveEnabledChange(it)
            },
            showAppIcons = settings.showAppIcons,
            onShowAppIconsChange = { scope.launch { prefs.setShowAppIcons(it) } },
            iconSizePercent = settings.iconSizePercent,
            onIconSizePercentChange = { scope.launch { prefs.setIconSizePercent(it) } },
            revealAnimation = settings.revealAnimation,
            onRevealAnimationChange = { scope.launch { prefs.setRevealAnimation(it) } },
            animationSpeed = settings.animationSpeed,
            onAnimationSpeedChange = { scope.launch { prefs.setAnimationSpeed(it) } },
            showWallpaper = settings.showWallpaper,
            onShowWallpaperChange = { scope.launch { prefs.setShowWallpaper(it) } },
            backgroundAnimation = settings.backgroundAnimation,
            onBackgroundAnimationChange = { scope.launch { prefs.setBackgroundAnimation(it) } },
            backgroundOpacity = settings.backgroundOpacity,
            onBackgroundOpacityChange = { scope.launch { prefs.setBackgroundOpacity(it) } },
            slots = slots,
            tileColors = settings.tileColors,
            wallpaperBitmap = wallpaperBitmap,
            onFaqClick = { showFaq = true },
            onAboutClick = { showAbout = true },
            onResetClick = { scope.launch { prefs.resetSettings() } },
            onDismiss = { showSettings = false }
        )
    }

    if (showAbout) {
        AboutSheet(immersiveEnabled = settings.immersiveEnabled, onDismiss = { showAbout = false })
    }

    if (showFaq) {
        FaqSheet(immersiveEnabled = settings.immersiveEnabled, onDismiss = { showFaq = false })
    }

    if (showLauncherHint) {
        DefaultLauncherHintSheet(
            immersiveEnabled = settings.immersiveEnabled,
            onDismiss = {
                showLauncherHint = false
                // Redundant with the LaunchedEffect write above, deliberately — dismissing here
                // via a direct user tap is a second, independent chance to persist the flag in
                // case the very first write raced with the activity backgrounding (tapping "set
                // now" immediately navigates to system Settings, pausing this activity).
                scope.launch { prefs.setHasShownDefaultLauncherHint(true) }
                // Chains straight into the gesture hint — but only for a fresh install where this
                // is the very first hint the user is dismissing; a returning user who already saw
                // hint 1 gets hint 2 (if still owed) from the LaunchedEffect above instead, not here.
                if (!settings.hasShownGestureHint) {
                    showGestureHint = true
                    scope.launch { prefs.setHasShownGestureHint(true) }
                }
            }
        )
    }

    if (showGestureHint) {
        GestureHintSheet(
            immersiveEnabled = settings.immersiveEnabled,
            onDismiss = {
                showGestureHint = false
                scope.launch { prefs.setHasShownGestureHint(true) }
            }
        )
    }
}
