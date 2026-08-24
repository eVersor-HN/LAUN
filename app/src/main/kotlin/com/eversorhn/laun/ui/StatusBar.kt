package com.eversorhn.laun.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eversorhn.laun.ui.theme.LaunColors
import com.eversorhn.laun.ui.theme.MonoFontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun readBatteryPercent(context: Context): Int {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level >= 0 && scale > 0) (level * 100 / scale) else 0
}

private fun readNetworkConnected(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** Terminal-style readout — direct port of demo.html's "08" status bar variant, no box/border. */
@Composable
fun StatusBar(
    isOpen: Boolean,
    appCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var clock by remember { mutableStateOf("00:00:00") }
    var battery by remember { mutableIntStateOf(0) }
    var connected by remember { mutableStateOf(false) }
    var cursorOn by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        while (true) {
            clock = fmt.format(Date())
            battery = readBatteryPercent(context)
            connected = readNetworkConnected(context)
            delay(1000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorOn = !cursorOn
        }
    }

    // A true-centered clock (not just "centered in leftover space") needs the trailing group's
    // width reserved on the left too — an invisible mirror copy does that without a custom layout.
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatusEndGroup(isOpen, battery, connected, appCount, cursorOn, modifier = Modifier.alpha(0f))
        MonoText(clock, LaunColors.fg)
        StatusEndGroup(isOpen, battery, connected, appCount, cursorOn)
    }
}

@Composable
private fun StatusEndGroup(
    isOpen: Boolean,
    battery: Int,
    connected: Boolean,
    appCount: Int,
    cursorOn: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MonoText(">", LaunColors.dim)
        MonoText(if (isOpen) "ACTIVE" else "STANDBY", LaunColors.fg)
        BatteryGlyph(percent = battery)
        SignalGlyph(active = connected)
        MonoText("$appCount", LaunColors.fg)
        Box(
            modifier = Modifier
                .width(7.dp)
                .height(12.dp)
                .background(if (cursorOn) LaunColors.fg else Color.Transparent)
        )
    }
}

@Composable
private fun MonoText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text = text, color = color, fontFamily = MonoFontFamily, fontSize = 11.sp, modifier = modifier)
}

@Composable
private fun SignalGlyph(active: Boolean) {
    val barColor = if (active) LaunColors.fg else LaunColors.dim2
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        val heights = listOf(3.5f, 5.8f, 8f, 10f)
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(h.dp)
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun BatteryGlyph(percent: Int) {
    Box(
        modifier = Modifier
            .width(19.dp)
            .height(10.dp)
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(19.dp, 10.dp)) {
            val strokeWidth = 1.dp.toPx()
            drawRect(
                color = LaunColors.dim,
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                size = Size(size.width - strokeWidth - 3.dp.toPx(), size.height - strokeWidth),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )
            val innerW = size.width - strokeWidth - 3.dp.toPx()
            drawRect(
                color = LaunColors.fg,
                topLeft = Offset(strokeWidth, strokeWidth),
                size = Size((innerW - strokeWidth * 2) * (percent / 100f), size.height - strokeWidth * 3)
            )
            // terminal nub
            drawRect(
                color = LaunColors.dim,
                topLeft = Offset(size.width - 2.dp.toPx(), size.height * 0.3f),
                size = Size(2.dp.toPx(), size.height * 0.4f)
            )
        }
    }
}
