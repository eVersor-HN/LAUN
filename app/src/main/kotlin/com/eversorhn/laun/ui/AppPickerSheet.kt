package com.eversorhn.laun.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eversorhn.laun.data.AppInfo
import com.eversorhn.laun.data.MAX_CUSTOM_APP_NAME_LENGTH
import com.eversorhn.laun.ui.theme.HeadFontFamily
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

/**
 * App picker for a grid slot — tapping an empty tile opens this, as does "EDIT APPS" on an
 * occupied one. A centered dialog (not a bottom sheet) so it doesn't crowd the honeycomb, with a
 * search field since the full app list can run into the hundreds.
 *
 * Multi-select: picking one app makes a normal tile, picking more than one makes a folder tile —
 * taps toggle membership instead of closing immediately, confirmed with DONE.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppPickerSheet(
    apps: List<AppInfo>,
    initiallySelected: Set<String>,
    immersiveEnabled: Boolean,
    onConfirm: (List<String>) -> Unit,
    /** name == null (or blank) clears the override, reverting to the app's real label. */
    onRenameApp: (packageName: String, name: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initiallySelected) }
    var renameTarget by remember { mutableStateOf<AppInfo?>(null) }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HideSystemBarsWhileShown(immersiveEnabled)

        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .heightIn(max = 520.dp)
                .background(LaunColors.bg2)
                .border(1.dp, LaunColors.border)
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (selected.size > 1) "FOLDER (${selected.size})" else "CHOOSE APPS",
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "DONE",
                    color = LaunColors.fg,
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier.clickable { onConfirm(selected.toList()) }
                )
            }

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
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(LaunColors.fg),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }

            if (filtered.isEmpty()) {
                Text(
                    text = if (apps.isEmpty()) "No more apps available." else "No results.",
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        val isSelected = app.packageName in selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        selected = if (isSelected) selected - app.packageName else selected + app.packageName
                                    },
                                    onLongClick = { renameTarget = app }
                                )
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = highlightedLabel(app.label, query),
                                color = if (isSelected) LaunColors.fg else LaunColors.dim,
                                fontFamily = HeadFontFamily,
                                fontSize = 13.sp
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .border(1.dp, LaunColors.fg)
                                        .background(LaunColors.fg)
                                ) {
                                    Text(
                                        text = "✓",
                                        color = LaunColors.bg,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { app ->
        var name by remember(app.packageName) { mutableStateOf(app.label) }
        val renameFocusRequester = remember { FocusRequester() }
        val renameKeyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(app.packageName) {
            renameFocusRequester.requestFocus()
            renameKeyboard?.show()
        }

        Dialog(onDismissRequest = { renameTarget = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            HideSystemBarsWhileShown(immersiveEnabled)

            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .background(LaunColors.bg2)
                    .border(1.dp, LaunColors.border)
                    .padding(20.dp)
            ) {
                Text(
                    text = "RENAME TILE",
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .border(1.dp, LaunColors.border)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = name,
                        onValueChange = { if (it.length <= MAX_CUSTOM_APP_NAME_LENGTH) name = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = LaunColors.fg,
                            fontFamily = HeadFontFamily,
                            fontSize = 13.sp
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(LaunColors.fg),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(renameFocusRequester)
                    )
                }
                Text(
                    text = "${name.length}/$MAX_CUSTOM_APP_NAME_LENGTH",
                    color = LaunColors.dim,
                    fontFamily = MonoFontFamily,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RESET",
                        color = LaunColors.dim,
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clickable {
                            onRenameApp(app.packageName, null)
                            renameTarget = null
                        }
                    )
                    Text(
                        text = "SAVE",
                        color = LaunColors.fg,
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clickable {
                            onRenameApp(app.packageName, name)
                            renameTarget = null
                        }
                    )
                }
            }
        }
    }
}

/** Highlights the first case-insensitive occurrence of [query] inside [label] in the search-match
 *  red — shared with [AppLaunchSearchSheet]'s result list. */
internal fun highlightedLabel(label: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(label)
    val idx = label.indexOf(query, ignoreCase = true)
    if (idx < 0) return AnnotatedString(label)
    return buildAnnotatedString {
        append(label.substring(0, idx))
        withStyle(SpanStyle(color = LaunColors.searchMatch)) {
            append(label.substring(idx, idx + query.length))
        }
        append(label.substring(idx + query.length))
    }
}
