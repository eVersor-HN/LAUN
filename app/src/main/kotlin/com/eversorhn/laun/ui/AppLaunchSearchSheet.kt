package com.eversorhn.laun.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

/**
 * Swipe-up-anywhere search — a lighter, single-purpose cousin of [AppPickerSheet]: search, then
 * tap an app to launch it directly. Also auto-launches on its own the moment the query narrows
 * the list down to exactly one match, so typing enough of a name is enough on its own — no need
 * to also tap it.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AppLaunchSearchSheet(
    apps: List<AppInfo>,
    immersiveEnabled: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(filtered, query) {
        if (query.isNotBlank() && filtered.size == 1) onLaunch(filtered[0])
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HideSystemBarsWhileShown(immersiveEnabled)

        // More of the screen is free once the keyboard's gone — grow into it instead of leaving
        // dead space, same idea as dismissing the keyboard to see more of a normal search result.
        val imeVisible = WindowInsets.isImeVisible
        val maxHeight = if (imeVisible) 520.dp else 640.dp
        val listHeight = if (imeVisible) 400.dp else 520.dp

        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .heightIn(max = maxHeight)
                .background(LaunColors.bg2)
                .border(1.dp, LaunColors.border)
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = "LAUNCH APP",
                color = LaunColors.dim,
                fontFamily = MonoFontFamily,
                fontSize = 10.sp,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .border(1.dp, LaunColors.border)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = "SEARCH_",
                        color = LaunColors.dim,
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp,
                        letterSpacing = 0.6.sp
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = LaunColors.fg,
                        fontFamily = MonoFontFamily,
                        fontSize = 12.sp,
                        letterSpacing = 0.6.sp
                    ),
                    cursorBrush = SolidColor(LaunColors.fg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            if (filtered.isEmpty()) {
                Text(
                    text = "No results.",
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(listHeight)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLaunch(app) }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Text(
                                text = highlightedLabel(app.label, query),
                                color = LaunColors.fg,
                                fontFamily = HeadFontFamily,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
