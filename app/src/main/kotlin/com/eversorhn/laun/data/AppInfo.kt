package com.eversorhn.laun.data

import androidx.compose.ui.graphics.ImageBitmap

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap
)
