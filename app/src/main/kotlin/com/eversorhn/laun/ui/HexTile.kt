package com.eversorhn.laun.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.MonoFontFamily
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.random.Random

private fun lighten(color: Color, amount: Float): Color = Color(
    red = color.red + (1f - color.red) * amount,
    green = color.green + (1f - color.green) * amount,
    blue = color.blue + (1f - color.blue) * amount
)

private fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

/** Stable per-app id derived from the package name — reads like a system tag, not a list index. */
private fun hexTag(packageName: String): String =
    "0x" + (packageName.hashCode() and 0xFFFF).toString(16).uppercase().padStart(4, '0')

/** REVEAL_ANIMATIONS indices, kept in sync with [com.eversorhn.laun.data.REVEAL_ANIMATIONS]. */
private const val ANIM_VOLTAGE_SURGE = 0
private const val ANIM_SIGNAL_LOCK_ON = 1
private const val ANIM_DATA_PACKET_PING = 2
private const val ANIM_SERVO_LOCK_ROTATE = 3
private const val ANIM_QUANTUM_FLICKER = 4
private const val ANIM_HOLO_SCANLINE = 5
private const val ANIM_IRIS_APERTURE = 6
private const val ANIM_WIREFRAME_BUILD = 7
private const val ANIM_PIXEL_DECODE = 8
private const val ANIM_GLITCH_SLICE = 9
private const val ANIM_SHUTTER_BLINDS = 10
private const val ANIM_HEX_PULSE = 11
private const val ANIM_GYRO_SPIN = 12
private const val ANIM_DEPLOY_DROP = 13

/**
 * Compose scales every tween by the system's "Animator duration scale" (Developer options) — at
 * 0 (a common "make the phone snappy" tweak) each reveal completes on its first frame, so only the
 * per-tile stagger (a plain delay, which isn't scaled) survived: tiles just popped in one after
 * another with no motion at all, regardless of ANIMATION SPEED. Confirmed on-device via logging.
 * The reveal is LAUN's own signature, and LAUN already has its own speed control (including
 * INSTANT), so the reveal animates under a fixed scale of 1 and ANIMATION SPEED alone decides.
 */
private val UnscaledMotion = object : MotionDurationScale {
    override val scaleFactor: Float get() = 1f
}

/** The tile's rendered footprint inside its layout box — same 0.9 as HexGrid's hit-testing. */
private const val RENDER_SCALE = 0.9f

/** Reveals implemented in the draw phase (clipping/overlays around the tile's own content) rather
 *  than purely as a graphicsLayer transform — these get a [Modifier.drawWithContent] on the tile. */
private val DRAW_PHASE_REVEALS = setOf(
    ANIM_HOLO_SCANLINE, ANIM_IRIS_APERTURE, ANIM_WIREFRAME_BUILD, ANIM_PIXEL_DECODE,
    ANIM_GLITCH_SLICE, ANIM_SHUTTER_BLINDS, ANIM_HEX_PULSE
)

/** Pointy-top hexagon, matching demo.html's clip-path: polygon(50% 0%,100% 25%,100% 75%,50% 100%,0% 75%,0% 25%). */
internal val HexShape = GenericShape { size, _ ->
    moveTo(size.width * 0.5f, 0f)
    lineTo(size.width, size.height * 0.25f)
    lineTo(size.width, size.height * 0.75f)
    lineTo(size.width * 0.5f, size.height)
    lineTo(0f, size.height * 0.75f)
    lineTo(0f, size.height * 0.25f)
    close()
}

// The functions below are read from inside Modifier.graphicsLayer { ... } / drawWithContent { ... }
// lambdas instead of the composable body. Those reads happen at layout/draw time and don't
// invalidate composition, so with up to 91 tiles animating at once, per-frame updates no longer
// force 91 recompositions — only the cheap draw-phase transform re-runs. Reading progress.value
// directly in the function body (the previous approach) was the actual jank source, not the
// animation math itself.

