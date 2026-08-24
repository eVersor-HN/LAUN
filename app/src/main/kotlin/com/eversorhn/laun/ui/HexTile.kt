package com.eversorhn.laun.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.MonoFontFamily
import kotlinx.coroutines.delay

/** Pointy-top hexagon, matching demo.html's clip-path: polygon(50% 0%,100% 25%,100% 75%,50% 100%,0% 75%,0% 25%). */
private val HexShape = GenericShape { size, _ ->
    moveTo(size.width * 0.5f, 0f)
    lineTo(size.width, size.height * 0.25f)
    lineTo(size.width, size.height * 0.75f)
    lineTo(size.width * 0.5f, size.height)
    lineTo(0f, size.height * 0.75f)
    lineTo(0f, size.height * 0.25f)
    close()
}

@Composable
internal fun HexTile(
    tile: TileLayout,
    isOpen: Boolean,
    isActive: Boolean,
    colorHex: String?,
    modifier: Modifier = Modifier
) {
    val progress = remember(tile.index) { Animatable(0f) }
    LaunchedEffect(isOpen, tile.delayMs) {
        if (isOpen) {
            delay(tile.delayMs.toLong())
            progress.animateTo(1f, tween(600, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
        } else {
            progress.snapTo(0f)
        }
    }

    val density = LocalDensity.current
    val fxPx = with(density) { tile.fxDp.dp.toPx() }
    val fyPx = with(density) { tile.fyDp.dp.toPx() }

    val tileColor = remember(colorHex) {
        colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    }

    val ringColor = when {
        isActive -> LaunColors.accent
        tileColor != null -> tileColor
        else -> LaunColors.border
    }
    val faceColor = when {
        isActive -> Color(0xFF161616)
        tileColor != null -> Color(
            red = (tileColor.red * 0.2f + LaunColors.bg2.red * 0.8f),
            green = (tileColor.green * 0.2f + LaunColors.bg2.green * 0.8f),
            blue = (tileColor.blue * 0.2f + LaunColors.bg2.blue * 0.8f)
        )
        else -> LaunColors.bg2
    }

    Box(
        modifier = modifier
            .size(width = tile.widthDp, height = tile.heightDp)
            .graphicsLayer {
                alpha = progress.value
                scaleX = 0.9f
                scaleY = 0.9f
                translationX = (1f - progress.value) * fxPx
                translationY = (1f - progress.value) * fyPx
                rotationZ = (1f - progress.value) * tile.frDeg
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "APP_" + (tile.index + 1).toString().padStart(2, '0'),
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 8.5.sp,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
                if (tile.app != null) {
                    Text(
                        text = tile.app.label,
                        color = LaunColors.fg,
                        fontFamily = HeadFontFamily,
                        fontSize = 10.5.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                } else {
                    Text(
                        text = "+",
                        color = LaunColors.dim,
                        fontFamily = HeadFontFamily,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }
}
