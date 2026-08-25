package com.eversorhn.laun.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.data.InstalledAppsRepository
import com.eversorhn.laun.data.LauncherPrefs
import com.eversorhn.laun.ui.theme.LaunColors
import kotlinx.coroutines.launch

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
    onImmersiveEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by prefs.settings.collectAsState(initial = com.eversorhn.laun.data.LauncherSettings())

    val repository = remember { InstalledAppsRepository(context) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(Unit) { apps = repository.loadApps() }

    var isOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var colorPickerSlot by remember { mutableStateOf<Int?>(null) }
    var colorPickerApp by remember { mutableStateOf<AppInfo?>(null) }
    var colorPickerAnchor by remember { mutableStateOf(Offset.Zero) }
    var appPickerSlot by remember { mutableStateOf<Int?>(null) }

    // Local, immediate copies of size/count: the slider must resize the grid live, on every
    // pixel of drag — round-tripping every change through DataStore's async Flow first would
    // lag. These drive the grid directly; the DataStore write underneath is fire-and-forget.
    var liveHexSizeDp by remember { mutableStateOf(settings.hexSizeDp) }
    var liveHexCountIndex by remember { mutableStateOf(settings.hexCountIndex) }
    LaunchedEffect(settings.hexSizeDp) { liveHexSizeDp = settings.hexSizeDp }
    LaunchedEffect(settings.hexCountIndex) { liveHexCountIndex = settings.hexCountIndex }
    val liveHexCount = com.eversorhn.laun.data.RING_COUNTS[liveHexCountIndex.coerceIn(com.eversorhn.laun.data.RING_COUNTS.indices)]

    // Whether the grid actually had to shrink tiles below the requested size to keep every
    // tile on screen — tiles must never leave the screen, so this can happen at any live
    // combination of size/count; SettingsSheet shows a hint whenever it's true.
    var didShrinkToFit by remember { mutableStateOf(false) }

    BackHandler(enabled = isOpen) { isOpen = false }

    val appsByPackage = remember(apps) { apps.associateBy { it.packageName } }
    val slots = remember(appsByPackage, settings.slotApps, liveHexCount) {
        (0 until liveHexCount).map { i -> settings.slotApps[i]?.let { appsByPackage[it] } }
    }
    val availableApps = remember(apps, settings.slotApps) {
        val assigned = settings.slotApps.values.toSet()
        apps.filterNot { it.packageName in assigned }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LaunColors.bg)
    ) {
        HexGrid(
            slots = slots,
            hexSizeDp = liveHexSizeDp,
            tileColors = settings.tileColors,
            showIcons = settings.showAppIcons,
            revealAnimation = settings.revealAnimation,
            isOpen = isOpen,
            onOpen = { isOpen = true },
            onCloseBackground = { isOpen = false },
            onLongPressApp = { app, slotIndex, pos ->
                colorPickerApp = app
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
            onShrinkToFitChange = { didShrinkToFit = it },
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(top = if (settings.hudVisible) 64.dp else 0.dp)
        )

        if (settings.hudVisible) {
            StatusBar(
                isOpen = isOpen,
                appCount = settings.slotApps.size,
                showStatus = settings.hudShowStatus,
                showClock = settings.hudShowClock,
                showBattery = settings.hudShowBattery,
                showSignal = settings.hudShowSignal,
                showAppCount = settings.hudShowAppCount,
                showCursor = settings.hudShowCursor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            )
        }
    }

    colorPickerApp?.let { app ->
        ColorPickerSheet(
            anchor = colorPickerAnchor,
            onPick = { hex ->
                scope.launch { prefs.setTileColor(app.packageName, hex) }
                colorPickerApp = null
                colorPickerSlot = null
            },
            onReset = {
                scope.launch { prefs.setTileColor(app.packageName, null) }
                colorPickerApp = null
                colorPickerSlot = null
            },
            onClearTile = {
                colorPickerSlot?.let { slot -> scope.launch { prefs.setSlotApp(slot, null) } }
                colorPickerApp = null
                colorPickerSlot = null
            },
            onDismiss = {
                colorPickerApp = null
                colorPickerSlot = null
            }
        )
    }

    appPickerSlot?.let { slot ->
        AppPickerSheet(
            apps = availableApps,
            onPick = { app ->
                scope.launch { prefs.setSlotApp(slot, app.packageName) }
                appPickerSlot = null
            },
            onDismiss = { appPickerSlot = null }
        )
    }

    if (showSettings) {
        SettingsSheet(
            hexSizeDp = liveHexSizeDp,
            onHexSizeChange = { liveHexSizeDp = it; scope.launch { prefs.setHexSize(it) } },
            hexCountIndex = liveHexCountIndex,
            onHexCountIndexChange = { liveHexCountIndex = it; scope.launch { prefs.setHexCountIndex(it) } },
            didShrinkToFit = didShrinkToFit,
            hudVisible = settings.hudVisible,
            onHudVisibleChange = { scope.launch { prefs.setHudVisible(it) } },
            hudShowStatus = settings.hudShowStatus,
            onHudShowStatusChange = { scope.launch { prefs.setHudShowStatus(it) } },
            hudShowClock = settings.hudShowClock,
            onHudShowClockChange = { scope.launch { prefs.setHudShowClock(it) } },
            hudShowBattery = settings.hudShowBattery,
            onHudShowBatteryChange = { scope.launch { prefs.setHudShowBattery(it) } },
            hudShowSignal = settings.hudShowSignal,
            onHudShowSignalChange = { scope.launch { prefs.setHudShowSignal(it) } },
            hudShowAppCount = settings.hudShowAppCount,
            onHudShowAppCountChange = { scope.launch { prefs.setHudShowAppCount(it) } },
            hudShowCursor = settings.hudShowCursor,
            onHudShowCursorChange = { scope.launch { prefs.setHudShowCursor(it) } },
            immersiveEnabled = settings.immersiveEnabled,
            onImmersiveEnabledChange = {
                scope.launch { prefs.setImmersiveEnabled(it) }
                onImmersiveEnabledChange(it)
            },
            showAppIcons = settings.showAppIcons,
            onShowAppIconsChange = { scope.launch { prefs.setShowAppIcons(it) } },
            revealAnimation = settings.revealAnimation,
            onRevealAnimationChange = { scope.launch { prefs.setRevealAnimation(it) } },
            onDismiss = { showSettings = false }
        )
    }
}
