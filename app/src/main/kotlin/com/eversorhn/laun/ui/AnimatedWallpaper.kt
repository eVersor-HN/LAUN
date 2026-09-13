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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import com.eversorhn.laun.data.BACKGROUND_ANIMATIONS
import com.eversorhn.laun.data.OLED_BLACK_INDEX
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** BACKGROUND_ANIMATIONS indices, kept in sync with [com.eversorhn.laun.data.BACKGROUND_ANIMATIONS]. */
private const val BG_NEURO_LINKS = 0
private const val BG_CIRCUIT_TRACE = 1
private const val BG_WARP_TUNNEL = 2
private const val BG_SERVER_GRID = 3
private const val BG_THREAT_MAP = 4
private const val BG_SHARD_DRIFT = 5
private const val BG_CIPHER_SCROLL = 6
private const val BG_STARFIELD = 7
private const val BG_HEX_MESH = 9
private const val BG_RADAR_SWEEP = 10
private const val BG_DATA_RAIN = 11
private const val BG_CRT_SCANLINES = 12
private const val BG_HORIZON_GRID = 13
private const val BG_WAVEFORM = 14
private const val BG_DATA_STREAM = 15
private const val BG_BINARY_NOISE = 16
private const val BG_ORBITAL_RINGS = 17

/**
 * Selectable animated background rendered behind the hex grid — native Compose Canvas ports of
 * the concepts prototyped in wallpaper-demo.html plus the newer ones added directly here. Frame
 * time is read only inside the Canvas draw lambda (never in the composable body), same trick as
 * HexTile's reveal animations: this drives continuous per-frame redraws without ever triggering
 * full recomposition, so it stays smooth behind the grid regardless of how many tiles are on screen.
 *
 * Every concept keeps its own mutable scene state in [BackgroundState] (created lazily per kind)
 * and rebuilds it only when the canvas size — or the effect-size setting, where it changes
 * geometry — actually changes; per-frame work is drawing, not allocation.
 */
@Composable
fun AnimatedWallpaper(
    kind: Int,
    opacity: Float = 1f,
    /** 0.5..2 — brightness of the effect's own elements, independent of [opacity]'s overall dim. */
    intensity: Float = 1f,
    /** 0.5..2 — visual size of the effect's particles/lines/shards. */
    sizeScale: Float = 1f,
    /** Color of the effect's own elements (nodes/lines/particles/text) — white by default, same
     *  as the app's whole monochrome look; any of the tile accent colors can replace it. Doesn't
     *  apply to OLED BLACK, which is a flat black fill with nothing to tint. */
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    if (kind !in BACKGROUND_ANIMATIONS.indices) return

    var frameTimeMs by remember { mutableFloatStateOf(0f) }
    // OLED BLACK is a static fill — no frame clock needed, so none is started for it.
    if (kind != OLED_BLACK_INDEX) {
        LaunchedEffect(Unit) {
            val startNanos = withFrameNanosCompat()
            while (isActive) {
                val now = withFrameNanosCompat()
                frameTimeMs = (now - startNanos) / 1_000_000f
            }
        }
    }

    val textMeasurer = rememberTextMeasurer()
    val state = remember(kind) { BackgroundState() }

    Canvas(modifier = modifier.fillMaxSize().alpha(opacity.coerceIn(0f, 1f))) {
        val t = frameTimeMs
        val i = intensity.coerceIn(0.5f, 2f)
        val s = sizeScale.coerceIn(0.5f, 2f)
        when (kind) {
            BG_NEURO_LINKS -> drawNeuroLinks(state.neuro, t, i, s, tint)
            BG_CIRCUIT_TRACE -> drawCircuitTrace(state.circuit, t, i, s, tint)
            BG_WARP_TUNNEL -> drawWarpTunnel(t, i, s, tint)
            BG_SERVER_GRID -> drawServerGrid(state.server, t, i, s, tint)
            BG_THREAT_MAP -> drawThreatMap(state.threat, t, i, s, tint)
            BG_SHARD_DRIFT -> drawShardDrift(state.shards, t, i, s, tint)
            BG_CIPHER_SCROLL -> drawCipherScroll(state.cipher, t, textMeasurer, i, s, tint)
            BG_STARFIELD -> drawStarfieldFlythrough(state.stars, i, s, tint)
            OLED_BLACK_INDEX -> drawRect(Color.Black, size = size)
            BG_HEX_MESH -> drawHexMesh(state.hexMesh, t, i, s, tint)
            BG_RADAR_SWEEP -> drawRadarSweep(state.radar, t, i, s, tint)
            BG_DATA_RAIN -> drawDataRain(state.rain, t, textMeasurer, i, s, tint)
            BG_CRT_SCANLINES -> drawCrtScanlines(state.crt, t, i, s, tint)
            BG_HORIZON_GRID -> drawHorizonGrid(t, i, s, tint)
            BG_WAVEFORM -> drawWaveform(state.wave, t, i, s, tint)
            BG_DATA_STREAM -> drawDataStream(state.stream, t, i, s, tint)
            BG_BINARY_NOISE -> drawBinaryNoise(state.binary, t, textMeasurer, i, s, tint)
            BG_ORBITAL_RINGS -> drawOrbitalRings(state.orbit, t, i, s, tint)
        }
    }
}

private suspend fun withFrameNanosCompat(): Long =
    androidx.compose.runtime.withFrameNanos { it }