/** Quantum Flicker's alpha keyframes (time -> alpha). Top-level so the per-frame graphicsLayer
 *  lambda below reads a shared constant instead of allocating this list on every frame of every
 *  animating tile (up to 91 at once). */
private val QUANTUM_FLICKER_STOPS = listOf(
    0f to 0f, 0.12f to .8f, 0.20f to 0f, 0.34f to .6f, 0.42f to .1f,
    0.58f to 1f, 0.66f to .3f, 0.80f to 1f, 1f to 1f
)

/** Open-easing per reveal animation — allocated once, not per HexTile recomposition. Quantum
 *  Flicker / the draw-phase reveals need raw linear time to key their keyframes off; the others
 *  use an eased "openness" curve directly. */
private val EASING_SIGNAL_LOCK_ON = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val EASING_DATA_PACKET_PING = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
private val EASING_SERVO_LOCK_ROTATE = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.15f)
private val EASING_VOLTAGE_SURGE = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
private val EASING_SMOOTH = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private val EASING_CLOSE = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)

private fun mainAlpha(revealAnimation: Int, isOpen: Boolean, t: Float): Float {
    if (!isOpen) return t
    return when (revealAnimation) {
        ANIM_SIGNAL_LOCK_ON -> if (t < 0.55f) 0f else 1f
        ANIM_QUANTUM_FLICKER -> {
            var alpha = 0f
            for ((time, a) in QUANTUM_FLICKER_STOPS) {
                if (time <= t) alpha = a else break
            }
            alpha
        }
        // Revealed by clipping in the draw phase, so the layer itself is fully opaque throughout.
        ANIM_HOLO_SCANLINE, ANIM_IRIS_APERTURE, ANIM_PIXEL_DECODE, ANIM_SHUTTER_BLINDS -> 1f
        // Outline draws first, the face fades in underneath it during the second half.
        ANIM_WIREFRAME_BUILD -> ((t - 0.4f) / 0.5f).coerceIn(0f, 1f)
        // Slices are visible almost immediately; what animates is their misalignment settling.
        ANIM_GLITCH_SLICE -> (t * 3f).coerceIn(0f, 1f)
        else -> t
    }
}

private fun mainScale(revealAnimation: Int, isOpen: Boolean, t: Float, rawProgress: Float): Float {
    if (!isOpen) return t * RENDER_SCALE
    return when (revealAnimation) {
        ANIM_SIGNAL_LOCK_ON, ANIM_QUANTUM_FLICKER,
        ANIM_HOLO_SCANLINE, ANIM_IRIS_APERTURE, ANIM_WIREFRAME_BUILD,
        ANIM_PIXEL_DECODE, ANIM_GLITCH_SLICE, ANIM_SHUTTER_BLINDS -> RENDER_SCALE
        // Comes in oversized and "lands" — the overshooting easing dips it briefly below rest size
        // right at touchdown, which reads as the impact.
        ANIM_DEPLOY_DROP -> lerp(1.3f, RENDER_SCALE, rawProgress.coerceAtMost(1.15f))
        else -> t * RENDER_SCALE
    }
}

private fun mainRotation(revealAnimation: Int, isOpen: Boolean, rawProgress: Float): Float {
    if (!isOpen) return 0f
    return when (revealAnimation) {
        // Uses the raw (uncoerced) eased value so the overshoot in openEasing carries the rotation
        // slightly past 0deg before it settles — the "mechanical snap" wobble.
        ANIM_SERVO_LOCK_ROTATE -> lerp(160f, 0f, rawProgress.coerceAtMost(1.2f))
        // A full turn while growing in — the servo's bigger sibling.
        ANIM_GYRO_SPIN -> lerp(360f, 0f, rawProgress.coerceAtMost(1.15f))
        else -> 0f
    }
}

private fun mainTranslationY(revealAnimation: Int, isOpen: Boolean, rawProgress: Float, heightPx: Float): Float {
    if (!isOpen || revealAnimation != ANIM_DEPLOY_DROP) return 0f
    // Drops in from above; the overshoot past 1 pushes it a hair below rest before it settles.
    return lerp(-heightPx * 0.7f, 0f, rawProgress.coerceAtMost(1.15f))
}

