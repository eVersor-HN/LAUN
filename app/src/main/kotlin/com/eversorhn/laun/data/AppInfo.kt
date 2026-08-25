package com.eversorhn.laun.data

import androidx.compose.ui.graphics.ImageBitmap

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap,
    /** True when [icon] came from a chosen icon-pack drawable rather than the app's real icon —
     *  a tile for this app shows that icon regardless of the global "app icons instead of name"
     *  setting, since picking one specific icon is the whole point of overriding it. */
    val iconOverridden: Boolean = false
)