/** One holder per active kind; each concept's scene is created the first time it's drawn. */
private class BackgroundState {
    val neuro by lazy { NeuroLinksState() }
    val circuit by lazy { CircuitTraceState() }
    val server by lazy { ServerGridState() }
    val threat by lazy { ThreatMapState() }
    val shards by lazy { ShardDriftState() }
    val cipher by lazy { CipherScrollState() }
    val stars by lazy { StarfieldState() }
    val hexMesh by lazy { HexMeshState() }
    val radar by lazy { RadarState() }
    val rain by lazy { DataRainState() }
    val crt by lazy { CrtState() }
    val wave by lazy { WaveformState() }
    val stream by lazy { DataStreamState() }
    val binary by lazy { BinaryNoiseState() }
    val orbit by lazy { OrbitalState() }
}

private const val TWO_PI = (2.0 * PI).toFloat()

/** EFFECT SIZE applied to glyph-based concepts: 0.5..2 maps to 0.8..1.35 — text that scaled
 *  linearly with the slider turned into a billboard at 2x instead of just "bigger rain". */
private fun textScale(sizeScale: Float): Float = 0.7f + 0.325f * sizeScale

// ============================================================ 1. NEURO LINKS ============================================================

private class NeuroLinksState {
    var nodes: MutableList<FloatArray> = mutableListOf()
    var lastW = -1f
    var lastH = -1f
}

private fun DrawScope.drawNeuroLinks(state: NeuroLinksState, tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
    val w = size.width
    val h = size.height
    if (state.nodes.isEmpty() || state.lastW != w || state.lastH != h) {
        state.lastW = w
        state.lastH = h
        state.nodes = MutableList(30) {
            floatArrayOf(Random.nextFloat() * w, Random.nextFloat() * h, (Random.nextFloat() - 0.5f) * 1.4f, (Random.nextFloat() - 0.5f) * 1.4f, Random.nextFloat() * TWO_PI)
        }
    }
    val nodes = state.nodes
    for (n in nodes) {
        n[0] += n[2]
        n[1] += n[3]
        if (n[0] < 0f || n[0] > w) n[2] *= -1f
        if (n[1] < 0f || n[1] > h) n[3] *= -1f
    }
    val linkDist = 160f * sizeScale
    for (i in nodes.indices) {
        for (j in i + 1 until nodes.size) {
            val a = nodes[i]
            val b = nodes[j]
            val dist = hypot(a[0] - b[0], a[1] - b[1])
            if (dist < linkDist) {
                val strength = 1f - dist / linkDist
                drawLine(
                    tint.copy(alpha = (strength * 0.22f * intensity).coerceIn(0f, 1f)),
                    Offset(a[0], a[1]), Offset(b[0], b[1]), strokeWidth = 1f * sizeScale
                )
                // A signal pulse travelling along every strong link — the "thinking" cue that a
                // static web of lines lacked. Position along the link is a per-pair phase so
                // pulses don't all march in lockstep.
                if (strength > 0.35f) {
                    val phase = ((tMs * 0.0006f) + (i * 7 + j * 13) * 0.173f) % 1f
                    drawCircle(
                        tint.copy(alpha = (strength * 0.7f * intensity).coerceIn(0f, 1f)),
                        radius = 1.3f * sizeScale,
                        center = Offset(a[0] + (b[0] - a[0]) * phase, a[1] + (b[1] - a[1]) * phase)
                    )
                }
            }
        }
    }
    for (n in nodes) {
        // Nodes breathe — each on its own phase.
        val pulse = 1f + 0.45f * sin(tMs * 0.003f + n[4])
        drawCircle(tint.copy(alpha = (.6f * intensity).coerceIn(0f, 1f)), radius = 1.6f * sizeScale * pulse, center = Offset(n[0], n[1]))
    }
}

// ============================================================ 2. CIRCUIT TRACE ============================================================

/** One trace. Everything derivable from [pts] alone — the drawable [path], per-segment start
 *  distances/lengths, and the [total] length — is computed once here rather than rebuilt on
 *  every frame for every one of the paths. */
private class CircuitPath(val pts: List<Offset>, val offset: Float, val speed: Float) {
    val path: Path = Path().apply {
        pts.forEachIndexed { i, pt -> if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }
    }
    val segStarts = FloatArray(maxOf(0, pts.size - 1))
    val segLengths = FloatArray(maxOf(0, pts.size - 1))
    val total: Float

    init {
        var acc = 0f
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            segStarts[i - 1] = acc
            segLengths[i - 1] = hypot(b.x - a.x, b.y - a.y)
            acc += segLengths[i - 1]
        }
        total = acc
    }
}
private class CircuitTraceState {
    var paths: List<CircuitPath> = emptyList()
    var lastW = -1f
    var lastH = -1f
}

private fun DrawScope.drawCircuitTrace(state: CircuitTraceState, tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
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
    val traceColor = tint.copy(alpha = (.14f * intensity).coerceIn(0f, 1f))
    val padColor = tint.copy(alpha = (.32f * intensity).coerceIn(0f, 1f))
    val dotColor = tint.copy(alpha = (.95f * intensity).coerceIn(0f, 1f))
    val stroke = Stroke(width = 1f * sizeScale)
    val pad = 3f * sizeScale
    state.paths.forEach { p ->
        drawPath(p.path, traceColor, style = stroke)
        // Solder pads at every corner and end — what makes it read as a PCB rather than a maze.
        for (pt in p.pts) drawRect(padColor, Offset(pt.x - pad, pt.y - pad), Size(pad * 2f, pad * 2f))
        if (p.total <= 0f || p.segLengths.isEmpty()) return@forEach
        val pos = (tMs * p.speed * 0.03f + p.offset).mod(p.total)
        var idx = 0
        for (k in p.segStarts.indices) if (p.segStarts[k] <= pos) idx = k else break
        val a = p.pts[idx]
        val b = p.pts[idx + 1]
        val len = p.segLengths[idx]
        val localT = if (len > 0f) (pos - p.segStarts[idx]) / len else 0f
        val px = a.x + (b.x - a.x) * localT
        val py = a.y + (b.y - a.y) * localT
        // Short bright tail behind the packet along the same segment.
        val tail = min(pos - p.segStarts[idx], 26f * sizeScale)
        if (len > 0f && tail > 0f) {
            val tx = px - (b.x - a.x) / len * tail
            val ty = py - (b.y - a.y) / len * tail
            drawLine(tint.copy(alpha = (.5f * intensity).coerceIn(0f, 1f)), Offset(tx, ty), Offset(px, py), strokeWidth = 1.5f * sizeScale)
        }
        drawCircle(dotColor, radius = 2.2f * sizeScale, center = Offset(px, py))
    }
}

