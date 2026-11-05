package com.smartcare.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val BlueLight = Color(0xFF5BB8F5)
private val BlueMid = Color(0xFF1E6FD9)
private val Green = Color(0xFF2E9E5B)
private val Amber = Color(0xFFF2A900)
private val Red = Color(0xFFD9453B)
private val Bg = Color(0xFFF4F9FF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartCareApp() }
    }
}

@Composable
fun SmartCareApp() {
    val scheme = lightColorScheme(
        primary = BlueMid,
        secondary = BlueLight,
        background = Bg,
        surface = Color.White
    )
    MaterialTheme(colorScheme = scheme) {
        val ctx = LocalContext.current
        var tab by remember { mutableStateOf(0) }
        var snap by remember { mutableStateOf(Metrics.read(ctx)) }
        LaunchedEffect(tab) {
            if (tab == 2) {
                while (true) {
                    delay(4000)
                    snap = Metrics.read(ctx)
                }
            }
        }
        val tabs = listOf(
            "Início" to "🏠",
            "Limpeza" to "🧹",
            "Bateria" to "🔋",
            "Monitor" to "📊",
            "Arquivos" to "📁"
        )
        Scaffold(
            containerColor = Bg,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(item.second) },
                            label = { Text(item.first, fontSize = 11.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> Dashboard(snap) { snap = Metrics.read(ctx) }
                    2 -> BatteryPanel(snap.battery) { snap = Metrics.read(ctx) }
                    else -> Placeholder(tabs[tab].first)
                }
            }
        }
    }
}

@Composable
fun Placeholder(name: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BlueMid)
        Spacer(Modifier.height(8.dp))
        Text("Esta área será construída nas próximas etapas.", color = Color.Gray)
    }
}

@Composable
fun Dashboard(snap: Snapshot, onRefresh: () -> Unit) {
    val h = snap.health
    val scoreColor = if (h.score >= 80) Green else if (h.score >= 60) Amber else Red
    val storageColor = if (snap.storage.percent >= 90f) Red else if (snap.storage.percent >= 70f) Amber else BlueMid
    val time = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(snap.timeMillis))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("SMART CARE", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BlueMid)
            Text("Seu celular cuidado em um só lugar.", color = Color.Gray, fontSize = 13.sp)
        }

        CardBox {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Ring(h.score.toFloat(), "${h.score}", "de 100", scoreColor, 150.dp)
                Spacer(Modifier.height(8.dp))
                Text("Saúde geral do aparelho", fontWeight = FontWeight.SemiBold)
                Text("Última análise: $time", fontSize = 12.sp, color = Color.Gray)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1f)) {
                CardBox {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Ring(snap.storage.percent, "${snap.storage.percent.toInt()}%", "usado", storageColor, 100.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("Armazenamento", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${fmt(snap.storage.free)} livres", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                CardBox {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Ring(snap.ram.percent, "${snap.ram.percent.toInt()}%", "em uso", BlueLight, 100.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("Memória RAM", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${fmt(snap.ram.available)} livres", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }

        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Bateria", fontWeight = FontWeight.SemiBold)
                val b = snap.battery
                Line("Nível", if (b.percent >= 0) "${b.percent}%" else "Indisponível")
                Line("Estado", b.status)
                Line("Temperatura", if (b.tempC.isNaN()) "Indisponível" else "%.1f °C".format(b.tempC))
                Line("Voltagem", if (b.voltageV > 0f) "%.2f V".format(b.voltageV) else "Indisponível")
                Line("Tecnologia", b.technology)
            }
        }

        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Por que essa nota?", fontWeight = FontWeight.SemiBold)
                Text("A nota começa em 100 e perde pontos por: armazenamento acima de 70%, pouca memória sinalizada pelo Android e bateria quente. Não é uma certificação oficial do Android.", fontSize = 12.sp, color = Color.Gray)
                h.reasons.forEach { Text("• $it", fontSize = 13.sp) }
            }
        }

        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sobre a RAM", fontWeight = FontWeight.SemiBold)
                Text("O Android usa a RAM para manter apps prontos. Memória ocupada não significa problema, por isso o app não finge \"liberar\" RAM.", fontSize = 12.sp, color = Color.Gray)
                Line("Total", fmt(snap.ram.total))
                Line("Aparelho", "${Build.MANUFACTURER} ${Build.MODEL}")
                Line("Android", Build.VERSION.RELEASE)
            }
        }

        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("Atualizar agora")
        }
    }
}


