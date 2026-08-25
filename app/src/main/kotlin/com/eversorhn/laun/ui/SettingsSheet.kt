package com.eversorhn.laun.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.data.BACKGROUND_ANIMATIONS
import com.eversorhn.laun.data.MAX_HEX_COUNT
import com.eversorhn.laun.data.REVEAL_ANIMATIONS
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily
import com.eversorhn.laun.ui.theme.TILE_COLOR_PALETTE
import kotlin.math.abs
import kotlin.math.roundToInt

/** Picker previews always animate at this speed, independent of the live ANIMATION SPEED
 *  setting — otherwise a fast/instant configured speed would make the preview impossible to see. */
private const val PREVIEW_ANIMATION_SPEED = 40

/**
 * Settings panel — direct port of demo.html's bottom-left panel: tile size/count, HUD and
 * immersive-mode toggles. Sections read top-to-bottom in roughly the order things are drawn:
 * the grid's own look (GRID), how tiles reveal (ANIMATION), what's behind them (BACKGROUND),
 * the overlay on top of everything (STATUS BAR), then app-level meta (SYSTEM).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    hexSizeDp: Int,
    onHexSizeChange: (Int) -> Unit,
    hexCount: Int,
    onHexCountChange: (Int) -> Unit,
    didShrinkToFit: Boolean,
    freePositionMode: Boolean,
    onFreePositionModeChange: (Boolean) -> Unit,
    snapMode: Boolean,
    onSnapModeChange: (Boolean) -> Unit,
    freeTilePlacement: Boolean,
    onFreeTilePlacementChange: (Boolean) -> Unit,
    tileSpacingDp: Int,
    onTileSpacingChange: (Int) -> Unit,
    marginTopDp: Int,
    onMarginTopChange: (Int) -> Unit,
    marginBottomDp: Int,
    onMarginBottomChange: (Int) -> Unit,
    marginStartDp: Int,
    onMarginStartChange: (Int) -> Unit,
    marginEndDp: Int,
    onMarginEndChange: (Int) -> Unit,
    hideEmptyTiles: Boolean,
    onHideEmptyTilesChange: (Boolean) -> Unit,
    colorMenuAutoOpenSeconds: Int,
    onColorMenuAutoOpenSecondsChange: (Int) -> Unit,
    mainMenuAutoOpenSeconds: Int,
    onMainMenuAutoOpenSecondsChange: (Int) -> Unit,
    hudVisible: Boolean,
    onHudVisibleChange: (Boolean) -> Unit,
    hudShowStatus: Boolean,
    onHudShowStatusChange: (Boolean) -> Unit,
    hudShowClock: Boolean,
    onHudShowClockChange: (Boolean) -> Unit,
    hudShowClockMillis: Boolean,
    onHudShowClockMillisChange: (Boolean) -> Unit,
    hudShowBattery: Boolean,
    onHudShowBatteryChange: (Boolean) -> Unit,
    hudShowBatteryPercent: Boolean,
    onHudShowBatteryPercentChange: (Boolean) -> Unit,
    hudShowSignal: Boolean,
    onHudShowSignalChange: (Boolean) -> Unit,
    hudShowWifi: Boolean,
    onHudShowWifiChange: (Boolean) -> Unit,
    hudShowBluetooth: Boolean,
    onHudShowBluetoothChange: (Boolean) -> Unit,
    hudShowCursor: Boolean,
    onHudShowCursorChange: (Boolean) -> Unit,
    immersiveEnabled: Boolean,
    onImmersiveEnabledChange: (Boolean) -> Unit,
    alwaysShowGrid: Boolean,
    onAlwaysShowGridChange: (Boolean) -> Unit,
    showAppIcons: Boolean,
    onShowAppIconsChange: (Boolean) -> Unit,
    iconSizePercent: Int,
    onIconSizePercentChange: (Int) -> Unit,
    revealAnimation: Int,
    onRevealAnimationChange: (Int) -> Unit,
    animationSpeed: Int,
    onAnimationSpeedChange: (Int) -> Unit,
    showWallpaper: Boolean,
    onShowWallpaperChange: (Boolean) -> Unit,
    backgroundAnimation: Int,
    onBackgroundAnimationChange: (Int) -> Unit,
    backgroundOpacity: Int,
    onBackgroundOpacityChange: (Int) -> Unit,
    backgroundIntensity: Int,
    onBackgroundIntensityChange: (Int) -> Unit,
    backgroundEffectSize: Int,
    onBackgroundEffectSizeChange: (Int) -> Unit,
    backgroundColor: String?,
    onBackgroundColorChange: (String?) -> Unit,
    slots: List<List<AppInfo>>,
    tileColors: Map<String, String>,
    wallpaperBitmap: ImageBitmap?,
    onSetSystemWallpaperBlack: () -> Unit,
    onFaqClick: () -> Unit,
    onAboutClick: () -> Unit,
    onResetClick: () -> Unit,
    onDismiss: () -> Unit
) {
    // Tapping a slider's own label switches to a solo view showing just that one control — the
    // sheet then has almost no content, so it renders as a small bar instead of covering most of
    // the screen, and the grid stays visible above while dragging. Toggles don't need this: they
    // resolve in one tap, not a drag you want to watch land.
    var soloControl by remember { mutableStateOf<String?>(null) }
    // Accordion, not independent per-section flags — one section open at a time keeps the sheet
    // short regardless of how many sections exist. Collapsed by default: opening Settings should
    // show the 7 section titles, not the full list of every control underneath all of them.
    var expandedSection by remember { mutableStateOf<String?>(null) }

    var showAnimationPicker by remember { mutableStateOf(false) }
    var showBackgroundPicker by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    // Toggling this replays the picker preview's reveal animation on demand (tapping an option,
    // or tapping the preview itself) instead of it only ever playing once on open.
    var previewOpen by remember { mutableStateOf(true) }
    var previewNonce by remember { mutableStateOf(0) }
    fun replayPreview() { previewOpen = false; previewNonce++ }
    androidx.compose.runtime.LaunchedEffect(previewNonce) {
        if (previewNonce > 0) {
            kotlinx.coroutines.delay(40)
            previewOpen = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        shape = androidx.compose.ui.graphics.RectangleShape,
        containerColor = LaunColors.bg2,
        contentColor = LaunColors.fg,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(28.dp)
                    .height(2.dp)
                    .background(LaunColors.border)
            )
        }
    ) {
        HideSystemBarsWhileShown(immersiveEnabled)

        if (soloControl != null) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text(
                    text = "← BACK",
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .padding(bottom = 14.dp)
                        .clickable { soloControl = null }
                )
                when (soloControl) {
                    "size" -> SettingRow(label = "SIZE", value = "${hexSizeDp}dp") {
                        CorpoSlider(
                            value = hexSizeDp.toFloat(),
                            onValueChange = { onHexSizeChange(it.toInt()) },
                            valueRange = 60f..170f,
                        )
                    }
                    "count" -> SettingRow(label = "COUNT", value = "$hexCount") {
                        CorpoSlider(
                            value = hexCount.toFloat(),
                            onValueChange = { onHexCountChange(it.toInt()) },
                            valueRange = 1f..MAX_HEX_COUNT.toFloat(),
                        )
                    }
                    "spacing" -> SettingRow(label = "SPACING", value = "${tileSpacingDp}dp") {
                        CorpoSlider(
                            value = tileSpacingDp.toFloat(),
                            onValueChange = { onTileSpacingChange(it.toInt()) },
                            valueRange = 0f..60f,
                        )
                    }
                    "marginTop" -> MarginRow(label = "MARGIN TOP", valueDp = marginTopDp, onValueChange = onMarginTopChange)
                    "marginBottom" -> MarginRow(label = "MARGIN BOTTOM", valueDp = marginBottomDp, onValueChange = onMarginBottomChange)
                    "marginStart" -> MarginRow(label = "MARGIN LEFT", valueDp = marginStartDp, onValueChange = onMarginStartChange)
                    "marginEnd" -> MarginRow(label = "MARGIN RIGHT", valueDp = marginEndDp, onValueChange = onMarginEndChange)
                    "colorMenuDelay" -> SettingRow(
                        label = "COLOR MENU DELAY",
                        value = if (colorMenuAutoOpenSeconds == 1) "1 SEC" else "$colorMenuAutoOpenSeconds SEC"
                    ) {
                        CorpoSlider(
                            value = colorMenuAutoOpenSeconds.toFloat(),
                            onValueChange = { onColorMenuAutoOpenSecondsChange(it.toInt()) },
                            valueRange = 1f..60f,
                        )
                    }
                    "mainMenuDelay" -> SettingRow(
                        label = "SETTINGS MENU DELAY",
                        value = if (mainMenuAutoOpenSeconds == 1) "1 SEC" else "$mainMenuAutoOpenSeconds SEC"
                    ) {
                        CorpoSlider(
                            value = mainMenuAutoOpenSeconds.toFloat(),
                            onValueChange = { onMainMenuAutoOpenSecondsChange(it.toInt()) },
                            valueRange = 1f..60f,
                        )
                    }
                }
                if (didShrinkToFit && (soloControl == "size" || soloControl == "count" || soloControl == "spacing" ||
                        soloControl == "marginTop" || soloControl == "marginBottom" ||
                        soloControl == "marginStart" || soloControl == "marginEnd")
                ) {
                    Text(
                        text = "DOESN'T FIT THE SCREEN — SIZE AUTO-SHRUNK",
                        color = LaunColors.dim,
                        fontFamily = MonoFontFamily,
                        fontSize = 9.sp,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            return@ModalBottomSheet
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader(
                title = "GRID",
                expanded = expandedSection == "GRID",
                onClick = { expandedSection = if (expandedSection == "GRID") null else "GRID" },
                topPadding = 0.dp
            )
            if (expandedSection == "GRID") {
            SettingRow(
                label = "SIZE",
                value = "${hexSizeDp}dp",
                onLabelClick = { soloControl = "size" }
            ) {
                CorpoSlider(
                    value = hexSizeDp.toFloat(),
                    onValueChange = { onHexSizeChange(it.toInt()) },
                    valueRange = 60f..170f,
                )
            }

            SettingRow(
                label = "COUNT",
                value = "$hexCount",
                onLabelClick = { soloControl = "count" }
            ) {
                // Exact, one-tile-at-a-time — not snapped to symmetric ring totals. How many
                // actually fit at the current size is the user's call; if a combination doesn't
                // fit, the grid auto-shrinks tile size and the hint below explains why.
                CorpoSlider(
                    value = hexCount.toFloat(),
                    onValueChange = { onHexCountChange(it.toInt()) },
                    valueRange = 1f..MAX_HEX_COUNT.toFloat(),
                )
            }

            if (didShrinkToFit) {
                Text(
                    text = "DOESN'T FIT THE SCREEN — SIZE AUTO-SHRUNK",
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 9.sp,
                    letterSpacing = 0.6.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            SettingRow(
                label = "SPACING",
                value = "${tileSpacingDp}dp",
                onLabelClick = { soloControl = "spacing" }
            ) {
                CorpoSlider(
                    value = tileSpacingDp.toFloat(),
                    onValueChange = { onTileSpacingChange(it.toInt()) },
                    valueRange = 0f..60f,
                )
            }

            MarginRow(label = "MARGIN TOP", valueDp = marginTopDp, onValueChange = onMarginTopChange, onLabelClick = { soloControl = "marginTop" })
            MarginRow(label = "MARGIN BOTTOM", valueDp = marginBottomDp, onValueChange = onMarginBottomChange, onLabelClick = { soloControl = "marginBottom" })
            MarginRow(label = "MARGIN LEFT", valueDp = marginStartDp, onValueChange = onMarginStartChange, onLabelClick = { soloControl = "marginStart" })
            MarginRow(label = "MARGIN RIGHT", valueDp = marginEndDp, onValueChange = onMarginEndChange, onLabelClick = { soloControl = "marginEnd" })

            ToggleRow(
                label = "HIDE EMPTY TILES",
                checked = hideEmptyTiles,
                onCheckedChange = onHideEmptyTilesChange,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "UNASSIGNED SLOTS STAY INVISIBLE UNTIL PRESSED — STILL TAP THERE TO ADD AN APP",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            }

            // Interaction, not geometry — how touch on a tile resolves (drag freedom, how long a
            // hold takes to open the color menu), as opposed to GRID above (what the layout itself
            // looks like: tile size/count). Kept as its own section rather than folded into GRID —
            // that reads as "the grid's own look," and gesture timing/reach isn't that.
            SectionHeader(
                title = "TILE INTERACTION",
                expanded = expandedSection == "TILE INTERACTION",
                onClick = { expandedSection = if (expandedSection == "TILE INTERACTION") null else "TILE INTERACTION" }
            )
            if (expandedSection == "TILE INTERACTION") {
            ToggleRow(
                label = "FREE POSITION MODE",
                checked = freePositionMode,
                onCheckedChange = onFreePositionModeChange,
            )
            Text(
                text = "DROP A TILE ANYWHERE — NO GRID, NO EDGE MARGIN. TILES STILL WON'T OVERLAP EACH OTHER OR EXCEED COUNT",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            ToggleRow(
                label = "SNAP MODE",
                checked = snapMode,
                onCheckedChange = onSnapModeChange,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = if (freePositionMode) {
                    "NO EFFECT WHILE FREE POSITION MODE IS ON"
                } else {
                    "DRAG DOESN'T NEED TO LAND EXACTLY ON A TILE — IT SNAPS TO WHICHEVER SLOT IS CLOSEST"
                },
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            ToggleRow(
                label = "FREE TILE PLACEMENT",
                checked = freeTilePlacement,
                onCheckedChange = onFreeTilePlacementChange,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = if (freePositionMode) {
                    "NO EFFECT WHILE FREE POSITION MODE IS ON"
                } else {
                    "DRAG A TILE ONTO ANY OPEN SPACE ON THE GRID, NOT JUST ANOTHER TILE — NOTHING ELSE MOVES. NEEDS ROOM ON SCREEN BEYOND YOUR CURRENT TILES"
                },
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            SettingRow(
                label = "COLOR MENU DELAY",
                value = if (colorMenuAutoOpenSeconds == 1) "1 SEC" else "$colorMenuAutoOpenSeconds SEC",
                onLabelClick = { soloControl = "colorMenuDelay" },
                modifier = Modifier.padding(top = 10.dp)
            ) {
                CorpoSlider(
                    value = colorMenuAutoOpenSeconds.toFloat(),
                    onValueChange = { onColorMenuAutoOpenSecondsChange(it.toInt()) },
                    valueRange = 1f..60f,
                )
            }

            SettingRow(
                label = "SETTINGS MENU DELAY",
                value = if (mainMenuAutoOpenSeconds == 1) "1 SEC" else "$mainMenuAutoOpenSeconds SEC",
                onLabelClick = { soloControl = "mainMenuDelay" },
            ) {
                CorpoSlider(
                    value = mainMenuAutoOpenSeconds.toFloat(),
                    onValueChange = { onMainMenuAutoOpenSecondsChange(it.toInt()) },
                    valueRange = 1f..60f,
                )
            }
            }

            SectionHeader(
                title = "APPEARANCE",
                expanded = expandedSection == "APPEARANCE",
                onClick = { expandedSection = if (expandedSection == "APPEARANCE") null else "APPEARANCE" }
            )
            if (expandedSection == "APPEARANCE") {
            ToggleRow(label = "APP ICONS INSTEAD OF NAME", checked = showAppIcons, onCheckedChange = onShowAppIconsChange)
            if (showAppIcons) {
                // Range is capped at the same 20..75% the icon is clamped to at draw time — the
                // slider itself can't offer a value that would ever crowd or cross the tile edge.
                SettingRow(
                    label = "ICON SIZE",
                    value = "$iconSizePercent%",
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    CorpoSlider(
                        value = iconSizePercent.toFloat(),
                        onValueChange = { onIconSizePercentChange(it.toInt()) },
                        valueRange = 20f..75f,
                    )
                }
            }
            }

            SectionHeader(
                title = "ANIMATION",
                expanded = expandedSection == "ANIMATION",
                onClick = { expandedSection = if (expandedSection == "ANIMATION") null else "ANIMATION" }
            )
            if (expandedSection == "ANIMATION") {
            PickerRow(
                label = "TILE REVEAL",
                value = if (revealAnimation < 0) "NONE" else REVEAL_ANIMATIONS[revealAnimation],
                onClick = { showAnimationPicker = true }
            )
            SettingRow(
                label = "ANIMATION SPEED",
                value = if (animationSpeed == 0) "INSTANT" else "$animationSpeed",
                modifier = Modifier.padding(top = 10.dp)
            ) {
                CorpoSlider(
                    value = animationSpeed.toFloat(),
                    onValueChange = { onAnimationSpeedChange(it.toInt()) },
                    valueRange = 0f..100f,
                )
            }
            }

            SectionHeader(
                title = "BACKGROUND",
                expanded = expandedSection == "BACKGROUND",
                onClick = { expandedSection = if (expandedSection == "BACKGROUND") null else "BACKGROUND" }
            )
            if (expandedSection == "BACKGROUND") {
            PickerRow(
                label = "SOURCE",
                value = when {
                    backgroundAnimation >= 0 -> BACKGROUND_ANIMATIONS[backgroundAnimation]
                    showWallpaper -> "ANDROID WALLPAPER"
                    else -> "NONE"
                },
                onClick = { showBackgroundPicker = true }
            )
            if (showWallpaper || backgroundAnimation >= 0) {
                SettingRow(
                    label = "OPACITY",
                    value = "$backgroundOpacity%",
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    CorpoSlider(
                        value = backgroundOpacity.toFloat(),
                        onValueChange = { onBackgroundOpacityChange(it.toInt()) },
                        valueRange = 0f..100f,
                    )
                }
            }
            // OLED BLACK is flat solid color — brightness/size controls don't apply to it, only
            // to the other 8 animated concepts.
            if (backgroundAnimation >= 0 && backgroundAnimation != BACKGROUND_ANIMATIONS.lastIndex) {
                SettingRow(
                    label = "INTENSITY",
                    value = "$backgroundIntensity%",
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    CorpoSlider(
                        value = backgroundIntensity.toFloat(),
                        onValueChange = { onBackgroundIntensityChange(it.toInt()) },
                        valueRange = 50f..200f,
                    )
                }
                SettingRow(
                    label = "EFFECT SIZE",
                    value = "$backgroundEffectSize%",
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    CorpoSlider(
                        value = backgroundEffectSize.toFloat(),
                        onValueChange = { onBackgroundEffectSizeChange(it.toInt()) },
                        valueRange = 50f..200f,
                    )
                }
                PickerRow(
                    label = "COLOR",
                    value = backgroundColor ?: "DEFAULT",
                    onClick = { showColorPicker = true }
                )
            }
            }

            SectionHeader(
                title = "STATUS BAR",
                expanded = expandedSection == "STATUS BAR",
                onClick = { expandedSection = if (expandedSection == "STATUS BAR") null else "STATUS BAR" }
            )
            if (expandedSection == "STATUS BAR") {
            ToggleRow(label = "SHOW", checked = hudVisible, onCheckedChange = onHudVisibleChange)
            Column(modifier = Modifier.padding(start = 12.dp).alpha(if (hudVisible) 1f else 0.35f)) {
                ToggleRow(label = "STATUS", checked = hudShowStatus, onCheckedChange = onHudShowStatusChange, enabled = hudVisible)
                ToggleRow(label = "CLOCK", checked = hudShowClock, onCheckedChange = onHudShowClockChange, enabled = hudVisible)
                ToggleRow(label = "MILLISECONDS", checked = hudShowClockMillis, onCheckedChange = onHudShowClockMillisChange, enabled = hudVisible && hudShowClock)
                ToggleRow(label = "BATTERY", checked = hudShowBattery, onCheckedChange = onHudShowBatteryChange, enabled = hudVisible)
                ToggleRow(label = "BATTERY PERCENT", checked = hudShowBatteryPercent, onCheckedChange = onHudShowBatteryPercentChange, enabled = hudVisible && hudShowBattery)
                ToggleRow(label = "SIGNAL", checked = hudShowSignal, onCheckedChange = onHudShowSignalChange, enabled = hudVisible)
                ToggleRow(label = "WIFI", checked = hudShowWifi, onCheckedChange = onHudShowWifiChange, enabled = hudVisible)
                ToggleRow(label = "BLUETOOTH", checked = hudShowBluetooth, onCheckedChange = onHudShowBluetoothChange, enabled = hudVisible)
                ToggleRow(label = "CURSOR", checked = hudShowCursor, onCheckedChange = onHudShowCursorChange, enabled = hudVisible)
            }
            }

            SectionHeader(
                title = "SYSTEM",
                expanded = expandedSection == "SYSTEM",
                onClick = { expandedSection = if (expandedSection == "SYSTEM") null else "SYSTEM" }
            )
            if (expandedSection == "SYSTEM") {
            ToggleRow(label = "FULLSCREEN MODE", checked = immersiveEnabled, onCheckedChange = onImmersiveEnabledChange)
            ToggleRow(
                label = "ALWAYS SHOW GRID",
                checked = alwaysShowGrid,
                onCheckedChange = onAlwaysShowGridChange,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "TILES ARE SHOWN RIGHT AWAY, NO TAP TO OPEN. SET TILE REVEAL ABOVE TO NONE SO THEY APPEAR FIXED WITH NO ANIMATION EITHER",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )
            LinkRow(text = "FAQ", topPadding = 14.dp, onClick = onFaqClick)
            LinkRow(text = "ABOUT LAUNCHER", onClick = onAboutClick)
            LinkRow(text = "RESET TO DEFAULTS", onClick = onResetClick)
            }
        }
    }

    if (showAnimationPicker) {
        OptionPickerSheet(
            title = "TILE REVEAL ANIMATION",
            options = listOf("NONE") + REVEAL_ANIMATIONS,
            selectedIndex = revealAnimation + 1,
            onSelect = { onRevealAnimationChange(it - 1); replayPreview() },
            onDismiss = { showAnimationPicker = false },
            immersiveEnabled = immersiveEnabled
        ) {
            TilePreviewCluster(
                slots = slots,
                tileColors = tileColors,
                showIcons = showAppIcons,
                iconSizePercent = iconSizePercent,
                revealAnimation = revealAnimation,
                // Fixed, not the live setting — otherwise a fast/instant configured speed would
                // make the preview flash by too quickly (or not animate at all) to see anything.
                animationSpeed = PREVIEW_ANIMATION_SPEED,
                isOpen = previewOpen,
                tileCount = hexCount,
                backgroundAnimationKind = backgroundAnimation,
                showAndroidWallpaper = showWallpaper,
                wallpaperBitmap = wallpaperBitmap,
                backgroundOpacity = backgroundOpacity / 100f,
                backgroundIntensity = backgroundIntensity / 100f,
                backgroundEffectSize = backgroundEffectSize / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clickable { replayPreview() }
            )
        }
    }

    if (showBackgroundPicker) {
        // -1 = NONE, -2 = the real Android wallpaper, 0.. = an index into BACKGROUND_ANIMATIONS.
        val options = listOf("NONE", "ANDROID WALLPAPER") + BACKGROUND_ANIMATIONS
        val selectedIndex = when {
            backgroundAnimation >= 0 -> backgroundAnimation + 2
            showWallpaper -> 1
            else -> 0
        }
        OptionPickerSheet(
            title = "BACKGROUND",
            options = options,
            selectedIndex = selectedIndex,
            onSelect = { index ->
                when (index) {
                    0 -> { onShowWallpaperChange(false); onBackgroundAnimationChange(-1) }
                    1 -> onShowWallpaperChange(true)
                    else -> {
                        val animIndex = index - 2
                        onBackgroundAnimationChange(animIndex)
                        // Selecting OLED BLACK also sets it as the real system wallpaper — the
                        // whole point of a true-black option is the OS-level power saving, which
                        // only the actual wallpaper (not just our in-app canvas) can deliver.
                        if (animIndex == BACKGROUND_ANIMATIONS.lastIndex) onSetSystemWallpaperBlack()
                    }
                }
            },
            onDismiss = { showBackgroundPicker = false },
            immersiveEnabled = immersiveEnabled
        ) {
            TilePreviewCluster(
                slots = slots,
                tileColors = tileColors,
                showIcons = showAppIcons,
                iconSizePercent = iconSizePercent,
                revealAnimation = revealAnimation,
                // Fixed, not the live setting — otherwise a fast/instant configured speed would
                // make the preview flash by too quickly (or not animate at all) to see anything.
                animationSpeed = PREVIEW_ANIMATION_SPEED,
                isOpen = true,
                tileCount = hexCount,
                backgroundAnimationKind = backgroundAnimation,
                showAndroidWallpaper = showWallpaper,
                wallpaperBitmap = wallpaperBitmap,
                backgroundOpacity = backgroundOpacity / 100f,
                backgroundIntensity = backgroundIntensity / 100f,
                backgroundEffectSize = backgroundEffectSize / 100f,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            )
        }
    }

    // A compact standalone dialog rather than another row buried in the scrolling list below —
    // all 20 swatches reachable in one tap, nothing to scroll to find.
    if (showColorPicker) {
        Dialog(onDismissRequest = { showColorPicker = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            HideSystemBarsWhileShown(immersiveEnabled)
            Column(
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .background(LaunColors.bg2)
                    .border(1.dp, LaunColors.border)
                    .padding(16.dp)
            ) {
                Text(
                    text = "BACKGROUND COLOR",
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(TILE_COLOR_PALETTE) { color ->
                        val hex = color.toHex()
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .background(color)
                                .border(1.dp, if (backgroundColor == hex) LaunColors.fg else Color.White.copy(alpha = 0.15f))
                                .clickable {
                                    onBackgroundColorChange(hex)
                                    showColorPicker = false
                                }
                        )
                    }
                }
                Text(
                    text = "RESET",
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .border(1.dp, LaunColors.border)
                        .clickable {
                            onBackgroundColorChange(null)
                            showColorPicker = false
                        }
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Flat, hard-edged slider matching the terminal aesthetic — a thin rectangular track with a
 * bar-shaped thumb, no pill shapes or rounded caps like Material3's default Slider.
 */