// ============================================================ 3. WARP TUNNEL ============================================================

private fun DrawScope.drawWarpTunnel(tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = hypot(size.width, size.height) / 2f * 1.6f * sizeScale
    val rotationDeg = (tMs * 0.012f) % 360f
    val zoom = 1f + 0.4f * ((sin(tMs * 0.0012f) + 1f) / 2f)
    val lines = 40
    val lineColor = tint.copy(alpha = (.16f * intensity).coerceIn(0f, 1f))
    for (i in 0 until lines) {
        val angleDeg = (360f / lines) * i + rotationDeg
        val rad = angleDeg * PI.toFloat() / 180f
        val innerR = maxR * 0.05f
        val outerR = maxR * zoom
        val cosA = cos(rad)
        val sinA = sin(rad)
        drawLine(
            lineColor,
            Offset(cx + cosA * innerR, cy + sinA * innerR),
            Offset(cx + cosA * outerR, cy + sinA * outerR),
            strokeWidth = 1.4f * sizeScale
        )
    }
    // Rings rushing outward from the vanishing point give the tunnel its forward motion — the
    // spokes alone only ever rotated in place.
    val ringCount = 7
    val visibleR = hypot(size.width, size.height) / 2f
    for (k in 0 until ringCount) {
        val p = ((tMs * 0.00022f) + k / ringCount.toFloat()) % 1f
        // Ease: rings accelerate as they approach the viewer (perspective), so quadratic.
        val r = visibleR * p * p * 1.1f
        if (r < 2f) continue
        drawCircle(
            tint.copy(alpha = ((1f - p) * 0.35f * intensity).coerceIn(0f, 1f)),
            radius = r, center = Offset(cx, cy),
            style = Stroke(width = (0.8f + p * 1.6f) * sizeScale)
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

private fun DrawScope.drawServerGrid(state: ServerGridState, tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
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
    val gap = 6f * sizeScale
    val cellW = (gridW - gap * (state.cols - 1)) / state.cols
    val cellH = (gridH - gap * (state.rows - 1)) / state.rows
    val offX = w * paddingFrac
    val offY = h * paddingFrac
    val off = LaunColors.border
    // A diagnostic sweep walking down the rack every few seconds, briefly lifting each row —
    // the "something is polling this" cue.
    val sweepRow = ((tMs / 5200f) % 1f) * (state.rows + 2f) - 1f
    state.cells.forEach { cell ->
        val x = offX + cell.col * (cellW + gap)
        val y = offY + cell.row * (cellH + gap)
        val sweepLift = (1f - kotlin.math.abs(cell.row - sweepRow)).coerceIn(0f, 1f) * 0.35f * intensity
        val color = if (cell.on) {
            val phase = ((tMs + cell.phase) % cell.durationMs) / cell.durationMs
            val pulse = ((sin(phase * TWO_PI) + 1f) / 2f * intensity).coerceIn(0f, 1f)
            lerp(off, tint, (pulse + sweepLift).coerceIn(0f, 1f))
        } else lerp(off, tint, sweepLift.coerceIn(0f, 1f))
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

private fun DrawScope.drawThreatMap(state: ThreatMapState, tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
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
    state.points.forEach { p -> drawCircle(tint.copy(alpha = (.28f * intensity).coerceIn(0f, 1f)), radius = 1.4f * sizeScale, center = p) }
    if (tMs > state.nextPing && state.points.isNotEmpty()) {
        val p = state.points[Random.nextInt(state.points.size)]
        state.pings.add(Ping(p.x, p.y, tMs))
        state.nextPing = tMs + 500f + Random.nextFloat() * 700f
    }
    state.pings.removeAll { tMs - it.start > 1200f }
    state.pings.forEach { pg ->
        val prog = (tMs - pg.start) / 1200f
        drawCircle(
            tint.copy(alpha = ((1f - prog) * 0.55f * intensity).coerceIn(0f, 1f)),
            radius = (prog * 70f * sizeScale).coerceAtLeast(0.1f),
            center = Offset(pg.x, pg.y),
            style = Stroke(width = 1.5f * sizeScale)
        )
        // Target brackets around the fresh hit, snapping shut as the ring expands.
        val bracket = (1f - prog) * 14f * sizeScale + 6f * sizeScale
        val arm = 4f * sizeScale
        val bColor = tint.copy(alpha = ((1f - prog) * 0.9f * intensity).coerceIn(0f, 1f))
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            val cxb = pg.x + sx * bracket
            val cyb = pg.y + sy * bracket
            drawLine(bColor, Offset(cxb, cyb), Offset(cxb - sx * arm, cyb), strokeWidth = 1f * sizeScale)
            drawLine(bColor, Offset(cxb, cyb), Offset(cxb, cyb - sy * arm), strokeWidth = 1f * sizeScale)
        }
        drawCircle(tint.copy(alpha = ((1f - prog) * 0.85f * intensity).coerceIn(0f, 1f)), radius = 2f * sizeScale, center = Offset(pg.x, pg.y))
    }
}

// ============================================================ 6. SHARD DRIFT ============================================================

private class Shard(val xFrac: Float, val sizePx: Float, val durationMs: Float, val phaseMs: Float)
private class ShardDriftState {
    var shards: List<Shard> = emptyList()
}

private fun DrawScope.drawShardDrift(state: ShardDriftState, tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
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
        val alpha = (when {
            localT < 0.1f -> localT / 0.1f
            localT > 0.9f -> (1f - localT) / 0.1f
            else -> 1f
        }.coerceIn(0f, 1f) * 0.55f * intensity).coerceIn(0f, 1f)
        val x = s.xFrac * w
        rotate(localT * 180f, pivot = Offset(x, y)) {
            drawHexShard(Offset(x, y), s.sizePx * sizeScale, tint.copy(alpha = alpha))
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

private class CipherRowState(val layout: TextLayoutResult, val durationMs: Float, val reverse: Boolean)
private class CipherScrollState {
    var rows: List<CipherRowState> = emptyList()
    var lastW = -1f
    var lastH = -1f
    var lastSizeScale = -1f
}

private fun DrawScope.drawCipherScroll(state: CipherScrollState, tMs: Float, textMeasurer: TextMeasurer, @Suppress("UNUSED_PARAMETER") intensity: Float, sizeScale: Float, tint: Color) {
    val w = size.width
    val h = size.height
    if (state.rows.isEmpty() || state.lastW != w || state.lastH != h || state.lastSizeScale != sizeScale) {
        state.lastW = w
        state.lastH = h
        state.lastSizeScale = sizeScale
        val style = TextStyle(fontFamily = MonoFontFamily, fontSize = (11f * sizeScale).sp, color = Color.White)
        val probe = textMeasurer.measure(AnnotatedString("0"), style)
        val lineHeightPx = probe.size.height.toFloat().coerceAtLeast(1f)
        val rowCount = ceil(h / lineHeightPx).toInt() + 1
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
            drawText(row.layout, color = tint, topLeft = Offset(cursor, y))
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

private fun DrawScope.drawStarfieldFlythrough(state: StarfieldState, intensity: Float, sizeScale: Float, tint: Color) {
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
                angle = Random.nextFloat() * TWO_PI,
                dist = Random.nextFloat() * maxDist,
                speed = 0.15f + Random.nextFloat() * 0.45f
            )
        }
    }
    state.stars.forEach { s ->
        val progress = (s.dist / maxDist).coerceIn(0f, 1f)
        val step = s.speed * (0.6f + progress * 3.2f)
        s.dist += step
        if (s.dist > maxDist) {
            s.dist = Random.nextFloat() * 6f
            s.angle = Random.nextFloat() * TWO_PI
            s.speed = 0.15f + Random.nextFloat() * 0.45f
        }
        val ca = cos(s.angle)
        val sa = sin(s.angle)
        val x = cx + ca * s.dist
        val y = cy + sa * s.dist
        val alpha = ((0.15f + progress * 0.75f) * intensity).coerceIn(0f, 1f)
        val r = (0.5f + progress * 2.2f) * sizeScale
        // Fast, near stars streak — a short motion trail pointing back at the vanishing point.
        if (progress > 0.45f) {
            val trail = step * 6f * (progress - 0.45f)
            drawLine(tint.copy(alpha = alpha * 0.5f), Offset(x - ca * trail, y - sa * trail), Offset(x, y), strokeWidth = r)
        }
        drawCircle(tint.copy(alpha = alpha), radius = r, center = Offset(x, y))
    }
}

// ============================================================ 9. HEX MESH ============================================================

private class HexMeshState {
    var mesh = Path()
    var centers: List<Offset> = emptyList()
    var pulses: List<FloatArray> = emptyList() // [centerIndex, phaseMs, durationMs]
    val scratch = Path()
    var lastW = -1f
    var lastH = -1f
    var lastSizeScale = -1f
    var cellW = 0f
    var cellH = 0f
}

private fun DrawScope.drawHexMesh(state: HexMeshState, tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
    val w = size.width
    val h = size.height
    if (state.centers.isEmpty() || state.lastW != w || state.lastH != h || state.lastSizeScale != sizeScale) {
        state.lastW = w
        state.lastH = h
        state.lastSizeScale = sizeScale
        val cellW = 64f * sizeScale
        val cellH = cellW * 2f / sqrt(3f)
        state.cellW = cellW
        state.cellH = cellH
        val cols = ceil(w / cellW).toInt() + 2
        val rows = ceil(h / (cellH * 0.75f)).toInt() + 2
        val mesh = Path()
        val centers = ArrayList<Offset>(cols * rows)
        for (r in 0 until rows) for (c in 0 until cols) {
            val cx = c * cellW + (if (r % 2 == 1) cellW / 2f else 0f) - cellW / 2f
            val cy = r * cellH * 0.75f - cellH / 2f
            centers += Offset(cx, cy)
            appendHex(mesh, cx, cy, cellW * 0.94f, cellH * 0.94f)
        }
        state.mesh = mesh
        state.centers = centers
        state.pulses = List(14) {
            floatArrayOf(Random.nextInt(centers.size).toFloat(), Random.nextFloat() * 6000f, 2200f + Random.nextFloat() * 2600f)
        }
    }
    // The whole honeycomb is one Path — a single stroke call regardless of how many cells fit.
    drawPath(state.mesh, tint.copy(alpha = (0.09f * intensity).coerceIn(0f, 1f)), style = Stroke(width = 1f * sizeScale))
    // A handful of cells light up and fade, occasionally handing off to a different cell.
    for (p in state.pulses) {
        val cycle = (tMs + p[1]) / p[2]
        val local = cycle % 1f
        if (local < 0.02f && Random.nextFloat() < 0.3f) p[0] = Random.nextInt(state.centers.size).toFloat()
        val c = state.centers[p[0].toInt()]
        val glow = sin(local * PI.toFloat())
        appendHex(state.scratch.also { it.reset() }, c.x, c.y, state.cellW * 0.94f, state.cellH * 0.94f)
        drawPath(state.scratch, tint.copy(alpha = (glow * 0.16f * intensity).coerceIn(0f, 1f)))
        drawPath(state.scratch, tint.copy(alpha = (glow * 0.55f * intensity).coerceIn(0f, 1f)), style = Stroke(width = 1.2f * sizeScale))
    }
}

private fun appendHex(path: Path, cx: Float, cy: Float, w: Float, h: Float) {
    val hw = w / 2f
    val hh = h / 2f
    path.moveTo(cx, cy - hh)
    path.lineTo(cx + hw, cy - hh / 2f)
    path.lineTo(cx + hw, cy + hh / 2f)
    path.lineTo(cx, cy + hh)
    path.lineTo(cx - hw, cy + hh / 2f)
    path.lineTo(cx - hw, cy - hh / 2f)
    path.close()
}

// ============================================================ 10. RADAR SWEEP ============================================================

private class RadarBlip(val x: Float, val y: Float, val angleDeg: Float, var lastHitMs: Float)
private class RadarState {
    var blips: List<RadarBlip> = emptyList()
    var lastSweep = 0f
    var lastW = -1f
    var lastH = -1f
}

private fun DrawScope.drawRadarSweep(state: RadarState, tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val maxR = hypot(w, h) / 2f
    if (state.blips.isEmpty() || state.lastW != w || state.lastH != h) {
        state.lastW = w
        state.lastH = h
        state.blips = List(14) {
            val x = Random.nextFloat() * w
            val y = Random.nextFloat() * h
            RadarBlip(x, y, ((atan2(y - cy, x - cx) * 180f / PI.toFloat()) + 360f) % 360f, -100000f)
        }
    }
    val faint = tint.copy(alpha = (0.10f * intensity).coerceIn(0f, 1f))
    val thin = Stroke(width = 1f * sizeScale)
    // Range rings + crosshair.
    for (k in 1..4) drawCircle(faint, radius = maxR * k / 4f * 0.72f, center = Offset(cx, cy), style = thin)
    drawLine(faint, Offset(0f, cy), Offset(w, cy), strokeWidth = 1f * sizeScale)
    drawLine(faint, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1f * sizeScale)
    // Sweep: one revolution every 4s, with a fading wedge trailing the leading edge.
    val sweep = (tMs * 0.09f) % 360f
    val trailSteps = 28
    val stepDeg = 2.2f
    for (k in 0 until trailSteps) {
        val a = (1f - k / trailSteps.toFloat())
        drawArc(
            tint.copy(alpha = (a * a * 0.22f * intensity).coerceIn(0f, 1f)),
            startAngle = sweep - (k + 1) * stepDeg, sweepAngle = stepDeg + 0.3f, useCenter = true,
            topLeft = Offset(cx - maxR, cy - maxR), size = Size(maxR * 2f, maxR * 2f)
        )
    }
    val rad = sweep * PI.toFloat() / 180f
    drawLine(tint.copy(alpha = (0.9f * intensity).coerceIn(0f, 1f)), Offset(cx, cy), Offset(cx + cos(rad) * maxR, cy + sin(rad) * maxR), strokeWidth = 1.5f * sizeScale)
    // Blips light up as the sweep passes and decay until the next pass.
    val passed = if (sweep >= state.lastSweep) (state.lastSweep..sweep) else null
    for (b in state.blips) {
        val hit = if (passed != null) b.angleDeg in passed else (b.angleDeg >= state.lastSweep || b.angleDeg <= sweep)
        if (hit) b.lastHitMs = tMs
        val age = tMs - b.lastHitMs
        if (age < 0f || age > 4000f) continue
        val a = exp(-age / 1400f)
        drawCircle(tint.copy(alpha = (a * 0.95f * intensity).coerceIn(0f, 1f)), radius = 3f * sizeScale, center = Offset(b.x, b.y))
        drawCircle(tint.copy(alpha = (a * 0.35f * intensity).coerceIn(0f, 1f)), radius = (3f + (1f - a) * 10f) * sizeScale, center = Offset(b.x, b.y), style = thin)
    }
    state.lastSweep = sweep
}

// ============================================================ 11. DATA RAIN ============================================================

private class DataRainState {
    var glyphs: List<TextLayoutResult> = emptyList()
    var cols = 0
    var rows = 0
    var cellW = 0f
    var cellH = 0f
    var chars = IntArray(0)
    var speeds = FloatArray(0)
    var phases = FloatArray(0)
    var trails = IntArray(0)
    var lastW = -1f
    var lastH = -1f
    var lastSizeScale = -1f
}

private fun DrawScope.drawDataRain(state: DataRainState, tMs: Float, textMeasurer: TextMeasurer, intensity: Float, sizeScale: Float, tint: Color) {
    val w = size.width
    val h = size.height
    if (state.glyphs.isEmpty() || state.lastW != w || state.lastH != h || state.lastSizeScale != sizeScale) {
        state.lastW = w
        state.lastH = h
        state.lastSizeScale = sizeScale
        // Glyph size follows EFFECT SIZE only gently — text at 2x reads as a billboard, not rain.
        val style = TextStyle(fontFamily = MonoFontFamily, fontSize = (11f * textScale(sizeScale)).sp, color = Color.White)
        state.glyphs = HEX_CHARS.map { textMeasurer.measure(AnnotatedString(it.toString()), style) }
        val gw = state.glyphs[0].size.width.toFloat().coerceAtLeast(1f)
        val gh = state.glyphs[0].size.height.toFloat().coerceAtLeast(1f)
        state.cellW = gw * 1.6f
        state.cellH = gh * 0.95f
        state.cols = ceil(w / state.cellW).toInt()
        state.rows = ceil(h / state.cellH).toInt() + 1
        state.chars = IntArray(state.cols * state.rows) { Random.nextInt(16) }
        state.speeds = FloatArray(state.cols) { 4f + Random.nextFloat() * 9f } // rows per second
        state.phases = FloatArray(state.cols) { Random.nextFloat() * 60f }
        state.trails = IntArray(state.cols) { 8 + Random.nextInt(10) }
    }
    // A few glyphs mutate every frame — the stream is alive even where nothing is falling yet.
    repeat(6) { state.chars[Random.nextInt(state.chars.size)] = Random.nextInt(16) }
    val rows = state.rows
    for (c in 0 until state.cols) {
        val trail = state.trails[c]
        val cycle = rows + trail
        val head = ((tMs / 1000f) * state.speeds[c] + state.phases[c]) % cycle - trail
        val x = c * state.cellW
        for (k in 0 until trail) {
            val row = floor(head).toInt() - k
            if (row < 0 || row >= rows) continue
            val fade = 1f - k / trail.toFloat()
            val alpha = if (k == 0) 0.95f else fade * fade * 0.55f
            drawText(
                state.glyphs[state.chars[c * rows + row]],
                color = tint.copy(alpha = (alpha * intensity).coerceIn(0f, 1f)),
                topLeft = Offset(x, row * state.cellH)
            )
        }
    }
}

// ============================================================ 12. CRT SCANLINES ============================================================

private class CrtState {
    var lines = Path()
    var lastW = -1f
    var lastH = -1f
    var lastSizeScale = -1f
    var nextGlitch = 0f
    var glitchY = 0f
    var glitchUntil = -1f
}

private fun DrawScope.drawCrtScanlines(state: CrtState, tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
    val w = size.width
    val h = size.height
    if (state.lastW != w || state.lastH != h || state.lastSizeScale != sizeScale) {
        state.lastW = w
        state.lastH = h
        state.lastSizeScale = sizeScale
        val p = Path()
        val gap = 4f * sizeScale
        var y = 0f
        while (y < h) {
            p.moveTo(0f, y)
            p.lineTo(w, y)
            y += gap
        }
        state.lines = p
    }
    // Static raster lines — one path, one draw.
    drawPath(state.lines, tint.copy(alpha = (0.07f * intensity).coerceIn(0f, 1f)), style = Stroke(width = 1f))
    // A slow refresh band rolling down the tube, brightest at its trailing edge.
    val band = 160f * sizeScale
    val y = ((tMs * 0.00018f) % 1f) * (h + band) - band
    drawRect(
        Brush.verticalGradient(
            0f to tint.copy(alpha = 0f),
            0.85f to tint.copy(alpha = (0.14f * intensity).coerceIn(0f, 1f)),
            1f to tint.copy(alpha = (0.4f * intensity).coerceIn(0f, 1f)),
            startY = y, endY = y + band
        ),
        topLeft = Offset(0f, y), size = Size(w, band)
    )
    // Rare horizontal tearing: a thin strip shifts sideways for a few frames.
    if (tMs > state.nextGlitch) {
        state.nextGlitch = tMs + 2500f + Random.nextFloat() * 4500f
        state.glitchY = Random.nextFloat() * h
        state.glitchUntil = tMs + 90f + Random.nextFloat() * 120f
    }
    if (tMs < state.glitchUntil) {
        val gh = (6f + Random.nextFloat() * 10f) * sizeScale
        val shift = (Random.nextFloat() - 0.5f) * 40f * sizeScale
        drawRect(tint.copy(alpha = (0.25f * intensity).coerceIn(0f, 1f)), Offset(shift, state.glitchY), Size(w, gh))
        drawRect(LaunColors.bg, Offset(if (shift > 0) 0f else w + shift, state.glitchY), Size(kotlin.math.abs(shift), gh))
    }
    // Vignette edges — a soft darkening top and bottom that sells the curved glass.
    val vig = h * 0.18f
    drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.5f), 1f to Color.Transparent, startY = 0f, endY = vig), topLeft = Offset.Zero, size = Size(w, vig))
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.5f), startY = h - vig, endY = h), topLeft = Offset(0f, h - vig), size = Size(w, vig))
}

