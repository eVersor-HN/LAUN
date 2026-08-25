package com.eversorhn.laun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

private data class FaqEntry(val question: String, val answer: String)

private val FAQ_ENTRIES = listOf(
    FaqEntry("HOW DO I OPEN THE GRID?", "Tap anywhere on the home screen."),
    FaqEntry("HOW DO I OPEN SETTINGS?", "Long-press an empty spot on the home screen — works whether the grid is open or closed."),
    FaqEntry("HOW DO I ASSIGN AN APP TO A TILE?", "Open the grid, then tap an empty tile — the picker there has its own search field."),
    FaqEntry("HOW DO I CREATE A FOLDER?", "Select more than one app when assigning a tile."),
    FaqEntry("HOW DO I CHANGE A TILE'S COLOR, EDIT ITS APPS, OR CLEAR IT?", "Long-press an occupied tile."),
    FaqEntry("HOW DO I MOVE A TILE?", "Long-press an occupied tile, then drag it onto another tile once it lifts — the two swap places."),
    FaqEntry("HOW DO I SET LAUNCHER AS MY DEFAULT HOME APP?", "Go to your phone's Settings → Apps → Default apps → Home app, and choose LAUNCHER.")
)

/** Minimal FAQ covering the app's gestures — everything else in the UI is discoverable on sight. */
@Composable
fun FaqSheet(immersiveEnabled: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HideSystemBarsWhileShown(immersiveEnabled)

        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .heightIn(max = 560.dp)
                .background(LaunColors.bg2)
                .border(1.dp, LaunColors.border)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "FAQ",
                color = LaunColors.fg,
                fontFamily = HeadFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            FAQ_ENTRIES.forEachIndexed { i, entry ->
                Column(modifier = Modifier.padding(top = if (i == 0) 0.dp else 16.dp)) {
                    Text(
                        text = entry.question,
                        color = LaunColors.dim,
                        fontFamily = MonoFontFamily,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = entry.answer,
                        color = LaunColors.fg,
                        fontFamily = HeadFontFamily,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