@Composable
internal fun CorpoSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(0f) }

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(valueRange.start, valueRange.endInclusive)
        if (steps <= 0) return clamped
        val segments = steps + 1
        val stepSize = (valueRange.endInclusive - valueRange.start) / segments
        val idx = ((clamped - valueRange.start) / stepSize).roundToInt()
        return (valueRange.start + idx * stepSize).coerceIn(valueRange.start, valueRange.endInclusive)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { widthPx = it.width.toFloat() }
            .pointerInput(valueRange, steps) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun updateFromX(x: Float) {
                        if (widthPx <= 0f) return
                        val fraction = (x / widthPx).coerceIn(0f, 1f)
                        onValueChange(snap(valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)))
                    }
                    // Don't move the slider on down alone — inside a scrolling menu, a finger
                    // that lands on a slider but is actually scrolling vertically must not snap
                    // the value. Only commit once movement is clearly horizontal (past touch
                    // slop); a genuine tap with no real movement still jumps to that position.
                    val touchSlop = viewConfiguration.touchSlop
                    var dragging = false
                    var scrolling = false
                    var totalDx = 0f
                    var totalDy = 0f
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!dragging && !scrolling) {
                            val delta = change.positionChange()
                            totalDx += delta.x
                            totalDy += delta.y
                            if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                                if (abs(totalDx) > abs(totalDy)) dragging = true else scrolling = true
                            }
                        }
                        if (dragging) {
                            change.consume()
                            updateFromX(change.position.x)
                        }
                        if (change.changedToUp()) {
                            if (!dragging && !scrolling) updateFromX(change.position.x)
                            break
                        }
                        if (!change.pressed) break
                    }
                }
            }
    ) {
        val fraction = if (valueRange.endInclusive > valueRange.start) {
            ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        } else 0f

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val trackPx = 2.dp.toPx()
            drawRect(
                color = LaunColors.border,
                topLeft = Offset(0f, cy - trackPx / 2f),
                size = Size(size.width, trackPx)
            )
            drawRect(
                color = LaunColors.fg,
                topLeft = Offset(0f, cy - trackPx / 2f),
                size = Size(size.width * fraction, trackPx)
            )
            if (steps > 0) {
                val tickPx = 1.dp.toPx()
                val tickHeightPx = 8.dp.toPx()
                for (i in 0..(steps + 1)) {
                    val tx = size.width * i / (steps + 1)
                    drawRect(
                        color = LaunColors.bg2,
                        topLeft = Offset((tx - tickPx / 2f).coerceIn(0f, size.width - tickPx), cy - tickHeightPx / 2f),
                        size = Size(tickPx, tickHeightPx)
                    )
                }
            }
            val thumbWidthPx = 3.dp.toPx()
            val thumbHeightPx = 18.dp.toPx()
            val thumbX = (size.width * fraction).coerceIn(thumbWidthPx / 2f, size.width - thumbWidthPx / 2f)
            drawRect(
                color = LaunColors.fg,
                topLeft = Offset(thumbX - thumbWidthPx / 2f, cy - thumbHeightPx / 2f),
                size = Size(thumbWidthPx, thumbHeightPx)
            )
        }
    }
}

