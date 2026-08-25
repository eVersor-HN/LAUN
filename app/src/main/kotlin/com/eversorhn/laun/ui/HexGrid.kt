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
    val shrunk: Boolean
)

private const val LAYOUT_MARGIN_DP = 32f
private const val TILE_RENDER_SCALE = 0.9f
private const val TOP_DEAD_ZONE_DP = 24f
private const val DRAG_SLOP_DP = 12f
/** How far a background press has to travel straight up before release counts as "swipe up to
 *  search" instead of a tap/long-press — deliberately more than a flick so it can't be triggered
 *  by accident while just opening the grid. */
private const val SWIPE_UP_THRESHOLD_DP = 72f

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
 * Free-position mode's one remaining constraint: a tile can be dropped anywhere on screen (no
 * grid, no edge margin) but not close enough to overlap another tile. [candidateTopLeft] is
 * pushed away from any [others] entry (also top-left dp) that's closer than [hexW] center-to-
 * center, then re-clamped to stay fully on screen — pushing can walk it back out of bounds, so
 * clamp is re-applied after resolving, in that order, a few times to settle multi-tile crowding.
 */
private fun resolveFreePosition(
    candidateTopLeft: Offset,
    hexW: Float,
    hexH: Float,
    boxWDp: Float,
    boxHDp: Float,
    others: List<Offset>
): Offset {
    var pos = candidateTopLeft
    val maxX = max(0f, boxWDp - hexW)
    val maxY = max(0f, boxHDp - hexH)
    repeat(8) {
        pos = Offset(pos.x.coerceIn(0f, maxX), pos.y.coerceIn(0f, maxY))
        val centerX = pos.x + hexW / 2
        val centerY = pos.y + hexH / 2
        val conflict = others.firstOrNull { other ->
            val ocx = other.x + hexW / 2
            val ocy = other.y + hexH / 2
            hypot((centerX - ocx).toDouble(), (centerY - ocy).toDouble()).toFloat() < hexW
        } ?: return pos
        val ocx = conflict.x + hexW / 2
        val ocy = conflict.y + hexH / 2
        val dx = centerX - ocx
        val dy = centerY - ocy
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        val (dirX, dirY) = if (dist < 0.01f) 1f to 0f else dx / dist to dy / dist
        val pushedCenterX = ocx + dirX * hexW
        val pushedCenterY = ocy + dirY * hexH
        pos = Offset(pushedCenterX - hexW / 2, pushedCenterY - hexH / 2)
    }
    return Offset(pos.x.coerceIn(0f, maxX), pos.y.coerceIn(0f, maxY))
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
    /** When on, a drag drops the tile at the exact pixel released — no grid snapping, no edge
     *  margin — instead of resolving through [onReorderSlots] at all. Tiles still can't end up
     *  close enough to overlap (see [resolveFreePosition]), which is the one constraint kept. */
    freePositionMode: Boolean = false,
    /** Explicit per-slot (x, y) in dp, top-left origin — only consulted while [freePositionMode]
     *  is on; a slot missing here still renders at its normal honeycomb position until dragged. */
    freeformPositions: Map<Int, Offset> = emptyMap(),
    /** Seconds of holding still on a tile before its color menu auto-opens — 1..60; the arm delay
     *  itself (drag-vs-tap distinction) always stays 500ms regardless, this only controls the
     *  further grace window after that before the menu fires on its own. */
    colorMenuAutoOpenSeconds: Int = 1,
    /** Per-slot size as a percent of [hexSizeDp] — 50..200. A slot missing here renders at the
     *  shared size. Resizing a tile doesn't move it or its neighbors, so an enlarged tile can
     *  visually overlap adjacent ones in the normal honeycomb — expected, same tradeoff as
     *  free-position mode's own tiles-can-crowd-each-other-loosely nature. */
    tileSizeOverrides: Map<Int, Int> = emptyMap(),
    onOpen: () -> Unit,
    onCloseBackground: () -> Unit,
    onLongPressSlot: (List<AppInfo>, Int, Offset) -> Unit,
    onLongPressBackground: () -> Unit,
    /** A background press (open or closed stage) that travels straight up past
     *  [SWIPE_UP_THRESHOLD_DP] before release — anywhere on screen, not tied to a tile. */
    onSwipeUpBackground: () -> Unit = {},
    onTapEmptySlot: (Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenFolder: (Int, List<AppInfo>) -> Unit,
    onReorderSlots: (from: Int, to: Int) -> Unit,
    onFreeformPositionChange: (index: Int, position: Offset) -> Unit = { _, _ -> },
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

        val layoutResult = remember(slots, hexSizeDp, availWDp, availHDp, freePositionMode, freeformPositions, boxWDp, boxHDp, tileSizeOverrides) {
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
            val rankedTiles = slots.take(ranked.size).mapIndexed { i, slotApps ->
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
            // Exactly the current COUNT worth of tiles, always — no tile beyond it is ever
            // rendered, pinned, or otherwise kept visible, so COUNT is the one source of truth for
            // how many tiles are on screen. An app sitting in a slot index beyond COUNT (e.g. left
            // over after COUNT was lowered) simply isn't shown until COUNT is raised again or the
            // app is reassigned to a slot within range via the app picker.
            val positionedTiles = rankedTiles
            // Explicit freeform positions override the honeycomb slot entirely while the mode is
            // on — a tile the user hasn't dragged yet still falls back to its normal position, so
            // switching the mode on doesn't scatter everything to (0,0) first.
            val tileList = if (freePositionMode) {
                positionedTiles.map { tile ->
                    val pos = freeformPositions[tile.index] ?: return@map tile
                    // Re-clamped defensively at render time too (not just at drop time) in case the
                    // screen size changed since — e.g. rotation — and the stored position no longer
                    // fits.
                    val clampedX = pos.x.coerceIn(0f, max(0f, boxWDp - hexW))
                    val clampedY = pos.y.coerceIn(0f, max(0f, boxHDp - hexH))
                    tile.copy(centerXDp = clampedX + hexW / 2, centerYDp = clampedY + hexH / 2)
                }
            } else positionedTiles

            // A per-tile size override scales around the tile's own already-resolved center — it
            // never shifts position, whether that position came from the honeycomb or a freeform
            // drag, so resizing never itself moves anything.
            val sizedTileList = if (tileSizeOverrides.isEmpty()) tileList else tileList.map { tile ->
                val percent = tileSizeOverrides[tile.index] ?: return@map tile
                val scale = percent.coerceIn(50, 200) / 100f
                tile.copy(widthDp = tile.widthDp * scale, heightDp = tile.heightDp * scale)
            }

            LayoutResult(sizedTileList, shrunk = hexW < hexSizeDp.toFloat() - 0.5f)
        }
        // In free position mode tiles can visually overlap (spacing is push-apart, not exact),
        // and draw order = hit-test priority order (see hitTile below). Without this, an empty
        // "+" placeholder drawn after a real tile can occlude it entirely. Occupied tiles always
        // draw on top of empty ones; order is otherwise stable.
        val tiles = if (freePositionMode) {
            layoutResult.tiles.sortedBy { it.apps.isNotEmpty() }
        } else {
            layoutResult.tiles
        }
        LaunchedEffect(layoutResult.shrunk) { onShrinkToFitChange(layoutResult.shrunk) }

        val density = LocalDensity.current
        fun hitTile(point: Offset): Int? =
            tiles.lastOrNull { hexContains(point, it, density) }?.index

        var activeSlot by remember { mutableStateOf<Int?>(null) }
        var pressActive by remember { mutableStateOf(false) }
        // Set once the 500ms hold elapses. A tile press doesn't resolve to anything by itself right
        // here — the gesture loop still needs a short further window to see whether the finger moves
        // (drag) before the color menu auto-fires from the LaunchedEffect below.
        var longPressArmed by remember { mutableStateOf(false) }
        // Guards against firing the color menu twice — once from the auto-fire timer while still
        // held, and again from the release-time fallback below (for a press that got released
        // before the timer's grace window elapsed).
        var longPressSlotFired by remember { mutableStateOf(false) }
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
        // there's no reason to make the user lift their finger first to see Settings appear.
        // A tile press gets a further grace window after arming before its color menu auto-fires
        // the same way — long enough that a real drag (which moves almost immediately once armed)
        // has already set isDragging and pre-empts it, short enough that just holding still opens
        // the menu on its own instead of waiting for the finger to lift.
        LaunchedEffect(activeSlot, pressActive) {
            if (pressActive) {
                delay(500)
                longPressArmed = true
                if (pressStartedOnBackground) {
                    onLongPressBackground()
                } else {
                    // Total time from press-down to auto-fire is colorMenuAutoOpenSeconds — the
                    // 500ms arm delay above already ate part of that, and this grace window (kept
                    // at least 500ms so a real drag, which moves almost immediately once armed,
                    // still has a moment to set isDragging and pre-empt it) makes up the rest.
                    val graceMs = (colorMenuAutoOpenSeconds.coerceIn(1, 60) * 1000L - 500L).coerceAtLeast(500L)
                    delay(graceMs)
                    val slot = activeSlot
                    if (pressActive && !isDragging && slot != null) {
                        val slotApps = tiles.firstOrNull { it.index == slot }?.apps.orEmpty()
                        if (slotApps.isNotEmpty()) {
                            longPressSlotFired = true
                            onLongPressSlot(slotApps, slot, lastPointerPos)
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // freePositionMode is included even though it never changes tiles' own content by
                // itself — the drag-release branch below reads it directly, and pointerInput only
                // gets a fresh closure (with that decision's current value) when one of its keys
                // actually changes, not on every recomposition.
                .pointerInput(tiles, isOpen, freePositionMode) {
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
                            // LaunchedEffect above, while still held); swiping straight up opens
                            // search instead — long-press on any non-tile area (open or closed)
                            // always reaches Settings, and swipe-up always reaches search, the
                            // same way regardless of stage.
                            pressActive = true
                            longPressArmed = false
                            activeSlot = null
                            pressStartedOnBackground = true
                            val swipeStartPos = down.position
                            var lastPos = down.position
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    lastPos = change.position
                                    if (change.changedToUp()) break
                                }
                                val upDist = swipeStartPos.y - lastPos.y
                                val sideDist = abs(lastPos.x - swipeStartPos.x)
                                val swipeUpThresholdPx = with(density) { SWIPE_UP_THRESHOLD_DP.dp.toPx() }
                                when {
                                    longPressArmed -> {}
                                    upDist > swipeUpThresholdPx && upDist > sideDist -> onSwipeUpBackground()
                                    else -> onOpen()
                                }
                            } finally {
                                pressActive = false
                            }
                            return@awaitEachGesture
                        }

                        pressActive = true
                        longPressArmed = false
                        longPressSlotFired = false
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
                                    // Free-position mode has no discrete drop targets — the tile
                                    // just follows the finger and lands wherever it's released.
                                    if (!freePositionMode) dragTargetSlot = hitTile(change.position)
                                }

                                if (change.changedToUp()) break
                            }

                            if (isDragging) {
                                val from = dragSlot
                                if (from != null && freePositionMode) {
                                    val original = tiles.firstOrNull { it.index == from }
                                    if (original != null) {
                                        val hexWDp = original.widthDp.value
                                        val hexHDp = original.heightDp.value
                                        val deltaXDp = with(density) { dragOffsetPx.x.toDp().value }
                                        val deltaYDp = with(density) { dragOffsetPx.y.toDp().value }
                                        val candidateTopLeft = Offset(
                                            original.centerXDp - hexWDp / 2 + deltaXDp,
                                            original.centerYDp - hexHDp / 2 + deltaYDp
                                        )
                                        val others = tiles.filter { it.index != from }
                                            .map { Offset(it.centerXDp - it.widthDp.value / 2, it.centerYDp - it.heightDp.value / 2) }
                                        val resolved = resolveFreePosition(candidateTopLeft, hexWDp, hexHDp, boxWDp, boxHDp, others)
                                        onFreeformPositionChange(from, resolved)
                                    }
                                } else {
                                    val to = dragTargetSlot
                                    if (from != null && to != null && from != to) onReorderSlots(from, to)
                                }
                            } else if (longPressArmed && !longPressSlotFired) {
                                // Background long-press, and most tile long-presses, already fired
                                // immediately from the LaunchedEffect above once armed (plus its
                                // grace window) — this only catches a tile press released before
                                // that window elapsed, so the color menu still opens rather than
                                // being lost entirely.
                                val slot = activeSlot
                                if (slot != null) {
                                    val slotApps = tiles.firstOrNull { it.index == slot }?.apps.orEmpty()
                                    if (slotApps.isNotEmpty()) onLongPressSlot(slotApps, slot, lastPointerPos)
                                }
                            } else if (!isDragging && !longPressArmed) {
                                val slot = activeSlot
                                val slotApps = tiles.firstOrNull { it.index == slot }?.apps.orEmpty()
                                val upDist = downPos.y - lastPointerPos.y
                                val sideDist = abs(lastPointerPos.x - downPos.x)
                                val swipeUpThresholdPx = with(density) { SWIPE_UP_THRESHOLD_DP.dp.toPx() }
                                when {
                                    slot == null && pressStartedOnBackground &&
                                        upDist > swipeUpThresholdPx && upDist > sideDist -> onSwipeUpBackground()
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

        }
    }
}
