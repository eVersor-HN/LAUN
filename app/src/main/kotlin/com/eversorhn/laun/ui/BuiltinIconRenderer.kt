package com.eversorhn.laun.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.eversorhn.laun.data.BUILTIN_ICON_STROKE
import com.eversorhn.laun.data.BUILTIN_ICON_VIEWPORT
import com.eversorhn.laun.data.BuiltinIcon

/**
 * Draws [com.eversorhn.laun.data.BUILTIN_ICONS] — LAUN's own tile glyph set. Each icon's `ops`
 * string is a tiny pre-flattened drawing program (see [BuiltinIcon] for the format); this parses it
 * once per icon into real [Path] objects and caches them, since the picker re-draws the same icons
 * on every scroll frame and a tile re-rasterizes its own icon whenever the override or its color
 * changes.
 */
private class IconOp(
    val path: Path,
    val fill: Boolean,
    val alpha: Float,
    val strokeWidth: Float,
    val dash: PathEffect?,
    val translateX: Float,
    val translateY: Float,
    val rotationDeg: Float,
    val pivotX: Float,
    val pivotY: Float
)

/** Parsed icons by id. Bounded only by the set's own size (300), all of them small. */
private val opCache = HashMap<String, List<IconOp>>()

private fun parseOps(icon: BuiltinIcon): List<IconOp> = opCache.getOrPut(icon.id) {
    icon.ops.split(';').mapNotNull { op ->
        val bar = op.indexOf('|')
        if (bar < 0) return@mapNotNull null
        val flags = op.substring(0, bar)
        val path = runCatching { PathParser().parsePathString(op.substring(bar + 1)).toPath() }
            .getOrNull() ?: return@mapNotNull null

        var fill = false
        var alpha = 1f
        var strokeWidth = BUILTIN_ICON_STROKE
        var dash: PathEffect? = null
        var tx = 0f
        var ty = 0f
        var rot = 0f
        var px = 0f
        var py = 0f
        if (flags.isNotEmpty()) {
            for (flag in flags.split('~')) {
                if (flag.isEmpty()) continue
                val value = flag.substring(1)
                when (flag[0]) {
                    'f' -> fill = true
                    'o' -> alpha = value.toFloatOrNull() ?: 1f
                    'w' -> strokeWidth = value.toFloatOrNull() ?: BUILTIN_ICON_STROKE
                    'a' -> {
                        val (on, off) = value.split(',').let {
                            (it.getOrNull(0)?.toFloatOrNull() ?: 0f) to (it.getOrNull(1)?.toFloatOrNull() ?: 0f)
                        }
                        if (on > 0f) dash = PathEffect.dashPathEffect(floatArrayOf(on, off))
                    }
                    't' -> {
                        val parts = value.split(',')
                        tx = parts.getOrNull(0)?.toFloatOrNull() ?: 0f
                        ty = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                    }
                    'r' -> {
                        val parts = value.split(',')
                        rot = parts.getOrNull(0)?.toFloatOrNull() ?: 0f
                        px = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                        py = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
                    }
                }
            }
        }
        IconOp(path, fill, alpha, strokeWidth, dash, tx, ty, rot, px, py)
    }
}

/**
 * Draws [icon] in [color], scaled to fit a [sizePx]-wide square whose top-left corner is at
 * [topLeft]. Stroke widths scale with the glyph, so it stays proportionate at any tile size.
 */
fun DrawScope.drawBuiltinIcon(icon: BuiltinIcon, color: Color, sizePx: Float, topLeft: Offset = Offset.Zero) {
    val scale = sizePx / BUILTIN_ICON_VIEWPORT
    withTransform({
        translate(topLeft.x, topLeft.y)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        for (op in parseOps(icon)) {
            withTransform({
                if (op.translateX != 0f || op.translateY != 0f) translate(op.translateX, op.translateY)
                if (op.rotationDeg != 0f) rotate(op.rotationDeg, Offset(op.pivotX, op.pivotY))
            }) {
                if (op.fill) {
                    drawPath(op.path, color, alpha = op.alpha)
                } else {
                    drawPath(
                        op.path,
                        color,
                        alpha = op.alpha,
                        style = Stroke(
                            width = op.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                            pathEffect = op.dash
                        )
                    )
                }
            }
        }
    }
}

/**
 * Rasterizes [icon] into a bitmap so it can travel through the same path a real app icon does —
 * [com.eversorhn.laun.data.AppInfo.icon] is an [ImageBitmap], so everything downstream (tiles,
 * folder previews, the icon-size setting) keeps working untouched.
 */
fun buildBuiltinIconBitmap(icon: BuiltinIcon, sizePx: Int, color: Color): ImageBitmap {
    val bitmap = ImageBitmap(sizePx, sizePx)
    val size = sizePx.toFloat()
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(size, size)) {
        drawBuiltinIcon(icon, color, size)
    }
    return bitmap
}
