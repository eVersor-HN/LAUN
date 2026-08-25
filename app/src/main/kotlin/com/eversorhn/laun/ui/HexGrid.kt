package com.eversorhn.laun.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.ui.theme.LaunColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private data class RectCapacity(val cols: Int, val rows: Int, val hexH: Float)

/** How many pointy-top hex columns/rows of width [hexW] fit an [availW]x[availH] rectangle. */
private fun hexCapacity(hexW: Float, availW: Float, availH: Float): RectCapacity {
    val s = hexW / sqrt(3f)
    val hexH = s * 2f
    val cols = max(1, (availW / hexW).toInt())
    // Total vertical span of `rows` staggered rows is hexH (the first row's full height) plus
    // 0.75*hexH per additional row — so the max rows that fit availH is (availH-hexH)/(0.75hexH)+1.
    // This previously subtracted 0.25*hexH instead of hexH, overestimating rows by exactly one,
    // which let the honeycomb's top/bottom edge rows spill past the margin (visible once a tile
    // is a large enough fraction of the screen, e.g. a small/narrow display with big tiles).
    val rows = max(1, ((availH - hexH) / (hexH * 0.75f)).toInt() + 1)
    return RectCapacity(cols, rows, hexH)
}

internal data class TileLayout(
    val apps: List<AppInfo>,
    val index: Int,
    val centerXDp: Float,
    val centerYDp: Float,
    val widthDp: Dp,
    val heightDp: Dp,
    val delayMs: Int
)

private data class LayoutResult(
    val tiles: List<TileLayout>,
    /** Cells that fit at the current size but aren't part of the active [tiles] set — only
     *  offered as drag-drop targets, never rendered/interactive otherwise. Dropping onto one
     *  expands the grid to include it (see LauncherScreen's onReorderSlots handling). */
    val extraCells: List<TileLayout>,
    val shrunk: Boolean
)

private const val LAYOUT_MARGIN_DP = 32f
private const val TILE_RENDER_SCALE = 0.9f
private const val TOP_DEAD_ZONE_DP = 24f
private const val DRAG_SLOP_DP = 12f

/**
 * True point-in-hexagon test against the tile's actual rendered (scaled) shape — not its
 * rectangular bounding box. Without this, the "background" gaps between hexes (and the pointed
 * corners of each tile's bounding box) would wrongly register as hits on the nearest tile,
 * matching a browser's clip-path-aware elementFromPoint(), which the demo relies on.
 */
private fun hexContains(point: Offset, tile: TileLayout, density: Density): Boolean {
    val wPx = with(density) { tile.widthDp.toPx() }
    val hPx = with(density) { tile.heightDp.toPx() }
    val cxPx = with(density) { tile.centerXDp.dp.toPx() }
    val cyPx = with(density) { tile.centerYDp.dp.toPx() }
    val dx = abs(point.x - cxPx) / TILE_RENDER_SCALE
    val dy = abs(point.y - cyPx) / TILE_RENDER_SCALE
    val halfW = wPx / 2f
    if (dx > halfW) return false
    val allowedHalfH = hPx / 2f - (dx / halfW) * (hPx / 4f)
    return dy <= allowedHalfH
}

/**
 * Symmetric honeycomb (always a complete hex-of-hexes, per RING_COUNTS), auto-shrinking the tile
 * size to fit the available space — a direct port of demo.html's layoutHexes()/hexSpiral(), plus
 * the press-drag-highlight and release-to-launch interaction from the same file.
 *
 * [slots] is always exactly [hexCount]-sized, index-stable: an empty list means the slot is
 * unassigned, one app is a normal tile, more than one is a folder tile.
 */
