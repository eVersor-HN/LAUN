package com.eversorhn.laun.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
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

private fun readCharging(context: Context): Boolean {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}

private fun readNetworkConnected(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun readWifiConnected(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

private fun readBluetoothEnabled(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 31 &&
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
    ) return false
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return try {
        manager?.adapter?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }
}

/** Terminal-style readout — direct port of demo.html's "08" status bar variant, no box/border. */
@Composable
fun StatusBar(
    isOpen: Boolean,
    showStatus: Boolean,
    showClock: Boolean,
    showClockMillis: Boolean,
    showBattery: Boolean,
    showBatteryPercent: Boolean,
    showSignal: Boolean,
    showWifi: Boolean,
    showBluetooth: Boolean,
    showCursor: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var clock by remember { mutableStateOf("00:00:00") }
    var battery by remember { mutableIntStateOf(0) }
    var charging by remember { mutableStateOf(false) }
    var connected by remember { mutableStateOf(false) }
    var wifiConnected by remember { mutableStateOf(false) }
    var bluetoothOn by remember { mutableStateOf(false) }
    var cursorOn by remember { mutableStateOf(true) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) bluetoothOn = readBluetoothEnabled(context) }
    LaunchedEffect(showBluetooth) {
        if (showBluetooth && Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothPermissionLauncher.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else if (showBluetooth) {
            bluetoothOn = readBluetoothEnabled(context)
        }
    }

    // Both loops only run while LAUN is actually on screen — without this they'd keep ticking
    // forever in the background, since stopping the Activity doesn't destroy the Compose
    // coroutine scope that owns them.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, showClockMillis) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val pattern = if (showClockMillis) "HH:mm:ss.SSS" else "HH:mm:ss"
            val fmt = SimpleDateFormat(pattern, Locale.getDefault())
            // Millisecond display only ticks fast while actually shown — the plain-seconds
            // clock stays a cheap once-a-second update like before.
            val tickMs = if (showClockMillis) 33L else 1000L
            while (true) {
                clock = fmt.format(Date())
                delay(tickMs)
            }
        }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(500)
                cursorOn = !cursorOn
            }
        }
    }

    // Battery/signal only change occasionally — a callback per change (registered while visible,
    // torn down while backgrounded) reads the real state exactly as often as it actually changes,
    // instead of re-querying it every second regardless of whether anything moved.
    DisposableEffect(lifecycleOwner) {
        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) battery = level * 100 / scale
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                wifiConnected = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }
            override fun onLost(network: Network) {
                connected = readNetworkConnected(context)
                wifiConnected = readWifiConnected(context)
            }
        }
        val bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                bluetoothOn = readBluetoothEnabled(context)
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    battery = readBatteryPercent(context)
                    charging = readCharging(context)
                    connected = readNetworkConnected(context)
                    wifiConnected = readWifiConnected(context)
                    bluetoothOn = readBluetoothEnabled(context)
                    ContextCompat.registerReceiver(
                        context, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                    connectivityManager.registerDefaultNetworkCallback(networkCallback)
                    ContextCompat.registerReceiver(
                        context, bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                        ContextCompat.RECEIVER_NOT_EXPORTED
                    )
                }
                Lifecycle.Event.ON_STOP -> {
                    context.unregisterReceiver(batteryReceiver)
                    connectivityManager.unregisterNetworkCallback(networkCallback)
                    context.unregisterReceiver(bluetoothReceiver)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // A Box with per-child alignment anchors — clock pinned left, status truly centered on the
    // full width, battery/signal pinned right — regardless of how wide any of them are.
    Box(
        modifier = modifier.fillMaxWidth().height(20.dp)
    ) {
        if (showClock) {
            MonoText(clock, LaunColors.fg, modifier = Modifier.align(Alignment.CenterStart))
        }
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showStatus) {
                MonoText(">", LaunColors.dim)
                MonoText(if (isOpen) "ACTIVE" else "STANDBY", LaunColors.fg)
            }
            if (showCursor) {
                Box(
                    modifier = Modifier
                        .width(7.dp)
                        .height(12.dp)
                        .background(if (cursorOn) LaunColors.fg else Color.Transparent)
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBattery) {
                if (showBatteryPercent) MonoText("$battery%", LaunColors.dim)
                BatteryGlyph(percent = battery, charging = charging)
            }
            if (showWifi) WifiGlyph(active = wifiConnected)
            if (showBluetooth) BluetoothGlyph(active = bluetoothOn)
            if (showSignal) SignalGlyph(active = connected)
        }
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
private fun BatteryGlyph(percent: Int, charging: Boolean) {
    // Charging pulses the fill brightness instead of sitting static — a quiet "breathing" cue
    // that reads as "actively charging" without needing a battery-icon animation library.
    val fillAlpha = if (charging) {
        val transition = rememberInfiniteTransition(label = "batteryCharging")
        val pulse by transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
            label = "batteryPulse"
        )
        pulse
    } else 1f

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
                color = LaunColors.fg.copy(alpha = fillAlpha),
                topLeft = Offset(strokeWidth, strokeWidth),
                size = Size((innerW - strokeWidth * 2) * (percent / 100f), size.height - strokeWidth * 3)
            )
            // terminal nub
            drawRect(
                color = LaunColors.dim,
                topLeft = Offset(size.width - 2.dp.toPx(), size.height * 0.3f),
                size = Size(2.dp.toPx(), size.height * 0.4f)
            )
            if (charging) {
                // Small bolt notch cut from the fill, drawn in the background color so it reads
                // as a cutout rather than an overlay — visible against fill or empty alike.
                val cx = innerW / 2f
                val cy = size.height / 2f
                val bolt = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx + 1.5.dp.toPx(), cy - 4.dp.toPx())
                    lineTo(cx - 1.5.dp.toPx(), cy + 0.5.dp.toPx())
                    lineTo(cx + 0.3.dp.toPx(), cy + 0.5.dp.toPx())
                    lineTo(cx - 1.dp.toPx(), cy + 4.dp.toPx())
                    lineTo(cx + 2.dp.toPx(), cy - 0.8.dp.toPx())
                    lineTo(cx + 0.4.dp.toPx(), cy - 0.8.dp.toPx())
                    close()
                }
                drawPath(bolt, color = LaunColors.bg)
            }
        }
    }
}

