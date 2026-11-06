package com.smartcare.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val Green = Color(0xFF2E9E5B)
private val Amber = Color(0xFFF2A900)
private val Red = Color(0xFFD9453B)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartCareApp() }
        HomeCareWidgetProvider.updateAll(this)
    }
}

@Composable
fun SmartCareApp() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("smartcare_settings", Context.MODE_PRIVATE) }
    var themeName by remember { mutableStateOf(prefs.getString("theme", "classic") ?: "classic") }
    val scheme = when (themeName) {
        "petrol" -> lightColorScheme(
            primary = Color(0xFF0F6470),
            secondary = Color(0xFF48A9A6),
            background = Color(0xFFEAF3F3),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF183238),
            onSurfaceVariant = Color(0xFF60777B)
        )
        "black" -> darkColorScheme(
            primary = Color(0xFF69C8FF),
            secondary = Color(0xFF36CFC2),
            background = Color(0xFF090D12),
            surface = Color(0xFF141B23),
            onSurface = Color(0xFFEAF1F7),
            onSurfaceVariant = Color(0xFFA8B5C2)
        )
        else -> lightColorScheme(
            primary = Color(0xFF1E6FD9),
            secondary = Color(0xFF5BB8F5),
            background = Color(0xFFF4F9FF),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF17212B),
            onSurfaceVariant = Color(0xFF687582)
        )
    }
    MaterialTheme(colorScheme = scheme) {
        SideEffect {
            val activity = ctx as? Activity
            activity?.window?.let { window ->
                val dark = themeName == "black"
                window.statusBarColor = if (dark) android.graphics.Color.rgb(9, 13, 18) else
                    if (themeName == "petrol") android.graphics.Color.rgb(234, 243, 243) else android.graphics.Color.rgb(244, 249, 255)
                window.navigationBarColor = if (dark) android.graphics.Color.rgb(20, 27, 35) else android.graphics.Color.WHITE
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        var tab by remember { mutableStateOf(0) }
        var snap by remember { mutableStateOf(Metrics.read(ctx)) }
        LaunchedEffect(tab) {
            if (tab == 0 || tab == 3) {
                while (true) {
                    delay(4000)
                    snap = Metrics.read(ctx)
                }
            }
        }
        val tabs = listOf(
            "Home Care" to "🏠",
            "Diagnóstico" to "💚",
            "Limpeza" to "🧹",
            "Bateria" to "🔋",
            "Monitor" to "📊",
            "Arquivos" to "📁"
        )
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Text(item.second) },
                            label = { Text(item.first, fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> HomeCarePanel(
                        snap = snap,
                        themeName = themeName,
                        onThemeChange = { selected ->
                            themeName = selected
                            prefs.edit().putString("theme", selected).apply()
                        },
                        onRefresh = { snap = Metrics.read(ctx) }
                    ) { destination -> tab = destination }
                    1 -> Dashboard(snap) { snap = Metrics.read(ctx) }
                    2 -> CleanerPanel()
                    3 -> BatteryPanel(snap.battery) { snap = Metrics.read(ctx) }
                    4 -> RamPanel(snap.ram) { snap = Metrics.read(ctx) }
                    5 -> StoragePanel(snap.storage) { snap = Metrics.read(ctx) }
                    else -> Placeholder(tabs[tab].first)
                }
            }
        }
    }
}

