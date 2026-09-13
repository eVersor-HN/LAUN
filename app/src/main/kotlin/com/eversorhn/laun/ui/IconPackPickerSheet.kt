package com.eversorhn.laun.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eversorhn.laun.data.BUILTIN_ICONS
import com.eversorhn.laun.data.BUILTIN_ICON_PACK
import com.eversorhn.laun.data.BuiltinIcon
import com.eversorhn.laun.data.IconPackEntry
import com.eversorhn.laun.data.IconPackInfo
import com.eversorhn.laun.data.IconPackRepository
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

/**
 * Pick a replacement icon for one app's tile. Two sources, chosen in the same first step:
 *
 * - LAUN's own built-in set ([BUILTIN_ICONS]) — 300 monochrome glyphs in the app's own visual
 *   language, always available, drawn as vectors in the tile's own accent color.
 * - Any icon pack the user already has installed — same discovery convention every other launcher
 *   uses, so no pack-specific code is needed (see [IconPackRepository]).
 *
 * Either way the second step is "pick an icon", and either way the result is resettable from the
 * tile's own long-press menu, same as the per-tile size override.
 */
@Composable
fun IconPackPickerSheet(
    immersiveEnabled: Boolean,
    hasOverride: Boolean,
    /** The (pack, icon) currently chosen for this app, so it can be shown as selected. */
    currentOverride: Pair<String, String>?,
    onPick: (iconPackPackage: String, drawableName: String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { IconPackRepository(context) }

    val builtinPack = remember { IconPackInfo(BUILTIN_ICON_PACK, "LAUN ICONS") }
    var packs by remember { mutableStateOf<List<IconPackInfo>?>(null) }
    LaunchedEffect(Unit) { packs = repository.listInstalledPacks() }

    var selectedPack by remember { mutableStateOf<IconPackInfo?>(null) }
    var catalog by remember { mutableStateOf<List<IconPackEntry>?>(null) }
    LaunchedEffect(selectedPack) {
        val pack = selectedPack
        catalog = when {
            pack == null || pack.packageName == BUILTIN_ICON_PACK -> null
            else -> repository.loadCatalog(pack.packageName)
        }
    }

    var query by remember { mutableStateOf("") }
    val filteredCatalog = remember(catalog, query) {
        val list = catalog ?: return@remember null
        if (query.isBlank()) list
        else list.filter { it.drawableName.contains(query.replace(' ', '_'), ignoreCase = true) }
    }
    // Built-in icons match on their own name and on their category, so typing "game" finds the
    // whole Games section as well as anything named after it.
    val filteredBuiltin = remember(query) {
        if (query.isBlank()) BUILTIN_ICONS
        else BUILTIN_ICONS.filter {
            it.label.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        HideSystemBarsWhileShown(immersiveEnabled)

        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .heightIn(max = 560.dp)
                .background(LaunColors.bg2)
                .border(1.dp, LaunColors.border)
                .padding(vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedPack != null) {
                    Text(
                        text = "← BACK",
                        color = LaunColors.dim,
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clickable { selectedPack = null; query = "" }
                    )
                } else {
                    Text(
                        text = "CHOOSE ICON",
                        color = LaunColors.dim,
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
                    )
                }
                if (hasOverride) {
                    Text(
                        text = "RESET",
                        color = LaunColors.fg,
                        fontFamily = MonoFontFamily,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.clickable {
                            onReset()
                            onDismiss()
                        }
                    )
                }
            }

            val pack = selectedPack
            when {
                // ---- step 1: pick a source ----
                pack == null -> {
                    val list = packs
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                        item(key = BUILTIN_ICON_PACK) {
                            SourceRow(
                                label = builtinPack.label,
                                detail = "${BUILTIN_ICONS.size} ICONS",
                                onClick = { selectedPack = builtinPack }
                            )
                        }
                        when {
                            list == null -> item { LoadingRow() }
                            list.isEmpty() -> item {
                                Text(
                                    text = "No icon packs installed. Install one from the Play Store to use it here.",
                                    color = LaunColors.dim,
                                    fontFamily = MonoFontFamily,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                                )
                            }
                            else -> items(list, key = { it.packageName }) { p ->
                                SourceRow(label = p.label.uppercase(), detail = null, onClick = { selectedPack = p })
                            }
                        }
                    }
                }

                // ---- step 2a: LAUN's own set ----
                pack.packageName == BUILTIN_ICON_PACK -> {
                    SearchField(query = query, onQueryChange = { query = it })
                    if (filteredBuiltin.isEmpty()) {
                        NoResults()
                    } else {
                        // Grouped by category with a spanning header per group, so 300 icons stay
                        // navigable; a query flattens the groups it doesn't match out of the way
                        // rather than showing empty sections.
                        val grouped = remember(filteredBuiltin) { filteredBuiltin.groupBy { it.category } }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height(400.dp).padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            grouped.forEach { (category, icons) ->
                                item(key = "cat:$category", span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = category.uppercase(),
                                        color = LaunColors.dim,
                                        fontFamily = MonoFontFamily,
                                        fontSize = 9.sp,
                                        letterSpacing = 1.5.sp,
                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                                    )
                                }
                                gridItems(icons, key = { it.id }) { icon ->
                                    val selected = currentOverride?.first == BUILTIN_ICON_PACK &&
                                        currentOverride.second == icon.id
                                    BuiltinIconCell(icon = icon, selected = selected) {
                                        onPick(BUILTIN_ICON_PACK, icon.id)
                                        onDismiss()
                                    }
                                }
                            }
                        }
                    }
                }

                // ---- step 2b: an installed pack's own catalogue ----
                else -> {
                    SearchField(query = query, onQueryChange = { query = it })
                    val icons = filteredCatalog
                    when {
                        icons == null -> LoadingRow()
                        icons.isEmpty() -> NoResults()
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().height(400.dp).padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            // No explicit key: the same drawable name legitimately repeats across
                            // more than one category in real icon packs' own catalogues, which would
                            // collide as a LazyVerticalGrid key — position-based keys are fine here,
                            // the list is fully rebuilt on every query change anyway.
                            gridItems(icons) { entry ->
                                val bitmap by produceState(initialValue = null as androidx.compose.ui.graphics.ImageBitmap?, pack.packageName, entry.drawableName) {
                                    value = repository.loadIcon(pack.packageName, entry.drawableName)
                                }
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .border(1.dp, LaunColors.border)
                                        .clickable {
                                            onPick(pack.packageName, entry.drawableName)
                                            onDismiss()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    val bmp = bitmap
                                    if (bmp != null) {
                                        Image(bitmap = bmp, contentDescription = entry.drawableName, modifier = Modifier.size(30.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One built-in glyph in the picker grid, drawn as a vector rather than a rasterized bitmap. */
@Composable
private fun BuiltinIconCell(icon: BuiltinIcon, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .border(1.dp, if (selected) LaunColors.fg else LaunColors.border)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(7.dp)) {
            val side = minOf(size.width, size.height)
            drawBuiltinIcon(
                icon = icon,
                color = LaunColors.fg,
                sizePx = side,
                topLeft = Offset((size.width - side) / 2f, (size.height - side) / 2f)
            )
        }
    }
}

/** A selectable icon source in step 1 — LAUN's own set, or one installed pack. */
@Composable
private fun SourceRow(label: String, detail: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = LaunColors.fg,
            fontFamily = MonoFontFamily,
            fontSize = 12.sp,
            letterSpacing = 0.6.sp
        )
        if (detail != null) {
            Text(text = detail, color = LaunColors.dim, fontFamily = MonoFontFamily, fontSize = 9.sp, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
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
        @Suppress("UNUSED_VARIABLE") val keyboard = LocalSoftwareKeyboardController.current
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = LaunColors.fg,
                fontFamily = MonoFontFamily,
                fontSize = 12.sp,
                letterSpacing = 0.6.sp
            ),
            cursorBrush = SolidColor(LaunColors.fg),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun NoResults() {
    Text(
        text = "No results.",
        color = LaunColors.dim,
        fontFamily = MonoFontFamily,
        fontSize = 11.sp,
        modifier = Modifier.padding(20.dp)
    )
}

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = LaunColors.fg, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}
