package com.eversorhn.laun.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.eversorhn.laun.R

/** Style 07 — Ink / White. Pure monochrome, a single accent (white). See demo.html :root. */
object LaunColors {
    val bg = Color(0xFF000000)
    val bg2 = Color(0xFF0D0D0D)
    val fg = Color(0xFFFFFFFF)
    val dim = Color(0xFF666666)
    val dim2 = Color(0xFF3A3A3A)
    val accent = Color(0xFFFFFFFF)
    val border = Color(0xFF2A2A2A)
    /** Search-query match highlight — same red as the color popover's palette. */
    val searchMatch = Color(0xFFFF2D3C)
}

/** Same 20-color palette as the color-popover in demo.html, for the long-press tile picker. */
val TILE_COLOR_PALETTE = listOf(
    0xFF00E5FF, 0xFFFFB020, 0xFFFF2D3C, 0xFF3D7DFF, 0xFFAEF000, 0xFFFF2FB0, 0xFFFFFFFF, 0xFFDFE6EE, 0xFFD4AF37, 0xFF1FD3C4,
    0xFFFF6B35, 0xFFA855F7, 0xFF84CC16, 0xFFF43F5E, 0xFF6366F1, 0xFFFACC15, 0xFFFB7185, 0xFFC026D3, 0xFF34D399, 0xFF64748B
).map { Color(it) }

/** Same faces as the demo.html prototype (Chakra Petch / JetBrains Mono), bundled under res/font. */
val HeadFontFamily = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold)
)
val MonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal)
)

val TileNameStyle = TextStyle(
    fontFamily = HeadFontFamily,
    fontWeight = FontWeight.SemiBold,
    color = LaunColors.fg
)

val MonoStyle = TextStyle(
    fontFamily = MonoFontFamily,
    color = LaunColors.dim
)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        background = LaunColors.bg,
        surface = LaunColors.bg2,
        onBackground = LaunColors.fg,
        onSurface = LaunColors.fg,
        primary = LaunColors.accent,
        onPrimary = LaunColors.bg
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