@Composable
private fun WifiGlyph(active: Boolean) {
    val color = if (active) LaunColors.fg else LaunColors.dim2
    androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp, 10.dp)) {
        val strokeWidth = 1.3.dp.toPx()
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val cx = size.width / 2f
        val baseY = size.height * 0.85f
        // Three concentric arcs + a dot — the universal wifi glyph, drawn from arc rects.
        listOf(0.95f, 0.62f, 0.3f).forEachIndexed { i, scale ->
            val r = size.width * 0.5f * scale
            drawArc(
                color = color,
                startAngle = 210f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(cx - r, baseY - r * 1.6f),
                size = Size(r * 2f, r * 2f),
                style = stroke
            )
        }
        drawCircle(color = color, radius = 1.3.dp.toPx(), center = Offset(cx, baseY))
    }
}

@Composable
private fun BluetoothGlyph(active: Boolean) {
    val color = if (active) LaunColors.fg else LaunColors.dim2
    androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp, 12.dp)) {
        val strokeWidth = 1.2.dp.toPx()
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val cx = size.width * 0.5f
        val top = strokeWidth
        val bottom = size.height - strokeWidth
        val mid = size.height / 2f
        val w = size.width * 0.5f
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, top)
            lineTo(cx + w, mid - (mid - top) / 2f)
            lineTo(cx - w, (mid + bottom) / 2f)
            lineTo(cx, bottom)
            moveTo(cx, top)
            lineTo(cx - w, mid - (mid - top) / 2f)
            lineTo(cx + w, (mid + bottom) / 2f)
            lineTo(cx, bottom)
        }
        drawPath(path, color = color, style = stroke)
        drawLine(color = color, start = Offset(cx, top), end = Offset(cx, bottom), strokeWidth = strokeWidth)
    }
}
