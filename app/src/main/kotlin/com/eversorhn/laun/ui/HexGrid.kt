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
    /** Cells that fit on screen beyond [tiles]' own COUNT, still empty — only computed (non-empty)
     *  while free tile placement is on; surfaced as drag targets, not rendered otherwise. */
    val extraCells: List<TileLayout>,
    val shrunk: Boolean
)

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
    /** When on (and free position mode is off), a drag can be released anywhere near a slot —
     *  not precisely on its own small hex hitbox — and it snaps to whichever slot's center is
     *  closest, same grid-aligned result as a normal drag, just far more forgiving to aim. */
    snapMode: Boolean = false,
    /** When on, a drag can also target empty cells beyond [slots]' own COUNT — as many as still
     *  physically fit on screen at [hexSizeDp] — and dropping onto one pins the tile there via
     *  [onReorderSlots] without touching any other tile. Off, only the COUNT cells already in
     *  play are ever offered as drag targets, same as a normal drag. */
    freeTilePlacement: Boolean = false,
    /** Tiles pinned beyond [slots]' own COUNT via [freeTilePlacement] — index is the same rank
     *  space as the honeycomb's own cells, just past where [slots] ends. Rendered at a fixed
     *  screen position like any other tile as long as that rank still fits on screen; index and
     *  content are otherwise entirely up to the caller (same storage as [slots], just keyed past
     *  its end). Known limitation of reusing rank-space for this rather than a stable id: raising
     *  COUNT enough can pull a pinned index back inside [slots]' own range, at which point it
     *  silently becomes an ordinary swappable tile wherever that rank now falls — acceptable since
     *  it can only happen by deliberately raising COUNT, but worth knowing before relying on a pin
     *  staying put forever. */
    extraOccupiedSlots: Map<Int, List<AppInfo>> = emptyMap(),
    /** Fires whenever the set of [extraOccupiedSlots] keys actually rendered on screen right now
     *  changes — see the visibleExtraIndices comment below for why the caller needs this. */
    onVisibleExtraIndicesChange: (Set<Int>) -> Unit = {},
    /** Explicit per-slot (x, y) in dp, top-left origin — only consulted while [freePositionMode]
     *  is on; a slot missing here still renders at its normal honeycomb position until dragged. */
    freeformPositions: Map<Int, Offset> = emptyMap(),
    /** Seconds of holding still on a tile before its color menu auto-opens — 1..60; the arm delay
     *  itself (drag-vs-tap distinction) always stays 500ms regardless, this only controls the
     *  further grace window after that before the menu fires on its own. */
    colorMenuAutoOpenSeconds: Int = 1,
    /** Seconds of holding still on empty background before the main SETTINGS sheet auto-opens —
     *  1..60, same shape as [colorMenuAutoOpenSeconds] but for [onLongPressBackground] instead. */
    mainMenuAutoOpenSeconds: Int = 1,
    /** Per-slot size as a percent of [hexSizeDp] — 50..200. A slot missing here renders at the
     *  shared size. Resizing a tile doesn't move it or its neighbors, so an enlarged tile can
     *  visually overlap adjacent ones in the normal honeycomb — expected, same tradeoff as
     *  free-position mode's own tiles-can-crowd-each-other-loosely nature. */
    tileSizeOverrides: Map<Int, Int> = emptyMap(),
    /** Extra gap between adjacent tile centers, on top of their own size — 0 is edge-to-edge like
     *  the honeycomb always used to be. Grows the honeycomb's own cell pitch, not the tiles
     *  themselves, so it reduces how many fit on screen at a given [hexSizeDp] same as a bigger
     *  tile size would. */
    tileSpacingDp: Int = 0,
    /** Screen margin per edge, dp — independent of each other, unlike the honeycomb's single
     *  edge-to-edge default before this existed. Only shrinks the honeycomb's own available
     *  rectangle and where it's centered; [freePositionMode] tiles ignore it entirely by design
     *  (see [resolveFreePosition]). */
    marginTopDp: Int = 32,
    marginBottomDp: Int = 32,
    marginStartDp: Int = 32,
    marginEndDp: Int = 32,
    /** When on, an unassigned slot isn't drawn at all — no "+" placeholder — while idle. It's
     *  still there and still tappable (same hitbox, same [onTapEmptySlot]) so assigning an app to
     *  a specific position still works; the one exception is the slot currently under the finger
     *  (pressed, long-pressed, or being dragged), which stays visible for feedback even while
     *  otherwise empty. */
    hideEmptyTiles: Boolean = false,
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
        val availWDp = max(40f, maxWidth.value - marginStartDp - marginEndDp)
        val availHDp = max(40f, maxHeight.value - marginTopDp - marginBottomDp)
        val boxWDp = maxWidth.value
        val boxHDp = maxHeight.value
        val n = slots.size

        val layoutResult = remember(
            slots, hexSizeDp, availWDp, availHDp, freePositionMode, freeformPositions, boxWDp, boxHDp,
            tileSizeOverrides, freeTilePlacement, extraOccupiedSlots, tileSpacingDp, marginTopDp, marginStartDp
        ) {
            // Fill the actual screen rectangle (both width and height), not just a fixed
            // symmetric hex-of-hexes outline: find how many cols/rows fit at the requested tile
            // size, only shrinking the size if even a 1x1 grid wouldn't fit n tiles.
            // Odd rows have one fewer cell (see below), so the real capacity is cols*rows minus
            // the number of odd rows — using plain cols*rows here undercounted how much shrinking
            // was needed and let n exceed the actual cell count, crashing the ranked[i] lookup below.
            fun actualCapacity(c: RectCapacity) = c.cols * c.rows - c.rows / 2

            val spacing = tileSpacingDp.toFloat()
            var hexW = hexSizeDp.toFloat()
            // Capacity/position math runs on the *pitch* (tile + spacing), not the tile's own
            // size — that's what makes spacing shrink how many fit without shrinking the tiles
            // themselves. The tile's actual rendered size (hexW/hexH below) stays spacing-free.
            var cap = hexCapacity(hexW + spacing, availWDp, availHDp)
            while (actualCapacity(cap) < n && hexW > 20f) {
                hexW -= 1f
                cap = hexCapacity(hexW + spacing, availWDp, availHDp)
            }
            val cols = cap.cols
            val rows = cap.rows
            val pitchW = hexW + spacing
            val pitchH = cap.hexH
            val hexH = hexW / sqrt(3f) * 2f

            // Every honeycomb cell that fits the rectangle, offset-staggered rows. Odd rows get
            // one fewer tile than even rows (5,4,5,4,...) rather than the same count shifted —
            // that's what keeps each row centered under the next or previous instead of the
            // whole row just sliding right, so the overall shape stays symmetric at any size/count.
            val cells = ArrayList<Offset>(cols * rows)
            for (row in 0 until rows) {
                val rowCols = if (row % 2 == 0) cols else max(0, cols - 1)
                for (col in 0 until rowCols) {
                    cells += Offset(col * pitchW + (if (row % 2 == 1) pitchW / 2 else 0f), row * pitchH * 0.75f)
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
                minX = min(minX, dx - pitchW / 2); maxX = max(maxX, dx + pitchW / 2)
                minY = min(minY, dy - pitchH / 2); maxY = max(maxY, dy + pitchH / 2)
            }
            // Centered within the space marginStartDp..(boxWDp - marginEndDp), not the raw box —
            // equal margins reduce to the old boxWDp/2 centering exactly.
            val offsetX = marginStartDp + availWDp / 2 - (minX + maxX) / 2
            val offsetY = marginTopDp + availHDp / 2 - (minY + maxY) / 2
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
            // Cells beyond COUNT that still fit the screen at this tile size — pinned tiles render
            // there regardless of [freeTilePlacement] (once placed, an app stays put same as any
            // other tile); only whether NEW empty ones are offered as drag targets depends on the
            // toggle (see extraCells below). Bounded to what rankedAll actually generated, same
            // defensive reasoning as ranked.size above — a pin whose rank no longer fits on screen
            // (smaller tile size, smaller screen) simply isn't shown until it fits again.
            val pinnedIndices = extraOccupiedSlots.keys.filter { it >= ranked.size && it < rankedAll.size }.toSet()
            val pinnedTiles = pinnedIndices.mapNotNull { idx ->
                val apps = extraOccupiedSlots[idx] ?: return@mapNotNull null
                val (_, dx, dy) = rankedAll[idx]
                TileLayout(
                    apps = apps,
                    index = idx,
                    centerXDp = offsetX + dx,
                    centerYDp = offsetY + dy,
                    widthDp = hexW.dp,
                    heightDp = hexH.dp,
                    delayMs = 0
                )
            }
            val positionedTiles = rankedTiles + pinnedTiles
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

            // Genuinely free landing spots beyond COUNT — only surfaced while free tile placement
            // is on, so a plain drag never picks up an invisible target the user can't see. No
            // adjacency restriction: every cell that fits the screen at this size is offered,
            // exactly like the honeycomb's own COUNT cells are.
            val extraCells = if (freeTilePlacement) {
                (ranked.size until rankedAll.size).filterNot { it in pinnedIndices }.map { idx ->
                    val (_, dx, dy) = rankedAll[idx]
                    TileLayout(
                        apps = emptyList(),
                        index = idx,
                        centerXDp = offsetX + dx,
                        centerYDp = offsetY + dy,
                        widthDp = hexW.dp,
                        heightDp = hexH.dp,
                        delayMs = 0
                    )
                }
            } else emptyList()

            LayoutResult(sizedTileList, extraCells, shrunk = hexW < hexSizeDp.toFloat() - 0.5f)
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
        val extraCells = layoutResult.extraCells
        LaunchedEffect(layoutResult.shrunk) { onShrinkToFitChange(layoutResult.shrunk) }
        // Which pinned (beyond-[slots]) indices are actually rendered right now — shrinks or grows
        // with SIZE/COUNT/spacing/margins exactly like the rest of the layout. The caller needs
        // this to know whether a pinned app is genuinely reachable on screen, since an app pinned
        // at an index the current layout no longer has room for isn't just hidden — with nothing
        // marking it "still assigned," it'd otherwise silently block that app from being picked
        // again anywhere else, with no visible tile to explain why.
        val visibleExtraIndices = remember(tiles, slots.size) {
            tiles.mapNotNull { it.index.takeIf { idx -> idx >= slots.size } }.toSet()
        }
        LaunchedEffect(visibleExtraIndices) { onVisibleExtraIndicesChange(visibleExtraIndices) }

        val density = LocalDensity.current
        fun hitTile(point: Offset): Int? =
            tiles.lastOrNull { hexContains(point, it, density) }?.index
        // Snap mode's whole point is a forgiving target — released anywhere, not just precisely
        // on the target's own small hex hitbox — so this picks whichever of [candidates] center
        // is closest to the point instead of requiring the point to land inside a hex shape at
        // all. Defaults to the honeycomb's own tiles (plain Snap Mode); free tile placement passes
        // tiles + extraCells too, so a release can also land on genuinely empty screen space.
        fun nearestTile(point: Offset, candidates: List<TileLayout> = tiles): Int? =
            candidates.minByOrNull { tile ->
                val cx = with(density) { tile.centerXDp.dp.toPx() }
                val cy = with(density) { tile.centerYDp.dp.toPx() }
                hypot((point.x - cx).toDouble(), (point.y - cy).toDouble())
            }?.index

        // A geometry change while empty tiles are hidden would otherwise be invisible — SPACING
        // or a MARGIN slider has nothing to show for itself if every affected cell is blank and
        // undrawn. Briefly showing them on every such change (not just the first) is what makes
        // dragging one of those sliders from Settings actually readable.
        var revealHiddenEmpty by remember { mutableStateOf(false) }
        LaunchedEffect(hexSizeDp, slots.size, tileSpacingDp, marginTopDp, marginBottomDp, marginStartDp, marginEndDp, hideEmptyTiles) {
            if (hideEmptyTiles) {
                revealHiddenEmpty = true
                delay(900)
                revealHiddenEmpty = false
            }
        }

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
                    // Same total press-down-to-auto-fire shape as the tile branch below: the
                    // 500ms arm delay above already ate part of mainMenuAutoOpenSeconds, this
                    // grace window makes up the rest (floor of 500ms so lifting the finger right
                    // as it arms can't fire Settings on a press that was never really "held").
                    val graceMs = (mainMenuAutoOpenSeconds.coerceIn(1, 60) * 1000L - 500L).coerceAtLeast(500L)
                    delay(graceMs)
                    if (pressActive) onLongPressBackground()
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
                .pointerInput(tiles, extraCells, isOpen, freePositionMode, snapMode, freeTilePlacement) {
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
                                    dragTargetSlot = when {
                                        freePositionMode -> null
                                        freeTilePlacement -> nearestTile(change.position, tiles + extraCells)
                                        snapMode -> nearestTile(change.position)
                                        else -> hitTile(change.position)
                                    }
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
                                    // A genuine swipe-up always reaches search, even if the finger
                                    // happened to land on a tile first — otherwise an accidental
                                    // tile hit while swiping up opens that tile's own app-search
                                    // (to add an app to it) instead of the intended global search.
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
                // An idle empty slot stays undrawn (still tappable — see onTapEmptySlot below,
                // hit-testing works off geometry, not what's rendered) — except the one currently
                // under the finger (pressed or dragged), the one a drag is currently hovering as
                // its drop target, or right after a layout change, all of which need it visible
                // for feedback (see revealHiddenEmpty above).
                if (hideEmptyTiles && !revealHiddenEmpty && tile.apps.isEmpty() &&
                    tile.index != activeSlot && tile.index != dragTargetSlot && !beingDragged
                ) {
                    return@forEach
                }
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

            // Genuinely empty landing spots beyond COUNT — only shown while actually dragging in
            // free tile placement mode, so the rest of the time the screen looks exactly like a
            // normal grid. Faint on purpose: they're a drop target, not a real tile. Free position
            // mode overrides free tile placement entirely (see dragTargetSlot's own when-branch
            // order above) — without the same precedence here, these markers would still show up
            // during a free-position drag even though nothing can actually snap to them.
            if (isDragging && freeTilePlacement && !freePositionMode) {
                extraCells.forEach { cell ->
                    HexTile(
                        tile = cell,
                        isOpen = isOpen,
                        isActive = cell.index == dragTargetSlot,
                        colorHex = null,
                        showIcon = false,
                        revealAnimation = -1,
                        animationSpeed = animationSpeed,
                        modifier = Modifier
                            .offset(cell.centerXDp.dp - cell.widthDp / 2, cell.centerYDp.dp - cell.heightDp / 2)
                            .alpha(0.35f)
                    )
                }
            }
        }
    }
}