// ============================================================ 13. HORIZON GRID ============================================================

private fun DrawScope.drawHorizonGrid(tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val horizonY = h * 0.5f
    val floorH = h - horizonY
    val lineColor = tint.copy(alpha = (0.28f * intensity).coerceIn(0f, 1f))
    val stroke = 1f * sizeScale
    // Converging verticals — floor below the horizon, mirrored ceiling above (dimmer).
    val spokes = 18
    for (k in -spokes..spokes) {
        val xBottom = cx + k * (w * 1.4f / spokes) * sizeScale
        val a = 1f - kotlin.math.abs(k) / (spokes + 2f)
        drawLine(lineColor.copy(alpha = lineColor.alpha * a), Offset(cx, horizonY), Offset(xBottom, h), strokeWidth = stroke)
        drawLine(lineColor.copy(alpha = lineColor.alpha * a * 0.45f), Offset(cx, horizonY), Offset(xBottom, 0f), strokeWidth = stroke)
    }
    // Horizontal rungs rushing toward the viewer with perspective spacing.
    val rungs = 16
    val frac = (tMs * 0.00035f) % 1f
    for (n in 0 until rungs) {
        val p = ((n + frac) / rungs)
        val depth = p * p
        val y = horizonY + floorH * depth
        val a = depth * 0.55f * intensity
        drawLine(tint.copy(alpha = a.coerceIn(0f, 1f)), Offset(0f, y), Offset(w, y), strokeWidth = stroke * (0.6f + depth))
        val yUp = horizonY - floorH * depth
        drawLine(tint.copy(alpha = (a * 0.45f).coerceIn(0f, 1f)), Offset(0f, yUp), Offset(w, yUp), strokeWidth = stroke * (0.6f + depth))
    }
    // Horizon line with a soft glow band.
    val glow = 40f * sizeScale
    drawRect(
        Brush.verticalGradient(
            0f to Color.Transparent, 0.5f to tint.copy(alpha = (0.18f * intensity).coerceIn(0f, 1f)), 1f to Color.Transparent,
            startY = horizonY - glow, endY = horizonY + glow
        ),
        topLeft = Offset(0f, horizonY - glow), size = Size(w, glow * 2f)
    )
    drawLine(tint.copy(alpha = (0.9f * intensity).coerceIn(0f, 1f)), Offset(0f, horizonY), Offset(w, horizonY), strokeWidth = 1.5f * sizeScale)
}