@Composable
fun BatteryPanel(b: BatteryInfo, onRefresh: () -> Unit) {
    val tempKnown = !b.tempC.isNaN()
    val tempColor = when {
        !tempKnown -> Color.Gray
        b.tempC >= 40f -> Red
        b.tempC >= 35f -> Amber
        else -> Green
    }
    val tempStatus = when {
        !tempKnown -> "Temperatura indisponível neste aparelho"
        b.tempC >= 40f -> "Temperatura alta. Evite jogos e uso intenso enquanto esfria."
        b.tempC >= 35f -> "Atenção: a bateria está aquecida."
        else -> "Temperatura normal."
    }
    val currentText = b.currentMicroA?.let {
        val ma = kotlin.math.abs(it) / 1000.0
        if (ma >= 1000.0) "%.2f A".format(ma / 1000.0) else "%.0f mA".format(ma)
    } ?: "Indisponível neste aparelho"
    val etaText = b.minutesToFull?.let {
        val hours = it / 60
        val minutes = it % 60
        if (hours > 0) "$hours h $minutes min" else "$minutes min"
    } ?: if (b.charging && b.percent in 1..99) "Estimativa indisponível neste aparelho" else "Disponível durante o carregamento"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("MONITORAMENTO DA BATERIA", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = BlueMid)
            Text("Acompanhamento automático enquanto esta tela estiver aberta.", color = Color.Gray, fontSize = 13.sp)
        }
        CardBox {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (b.percent >= 0) "${b.percent}%" else "—", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = BlueMid)
                Text(b.status, fontWeight = FontWeight.SemiBold)
                Text(if (b.charging) "Carregador conectado" else "Carregador desconectado", color = Color.Gray, fontSize = 12.sp)
            }
        }
        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Temperatura da bateria", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(14.dp).then(Modifier), ) {}
                    Text(if (tempKnown) "%.1f °C".format(b.tempC) else "Indisponível", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = tempColor)
                }
                Text(tempStatus, color = tempColor, fontSize = 13.sp)
                Text("Verde: normal  •  Amarelo: aquecida  •  Vermelho: temperatura alta", color = Color.Gray, fontSize = 11.sp)
            }
        }
        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Informações de carregamento", fontWeight = FontWeight.SemiBold)
                Line("Corrente elétrica", currentText)
                Line("Tempo até carga completa", etaText)
                Line("Voltagem", if (b.voltageV > 0f) "%.2f V".format(b.voltageV) else "Indisponível neste aparelho")
                Line("Tecnologia", b.technology)
            }
        }
        Text("Os valores dependem dos sensores e dados disponibilizados pelo fabricante. A corrente pode variar durante a carga e o tempo restante é uma estimativa, não uma garantia.", color = Color.Gray, fontSize = 11.sp)
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("Atualizar agora")
        }
    }
}

@Composable
fun CardBox(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp)
    }
}

@Composable
fun Ring(percent: Float, big: String, small: String, color: Color, ringSize: Dp) {
    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(
                color = color.copy(alpha = 0.15f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f, sweepAngle = 360f * percent.coerceIn(0f, 100f) / 100f,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(big, fontSize = if (ringSize > 120.dp) 32.sp else 20.sp, fontWeight = FontWeight.Bold)
            Text(small, fontSize = 11.sp, color = Color.Gray)
        }
    }
}

fun fmt(bytes: Long): String = "%.1f GB".format(bytes / 1073741824.0)