private fun voltageFlashAlpha(isOpen: Boolean, t: Float): Float {
    if (!isOpen) return 0f
    val p1 = exp(-((t - 0.08f) * 22f).let { it * it })
    val p2 = exp(-((t - 0.40f) * 10f).let { it * it }) * 0.7f
    return (p1 + p2).coerceIn(0f, 1f) * 0.85f
}

private fun reticleAlpha(isOpen: Boolean, t: Float): Float {
    if (!isOpen) return 0f
    return when {
        t < 0.5f -> lerp(0f, 1f, t / 0.5f)
        t < 0.8f -> 1f
        else -> lerp(1f, 0f, (t - 0.8f) / 0.2f)
    }
}

private fun reticleScale(t: Float): Float = when {
    t < 0.5f -> lerp(2.4f, 1.05f, t / 0.5f)
    t < 0.65f -> lerp(1.05f, 1f, (t - 0.5f) / 0.15f)
    else -> lerp(1f, 0.96f, (t - 0.65f) / 0.35f)
}

private fun reticleRotation(t: Float): Float = when {
    t < 0.5f -> lerp(0f, 45f, t / 0.5f)
    t < 0.65f -> lerp(45f, 90f, (t - 0.5f) / 0.15f)
    else -> 90f
}

private fun pingProgress(t: Float): Float = (t / 0.5f).coerceIn(0f, 1f)

private fun pingAlpha(isOpen: Boolean, t: Float): Float =
    if (isOpen) (1f - pingProgress(t)) * 0.9f else 0f

private fun pingScale(t: Float): Float = lerp(0.15f, 1.4f, pingProgress(t))

/** Writes the tile's pointy-top hexagon outline at [scale] around the box center into [path]. */
private fun buildHexPath(path: Path, w: Float, h: Float, scale: Float) {
    path.reset()
    val cx = w / 2f
    val cy = h / 2f
    val hw = cx * scale
    val hh = cy * scale
    path.moveTo(cx, cy - hh)
    path.lineTo(cx + hw, cy - hh / 2f)
    path.lineTo(cx + hw, cy + hh / 2f)
    path.lineTo(cx, cy + hh)
    path.lineTo(cx - hw, cy + hh / 2f)
    path.lineTo(cx - hw, cy - hh / 2f)
    path.close()
}

/** Deterministic 0..1 noise for a grid cell / band of one tile — stable across frames (so a
 *  pixel-decode cell resolves once, not flickering), different per tile (so neighbors don't decode
 *  in lockstep). Plain integer hash, no allocation. */
private fun cellNoise(i: Int, j: Int, seed: Int): Float {
    var x = (i * 73856093) xor (j * 19349663) xor (seed * 83492791)
    x = x xor (x ushr 13)
    x *= -0x7a143595 // 0x85ebca6b
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 65535f
}

/** Per-tile scratch geometry for the draw-phase reveals — rebuilt only when the tile's pixel size
 *  changes, never per frame. */
private class RevealDrawCache {
    private var w = -1f
    private var h = -1f
    val hex = Path()
    val scratch = Path()
    val measure = PathMeasure()
    var hexLength = 0f
    lateinit var stroke: Stroke
    lateinit var strokeThin: Stroke

    fun ensure(size: Size, density: Float) {
        if (size.width == w && size.height == h) return
        w = size.width
        h = size.height
        buildHexPath(hex, w, h, RENDER_SCALE)
        measure.setPath(hex, forceClosed = true)
        hexLength = measure.length
        stroke = Stroke(width = 1.5f * density)
        strokeThin = Stroke(width = 1f * density)
    }
}

/**
 * The draw-phase reveals. [drawContent] is the fully-composed tile (ring, face, label/icon, the
 * graphicsLayer transform already applied) — each branch decides how much of it is visible at
 * [t] and what to draw on top. Nothing here touches composition: `progress` is read inside the
 * draw lambda that calls this, same as the graphicsLayer reveals.
 */
