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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.MonoFontFamily
import kotlinx.coroutines.delay
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

// The functions below are read from inside Modifier.graphicsLayer { ... } lambdas instead of the
// composable body. graphicsLayer reads happen at layout/draw time and don't invalidate composition,
// so with up to 91 tiles animating at once, per-frame updates no longer force 91 recompositions —
// only the cheap draw-phase transform re-runs. Reading progress.value directly in the function body
// (the previous approach) was the actual jank source, not the animation math itself.

private fun mainAlpha(revealAnimation: Int, isOpen: Boolean, t: Float): Float {
    if (!isOpen) return t
    return when (revealAnimation) {
        ANIM_SIGNAL_LOCK_ON -> if (t < 0.55f) 0f else 1f
        ANIM_QUANTUM_FLICKER -> {
            val stops = listOf(
                0f to 0f, 0.12f to .8f, 0.20f to 0f, 0.34f to .6f, 0.42f to .1f,
                0.58f to 1f, 0.66f to .3f, 0.80f to 1f, 1f to 1f
            )
            stops.last { it.first <= t }.second
        }
        else -> t
    }
}

private fun mainScale(revealAnimation: Int, isOpen: Boolean, t: Float): Float {
    if (!isOpen) return t * 0.9f
    return when (revealAnimation) {
        ANIM_SIGNAL_LOCK_ON, ANIM_QUANTUM_FLICKER -> 0.9f
        else -> t * 0.9f
    }
}

private fun mainRotation(revealAnimation: Int, isOpen: Boolean, rawProgress: Float): Float {
    if (!isOpen || revealAnimation != ANIM_SERVO_LOCK_ROTATE) return 0f
    // Uses the raw (uncoerced) eased value so the overshoot in openEasing carries the rotation
    // slightly past 0deg before it settles — the "mechanical snap" wobble.
    return lerp(160f, 0f, rawProgress.coerceAtMost(1.2f))
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
    // Each animation picks its own open easing/curve; Quantum Flicker needs raw linear time to
    // key its flicker keyframes off, the others use an eased "openness" curve directly.
    val openEasing = when (revealAnimation) {
        ANIM_SIGNAL_LOCK_ON -> CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
        ANIM_DATA_PACKET_PING -> CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
        ANIM_SERVO_LOCK_ROTATE -> CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.15f)
        ANIM_QUANTUM_FLICKER -> LinearEasing
        else -> CubicBezierEasing(0.16f, 1f, 0.3f, 1f) // Voltage Surge
    }
    LaunchedEffect(isOpen, tile.delayMs, flourish, speedMultiplier, revealAnimation) {
        if (revealAnimation < 0) {
            // NONE — tiles just snap to their final state, no stagger, no tween.
            progress.snapTo(if (isOpen) 1f else 0f)
            return@LaunchedEffect
        }
        if (isOpen) {
            delay(((tile.delayMs + flourish.first).coerceAtLeast(0) * speedMultiplier).toLong())
            progress.animateTo(1f, tween((flourish.second * speedMultiplier).toInt(), easing = openEasing))
        } else {
            delay((tile.delayMs / 2 * speedMultiplier).toLong())
            progress.animateTo(0f, tween((260 * speedMultiplier).toInt(), easing = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)))
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

    Box(
        modifier = modifier.size(width = tile.widthDp, height = tile.heightDp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = progress.value.coerceIn(0f, 1f)
                    this.alpha = mainAlpha(revealAnimation, isOpen, t)
                    // Lifted slightly above its resting size while dragged — reads as "raised off
                    // the grid," on top of the wiggle's rotational jitter.
                    val s = mainScale(revealAnimation, isOpen, t) * if (isDragging) 1.12f else 1f
                    scaleX = s
                    scaleY = s
                    rotationZ = mainRotation(revealAnimation, isOpen, progress.value)
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
            if (!effectiveShowIcon && (app != null || isFolder)) {
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