@Composable
fun HexGrid(
    slots: List<List<AppInfo>>,
    hexSizeDp: Int,
    tileColors: Map<String, String>,
    showIcons: Boolean,
    iconSizePercent: Int = 55,
    revealAnimation: Int,
    animationSpeed: Int,
    isOpen: Boolean,
    onOpen: () -> Unit,
    onCloseBackground: () -> Unit,
    onLongPressSlot: (List<AppInfo>, Int, Offset) -> Unit,
    onLongPressBackground: () -> Unit,
    onTapEmptySlot: (Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenFolder: (Int, List<AppInfo>) -> Unit,
    onReorderSlots: (from: Int, to: Int) -> Unit,
    onShrinkToFitChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (slots.isEmpty()) return

    BoxWithConstraints(modifier = modifier) {
        val availWDp = max(40f, maxWidth.value - LAYOUT_MARGIN_DP * 2)
        val availHDp = max(40f, maxHeight.value - LAYOUT_MARGIN_DP * 2)
        val boxWDp = maxWidth.value
        val boxHDp = maxHeight.value
        val n = slots.size

        val layoutResult = remember(slots, hexSizeDp, availWDp, availHDp) {
            // Fill the actual screen rectangle (both width and height), not just a fixed
            // symmetric hex-of-hexes outline: find how many cols/rows fit at the requested tile
            // size, only shrinking the size if even a 1x1 grid wouldn't fit n tiles.
            // Odd rows have one fewer cell (see below), so the real capacity is cols*rows minus
            // the number of odd rows — using plain cols*rows here undercounted how much shrinking
            // was needed and let n exceed the actual cell count, crashing the ranked[i] lookup below.
            fun actualCapacity(c: RectCapacity) = c.cols * c.rows - c.rows / 2

            var hexW = hexSizeDp.toFloat()
            var cap = hexCapacity(hexW, availWDp, availHDp)
            while (actualCapacity(cap) < n && hexW > 20f) {
                hexW -= 1f
                cap = hexCapacity(hexW, availWDp, availHDp)
            }
            val cols = cap.cols
            val rows = cap.rows
            val hexH = cap.hexH

            // Every honeycomb cell that fits the rectangle, offset-staggered rows. Odd rows get
            // one fewer tile than even rows (5,4,5,4,...) rather than the same count shifted —
            // that's what keeps each row centered under the next or previous instead of the
            // whole row just sliding right, so the overall shape stays symmetric at any size/count.
            val cells = ArrayList<Offset>(cols * rows)
            for (row in 0 until rows) {
                val rowCols = if (row % 2 == 0) cols else max(0, cols - 1)
                for (col in 0 until rowCols) {
                    cells += Offset(col * hexW + (if (row % 2 == 1) hexW / 2 else 0f), row * hexH * 0.75f)
                }
            }
            val cx = cells.sumOf { it.x.toDouble() }.toFloat() / cells.size
            val cy = cells.sumOf { it.y.toDouble() }.toFloat() / cells.size
            // closest-to-center cells first — this is what keeps the shape stable and centered
            // as n grows/shrinks, and what the outside-in reveal delay is based on below
            val rankedAll = cells
                .map { c -> Triple(c, c.x - cx, c.y - cy) }
                .sortedBy { (_, dx, dy) -> dx * dx + dy * dy }
            val ranked = rankedAll.take(n)

            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            ranked.forEach { (_, dx, dy) ->
                minX = min(minX, dx - hexW / 2); maxX = max(maxX, dx + hexW / 2)
                minY = min(minY, dy - hexH / 2); maxY = max(maxY, dy + hexH / 2)
            }
            val offsetX = boxWDp / 2 - (minX + maxX) / 2
            val offsetY = boxHDp / 2 - (minY + maxY) / 2
            val maxDist = ranked.maxOf { (_, dx, dy) -> sqrt(dx * dx + dy * dy) }.let { if (it == 0f) 1f else it }

            // Defensive: should always hold given actualCapacity() above, but never index past
            // what actually got generated — degrade to fewer rendered tiles rather than crash.
            val tileList = slots.take(ranked.size).mapIndexed { i, slotApps ->
                val (_, dx, dy) = ranked[i]
                val dist = sqrt(dx * dx + dy * dy)
                TileLayout(
                    apps = slotApps,
                    index = i,
                    centerXDp = offsetX + dx,
                    centerYDp = offsetY + dy,
                    widthDp = hexW.dp,
                    heightDp = hexH.dp,
                    delayMs = ((maxDist - dist) / maxDist * 350f).toInt()
                )
            }
            // Cells beyond n that still fit at this size — only surfaced as drag targets, so a
            // dragged tile can be pushed anywhere there's genuinely free room, not just onto one
            // of the n slots already in play.
            val extraCells = rankedAll.drop(ranked.size).mapIndexed { j, (_, dx, dy) ->
                TileLayout(
                    apps = emptyList(),
                    index = ranked.size + j,
                    centerXDp = offsetX + dx,
                    centerYDp = offsetY + dy,
                    widthDp = hexW.dp,
                    heightDp = hexH.dp,
                    delayMs = 0
                )
            }

            LayoutResult(tileList, extraCells, shrunk = hexW < hexSizeDp.toFloat() - 0.5f)
        }
        val tiles = layoutResult.tiles
        LaunchedEffect(layoutResult.shrunk) { onShrinkToFitChange(layoutResult.shrunk) }

        val density = LocalDensity.current
        fun hitTile(point: Offset): Int? =
            tiles.lastOrNull { hexContains(point, it, density) }?.index
        // Drag targets also consider the not-yet-active cells — see [LayoutResult.extraCells].
        fun hitDragTarget(point: Offset): Int? =
            hitTile(point) ?: layoutResult.extraCells.lastOrNull { hexContains(point, it, density) }?.index

        var activeSlot by remember { mutableStateOf<Int?>(null) }
        var pressActive by remember { mutableStateOf(false) }
        // Set once the 500ms hold elapses — doesn't fire anything by itself anymore (that used to
        // open the color picker immediately, which left no room to distinguish "held still" from
        // "held, then dragged to reorder"). What happens once armed is decided at release time —
        // or the moment the finger moves past the drag slop, whichever comes first.
        var longPressArmed by remember { mutableStateOf(false) }
        var isDragging by remember { mutableStateOf(false) }
        var dragSlot by remember { mutableStateOf<Int?>(null) }
        var dragTargetSlot by remember { mutableStateOf<Int?>(null) }
        var dragOffsetPx by remember { mutableStateOf(Offset.Zero) }
        var downPos by remember { mutableStateOf(Offset.Zero) }
        var lastPointerPos by remember { mutableStateOf(Offset.Zero) }
        // Whether THIS gesture started on background — fixed for the gesture's whole lifetime,
        // unlike activeSlot (which keeps tracking whatever's under the finger pre-arm, including
        // drifting onto background transiently mid-drag-attempt on a slow/imprecise real drag). If
        // the immediate-fire check below used live activeSlot instead, a press that started ON a
        // tile but happened to hover a gap between hexes for half a second on its way to the drop
        // target could pop Settings open mid-drag — this flag scopes the "background press" fast
        // path to presses that were actually ON background from the start.
        var pressStartedOnBackground by remember { mutableStateOf(false) }

        // awaitPointerEvent() only works inside AwaitPointerEventScope's restricted suspension —
        // it can't be raced against a concurrent delay() from inside awaitEachGesture (launching a
        // child coroutine there to run the timer doesn't compile: "restricted suspending functions
        // can invoke suspend functions only on their restricted scope"). So the long-press timer
        // lives out here instead, in a normal LaunchedEffect, and fires straight through to the
        // callback for a background press — nothing else a background press could become, so
        // there's no reason to make the user lift their finger first to see Settings appear. A
        // tile press only gets armed here; the tile case still needs to see whether the finger
        // moves before deciding drag vs. menu, so that decision stays in the gesture loop below.
        LaunchedEffect(activeSlot, pressActive) {
            if (pressActive) {
                delay(500)
                longPressArmed = true
                if (pressStartedOnBackground) onLongPressBackground()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tiles, isOpen) {
                    val topDeadZonePx = with(density) { TOP_DEAD_ZONE_DP.dp.toPx() }
                    val dragSlopPx = with(density) { DRAG_SLOP_DP.dp.toPx() }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        // A touch starting right at the top edge is almost always someone pulling
                        // down the Android status bar/notification shade, not tapping the grid —
                        // without this, that swipe both revealed the status bar AND opened the
                        // grid underneath it, so the next tap landed on a tile the user never
                        // meant to press. Leaving the down event unconsumed here lets the system's
                        // own edge-swipe gesture handle it normally.
                        if (down.position.y < topDeadZonePx) return@awaitEachGesture

                        if (!isOpen) {
                            // closed stage: a quick tap reveals the grid; holding past the
                            // long-press threshold opens Settings directly (fired by the
                            // LaunchedEffect above, while still held) — there's no separate
                            // "search" gesture anymore, long-press on any non-tile area (open or
                            // closed) always reaches Settings the same way.
                            pressActive = true
                            longPressArmed = false
                            activeSlot = null
                            pressStartedOnBackground = true
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.changedToUp()) break
                                }
                                if (!longPressArmed) onOpen()
                            } finally {
                                pressActive = false
                            }
                            return@awaitEachGesture
                        }

                        pressActive = true
                        longPressArmed = false
                        isDragging = false
                        dragSlot = null
                        dragTargetSlot = null
                        dragOffsetPx = Offset.Zero
                        downPos = down.position
                        lastPointerPos = down.position
                        activeSlot = hitTile(down.position)
                        pressStartedOnBackground = activeSlot == null

                        // A sheet/popup appearing mid-press (e.g. long-press opening settings)
                        // can cancel this gesture from underneath instead of delivering a normal
                        // "up" — without the finally, pressActive/activeSlot would stay stuck and
                        // every gesture after that one (including the next long-press) would never
                        // fire again until something else happened to force this whole block to
                        // restart.
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                lastPointerPos = change.position

                                if (!isDragging) {
                                    val movedPx = hypot(change.position.x - downPos.x, change.position.y - downPos.y)
                                    // Any slot is draggable, empty or occupied — dragging an empty
                                    // one onto an occupied one is just the same swap from the other
                                    // direction, and restricting the source to "occupied only" meant
                                    // a long-press that started a hair off the intended tile (landing
                                    // on its empty neighbor instead) silently refused to drag at all.
                                    if (longPressArmed && movedPx > dragSlopPx && activeSlot != null) {
                                        // Held still long enough, then dragged — pick up the tile
                                        // instead of opening the color picker on release.
                                        isDragging = true
                                        dragSlot = activeSlot
                                    } else if (!longPressArmed) {
                                        val hit = hitTile(change.position)
                                        if (hit != activeSlot) activeSlot = hit
                                    }
                                }

                                if (isDragging) {
                                    dragOffsetPx = Offset(change.position.x - downPos.x, change.position.y - downPos.y)
                                    dragTargetSlot = hitDragTarget(change.position)
                                }

                                if (change.changedToUp()) break
                            }

                            if (isDragging) {
                                val from = dragSlot
                                val to = dragTargetSlot
                                if (from != null && to != null && from != to) onReorderSlots(from, to)
                            } else if (longPressArmed) {
                                // Background long-press already fired immediately from the
                                // LaunchedEffect above the moment it armed — only the tile case
                                // (which had to wait and see whether a drag started) is resolved
                                // here, on release.
                                val slot = activeSlot
                                if (slot != null) {
                                    val slotApps = tiles.firstOrNull { it.index == slot }?.apps.orEmpty()
                                    if (slotApps.isNotEmpty()) onLongPressSlot(slotApps, slot, lastPointerPos)
                                }
                            } else {
                                val slot = activeSlot
                                val slotApps = tiles.firstOrNull { it.index == slot }?.apps.orEmpty()
                                when {
                                    slotApps.size == 1 -> onLaunch(slotApps[0])
                                    slotApps.size > 1 -> onOpenFolder(slot!!, slotApps)
                                    slot != null -> onTapEmptySlot(slot)
                                    else -> onCloseBackground()
                                }
                            }
                        } finally {
                            pressActive = false
                            activeSlot = null
                            isDragging = false
                            dragSlot = null
                            dragTargetSlot = null
                            dragOffsetPx = Offset.Zero
                        }
                    }
                }
        ) {
            tiles.forEach { tile ->
                val beingDragged = isDragging && tile.index == dragSlot
                HexTile(
                    tile = tile,
                    isOpen = isOpen,
                    isActive = tile.index == activeSlot || (isDragging && tile.index == dragTargetSlot),
                    isDragging = beingDragged,
                    colorHex = tile.apps.firstOrNull()?.let { tileColors[it.packageName] },
                    showIcon = showIcons,
                    iconSizePercent = iconSizePercent,
                    revealAnimation = revealAnimation,
                    animationSpeed = animationSpeed,
                    modifier = Modifier
                        .offset(tile.centerXDp.dp - tile.widthDp / 2, tile.centerYDp.dp - tile.heightDp / 2)
                        .let {
                            if (beingDragged) {
                                it.offset { IntOffset(dragOffsetPx.x.roundToInt(), dragOffsetPx.y.roundToInt()) }
                                    .zIndex(10f)
                            } else it
                        }
                )
            }

            // Only shown while actively dragging — faint outlines marking every spot that still
            // has room at the current tile size, even ones with no slot in play yet. Brightens on
            // whichever one the dragged tile is currently over, same as the highlight a real slot
            // gets, so it reads as a valid drop target rather than empty decoration.
            if (isDragging) {
                layoutResult.extraCells.forEach { ghost ->
                    val hovered = ghost.index == dragTargetSlot
                    Box(
                        modifier = Modifier
                            .offset(ghost.centerXDp.dp - ghost.widthDp / 2, ghost.centerYDp.dp - ghost.heightDp / 2)
                            .size(width = ghost.widthDp, height = ghost.heightDp)
                            .clip(HexShape)
                            .border(1.dp, if (hovered) LaunColors.fg else LaunColors.border)
                            .alpha(if (hovered) 0.9f else 0.35f)
                    )
                }
            }
        }
    }
}
