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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eversorhn.laun.data.AppInfo
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

/** Pointy-top axial hex coordinate, tagged with the ring it belongs to. */
private data class HexCell(val q: Int, val r: Int, val ring: Int)

private val HEX_DIRS = listOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)

private fun hexRing(radius: Int): List<HexCell> {
    if (radius == 0) return listOf(HexCell(0, 0, 0))
    val result = mutableListOf<HexCell>()
    var q = HEX_DIRS[4].first * radius
    var r = HEX_DIRS[4].second * radius
    for (side in 0 until 6) {
        repeat(radius) {
            result += HexCell(q, r, radius)
            q += HEX_DIRS[side].first
            r += HEX_DIRS[side].second
        }
    }
    return result
}

/** A full symmetric hex-of-hexes for n in RING_COUNTS (1, 7, 19, 37, 61, 91): every ring is complete. */
private fun hexSpiral(n: Int): List<HexCell> {
    val list = mutableListOf(HexCell(0, 0, 0))
    var ring = 1
    while (list.size < n) {
        list += hexRing(ring)
        ring++
    }
    return list.take(n)
}

internal data class TileLayout(
    val app: AppInfo,
    val centerXDp: Float,
    val centerYDp: Float,
    val sizeDp: Dp,
    val fxDp: Float,
    val fyDp: Float,
    val frDeg: Float,
    val delayMs: Int
)

private const val LAYOUT_MARGIN_DP = 16f

/**
 * Symmetric honeycomb (always a complete hex-of-hexes, per RING_COUNTS), auto-shrinking the tile
 * size to fit the available space — a direct port of demo.html's layoutHexes()/hexSpiral(), plus
 * the press-drag-highlight and release-to-launch interaction from the same file.
 */
@Composable
fun HexGrid(
    apps: List<AppInfo>,
    hexSizeDp: Int,
    tileColors: Map<String, String>,
    isOpen: Boolean,
    onOpen: () -> Unit,
    onCloseBackground: () -> Unit,
    onLongPressApp: (AppInfo, Offset) -> Unit,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    if (apps.isEmpty()) return

    BoxWithConstraints(modifier = modifier) {
        val availWDp = max(40f, maxWidth.value - LAYOUT_MARGIN_DP * 2)
        val availHDp = max(40f, maxHeight.value - LAYOUT_MARGIN_DP * 2)
        val boxWDp = maxWidth.value
        val boxHDp = maxHeight.value
        val n = apps.size

        val tiles = remember(apps, hexSizeDp, availWDp, availHDp) {
            val coords = hexSpiral(n)

            // capacity-fit-then-shrink: bounding box at the requested size, scaled down to fit
            var hexW = hexSizeDp.toFloat()
            val s0 = hexW / sqrt(3f)
            val hexH0 = s0 * 2f
            val pts0 = coords.map { c -> Offset(s0 * sqrt(3f) * (c.q + c.r / 2f), s0 * 1.5f * c.r) }
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            pts0.forEach { p ->
                minX = min(minX, p.x - hexW / 2); maxX = max(maxX, p.x + hexW / 2)
                minY = min(minY, p.y - hexH0 / 2); maxY = max(maxY, p.y + hexH0 / 2)
            }
            val scale = min(1f, min(availWDp / (maxX - minX), availHDp / (maxY - minY)))
            if (scale < 1f) hexW *= scale

            val s = hexW / sqrt(3f)
            val hexH = s * 2f
            val pts = coords.map { c -> Offset(s * sqrt(3f) * (c.q + c.r / 2f), s * 1.5f * c.r) }
            var bMinX = Float.MAX_VALUE; var bMaxX = -Float.MAX_VALUE
            var bMinY = Float.MAX_VALUE; var bMaxY = -Float.MAX_VALUE
            pts.forEach { p ->
                bMinX = min(bMinX, p.x - hexW / 2); bMaxX = max(bMaxX, p.x + hexW / 2)
                bMinY = min(bMinY, p.y - hexH / 2); bMaxY = max(bMaxY, p.y + hexH / 2)
            }
            val offsetX = boxWDp / 2 - (bMinX + bMaxX) / 2
            val offsetY = boxHDp / 2 - (bMinY + bMaxY) / 2
            val maxDist = coords.maxOf { hypot(it.q.toFloat(), it.r.toFloat()) }.let { if (it == 0f) 1f else it }

            apps.mapIndexed { i, app ->
                val c = coords[i]
                val p = pts[i]
                val dist = hypot(c.q.toFloat(), c.r.toFloat())
                TileLayout(
                    app = app,
                    centerXDp = offsetX + p.x,
                    centerYDp = offsetY + p.y,
                    sizeDp = hexW.dp,
                    fxDp = p.x * 0.5f,
                    fyDp = p.y * 0.5f,
                    frDeg = Random.nextFloat() * 40f - 20f,
                    delayMs = ((maxDist - dist) / maxDist * 420f).toInt()
                )
            }
        }

        var tileRects by remember { mutableStateOf<Map<String, Rect>>(emptyMap()) }
        var activePackage by remember { mutableStateOf<String?>(null) }
        var pressActive by remember { mutableStateOf(false) }
        var longPressTriggered by remember { mutableStateOf(false) }
        var lastPointerPos by remember { mutableStateOf(Offset.Zero) }

        // Long-press: (re)starts whenever the highlighted tile changes, matching the demo's
        // setActiveFace() — moving to a different tile resets the timer, holding still fires it.
        LaunchedEffect(activePackage, pressActive) {
            val pkg = activePackage
            if (pkg != null && pressActive) {
                delay(500)
                longPressTriggered = true
                val app = tiles.firstOrNull { it.app.packageName == pkg }?.app
                if (app != null) onLongPressApp(app, lastPointerPos)
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
                        activePackage = tileRects.entries.firstOrNull { it.value.contains(down.position) }?.key

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            lastPointerPos = change.position
                            if (!longPressTriggered) {
                                val hit = tileRects.entries.firstOrNull { it.value.contains(change.position) }?.key
                                if (hit != activePackage) activePackage = hit
                            }
                            if (change.changedToUp()) break
                        }

                        if (!longPressTriggered) {
                            val app = tiles.firstOrNull { it.app.packageName == activePackage }?.app
                            if (app != null) onLaunch(app) else onCloseBackground()
                        }
                        pressActive = false
                        activePackage = null
                    }
                }
        ) {
            tiles.forEach { tile ->
                HexTile(
                    tile = tile,
                    isOpen = isOpen,
                    isActive = tile.app.packageName == activePackage,
                    colorHex = tileColors[tile.app.packageName],
                    modifier = Modifier
                        .offset(tile.centerXDp.dp - tile.sizeDp / 2, tile.centerYDp.dp - tile.sizeDp / 2)
                        .onGloballyPositioned { coords ->
                            tileRects = tileRects + (tile.app.packageName to coords.boundsInParent())
                        }
                )
            }
        }
    }
}