@Composable
fun HomeCarePanel(
    snap: Snapshot,
    themeName: String,
    onThemeChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (Int) -> Unit
) {
    var themeMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("home_care_layout", Context.MODE_PRIVATE) }
    val ids = listOf("health", "battery", "ram", "storage")
    val order = remember {
        mutableStateListOf<String>().apply {
            val saved = prefs.getString("order", null)
            val restored = saved?.split(",")?.filter { it in ids } ?: emptyList()
            addAll(restored + ids.filterNot { it in restored })
        }
    }
    val hidden = remember {
        mutableStateListOf<String>().apply {
            addAll(prefs.getStringSet("hidden", emptySet<String>()) ?: emptySet())
        }
    }
    var editing by remember { mutableStateOf(false) }

    fun saveOrder() {
        prefs.edit().putString("order", order.joinToString(",")).apply()
    }
    fun saveHidden() {
        prefs.edit().putStringSet("hidden", hidden.toSet()).apply()
    }

    val scoreColor = if (snap.health.score >= 80) Green else if (snap.health.score >= 60) Amber else Red
    val batteryText = if (snap.battery.percent >= 0) "${snap.battery.percent}%" else "Indisponível"
    val batteryDetail = buildString {
        append(snap.battery.status)
        if (!snap.battery.tempC.isNaN()) append(" • %.1f °C".format(snap.battery.tempC))
    }
    val ramText = "${snap.ram.percent.toInt()}% em uso"
    val storageText = "${snap.storage.percent.toInt()}% ocupado"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("HOME CARE", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("Central de informações do aparelho", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { themeMenuExpanded = true }) { Text("Tema: " + when (themeName) { "petrol" -> "Petróleo"; "black" -> "Black"; else -> "Azul" }) }
                    DropdownMenu(
                        expanded = themeMenuExpanded,
                        onDismissRequest = { themeMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Azul clássico") },
                            onClick = { onThemeChange("classic"); themeMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Azul-petróleo") },
                            onClick = { onThemeChange("petrol"); themeMenuExpanded = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Black") },
                            onClick = { onThemeChange("black"); themeMenuExpanded = false }
                        )
                    }
                }
                Button(onClick = { editing = !editing }) {
                    Text(if (editing) "Concluir" else "Organizar")
                }
            }
        }
        if (editing) {
            Text(
                "Use as setas para mudar a posição e Ocultar para retirar um indicador do painel. A organização fica salva neste aparelho.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        } else {
            Text("Toque em um indicador para abrir essa área no Smart Care.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }

        order.filterNot { it in hidden }.forEach { id ->
            val title: String
            val value: String
            val detail: String
            val destination: Int
            val accent: Color
            when (id) {
                "health" -> {
                    title = "Saúde geral"
                    value = "${snap.health.score}/100"
                    detail = if (snap.health.reasons.isEmpty()) "Sem alertas no momento" else snap.health.reasons.first()
                    destination = 1
                    accent = scoreColor
                }
                "battery" -> {
                    title = "Bateria"
                    value = batteryText
                    detail = batteryDetail
                    destination = 3
                    accent = MaterialTheme.colorScheme.primary
                }
                "ram" -> {
                    title = "Memória RAM"
                    value = ramText
                    detail = "${fmt(snap.ram.available)} disponíveis de ${fmt(snap.ram.total)}"
                    destination = 4
                    accent = MaterialTheme.colorScheme.secondary
                }
                else -> {
                    title = "Armazenamento"
                    value = storageText
                    detail = "${fmt(snap.storage.free)} livres"
                    destination = 5
                    accent = if (snap.storage.percent >= 90f) Red else if (snap.storage.percent >= 70f) Amber else MaterialTheme.colorScheme.primary
                }
            }
            HomeCareItemCard(
                title = title,
                value = value,
                detail = detail,
                accent = accent,
                editing = editing,
                onOpen = { if (!editing) onOpen(destination) },
                onMoveUp = {
                    val index = order.indexOf(id)
                    if (index > 0) {
                        val item = order.removeAt(index)
                        order.add(index - 1, item)
                        saveOrder()
                    }
                },
                onMoveDown = {
                    val index = order.indexOf(id)
                    if (index >= 0 && index < order.lastIndex) {
                        val item = order.removeAt(index)
                        order.add(index + 1, item)
                        saveOrder()
                    }
                },
                onHide = {
                    if (id !in hidden) hidden.add(id)
                    saveHidden()
                }
            )
        }

        if (editing && hidden.isNotEmpty()) {
            CardBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Indicadores ocultos", fontWeight = FontWeight.SemiBold)
                    hidden.toList().forEach { id ->
                        val label = when (id) {
                            "health" -> "Saúde geral"
                            "battery" -> "Bateria"
                            "ram" -> "Memória RAM"
                            else -> "Armazenamento"
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontSize = 13.sp)
                            Button(onClick = {
                                hidden.remove(id)
                                saveHidden()
                            }) { Text("Mostrar") }
                        }
                    }
                }
            }
        }

        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("Atualizar indicadores")
        }
        Text(
            "SMART CARE 0.3  •  XFLUX",
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            "O Home Care faz leituras pontuais. Não mantém um serviço permanente em segundo plano.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
fun HomeCareItemCard(
    title: String,
    value: String,
    detail: String,
    accent: Color,
    editing: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().then(
            if (editing) Modifier else Modifier.clickable(onClick = onOpen)
        ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                if (!editing) Text("Abrir  ›", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            Text(value, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            if (editing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onMoveUp) { Text("↑") }
                    Button(onClick = onMoveDown) { Text("↓") }
                    Button(onClick = onHide) { Text("Ocultar") }
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
        Text(name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Esta área será construída nas próximas etapas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Dashboard(snap: Snapshot, onRefresh: () -> Unit) {
    val h = snap.health
    val scoreColor = if (h.score >= 80) Green else if (h.score >= 60) Amber else Red
    val storageColor = if (snap.storage.percent >= 90f) Red else if (snap.storage.percent >= 70f) Amber else MaterialTheme.colorScheme.primary
    val time = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(snap.timeMillis))

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text("SMART CARE", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Seu celular cuidado em um só lugar.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }

        CardBox {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Ring(h.score.toFloat(), "${h.score}", "de 100", scoreColor, 150.dp)
                Spacer(Modifier.height(8.dp))
                Text("Saúde geral do aparelho", fontWeight = FontWeight.SemiBold)
                Text("Última análise: $time", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.weight(1f)) {
                CardBox {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Ring(snap.storage.percent, "${snap.storage.percent.toInt()}%", "usado", storageColor, 100.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("Armazenamento", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${fmt(snap.storage.free)} livres", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Box(Modifier.weight(1f)) {
                CardBox {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Ring(snap.ram.percent, "${snap.ram.percent.toInt()}%", "em uso", MaterialTheme.colorScheme.secondary, 100.dp)
                        Spacer(Modifier.height(6.dp))
                        Text("Memória RAM", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text("${fmt(snap.ram.available)} livres", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("A nota começa em 100 e perde pontos por: armazenamento acima de 70%, pouca memória sinalizada pelo Android e bateria quente. Não é uma certificação oficial do Android.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                h.reasons.forEach { Text("• $it", fontSize = 13.sp) }
            }
        }

        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sobre a RAM", fontWeight = FontWeight.SemiBold)
                Text("O Android usa a RAM para manter apps prontos. Memória ocupada não significa problema, por isso o app não finge \"liberar\" RAM.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val context = LocalContext.current
    val tempKnown = !b.tempC.isNaN()
    val tempColor = when {
        !tempKnown -> MaterialTheme.colorScheme.onSurfaceVariant
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
            Text("MONITORAMENTO DA BATERIA", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("Acompanhamento automático enquanto esta tela estiver aberta.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        CardBox {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (b.percent >= 0) "${b.percent}%" else "—", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(b.status, fontWeight = FontWeight.SemiBold)
                Text(if (b.charging) "Carregador conectado" else "Carregador desconectado", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Temperatura da bateria", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(14.dp).background(tempColor, CircleShape)) {}
                    Text(if (tempKnown) "%.1f °C".format(b.tempC) else "Indisponível", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = tempColor)
                }
                Text(tempStatus, color = tempColor, fontSize = 13.sp)
                Text("Verde: normal  •  Amarelo: aquecida  •  Vermelho: temperatura alta", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
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
        Text("Os valores dependem dos sensores e dados disponibilizados pelo fabricante. A corrente pode variar durante a carga e o tempo restante é uma estimativa, não uma garantia.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tela de carregamento estilo Motorola", fontWeight = FontWeight.SemiBold)
                Text("Relógio grande, círculo animado da bateria e temperatura. Para ativar automaticamente durante o carregamento, abra a proteção de tela do Android e escolha Smart Care como proteção de tela; configure para iniciar enquanto o aparelho estiver carregando.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Button(
                    onClick = {
                        try {
                            context.startActivity(Intent(Settings.ACTION_DREAM_SETTINGS))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Configurar tela de carregamento") }
            }
        }
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
            Text("Atualizar agora")
        }
    }
}


@Composable
fun RamPanel(ram: RamInfo, onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("MONITORAMENTO DA RAM", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        CardBox {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Ring(ram.percent, "${ram.percent.toInt()}%", "em uso", MaterialTheme.colorScheme.secondary, 145.dp)
                Text(if (ram.lowMemory) "O Android sinalizou pouca memória disponível" else "Sem alerta de pouca memória do Android", color = if (ram.lowMemory) Amber else Green, fontSize = 12.sp)
            }
        }
        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Detalhes da memória", fontWeight = FontWeight.SemiBold)
                Line("Total", fmt(ram.total))
                Line("Em uso", fmt(ram.used))
                Line("Disponível", fmt(ram.available))
                Line("Alerta do sistema", if (ram.lowMemory) "Sim" else "Não")
            }
        }
        Text("O Android administra a memória automaticamente. RAM ocupada não significa defeito, e encerrar processos à força pode piorar o desempenho.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Atualizar agora") }
    }
}

@Composable
fun StoragePanel(storage: StorageInfo, onRefresh: () -> Unit) {
    val color = if (storage.percent >= 90f) Red else if (storage.percent >= 70f) Amber else MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("ARMAZENAMENTO", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        CardBox {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Ring(storage.percent, "${storage.percent.toInt()}%", "ocupado", color, 145.dp)
                Text(
                    when {
                        storage.percent >= 90f -> "Espaço quase cheio"
                        storage.percent >= 70f -> "Atenção ao espaço disponível"
                        else -> "Espaço disponível em nível confortável"
                    },
                    color = color,
                    fontSize = 12.sp
                )
            }
        }
        CardBox {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Detalhes do armazenamento", fontWeight = FontWeight.SemiBold)
                Line("Total", fmt(storage.total))
                Line("Ocupado", fmt(storage.used))
                Line("Livre", fmt(storage.free))
            }
        }
        Text("Esta tela apenas consulta o espaço disponível. Nenhum arquivo é apagado pelo Smart Care.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Atualizar agora") }
    }
}

@Composable
fun CardBox(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(small, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun fmt(bytes: Long): String = "%.1f GB".format(bytes / 1073741824.0)