// ============================================================ 14. WAVEFORM ============================================================

private class WaveformState {
    val paths = List(3) { Path() }
}

private fun DrawScope.drawWaveform(state: WaveformState, tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
    val w = size.width
    val h = size.height
    val cy = h / 2f
    val faint = tint.copy(alpha = (0.08f * intensity).coerceIn(0f, 1f))
    // Oscilloscope graticule: center line plus ticks.
    drawLine(faint, Offset(0f, cy), Offset(w, cy), strokeWidth = 1f)
    val tickGap = 48f * sizeScale
    var tx = 0f
    while (tx < w) {
        drawLine(faint, Offset(tx, cy - 5f * sizeScale), Offset(tx, cy + 5f * sizeScale), strokeWidth = 1f)
        tx += tickGap
    }
    val step = 5f
    val t = tMs * 0.001f
    // Three traces: a carrier, a slower modulated one, and a noisy high-frequency one.
    val specs = arrayOf(
        floatArrayOf(0.18f, 0.011f, 2.6f, 0.5f, 1.4f),
        floatArrayOf(0.12f, 0.019f, -1.9f, 0.3f, 1.1f),
        floatArrayOf(0.05f, 0.061f, 5.3f, 0.2f, 1f)
    )
    for ((k, sp) in specs.withIndex()) {
        val amp = h * sp[0] * sizeScale.coerceAtMost(1.4f)
        val path = state.paths[k]
        path.reset()
        var x = 0f
        var first = true
        while (x <= w + step) {
            val env = 0.55f + 0.45f * sin(x * 0.0035f * sp[1] * 60f - t * 0.7f)
            val y = cy + amp * env * sin(x * sp[1] + t * sp[2]) * (if (k == 2) sin(x * 0.23f + t * 9f) else 1f)
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            x += step
        }
        drawPath(path, tint.copy(alpha = (sp[3] * intensity).coerceIn(0f, 1f)), style = Stroke(width = sp[4] * sizeScale))
    }
    // Sweep cursor moving across like a scope's trigger position.
    val cursorX = ((tMs * 0.00025f) % 1f) * w
    drawLine(tint.copy(alpha = (0.35f * intensity).coerceIn(0f, 1f)), Offset(cursorX, cy - h * 0.25f), Offset(cursorX, cy + h * 0.25f), strokeWidth = 1f * sizeScale)
}

