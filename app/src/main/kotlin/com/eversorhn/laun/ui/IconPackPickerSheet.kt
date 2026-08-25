package com.eversorhn.laun.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eversorhn.laun.data.IconPackEntry
import com.eversorhn.laun.data.IconPackInfo
import com.eversorhn.laun.data.IconPackRepository
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily

/**
 * Browse an installed icon pack's own catalogue and pick a replacement icon for one app's tile —
 * same idea as Pie Launcher's icon-pack support: any pack already on the phone works here with no
 * pack-specific code, discovered the same way every other launcher finds them (see
 * [IconPackRepository]). Two steps: pick a pack, then pick an icon from its (often large)
 * catalogue — icons load lazily as their grid cell scrolls into view, not all at once.
 */
@Composable
fun IconPackPickerSheet(
    immersiveEnabled: Boolean,
    hasOverride: Boolean,
    onPick: (iconPackPackage: String, drawableName: String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { IconPackRepository(context) }

    var packs by remember { mutableStateOf<List<IconPackInfo>?>(null) }
    LaunchedEffect(Unit) { packs = repository.listInstalledPacks() }

    var selectedPack by remember { mutableStateOf<IconPackInfo?>(null) }
    var catalog by remember { mutableStateOf<List<IconPackEntry>?>(null) }
    LaunchedEffect(selectedPack) {
        val pack = selectedPack
        catalog = if (pack == null) null else repository.loadCatalog(pack.packageName)
    }

    var query by remember { mutableStateOf("") }
    val filteredCatalog = remember(catalog, query) {
        val list = catalog ?: return@remember null
        if (query.isBlank()) list
        else list.filter { it.drawableName.contains(query.replace(' ', '_'), ignoreCase = true) }
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
            if (pack == null) {
                // ---- step 1: pick a pack ----
                val list = packs
                when {
                    list == null -> LoadingRow()
                    list.isEmpty() -> Text(
                        text = "No icon packs installed. Install one from the Play Store, then reopen this.",
                        color = LaunColors.dim,
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(20.dp)
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                        items(list, key = { it.packageName }) { p ->
                            Text(
                                text = p.label.uppercase(),
                                color = LaunColors.fg,
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp,
                                letterSpacing = 0.6.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPack = p }
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                            )
                        }
                    }
                }
            } else {
                // ---- step 2: pick an icon from the pack's catalogue ----
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
                    val keyboard = LocalSoftwareKeyboardController.current
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val icons = filteredCatalog
                when {
                    icons == null -> LoadingRow()
                    icons.isEmpty() -> Text(
                        text = "No results.",
                        color = LaunColors.dim,
                        fontFamily = MonoFontFamily,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(20.dp)
                    )
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

@Composable
private fun LoadingRow() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = LaunColors.fg, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    }
}
