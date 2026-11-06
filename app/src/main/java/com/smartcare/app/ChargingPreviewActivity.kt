package com.smartcare.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Prévia interativa da tela de carregamento. A tela segura de bloqueio continua sendo a do Android. */
class ChargingPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent { ChargingPreviewScreen(onSettings = {
            try {
                startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
            }
        }) }
    }
}

@androidx.compose.runtime.Composable
private fun ChargingPreviewScreen(onSettings: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val theme = remember {
        context.getSharedPreferences("smartcare_settings", Context.MODE_PRIVATE)
            .getString("theme", "classic") ?: "classic"
    }
    val background = when (theme) {
        "petrol" -> Color(0xFF071F25)
        "black" -> Color(0xFF080D14)
        else -> Color(0xFF07182B)
    }
    val accent = when (theme) {
        "petrol" -> Color(0xFF48A9A6)
        "black" -> Color(0xFF69C8FF)
        else -> Color(0xFF5BB8F5)
    }
    var level by remember { mutableIntStateOf(0) }
    var temp by remember { mutableStateOf<Float?>(null) }
    var charging by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val i = intent ?: return
                val l = i.getIntExtra("level", -1)
                val scale = i.getIntExtra("scale", 100).coerceAtLeast(1)
                level = if (l >= 0) (l * 100 / scale).coerceIn(0, 100) else 0
                val t = i.getIntExtra("temperature", Int.MIN_VALUE)
                temp = if (t == Int.MIN_VALUE) null else t / 10f
                val status = i.getIntExtra("status", -1)
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        onDispose { try { context.unregisterReceiver(receiver) } catch (_: Exception) {} }
    }

    LaunchedEffect(Unit) {
        while (true) {
            val now = Date()
            clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            date = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(now)
            delay(1000)
        }
    }

    Surface(color = background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(clock, color = Color.White, fontSize = 58.sp, fontWeight = FontWeight.Light)
                Text(date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() },
                    color = Color(0xFFB7C6D5), fontSize = 15.sp)
                Spacer(Modifier.height(34.dp))
                Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 12.dp.toPx()
                        val inset = stroke / 2
                        drawArc(Color(0xFF344454), 0f, 360f, false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round))
                        val ringColor = when {
                            temp != null && temp!! >= 40f -> Color(0xFFFF665E)
                            temp != null && temp!! >= 35f -> Color(0xFFFFC04D)
                            else -> accent
                        }
                        drawArc(ringColor, -90f, 360f * level / 100f, false,
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$level%", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (charging) "Carregando" else "Bateria", color = Color(0xFFB7C6D5), fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(if (temp == null) "Temperatura indisponível" else "Temperatura da bateria: %.1f °C".format(temp),
                    color = when { temp == null -> Color(0xFFB7C6D5); temp!! >= 40f -> Color(0xFFFF665E); temp!! >= 35f -> Color(0xFFFFC04D); else -> accent },
                    fontSize = 15.sp)
                Spacer(Modifier.height(26.dp))
                Text("Olá! Seja bem-vindo.", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text("SMART CARE  •  XFLUX", color = Color(0xFF8196AA), fontSize = 11.sp)
                Spacer(Modifier.height(30.dp))
                Button(onClick = onSettings) { Text("Ativar ao carregar") }
                Text("Prévia da tela. O bloqueio seguro continua protegido pelo Android.",
                    color = Color(0xFF8196AA), fontSize = 10.sp)
            }
        }
    }
}