private fun ContentDrawScope.drawReveal(kind: Int, t: Float, cache: RevealDrawCache, seed: Int) {
    cache.ensure(size, density)
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val hw = cx * RENDER_SCALE
    val hh = cy * RENDER_SCALE
    val left = cx - hw
    val right = cx + hw
    val top = cy - hh
    val bottom = cy + hh
    val fg = LaunColors.fg
    // Every bright edge/line fades out over the last stretch, so nothing pops when the reveal
    // ends and this whole function stops being called.
    val endFade = 1f - ((t - 0.85f) / 0.15f).coerceIn(0f, 1f)

    when (kind) {
        ANIM_HOLO_SCANLINE -> {
            // A hologram materializing: content exists only above the scan line, which sweeps
            // top to bottom with a soft glow trailing it.
            val scanY = top + (bottom - top) * t
            clipRect(top = 0f, bottom = scanY) { this@drawReveal.drawContent() }
            clipPath(cache.hex) {
                val glow = 12f * density
                drawRect(fg.copy(alpha = 0.22f * endFade), Offset(left, scanY - glow), Size(right - left, glow))
                drawRect(fg.copy(alpha = 0.95f * endFade), Offset(left, scanY - 1f * density), Size(right - left, 1.5f * density))
            }
        }
        ANIM_IRIS_APERTURE -> {
            // A hex-shaped iris opening from the center — content is visible only inside the
            // aperture, whose bright rim fades as it reaches the tile's own edge.
            val s = lerp(0.05f, RENDER_SCALE, t)
            buildHexPath(cache.scratch, w, h, s)
            clipPath(cache.scratch) { this@drawReveal.drawContent() }
            drawPath(cache.scratch, fg.copy(alpha = 0.9f * (1f - t)), style = cache.stroke)
        }
        ANIM_WIREFRAME_BUILD -> {
            // The outline is traced first (a bright cursor running the hex's perimeter), then the
            // face fades in beneath it (see mainAlpha) and the wire fades away.
            drawContent()
            val outlineT = (t / 0.55f).coerceIn(0f, 1f)
            cache.scratch.reset()
            cache.measure.getSegment(0f, cache.hexLength * outlineT, cache.scratch, startWithMoveTo = true)
            val wireFade = 1f - ((t - 0.7f) / 0.3f).coerceIn(0f, 1f)
            drawPath(cache.scratch, fg.copy(alpha = wireFade), style = cache.stroke)
            if (outlineT < 1f) {
                val head = cache.measure.getPosition(cache.hexLength * outlineT)
                drawCircle(fg, radius = 2.5f * density, center = head)
            }
        }
        ANIM_PIXEL_DECODE -> {
            // Content is there from the start, masked by a grid of black cells that each resolve
            // at their own (stable, per-tile) threshold — the ones resolving right now flash
            // bright, so the decode reads as a front sweeping through noise rather than a fade.
            drawContent()
            val cols = 7
            val cell = (right - left) / cols
            val rows = ceil((bottom - top) / cell).toInt()
            clipPath(cache.hex) {
                for (i in 0 until cols) for (j in 0 until rows) {
                    val n = cellNoise(i, j, seed)
                    val x = left + i * cell
                    val y = top + j * cell
                    when {
                        n > t -> drawRect(LaunColors.bg, Offset(x, y), Size(cell + 0.5f, cell + 0.5f))
                        n > t - 0.06f -> drawRect(
                            fg.copy(alpha = ((n - (t - 0.06f)) / 0.06f) * 0.6f),
                            Offset(x, y), Size(cell + 0.5f, cell + 0.5f)
                        )
                    }
                }
            }
        }
        ANIM_GLITCH_SLICE -> {
            // Horizontal slices of the tile land misaligned and snap into place — displacement
            // decays quadratically, each band jumps direction at its own step rate so the motion
            // is jittery-digital, not a smooth slide. Deterministic per tile, no randomness per frame.
            val bands = 5
            val bh = (bottom - top) / bands
            val decay = (1f - t) * (1f - t)
            val amp = hw * 0.7f * decay
            for (b in 0 until bands) {
                val step = (t * (9 + b * 2)).toInt()
                val dir = if (cellNoise(b, step, seed) > 0.5f) 1f else -1f
                val dx = amp * dir * (0.35f + 0.65f * cellNoise(b, 991, seed))
                translate(left = dx) {
                    clipRect(top = top + b * bh, bottom = top + (b + 1) * bh + 0.5f) { this@drawReveal.drawContent() }
                }
            }
        }
        ANIM_SHUTTER_BLINDS -> {
            // Four vertical louvres, each wiping open left-to-right in a staggered cascade with a
            // bright edge on its leading front.
            val strips = 4
            val sw = (right - left) / strips
            for (k in 0 until strips) {
                val f = ((t - k * 0.12f) / 0.55f).coerceIn(0f, 1f)
                if (f <= 0f) continue
                val x0 = left + k * sw
                clipRect(left = x0, right = x0 + sw * f + 0.5f) { this@drawReveal.drawContent() }
                if (f < 1f) {
                    clipPath(cache.hex) {
                        drawRect(fg.copy(alpha = 0.9f * endFade), Offset(x0 + sw * f - 1f * density, top), Size(2f * density, bottom - top))
                    }
                }
            }
        }
        ANIM_HEX_PULSE -> {
            // DATA PACKET PING's hexagonal sibling: two concentric hex rings expand outward from
            // the tile's center and dissolve — reads as the tile broadcasting its arrival.
            drawContent()
            for (k in 0..1) {
                val p = ((t - k * 0.18f) / 0.6f).coerceIn(0f, 1f)
                if (p <= 0f || p >= 1f) continue
                buildHexPath(cache.scratch, w, h, lerp(0.25f, 1.7f, p))
                drawPath(cache.scratch, fg.copy(alpha = (1f - p) * 0.85f), style = if (k == 0) cache.stroke else cache.strokeThin)
            }
        }
        else -> drawContent()
    }
}