// ============================================================ 15. DATA STREAM ============================================================

private class Streak(val xFrac: Float, val lenPx: Float, val speed: Float, val phase: Float, val width: Float)
private class DataStreamState {
    var streaks: List<Streak> = emptyList()
}

private fun DrawScope.drawDataStream(state: DataStreamState, tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
    if (state.streaks.isEmpty()) {
        state.streaks = List(70) {
            val speed = 0.06f + Random.nextFloat() * 0.22f // px per ms (at h = 1)
            Streak(Random.nextFloat(), 30f + Random.nextFloat() * 110f, speed, Random.nextFloat() * 20000f, 0.8f + Random.nextFloat() * 1.4f)
        }
    }
    val w = size.width
    val h = size.height
    for (s in state.streaks) {
        val len = s.lenPx * sizeScale
        val travel = h + len
        // Upward: "upload." Faster streaks are brighter — closer to the viewer.
        val yHead = h - ((tMs * s.speed * 1.4f + s.phase) % travel)
        val x = s.xFrac * w
        val bright = ((s.speed - 0.06f) / 0.22f).coerceIn(0f, 1f)
        val alpha = (0.15f + bright * 0.45f) * intensity
        drawLine(tint.copy(alpha = (alpha * 0.5f).coerceIn(0f, 1f)), Offset(x, yHead), Offset(x, yHead + len), strokeWidth = s.width * sizeScale)
        drawLine(tint.copy(alpha = alpha.coerceIn(0f, 1f)), Offset(x, yHead), Offset(x, yHead + len * 0.25f), strokeWidth = s.width * sizeScale)
        drawCircle(tint.copy(alpha = (alpha * 1.3f).coerceIn(0f, 1f)), radius = s.width * sizeScale, center = Offset(x, yHead))
    }
}

