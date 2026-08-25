package com.eversorhn.laun.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily
import kotlin.math.sqrt

/**
 * A small live preview of the actual home grid — same tile rendering (colors, icons, reveal
 * animation) as the real HexGrid, fed a sample of the user's real slots/colors instead of dummy
 * data, so picking a tile animation or background shows what it will really look like rather
 * than a generic mockup.
 */
@Composable
fun TilePreviewCluster(
    slots: List<List<AppInfo>>,
    tileColors: Map<String, String>,
    showIcons: Boolean,
    iconSizePercent: Int = 55,
    revealAnimation: Int,
    animationSpeed: Int,
    isOpen: Boolean,
    tileCount: Int,
    backgroundAnimationKind: Int,
    showAndroidWallpaper: Boolean,
    wallpaperBitmap: ImageBitmap?,
    backgroundOpacity: Float,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RectangleShape)
                .border(1.dp, LaunColors.border)
                .background(LaunColors.bg)
        ) {
            if (backgroundAnimationKind >= 0) {
                AnimatedWallpaper(kind = backgroundAnimationKind, opacity = backgroundOpacity, modifier = Modifier.fillMaxWidth().height(150.dp))
            } else if (showAndroidWallpaper && wallpaperBitmap != null) {
                Image(
                    bitmap = wallpaperBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(150.dp).alpha(backgroundOpacity)
                )
                Box(Modifier.fillMaxWidth().height(150.dp).background(Color.Black.copy(alpha = 0.55f)))
            }

            // Prefer real occupied+colored tiles first so the sample actually shows something,
            // even when the live grid is mostly empty — then pad out with empty slots.
            val occupied = slots.withIndex().filter { it.value.isNotEmpty() }
            val empty = slots.withIndex().filter { it.value.isEmpty() }
            val sample = (occupied.take(4) + empty.take(3)).take(7)

            val hexW = 40f
            val hexH = hexW * 2f / sqrt(3f)
            val ring = listOf(0 to 0, 1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)
            val containerW = 140f
            val containerH = 128f
            Box(modifier = Modifier.align(Alignment.Center).size(containerW.dp, containerH.dp)) {
                ring.forEachIndexed { i, (q, r) ->
                    val dx = hexW * (q + r / 2f)
                    val dy = hexH * 0.75f * r
                    val apps = sample.getOrNull(i)?.value.orEmpty()
                    HexTile(
                        tile = TileLayout(
                            apps = apps,
                            index = i,
                            centerXDp = containerW / 2f + dx,
                            centerYDp = containerH / 2f + dy,
                            widthDp = hexW.dp,
                            heightDp = hexH.dp,
                            delayMs = 0
                        ),
                        isOpen = isOpen,
                        isActive = false,
                        colorHex = apps.firstOrNull()?.let { tileColors[it.packageName] },
                        showIcon = showIcons,
                        iconSizePercent = iconSizePercent,
                        revealAnimation = revealAnimation,
                        animationSpeed = animationSpeed,
                        modifier = Modifier.offset((containerW / 2f + dx - hexW / 2f).dp, (containerH / 2f + dy - hexH / 2f).dp)
                    )
                }
            }
        }
        Text(
            text = "$tileCount TILES CONFIGURED",
            color = LaunColors.dim,
            fontFamily = MonoFontFamily,
            fontSize = 8.5.sp,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