/** Flat rectangular toggle — a hard-edged track + sliding block thumb instead of Material3's pill. */
@Composable
private fun CorpoSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val thumbOffset by animateDpAsState(targetValue = if (checked) 18.dp else 2.dp, label = "switchThumb")
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(16.dp)
            .background(if (checked) LaunColors.fg else Color.Transparent)
            .border(1.dp, if (checked) LaunColors.fg else LaunColors.border, RectangleShape)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset, top = 2.dp)
                .width(12.dp)
                .height(12.dp)
                .background(if (checked) LaunColors.bg else LaunColors.dim)
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    onLabelClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                modifier = if (onLabelClick != null) Modifier.clickable(onClick = onLabelClick) else Modifier
            )
            Text(value, color = LaunColors.fg, fontFamily = MonoFontFamily, fontSize = 10.sp)
        }
        content()
    }
}

/** One of the 4 MARGIN sliders — shared by the normal view and its solo-peek counterpart so the
 *  0..200 range only ever lives in one place (it previously drifted out of sync with the 0..300
 *  the settings themselves were stored/clamped to). */
@Composable
private fun MarginRow(
    label: String,
    valueDp: Int,
    onValueChange: (Int) -> Unit,
    onLabelClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SettingRow(label = label, value = "${valueDp}dp", onLabelClick = onLabelClick, modifier = modifier) {
        CorpoSlider(
            value = valueDp.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 0f..200f,
        )
    }
}