// ============================================================ 16. BINARY NOISE ============================================================

private class BinaryCell(var x: Float, var y: Float, var bit: Int, var phase: Float, var durationMs: Float)
private class BinaryNoiseState {
    var zero: TextLayoutResult? = null
    var one: TextLayoutResult? = null
    var cells: List<BinaryCell> = emptyList()
    var lastW = -1f
    var lastH = -1f
    var lastSizeScale = -1f
}

private fun DrawScope.drawBinaryNoise(state: BinaryNoiseState, tMs: Float, textMeasurer: TextMeasurer, intensity: Float, sizeScale: Float, tint: Color) {
    val w = size.width
    val h = size.height
    if (state.cells.isEmpty() || state.lastW != w || state.lastH != h || state.lastSizeScale != sizeScale) {
        state.lastW = w
        state.lastH = h
        state.lastSizeScale = sizeScale
        val style = TextStyle(fontFamily = MonoFontFamily, fontSize = (10f * textScale(sizeScale)).sp, color = Color.White)
        state.zero = textMeasurer.measure(AnnotatedString("0"), style)
        state.one = textMeasurer.measure(AnnotatedString("1"), style)
        val gw = state.zero!!.size.width.toFloat().coerceAtLeast(1f) * 1.7f
        val gh = state.zero!!.size.height.toFloat().coerceAtLeast(1f) * 1.5f
        val cols = (w / gw).toInt().coerceAtLeast(1)
        val rows = (h / gh).toInt().coerceAtLeast(1)
        // Sparse: only a fraction of the grid is ever lit, each cell on its own blink cycle.
        state.cells = List(min(600, cols * rows / 2)) {
            BinaryCell(Random.nextInt(cols) * gw, Random.nextInt(rows) * gh, Random.nextInt(2), Random.nextFloat() * 5000f, 1400f + Random.nextFloat() * 3200f)
        }
        state.cells.forEach { c -> c.x = (c.x / gw).toInt() * gw; c.y = (c.y / gh).toInt() * gh }
        state.cells = state.cells.distinctBy { it.x to it.y }
    }
    val zero = state.zero ?: return
    val one = state.one ?: return
    for (c in state.cells) {
        val local = ((tMs + c.phase) % c.durationMs) / c.durationMs
        // Flip the bit at the start of each cycle so the field keeps rewriting itself.
        if (local < 0.015f) c.bit = Random.nextInt(2)
        val a = sin(local * PI.toFloat())
        drawText(if (c.bit == 0) zero else one, color = tint.copy(alpha = (a * a * 0.55f * intensity).coerceIn(0f, 1f)), topLeft = Offset(c.x, c.y))
    }
}

