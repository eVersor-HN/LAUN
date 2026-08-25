package com.eversorhn.laun.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Selectable animated background rendered behind the hex grid — a native Compose Canvas port of
 * 8 concepts prototyped in wallpaper-demo.html. Frame time is read only inside the Canvas draw
 * lambda (never in the composable body), same trick as HexTile's reveal animations: this drives
 * continuous per-frame redraws without ever triggering full recomposition, so it stays smooth
 * behind the grid regardless of how many tiles are on screen.
 */
@Composable
fun AnimatedWallpaper(kind: Int, opacity: Float = 1f, modifier: Modifier = Modifier) {
    if (kind !in 0..7) return

    var frameTimeMs by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val startNanos = withFrameNanosCompat()
        while (isActive) {
            val now = withFrameNanosCompat()
            frameTimeMs = (now - startNanos) / 1_000_000f
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val neuro = remember(kind) { NeuroLinksState() }
    val circuit = remember(kind) { CircuitTraceState() }
    val server = remember(kind) { ServerGridState() }
    val threat = remember(kind) { ThreatMapState() }
    val shards = remember(kind) { ShardDriftState() }
    val cipher = remember(kind) { CipherScrollState() }
    val stars = remember(kind) { StarfieldState() }

    Canvas(modifier = modifier.fillMaxSize().alpha(opacity.coerceIn(0f, 1f))) {
        val t = frameTimeMs
        when (kind) {
            0 -> drawNeuroLinks(neuro, t)
            1 -> drawCircuitTrace(circuit, t)
            2 -> drawWarpTunnel(t)
            3 -> drawServerGrid(server, t)
            4 -> drawThreatMap(threat, t)
            5 -> drawShardDrift(shards, t)
            6 -> drawCipherScroll(cipher, t, textMeasurer)
            7 -> drawStarfieldFlythrough(stars)
        }
    }
}

private suspend fun withFrameNanosCompat(): Long =
    androidx.compose.runtime.withFrameNanos { it }

// ============================================================ 1. NEURO LINKS ============================================================

private class NeuroLinksState {
    var nodes: MutableList<FloatArray> = mutableListOf()
    var lastW = -1f
    var lastH = -1f
}

private fun DrawScope.drawNeuroLinks(state: NeuroLinksState, @Suppress("UNUSED_PARAMETER") tMs: Float) {
    val w = size.width
    val h = size.height
    if (state.nodes.isEmpty() || state.lastW != w || state.lastH != h) {
        state.lastW = w
        state.lastH = h
        state.nodes = MutableList(30) {
            floatArrayOf(Random.nextFloat() * w, Random.nextFloat() * h, (Random.nextFloat() - 0.5f) * 1.4f, (Random.nextFloat() - 0.5f) * 1.4f)
        }
    }
    val nodes = state.nodes
    for (n in nodes) {
        n[0] += n[2]
        n[1] += n[3]
        if (n[0] < 0f || n[0] > w) n[2] *= -1f
        if (n[1] < 0f || n[1] > h) n[3] *= -1f
    }
    for (i in nodes.indices) {
        for (j in i + 1 until nodes.size) {
            val a = nodes[i]
            val b = nodes[j]
            val dist = hypot(a[0] - b[0], a[1] - b[1])
            if (dist < 160f) {
                drawLine(
                    Color.White.copy(alpha = (1f - dist / 160f) * 0.22f),
                    Offset(a[0], a[1]), Offset(b[0], b[1]), strokeWidth = 1f
                )
            }
        }
    }
    for (n in nodes) {
        drawCircle(Color.White.copy(alpha = .6f), radius = 1.6f, center = Offset(n[0], n[1]))
    }
}

// ============================================================ 2. CIRCUIT TRACE ============================================================

private class CircuitPath(val pts: List<Offset>, val offset: Float, val speed: Float)
private class CircuitTraceState {
    var paths: List<CircuitPath> = emptyList()
    var lastW = -1f
    var lastH = -1f
}

private fun DrawScope.drawCircuitTrace(state: CircuitTraceState, tMs: Float) {
    val w = size.width
    val h = size.height
    if (state.paths.isEmpty() || state.lastW != w || state.lastH != h) {
        state.lastW = w
        state.lastH = h
        val cols = 8
        val rows = 6
        val cellW = w / cols
        val cellH = h / rows
        state.paths = List(11) {
            var x = Random.nextInt(cols) * cellW
            var y = Random.nextInt(rows) * cellH
            val pts = mutableListOf(Offset(x, y))
            repeat(4 + Random.nextInt(4)) {
                if (Random.nextFloat() < 0.5f) x += if (Random.nextFloat() < 0.5f) cellW else -cellW
                else y += if (Random.nextFloat() < 0.5f) cellH else -cellH
                x = x.coerceIn(0f, w)
                y = y.coerceIn(0f, h)
                pts.add(Offset(x, y))
            }
            CircuitPath(pts, Random.nextFloat() * 4000f, 0.4f + Random.nextFloat() * 0.4f)
        }
    }
    state.paths.forEach { p ->
        val path = Path().apply {
            p.pts.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
        }
        drawPath(path, Color.White.copy(alpha = .14f), style = Stroke(width = 1f))

        var total = 0f
        val starts = ArrayList<Float>(p.pts.size)
        val segs = ArrayList<Triple<Offset, Offset, Float>>(p.pts.size)
        for (i in 1 until p.pts.size) {
            val a = p.pts[i - 1]
            val b = p.pts[i]
            val len = hypot(b.x - a.x, b.y - a.y)
            starts.add(total)
            segs.add(Triple(a, b, len))
            total += len
        }
        if (total <= 0f) return@forEach
        val pos = (tMs * p.speed * 0.03f + p.offset).mod(total)
        val idx = (starts.indices.lastOrNull { starts[it] <= pos }) ?: 0
        val (a, b, len) = segs[idx]
        val localT = if (len > 0f) (pos - starts[idx]) / len else 0f
        val px = a.x + (b.x - a.x) * localT
        val py = a.y + (b.y - a.y) * localT
        drawCircle(Color.White.copy(alpha = .95f), radius = 2.2f, center = Offset(px, py))
    }
}

// ============================================================ 3. WARP TUNNEL ============================================================

private fun DrawScope.drawWarpTunnel(tMs: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = hypot(size.width, size.height) / 2f * 1.6f
    val rotationDeg = (tMs * 0.012f) % 360f
    val zoom = 1f + 0.4f * ((sin(tMs * 0.0012f) + 1f) / 2f)
    val lines = 40
    for (i in 0 until lines) {
        val angleDeg = (360f / lines) * i + rotationDeg
        val rad = angleDeg * PI.toFloat() / 180f
        val innerR = maxR * 0.05f
        val outerR = maxR * zoom
        val cosA = cos(rad)
        val sinA = sin(rad)
        drawLine(
            Color.White.copy(alpha = .16f),
            Offset(cx + cosA * innerR, cy + sinA * innerR),
            Offset(cx + cosA * outerR, cy + sinA * outerR),
            strokeWidth = 1.4f
        )
    }
}

// ============================================================ 4. SERVER GRID ============================================================

private class ServerCell(val col: Int, val row: Int, val on: Boolean, val phase: Float, val durationMs: Float)
private class ServerGridState {
    var cells: List<ServerCell> = emptyList()
    var cols = 16
    var rows = 10
}

private fun DrawScope.drawServerGrid(state: ServerGridState, tMs: Float) {
    if (state.cells.isEmpty()) {
        state.cells = buildList {
            for (r in 0 until state.rows) for (c in 0 until state.cols) {
                val on = Random.nextFloat() < 0.16f
                add(ServerCell(c, r, on, Random.nextFloat() * 2600f, 1800f + Random.nextFloat() * 1600f))
            }
        }
    }
    val paddingFrac = 0.12f
    val w = size.width
    val h = size.height
    val gridW = w * (1f - paddingFrac * 2f)
    val gridH = h * (1f - paddingFrac * 2f)
    val gap = 6f
    val cellW = (gridW - gap * (state.cols - 1)) / state.cols
    val cellH = (gridH - gap * (state.rows - 1)) / state.rows
    val offX = w * paddingFrac
    val offY = h * paddingFrac
    val off = LaunColors.border
    state.cells.forEach { cell ->
        val x = offX + cell.col * (cellW + gap)
        val y = offY + cell.row * (cellH + gap)
        val color = if (cell.on) {
            val phase = ((tMs + cell.phase) % cell.durationMs) / cell.durationMs
            val pulse = (sin(phase * 2f * PI.toFloat()) + 1f) / 2f
            lerp(off, Color.White, pulse)
        } else off
        drawRect(color, topLeft = Offset(x, y), size = Size(cellW, cellH))
    }
}

// ============================================================ 5. THREAT PING MAP ============================================================

private class Ping(val x: Float, val y: Float, val start: Float)
private class ThreatMapState {
    var points: List<Offset> = emptyList()
    val pings = mutableListOf<Ping>()
    var nextPing = 0f
    var lastW = -1f
    var lastH = -1f
}

private fun DrawScope.drawThreatMap(state: ThreatMapState, tMs: Float) {
    val w = size.width
    val h = size.height
    if (state.points.isEmpty() || state.lastW != w || state.lastH != h) {
        state.lastW = w
        state.lastH = h
        val cols = 11
        val rows = 7
        state.points = buildList {
            for (r in 0 until rows) for (c in 0 until cols) add(Offset((c + 0.5f) / cols * w, (r + 0.5f) / rows * h))
        }
        state.pings.clear()
        state.nextPing = tMs
    }
    state.points.forEach { p -> drawCircle(Color.White.copy(alpha = .28f), radius = 1.4f, center = p) }
    if (tMs > state.nextPing && state.points.isNotEmpty()) {
        val p = state.points[Random.nextInt(state.points.size)]
        state.pings.add(Ping(p.x, p.y, tMs))
        state.nextPing = tMs + 500f + Random.nextFloat() * 700f
    }
    state.pings.removeAll { tMs - it.start > 1200f }
    state.pings.forEach { pg ->
        val prog = (tMs - pg.start) / 1200f
        drawCircle(
            Color.White.copy(alpha = ((1f - prog) * 0.55f).coerceIn(0f, 1f)),
            radius = (prog * 70f).coerceAtLeast(0.1f),
            center = Offset(pg.x, pg.y),
            style = Stroke(width = 1.5f)
        )
        drawCircle(Color.White.copy(alpha = ((1f - prog) * 0.85f).coerceIn(0f, 1f)), radius = 2f, center = Offset(pg.x, pg.y))
    }
}

// ============================================================ 6. SHARD DRIFT ============================================================

private class Shard(val xFrac: Float, val sizePx: Float, val durationMs: Float, val phaseMs: Float)
private class ShardDriftState {
    var shards: List<Shard> = emptyList()
}

private fun DrawScope.drawShardDrift(state: ShardDriftState, tMs: Float) {
    if (state.shards.isEmpty()) {
        state.shards = List(16) {
            Shard(Random.nextFloat(), 8f + Random.nextFloat() * 14f, (9f + Random.nextFloat() * 10f) * 1000f, Random.nextFloat() * 18000f)
        }
    }
    val w = size.width
    val h = size.height
    state.shards.forEach { s ->
        val localT = ((tMs + s.phaseMs) % s.durationMs) / s.durationMs
        val y = h - localT * (h * 1.12f + 40f)
        val alpha = when {
            localT < 0.1f -> localT / 0.1f
            localT > 0.9f -> (1f - localT) / 0.1f
            else -> 1f
        }.coerceIn(0f, 1f) * 0.55f
        val x = s.xFrac * w
        rotate(localT * 180f, pivot = Offset(x, y)) {
            drawHexShard(Offset(x, y), s.sizePx, Color.White.copy(alpha = alpha))
        }
    }
}

private fun DrawScope.drawHexShard(center: Offset, sizePx: Float, color: Color) {
    val hw = sizePx / 2f
    val hh = sizePx / 2f
    val path = Path().apply {
        moveTo(center.x, center.y - hh)
        lineTo(center.x + hw, center.y - hh / 2f)
        lineTo(center.x + hw, center.y + hh / 2f)
        lineTo(center.x, center.y + hh)
        lineTo(center.x - hw, center.y + hh / 2f)
        lineTo(center.x - hw, center.y - hh / 2f)
        close()
    }
    drawPath(path, color)
}

// ============================================================ 7. CIPHER SCROLL ============================================================

private const val HEX_CHARS = "0123456789ABCDEF"
private fun randomHexLine(len: Int): String = buildString {
    for (i in 0 until len) {
        append(HEX_CHARS[Random.nextInt(16)])
        if (i % 4 == 3) append(' ')
    }
}

private class CipherRowState(val layout: androidx.compose.ui.text.TextLayoutResult, val durationMs: Float, val reverse: Boolean)
private class CipherScrollState {
    var rows: List<CipherRowState> = emptyList()
    var lastW = -1f
    var lastH = -1f
}

private fun DrawScope.drawCipherScroll(state: CipherScrollState, tMs: Float, textMeasurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    if (state.rows.isEmpty() || state.lastW != w || state.lastH != h) {
        state.lastW = w
        state.lastH = h
        val style = TextStyle(fontFamily = MonoFontFamily, fontSize = 11.sp, color = Color.White)
        val probe = textMeasurer.measure(AnnotatedString("0"), style)
        val lineHeightPx = probe.size.height.toFloat().coerceAtLeast(1f)
        val rowCount = kotlin.math.ceil(h / lineHeightPx).toInt() + 1
        // Every row packed flush against the next (no gap) — full-height coverage, per request.
        state.rows = List(rowCount) { i ->
            val layout = textMeasurer.measure(AnnotatedString(randomHexLine(240)), style)
            CipherRowState(layout, 18000f + Random.nextFloat() * 22000f, i % 2 == 1)
        }
    }
    state.rows.forEachIndexed { i, row ->
        val y = i * row.layout.size.height.toFloat()
        val textW = row.layout.size.width.toFloat()
        if (textW <= 0f) return@forEachIndexed
        val progress = (tMs % row.durationMs) / row.durationMs
        val rawX = -(progress * textW)
        val x = if (row.reverse) rawX + textW else rawX
        var drawX = x % textW
        if (drawX > 0f) drawX -= textW
        var cursor = drawX
        while (cursor < w) {
            drawText(row.layout, topLeft = Offset(cursor, y))
            cursor += textW
        }
    }
}

// ============================================================ 8. STARFIELD DRIFT (fly-through) ============================================================

private class Star(var angle: Float, var dist: Float, var speed: Float)
private class StarfieldState {
    var stars: List<Star> = emptyList()
    var lastW = -1f
    var lastH = -1f
}

private fun DrawScope.drawStarfieldFlythrough(state: StarfieldState) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val maxDist = hypot(w, h) / 2f * 1.05f
    if (state.stars.isEmpty() || state.lastW != w || state.lastH != h) {
        state.lastW = w
        state.lastH = h
        state.stars = List(160) {
            Star(
                angle = Random.nextFloat() * (2f * PI.toFloat()),
                dist = Random.nextFloat() * maxDist,
                speed = 0.15f + Random.nextFloat() * 0.45f
            )
        }
    }
    state.stars.forEach { s ->
        val progress = (s.dist / maxDist).coerceIn(0f, 1f)
        s.dist += s.speed * (0.6f + progress * 3.2f)
        if (s.dist > maxDist) {
            s.dist = Random.nextFloat() * 6f
            s.angle = Random.nextFloat() * (2f * PI.toFloat())
            s.speed = 0.15f + Random.nextFloat() * 0.45f
        }
        val x = cx + cos(s.angle) * s.dist
        val y = cy + sin(s.angle) * s.dist
        val alpha = (0.15f + progress * 0.75f).coerceIn(0f, 0.9f)
        val r = 0.5f + progress * 2.2f
        drawCircle(Color.White.copy(alpha = alpha), radius = r, center = Offset(x, y))
    }
}
