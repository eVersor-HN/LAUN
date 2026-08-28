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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.data.IconPackRepository
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

/** Sets the real system (home screen) wallpaper to solid black — for OLED power savings when the
 *  user explicitly picks the OLED BLACK background option, and automatically whenever the real
 *  wallpaper isn't shown in-app, so switching away to another app never flashes the actual system
 *  wallpaper underneath (see the showWallpaper LaunchedEffect below). */
private suspend fun setSystemWallpaperBlack(context: Context) {
    withContext(Dispatchers.IO) {
        try {
            val bmp = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.BLACK)
            WallpaperManager.getInstance(context).setBitmap(bmp)
            bmp.recycle()
        } catch (e: Exception) {
            // Best-effort — the in-app OLED BLACK background still applies even if this fails.
        }
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
    var rawApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    // Guards against opening the grid before the first loadApps() resolves — without it, a tap
    // in the first tens of milliseconds after cold start could momentarily render every assigned
    // tile as empty (appsByPackage still empty) before flashing to its real app once loaded.
    var appsLoaded by remember { mutableStateOf(false) }
    val reloadScope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        rawApps = repository.loadApps()
        appsLoaded = true
    }
    // Icon-pack overrides (chosen from an installed icon pack's own catalogue) resolve to real
    // bitmaps asynchronously — loaded once here per override set, cached by package name, so every
    // consumer downstream sees the swapped icon the same way it already sees renamed labels.
    val iconPackRepository = remember { IconPackRepository(context) }
    var iconOverrideBitmaps by remember { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    LaunchedEffect(settings.tileIconOverrides) {
        iconOverrideBitmaps = settings.tileIconOverrides.mapNotNull { (pkg, override) ->
            val (iconPackPkg, drawableName) = override
            iconPackRepository.loadIcon(iconPackPkg, drawableName)?.let { pkg to it }
        }.toMap()
    }
    // Custom names (long-press-to-rename in the app picker) and icon-pack overrides applied once
    // here so every consumer downstream — tiles, folders, the picker list itself — sees both
    // automatically.
    val apps = remember(rawApps, settings.customAppNames, iconOverrideBitmaps) {
        rawApps.map { app ->
            val name = settings.customAppNames[app.packageName]
            val icon = iconOverrideBitmaps[app.packageName]
            if (name == null && icon == null) app
            else app.copy(label = name ?: app.label, icon = icon ?: app.icon, iconOverridden = icon != null)
        }
    }

    // Installing/uninstalling an app while LAUN is running (the normal case — you background the
    // launcher to go install something from the Play Store) must update the app-picker and any
    // slot referencing a now-gone package without needing the launcher process to restart.
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                reloadScope.launch { rawApps = repository.loadApps() }
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

    // manualOpen tracks the ordinary tap-to-reveal/tap-to-close state; isOpen additionally forces
    // true whenever ALWAYS SHOW GRID is on, so the grid simply never has a closed STANDBY state to
    // fall back to. Every write below still only ever touches manualOpen — harmless no-ops while
    // the setting is on, since isOpen stays true regardless of what manualOpen says.
    var manualOpen by remember { mutableStateOf(false) }
    val isOpen = settings.alwaysShowGrid || manualOpen
    var showSettings by remember { mutableStateOf(false) }
    var colorPickerSlot by remember { mutableStateOf<Int?>(null) }
    var iconPickerPackage by remember { mutableStateOf<String?>(null) }
    // Carried straight from HexGrid's onLongPressSlot instead of re-derived via slots.getOrNull(slot)
    // below — that lookup is bounded to liveHexCount and silently came up empty for any pinned tile
    // beyond it (free placement), so picking a color for one did nothing.
    var colorPickerApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var colorPickerAnchor by remember { mutableStateOf(Offset.Zero) }
    var appPickerSlot by remember { mutableStateOf<Int?>(null) }
    var showAppSearch by remember { mutableStateOf(false) }
    var folderSlot by remember { mutableStateOf<Int?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    var showFaq by remember { mutableStateOf(false) }
    var showLauncherHint by remember { mutableStateOf(false) }
    var showGestureHint by remember { mutableStateOf(false) }
    var showBatteryHint by remember { mutableStateOf(false) }
    // Gated on settingsState (nullable) rather than settings.hasShownDefaultLauncherHint directly —
    // collectAsState's `initial` default (hasShownDefaultLauncherHint = false) would otherwise make
    // this fire on every cold start before the real, already-persisted value loads from DataStore.
    //
    // hintCheckDone guards against this effect re-running when settingsState re-emits after the
    // write below completes — without it, the gesture hint would auto-appear the instant the
    // default-launcher-hint flag finishes writing (milliseconds later), stacking both dialogs at
    // once instead of showing the gesture hint only once the user actually dismisses the first.
    // Each hint's own entry into the next one is handled in its own onDismiss below; this effect
    // only covers first-ever-load and "already saw an earlier hint, never saw a later one" (e.g.
    // updating from a build before that hint existed).
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
            !loaded.hasShownBatteryHint -> {
                // Only actually shown if not already exempt — no point nagging someone who's
                // already granted it (an OEM default, or set manually before ever seeing this).
                if (!com.eversorhn.laun.data.BatteryOptimization.isIgnoringBatteryOptimizations(context)) {
                    showBatteryHint = true
                }
                prefs.setHasShownBatteryHint(true)
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

    BackHandler(enabled = isOpen && !settings.alwaysShowGrid) { manualOpen = false }

    // Leaving the foreground for ANY reason (home-swipe while a sheet is open, recents, screen
    // off) must land back on a clean, closed home screen next time — otherwise a sheet that was
    // mid-gesture when the activity stopped can come back stuck, unresponsive to input.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                manualOpen = false
                showSettings = false
                colorPickerSlot = null
                appPickerSlot = null
                folderSlot = null
                showAbout = false
                showFaq = false
                showLauncherHint = false
                showGestureHint = false
                showBatteryHint = false
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
            manualOpen = false
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
    // Slots beyond liveHexCount that were manually pinned somewhere on screen via free tile
    // placement — rendered at their own fixed position regardless of COUNT. See HexGrid's
    // freeTilePlacement/extraOccupiedSlots.
    val pinnedSlots = remember(appsByPackage, settings.slotApps, liveHexCount) {
        settings.slotApps
            .filterKeys { it >= liveHexCount }
            .mapValues { (_, pkgs) -> pkgs.mapNotNull { appsByPackage[it] } }
            .filterValues { it.isNotEmpty() }
    }
    // Which of pinnedSlots' keys HexGrid is actually rendering right now — SIZE/COUNT/spacing/
    // margins can all shrink capacity below a previously-pinned index, and an app stuck there
    // with no visible tile shouldn't also stay stuck excluded from every picker (see
    // assignedElsewhere below).
    var visiblePinnedIndices by remember { mutableStateOf<Set<Int>>(emptySet()) }
    val freeformPositions = remember(settings.freeformPositions) {
        settings.freeformPositions.mapValues { (_, xy) -> Offset(xy.first, xy.second) }
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
            // No real wallpaper shown in-app — force the actual system wallpaper black too, so
            // a task-switch or recents transition never flashes the phone's real wallpaper
            // underneath. Best-effort and one-way: turning showWallpaper back on stops touching
            // it further, but doesn't restore whatever wallpaper was here before.
            setSystemWallpaperBlack(context)
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

    val rootView = LocalView.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LaunColors.bg)
            // Confirmed via logcat on-device: without this, the system's own gesture-nav monitor
            // (edge-swipe-back, swipe-up-home) can steal an in-progress touch away from this
            // screen mid-gesture — InputDispatcher logs it outright as "[Gesture Monitor] swipe-up
            // is stealing input gesture ... from [.../MainActivity]". A launcher's whole screen is
            // its own full-surface gesture area — there's no "content underneath" a system
            // back/home gesture should ever be revealing here — so the entire screen opts out of
            // being intercepted that way, and OUR OWN swipe/press handling always gets the whole
            // gesture uninterrupted, edge to edge. Compose has no modifier for this (there is no
            // Modifier.systemGestureExclusion — only the plain View property, added in API 29),
            // so it's set imperatively on the root View once we know our on-screen bounds.
            .onGloballyPositioned { coordinates ->
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    val bounds = coordinates.boundsInWindow()
                    rootView.systemGestureExclusionRects = listOf(
                        android.graphics.Rect(
                            bounds.left.toInt(),
                            bounds.top.toInt(),
                            bounds.right.toInt(),
                            bounds.bottom.toInt()
                        )
                    )
                }
            }
    ) {
        val backgroundOpacityFraction = settings.backgroundOpacity / 100f
        if (settings.backgroundAnimation >= 0) {
            val wallpaperTint = settings.backgroundColor
                ?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                ?: Color.White
            AnimatedWallpaper(
                kind = settings.backgroundAnimation,
                opacity = backgroundOpacityFraction,
                intensity = settings.backgroundIntensity / 100f,
                sizeScale = settings.backgroundEffectSize / 100f,
                tint = wallpaperTint,
                modifier = Modifier.fillMaxSize()
            )
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

        // Guards against a real, visible flash on every cold start (or an OEM-triggered process
        // restart mid-gesture — some launchers get relaunched from scratch on a plain app-switch
        // swipe instead of just resuming) where slotApps is already loaded from disk but the
        // installed-app list itself hasn't finished enumerating yet: every slot would briefly
        // render as empty (appsByPackage has nothing to map slotApps' package names to) before
        // snapping to its real content once loadApps() resolves. Skipping the whole grid until
        // then means that gap is just blank background, not a wrong grid correcting itself.
        if (appsLoaded) {
        HexGrid(
            slots = slots,
            hexSizeDp = liveHexSizeDp,
            tileColors = settings.tileColors,
            showIcons = settings.showAppIcons,
            iconSizePercent = settings.iconSizePercent,
            revealAnimation = settings.revealAnimation,
            animationSpeed = settings.animationSpeed,
            isOpen = isOpen,
            freePositionMode = settings.freePositionMode,
            snapMode = settings.snapMode,
            freeTilePlacement = settings.freeTilePlacement,
            extraOccupiedSlots = pinnedSlots,
            onVisibleExtraIndicesChange = { visiblePinnedIndices = it },
            freeformPositions = freeformPositions,
            onFreeformPositionChange = { index, pos -> scope.launch { prefs.setFreeformPosition(index, pos.x, pos.y) } },
            colorMenuAutoOpenSeconds = settings.colorMenuAutoOpenSeconds,
            mainMenuAutoOpenSeconds = settings.mainMenuAutoOpenSeconds,
            tileSizeOverrides = settings.tileSizeOverrides,
            tileSpacingDp = settings.tileSpacingDp,
            marginTopDp = settings.marginTopDp,
            marginBottomDp = settings.marginBottomDp,
            marginStartDp = settings.marginStartDp,
            marginEndDp = settings.marginEndDp,
            hideEmptyTiles = settings.hideEmptyTiles,
            onOpen = { if (appsLoaded) manualOpen = true },
            onLongPressSlot = { apps, slotIndex, pos ->
                colorPickerSlot = slotIndex
                colorPickerApps = apps
                colorPickerAnchor = pos
            },
            onLongPressBackground = { showSettings = true },
            onSwipeUpBackground = { showAppSearch = true },
            onTapEmptySlot = { slotIndex -> appPickerSlot = slotIndex },
            onLaunch = { app ->
                // Collapse before leaving, not after coming back — otherwise returning via the
                // back gesture (or task switcher) finds the grid still fully open, and its
                // predictive-back preview flashes the open grid over the launched app.
                manualOpen = false
                repository.launch(app)
            },
            onOpenFolder = { slotIndex, _ -> folderSlot = slotIndex },
            onReorderSlots = { from, to ->
                val fromApps = settings.slotApps[from] ?: emptyList()
                val toApps = settings.slotApps[to] ?: emptyList()
                // `to` is normally within the current COUNT-sized visible grid — a plain swap.
                // With free tile placement on, HexGrid can also offer a cell beyond COUNT as a
                // target; landing on an empty one there just pins the tile (toApps is empty, so
                // this is still the same two-way swap, just with nothing on the other side).
                // COUNT itself never changes as a side effect of dragging either way.
                scope.launch {
                    prefs.setSlotApps(from, toApps)
                    prefs.setSlotApps(to, fromApps)
                }
            },
            onShrinkToFitChange = { didShrinkToFit = it },
            modifier = Modifier
                .fillMaxSize()
                // Only follows the system bars' live inset while they're meant to stay visible.
                // In fullscreen mode they're hidden anyway, but Android's own home/app-switch
                // transition animations still show them for a frame or two regardless of our own
                // hide() request — reacting to that transient inset here reflowed every tile's
                // position for that instant (confirmed on-device: tiles visibly jump, snap back
                // once the transition settles), so fullscreen mode ignores system-bar insets
                // entirely instead of chasing their transient state.
                .then(if (!settings.immersiveEnabled) Modifier.systemBarsPadding() else Modifier)
                .padding(vertical = if (settings.hudVisible) 64.dp else 0.dp)
        )
        }

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
                    .then(if (!settings.immersiveEnabled) Modifier.systemBarsPadding() else Modifier)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            )
        }
    }

    colorPickerSlot?.let { slot ->
        val slotApps = colorPickerApps
        val colorKey = slotApps.firstOrNull()?.packageName
        ColorPickerSheet(
            anchor = colorPickerAnchor,
            slotApps = slotApps,
            sizePercent = settings.tileSizeOverrides[slot] ?: 100,
            onSizeChange = { percent -> scope.launch { prefs.setTileSizeOverride(slot, percent) } },
            onResetSize = { scope.launch { prefs.setTileSizeOverride(slot, null) } },
            onPick = { hex ->
                colorKey?.let { pkg -> scope.launch { prefs.setTileColor(pkg, hex) } }
                colorPickerSlot = null
            },
            onReset = {
                colorKey?.let { pkg -> scope.launch { prefs.setTileColor(pkg, null) } }
                colorPickerSlot = null
            },
            hasIconOverride = colorKey != null && settings.tileIconOverrides.containsKey(colorKey),
            onChooseIcon = {
                iconPickerPackage = colorKey
                colorPickerSlot = null
            },
            onResetIcon = {
                colorKey?.let { pkg -> scope.launch { prefs.setTileIconOverride(pkg, null, null) } }
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

    iconPickerPackage?.let { pkg ->
        IconPackPickerSheet(
            immersiveEnabled = settings.immersiveEnabled,
            hasOverride = settings.tileIconOverrides.containsKey(pkg),
            onPick = { iconPackPkg, drawableName ->
                scope.launch { prefs.setTileIconOverride(pkg, iconPackPkg, drawableName) }
                iconPickerPackage = null
            },
            onReset = {
                scope.launch { prefs.setTileIconOverride(pkg, null, null) }
                iconPickerPackage = null
            },
            onDismiss = { iconPickerPackage = null }
        )
    }

    appPickerSlot?.let { slot ->
        // The pool excludes apps assigned to OTHER *visible* slots, but keeps this slot's own apps
        // in so they show up pre-checked (and can be unchecked) instead of vanishing from the list.
        // A slot beyond COUNT only counts as "visible" while HexGrid is actually still rendering
        // it there (see visiblePinnedIndices) — an app pinned at an index the current SIZE/COUNT/
        // spacing/margins no longer have room for is free to pick again here, picking it moves it
        // into this slot (setSlotApps strips it out of its old one) rather than leaving it stuck
        // excluded forever with nothing showing it.
        val assignedElsewhere = remember(settings.slotApps, slot, liveHexCount, visiblePinnedIndices) {
            settings.slotApps
                .filterKeys { it != slot && (it < liveHexCount || it in visiblePinnedIndices) }
                .values.flatten().toSet()
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
            onRenameApp = { packageName, name -> scope.launch { prefs.setCustomAppName(packageName, name) } },
            onDismiss = { appPickerSlot = null }
        )
    }

    if (showAppSearch) {
        AppLaunchSearchSheet(
            apps = apps,
            immersiveEnabled = settings.immersiveEnabled,
            onLaunch = { app ->
                showAppSearch = false
                repository.launch(app)
            },
            onDismiss = { showAppSearch = false }
        )
    }

    folderSlot?.let { slot ->
        FolderSheet(
            apps = slots.getOrNull(slot) ?: pinnedSlots[slot].orEmpty(),
            immersiveEnabled = settings.immersiveEnabled,
            onLaunch = { app -> repository.launch(app) },
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
            freePositionMode = settings.freePositionMode,
            onFreePositionModeChange = { scope.launch { prefs.setFreePositionMode(it) } },
            snapMode = settings.snapMode,
            onSnapModeChange = { scope.launch { prefs.setSnapMode(it) } },
            freeTilePlacement = settings.freeTilePlacement,
            onFreeTilePlacementChange = { scope.launch { prefs.setFreeTilePlacement(it) } },
            tileSpacingDp = settings.tileSpacingDp,
            onTileSpacingChange = { scope.launch { prefs.setTileSpacing(it) } },
            marginTopDp = settings.marginTopDp,
            onMarginTopChange = { scope.launch { prefs.setMarginTop(it) } },
            marginBottomDp = settings.marginBottomDp,
            onMarginBottomChange = { scope.launch { prefs.setMarginBottom(it) } },
            marginStartDp = settings.marginStartDp,
            onMarginStartChange = { scope.launch { prefs.setMarginStart(it) } },
            marginEndDp = settings.marginEndDp,
            onMarginEndChange = { scope.launch { prefs.setMarginEnd(it) } },
            hideEmptyTiles = settings.hideEmptyTiles,
            onHideEmptyTilesChange = { scope.launch { prefs.setHideEmptyTiles(it) } },
            colorMenuAutoOpenSeconds = settings.colorMenuAutoOpenSeconds,
            onColorMenuAutoOpenSecondsChange = { scope.launch { prefs.setColorMenuAutoOpenSeconds(it) } },
            mainMenuAutoOpenSeconds = settings.mainMenuAutoOpenSeconds,
            onMainMenuAutoOpenSecondsChange = { scope.launch { prefs.setMainMenuAutoOpenSeconds(it) } },
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
            alwaysShowGrid = settings.alwaysShowGrid,
            onAlwaysShowGridChange = { scope.launch { prefs.setAlwaysShowGrid(it) } },
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
            backgroundIntensity = settings.backgroundIntensity,
            onBackgroundIntensityChange = { scope.launch { prefs.setBackgroundIntensity(it) } },
            backgroundEffectSize = settings.backgroundEffectSize,
            onBackgroundEffectSizeChange = { scope.launch { prefs.setBackgroundEffectSize(it) } },
            backgroundColor = settings.backgroundColor,
            onBackgroundColorChange = { scope.launch { prefs.setBackgroundColor(it) } },
            slots = slots,
            tileColors = settings.tileColors,
            wallpaperBitmap = wallpaperBitmap,
            onSetSystemWallpaperBlack = { scope.launch { setSystemWallpaperBlack(context) } },
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
                // Same fresh-install chaining as hint 1 -> hint 2 above, one step further.
                if (!settings.hasShownBatteryHint) {
                    if (!com.eversorhn.laun.data.BatteryOptimization.isIgnoringBatteryOptimizations(context)) {
                        showBatteryHint = true
                    }
                    scope.launch { prefs.setHasShownBatteryHint(true) }
                }
            }
        )
    }

    if (showBatteryHint) {
        BatteryHintSheet(
            immersiveEnabled = settings.immersiveEnabled,
            onDismiss = {
                showBatteryHint = false
                scope.launch { prefs.setHasShownBatteryHint(true) }
            }
        )
    }
}