@Composable
internal fun HexTile(
    tile: TileLayout,
    isOpen: Boolean,
    isActive: Boolean,
    isDragging: Boolean = false,
    colorHex: String?,
    showIcon: Boolean,
    iconSizePercent: Int = 55,
    revealAnimation: Int,
    animationSpeed: Int,
    modifier: Modifier = Modifier
) {
    val progress = remember(tile.index) { Animatable(0f) }
    // 0 = instant (no animation), 100 = near-standstill slow motion.
    val speedMultiplier = (animationSpeed.coerceIn(0, 100) / 100f) * 6f
    // Re-rolled fresh on every open (not memoized to the layout), same as the demo re-randomizing
    // on every layoutHexes() call — without it every reopen would play the exact same motion.
    // The jitter is what keeps neighboring tiles from popping in at the exact same instant.
    val flourish = remember(tile.index, isOpen) {
        Random.nextInt(-50, 51) to Random.nextInt(480, 681)
    }
    val openEasing = when (revealAnimation) {
        ANIM_SIGNAL_LOCK_ON, ANIM_GLITCH_SLICE -> EASING_SIGNAL_LOCK_ON
        ANIM_DATA_PACKET_PING, ANIM_HEX_PULSE, ANIM_DEPLOY_DROP -> EASING_DATA_PACKET_PING
        ANIM_SERVO_LOCK_ROTATE, ANIM_GYRO_SPIN -> EASING_SERVO_LOCK_ROTATE
        // Keyframe-driven reveals want an unwarped 0..1 so their own curves stay as authored.
        ANIM_QUANTUM_FLICKER, ANIM_WIREFRAME_BUILD, ANIM_PIXEL_DECODE, ANIM_SHUTTER_BLINDS -> LinearEasing
        ANIM_HOLO_SCANLINE, ANIM_IRIS_APERTURE -> EASING_SMOOTH
        else -> EASING_VOLTAGE_SURGE
    }
    // Whether the previous run of the effect below was an "open" — plain holder, not state, so
    // writing it never invalidates anything.
    val wasOpen = remember { booleanArrayOf(false) }
    LaunchedEffect(isOpen, tile.delayMs, flourish, speedMultiplier, revealAnimation) {
        if (revealAnimation < 0) {
            // NONE — tiles just snap to their final state, no stagger, no tween.
            progress.snapTo(if (isOpen) 1f else 0f)
            wasOpen[0] = isOpen
            return@LaunchedEffect
        }
        if (isOpen) {
            // A fresh open always plays from the start. Without this, an open that interrupts a
            // close (the picker preview's replay flips closed→open within 40ms; a quick re-tap
            // on the home screen does the same) resumed from wherever the close had gotten to —
            // ~0.99 for the preview — so the "replay" showed nothing at all.
            if (!wasOpen[0]) progress.snapTo(0f)
            wasOpen[0] = true
            delay(((tile.delayMs + flourish.first).coerceAtLeast(0) * speedMultiplier).toLong())
            withContext(UnscaledMotion) {
                progress.animateTo(1f, tween((flourish.second * speedMultiplier).toInt(), easing = openEasing))
            }
        } else {
            wasOpen[0] = false
            delay((tile.delayMs / 2 * speedMultiplier).toLong())
            withContext(UnscaledMotion) {
                progress.animateTo(0f, tween((260 * speedMultiplier).toInt(), easing = EASING_CLOSE))
            }
        }
    }

    val tileColor = remember(colorHex) {
        colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    }

    // Pressing a colored tile brightens its own color rather than replacing it with plain white —
    // it should still read as "that" tile while held, not flash into a different, unrelated color.
    val ringColor = when {
        isActive && tileColor != null -> lighten(tileColor, 0.45f)
        isActive -> LaunColors.accent
        tileColor != null -> tileColor
        else -> LaunColors.border
    }
    val faceColor = when {
        isActive && tileColor != null -> Color(
            red = (tileColor.red * 0.35f + LaunColors.bg2.red * 0.65f),
            green = (tileColor.green * 0.35f + LaunColors.bg2.green * 0.65f),
            blue = (tileColor.blue * 0.35f + LaunColors.bg2.blue * 0.65f)
        )
        isActive -> Color(0xFF161616)
        tileColor != null -> Color(
            red = (tileColor.red * 0.2f + LaunColors.bg2.red * 0.8f),
            green = (tileColor.green * 0.2f + LaunColors.bg2.green * 0.8f),
            blue = (tileColor.blue * 0.2f + LaunColors.bg2.blue * 0.8f)
        )
        else -> LaunColors.bg2
    }

    // Draw-phase reveals wrap the OUTER box (not the graphicsLayer one), so their clipping and
    // overlays sit on top of the already-transformed tile and aren't themselves faded/scaled by
    // the layer — a wireframe outline must stay crisp while the face underneath fades in.
    val drawCache = remember { RevealDrawCache() }
    val revealDrawModifier = if (revealAnimation in DRAW_PHASE_REVEALS) {
        Modifier.drawWithContent {
            val t = progress.value.coerceIn(0f, 1f)
            // Closing (or already fully open) falls back to the plain layer fade — these reveals
            // are one-way "materialize" effects, and at t == 1 there's nothing left to mask.
            if (!isOpen || t >= 1f) drawContent() else drawReveal(revealAnimation, t, drawCache, tile.index)
        }
    } else Modifier

    Box(
        modifier = modifier.size(width = tile.widthDp, height = tile.heightDp).then(revealDrawModifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val raw = progress.value
                    val t = raw.coerceIn(0f, 1f)
                    this.alpha = mainAlpha(revealAnimation, isOpen, t)
                    // Lifted slightly above its resting size while dragged — reads as "raised off
                    // the grid."
                    val s = mainScale(revealAnimation, isOpen, t, raw) * if (isDragging) 1.12f else 1f
                    scaleX = s
                    scaleY = s
                    rotationZ = mainRotation(revealAnimation, isOpen, raw)
                    translationY = mainTranslationY(revealAnimation, isOpen, raw, size.height)
                }
                .clip(HexShape)
                .background(ringColor)
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(HexShape)
                .background(faceColor)
        ) {
            val app = tile.apps.singleOrNull()
            val isFolder = tile.apps.size > 1
            // A chosen icon-pack icon shows regardless of the global toggle — picking one specific
            // icon for this tile is the whole point, it shouldn't stay hidden behind a setting.
            val effectiveShowIcon = showIcon || app?.iconOverridden == true
            // Text was tuned at the default 100dp tile size — scaling it by how far this tile's
            // actual width sits from that baseline keeps the label proportionate whether it's the
            // shared SIZE setting or a per-tile override (see the color menu's own SIZE slider)
            // that made this particular tile bigger or smaller than the rest.
            val textScale = (tile.widthDp / 100.dp).coerceIn(0.4f, 2.5f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (effectiveShowIcon && app != null) {
                    // Percent is of tile WIDTH, but a pointy-top hex is narrowest at its top/bottom
                    // points — capping the range itself (rather than trusting the caller) is what
                    // actually guarantees the icon can never crowd or cross the tile's edge,
                    // regardless of what's persisted.
                    val iconSizeDp = tile.widthDp * (iconSizePercent.coerceIn(20, 75) / 100f)
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        modifier = Modifier.size(iconSizeDp)
                    )
                } else if (showIcon && isFolder) {
                    // A small 2x2 preview of the folder's first four icons instead of one big one.
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (row in 0..1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                for (col in 0..1) {
                                    val member = tile.apps.getOrNull(row * 2 + col)
                                    if (member != null) {
                                        Image(
                                            bitmap = member.icon,
                                            contentDescription = member.label,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.size(12.dp))
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // The app name is the focal, centered element — the small hex tag (a stable
                    // per-app id, not a positional/sequential number) sits above it as a quiet
                    // technical accent instead of competing with it for the tile's center.
                    when {
                        app != null -> Text(
                            text = app.label,
                            color = LaunColors.fg,
                            fontFamily = HeadFontFamily,
                            fontSize = 10.5.sp * textScale,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        isFolder -> Text(
                            text = tile.apps.first().label,
                            color = LaunColors.fg,
                            fontFamily = HeadFontFamily,
                            fontSize = 10.5.sp * textScale,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        else -> Text(
                            text = "+",
                            color = LaunColors.dim,
                            fontFamily = HeadFontFamily,
                            fontSize = 15.sp * textScale
                        )
                    }
                }
            }
            // Below the SIZE slider's own floor (60dp — see TilePreviewCluster's fixed 40dp
            // preview tiles), the tag's top-padding and the centered label's line-height both
            // stop shrinking proportionally and start overlapping instead of just getting smaller
            // — so it's dropped entirely rather than rendered illegibly.
            if (!effectiveShowIcon && (app != null || isFolder) && tile.widthDp >= 60.dp) {
                Text(
                    text = if (isFolder) "FOLDER" else hexTag(app!!.packageName),
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 8.sp * textScale,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 22.dp * textScale)
                )
            }
            if (revealAnimation == ANIM_VOLTAGE_SURGE) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(HexShape)
                        .graphicsLayer {
                            alpha = voltageFlashAlpha(isOpen, progress.value.coerceIn(0f, 1f))
                        }
                        .background(LaunColors.fg)
                )
            }
        }
        }

        if (revealAnimation == ANIM_DATA_PACKET_PING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = progress.value.coerceIn(0f, 1f)
                        alpha = pingAlpha(isOpen, t)
                        val s = pingScale(t)
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
                    .border(1.5.dp, LaunColors.fg, CircleShape)
            )
        }

        if (revealAnimation == ANIM_SIGNAL_LOCK_ON) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = progress.value.coerceIn(0f, 1f)
                        alpha = reticleAlpha(isOpen, t)
                        val s = reticleScale(t)
                        scaleX = s
                        scaleY = s
                        rotationZ = reticleRotation(t)
                    }
                    .border(1.5.dp, LaunColors.fg)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .align(Alignment.Center)
                        .background(LaunColors.fg)
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .align(Alignment.Center)
                        .background(LaunColors.fg)
                )
            }
        }
    }
}
