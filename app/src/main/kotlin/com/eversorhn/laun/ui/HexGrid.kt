package com.eversorhn.laun.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eversorhn.laun.data.AppInfo
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

private data class RectCapacity(val cols: Int, val rows: Int, val hexH: Float)

/** How many pointy-top hex columns/rows of width [hexW] fit an [availW]x[availH] rectangle. */
private fun hexCapacity(hexW: Float, availW: Float, availH: Float): RectCapacity {
    val s = hexW / sqrt(3f)
    val hexH = s * 2f
    val cols = max(1, (availW / hexW).toInt())
    val rows = max(1, ((availH - hexH * 0.25f) / (hexH * 0.75f)).toInt() + 1)
    return RectCapacity(cols, rows, hexH)
}

internal data class TileLayout(
    val app: AppInfo?,
    val index: Int,
    val centerXDp: Float,
    val centerYDp: Float,
    val widthDp: Dp,
    val heightDp: Dp,
    val fxDp: Float,
    val fyDp: Float,
    val frDeg: Float,
    val delayMs: Int
)

private data class LayoutResult(val tiles: List<TileLayout>, val shrunk: Boolean)

private const val LAYOUT_MARGIN_DP = 32f
private const val TILE_RENDER_SCALE = 0.9f

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
 * [slots] is always exactly [hexCount]-sized, index-stable: null means the slot is unassigned.
 */
@Composable
fun HexGrid(
    slots: List<AppInfo?>,
    hexSizeDp: Int,
    tileColors: Map<String, String>,
    isOpen: Boolean,
    onOpen: () -> Unit,
    onCloseBackground: () -> Unit,
    onLongPressApp: (AppInfo, Int, Offset) -> Unit,
    onLongPressBackground: () -> Unit,
    onTapEmptySlot: (Int) -> Unit,
    onLaunch: (AppInfo) -> Unit,
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
            var hexW = hexSizeDp.toFloat()
            var cap = hexCapacity(hexW, availWDp, availHDp)
            while (cap.cols * cap.rows < n && hexW > 20f) {
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
            val ranked = cells
                .map { c -> Triple(c, c.x - cx, c.y - cy) }
                .sortedBy { (_, dx, dy) -> dx * dx + dy * dy }
                .take(n)

            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            ranked.forEach { (_, dx, dy) ->
                minX = min(minX, dx - hexW / 2); maxX = max(maxX, dx + hexW / 2)
                minY = min(minY, dy - hexH / 2); maxY = max(maxY, dy + hexH / 2)
            }
            val offsetX = boxWDp / 2 - (minX + maxX) / 2
            val offsetY = boxHDp / 2 - (minY + maxY) / 2
            val maxDist = ranked.maxOf { (_, dx, dy) -> sqrt(dx * dx + dy * dy) }.let { if (it == 0f) 1f else it }

            val tileList = slots.mapIndexed { i, app ->
                val (_, dx, dy) = ranked[i]
                val dist = sqrt(dx * dx + dy * dy)
                TileLayout(
                    app = app,
                    index = i,
                    centerXDp = offsetX + dx,
                    centerYDp = offsetY + dy,
                    widthDp = hexW.dp,
                    heightDp = hexH.dp,
                    fxDp = dx * 0.5f,
                    fyDp = dy * 0.5f,
                    frDeg = Random.nextFloat() * 40f - 20f,
                    delayMs = ((maxDist - dist) / maxDist * 420f).toInt()
                )
            }
            LayoutResult(tileList, shrunk = hexW < hexSizeDp.toFloat() - 0.5f)
        }
        val tiles = layoutResult.tiles
        LaunchedEffect(layoutResult.shrunk) { onShrinkToFitChange(layoutResult.shrunk) }

        val density = LocalDensity.current
        fun hitTile(point: Offset): Int? =
            tiles.lastOrNull { hexContains(point, it, density) }?.index

        var activeSlot by remember { mutableStateOf<Int?>(null) }
        var pressActive by remember { mutableStateOf(false) }
        var longPressTriggered by remember { mutableStateOf(false) }
        var lastPointerPos by remember { mutableStateOf(Offset.Zero) }

        // Long-press: (re)starts whenever the highlighted target changes, matching the demo's
        // setActiveFace() — moving to a different tile (or on/off the background) resets the
        // timer, holding still fires it. On an occupied tile: opens the color/clear picker. On
        // empty background (no tile under the pointer): opens settings — there's no button for
        // it. Long-pressing an empty slot does nothing (its tap action is already "add app").
        LaunchedEffect(activeSlot, pressActive) {
            if (pressActive) {
                delay(500)
                longPressTriggered = true
                val slot = activeSlot
                if (slot != null) {
                    val app = tiles.firstOrNull { it.index == slot }?.app
                    if (app != null) onLongPressApp(app, slot, lastPointerPos)
                } else {
                    onLongPressBackground()
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(tiles, isOpen) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        if (!isOpen) {
                            // closed stage: any tap anywhere reveals the grid, mirroring the
                            // demo's #stage click-to-open — no hit-testing needed yet.
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (change.changedToUp()) break
                            }
                            onOpen()
                            return@awaitEachGesture
                        }

                        pressActive = true
                        longPressTriggered = false
                        lastPointerPos = down.position
                        activeSlot = hitTile(down.position)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            lastPointerPos = change.position
                            if (!longPressTriggered) {
                                val hit = hitTile(change.position)
                                if (hit != activeSlot) activeSlot = hit
                            }
                            if (change.changedToUp()) break
                        }

                        if (!longPressTriggered) {
                            val slot = activeSlot
                            val app = tiles.firstOrNull { it.index == slot }?.app
                            when {
                                app != null -> onLaunch(app)
                                slot != null -> onTapEmptySlot(slot)
                                else -> onCloseBackground()
                            }
                        }
                        pressActive = false
                        activeSlot = null
                    }
                }
        ) {
            tiles.forEach { tile ->
                HexTile(
                    tile = tile,
                    isOpen = isOpen,
                    isActive = tile.index == activeSlot,
                    colorHex = tile.app?.let { tileColors[it.packageName] },
                    modifier = Modifier
                        .offset(tile.centerXDp.dp - tile.widthDp / 2, tile.centerYDp.dp - tile.heightDp / 2)
                )
            }
        }
    }
}