/** A settings row that opens a dedicated picker sheet instead of holding its own control inline —
 *  used for choices with enough options that a horizontal chip strip stopped being scannable. */
@Composable
private fun PickerRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .border(1.dp, LaunColors.border)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = LaunColors.dim, fontFamily = MonoFontFamily, fontSize = 10.sp, letterSpacing = 1.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = LaunColors.fg, fontFamily = MonoFontFamily, fontSize = 10.sp)
            Text(
                "  ›",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 11.sp
            )
        }
    }
}

/** Bordered, centered, clickable text row — used for the SYSTEM section's FAQ/About/Reset links. */
@Composable
private fun LinkRow(text: String, onClick: () -> Unit, topPadding: androidx.compose.ui.unit.Dp = 8.dp) {
    Text(
        text = text,
        color = LaunColors.dim,
        fontFamily = MonoFontFamily,
        fontSize = 10.sp,
        letterSpacing = 1.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .border(1.dp, LaunColors.border)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = LaunColors.dim, fontFamily = MonoFontFamily, fontSize = 10.sp, letterSpacing = 1.sp)
        CorpoSwitch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

/** Groups related settings under a labeled, clickable divider that collapses/expands the section
 *  below it — accordion-style, so the sheet reads as a short list of categories by default instead
 *  of every control in the app at once. */
@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp = 18.dp
) {
    Column(modifier = Modifier.padding(top = topPadding, bottom = 10.dp).clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = LaunColors.fg,
                fontFamily = MonoFontFamily,
                fontSize = 9.sp,
                letterSpacing = 2.sp
            )
            Text(
                text = if (expanded) "▾" else "▸",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 11.sp
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(LaunColors.border)
        )
    }
}