// ============================================================ 17. ORBITAL RINGS ============================================================

private class OrbitalState {
    var rings: List<FloatArray> = emptyList() // [radiusFrac, tiltDeg, squash, speedDegPerMs, phaseDeg, satellites]
}

private fun DrawScope.drawOrbitalRings(state: OrbitalState, tMs: Float, intensity: Float, sizeScale: Float, tint: Color) {
    if (state.rings.isEmpty()) {
        state.rings = List(6) { k ->
            floatArrayOf(
                0.16f + k * 0.11f,
                Random.nextFloat() * 180f,
                0.25f + Random.nextFloat() * 0.6f,
                (0.004f + Random.nextFloat() * 0.01f) * (if (k % 2 == 0) 1f else -1f),
                Random.nextFloat() * 360f,
                (1 + Random.nextInt(3)).toFloat()
            )
        }
    }
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val base = min(w, h) * 0.62f * sizeScale
    val ringColor = tint.copy(alpha = (0.22f * intensity).coerceIn(0f, 1f))
    // Core.
    drawCircle(tint.copy(alpha = (0.5f * intensity).coerceIn(0f, 1f)), radius = 3f * sizeScale, center = Offset(cx, cy))
    drawCircle(tint.copy(alpha = (0.12f * intensity).coerceIn(0f, 1f)), radius = 10f * sizeScale, center = Offset(cx, cy), style = Stroke(1f * sizeScale))
    for (r in state.rings) {
        val radius = base * r[0]
        val tilt = r[1] + tMs * r[3] * 0.15f // the whole orbit plane slowly precesses
        withTransform({
            rotate(tilt, pivot = Offset(cx, cy))
            scale(1f, r[2], pivot = Offset(cx, cy))
        }) {
            drawCircle(ringColor, radius = radius, center = Offset(cx, cy), style = Stroke(width = 1f * sizeScale))
            // Satellites riding the ring.
            val n = r[5].toInt()
            for (k in 0 until n) {
                val ang = (r[4] + tMs * r[3] * 6f + k * 360f / n) * PI.toFloat() / 180f
                val sx = cx + cos(ang) * radius
                val sy = cy + sin(ang) * radius
                drawCircle(tint.copy(alpha = (0.9f * intensity).coerceIn(0f, 1f)), radius = 2.4f * sizeScale, center = Offset(sx, sy))
                // Short trail behind each satellite.
                val trailAng = ang - 0.35f
                drawLine(
                    tint.copy(alpha = (0.35f * intensity).coerceIn(0f, 1f)),
                    Offset(cx + cos(trailAng) * radius, cy + sin(trailAng) * radius), Offset(sx, sy),
                    strokeWidth = 1.5f * sizeScale
                )
            }
        }
    }
}
