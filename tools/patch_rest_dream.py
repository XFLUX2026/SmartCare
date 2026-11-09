from pathlib import Path
import re

base = Path("app/src/main")
java = base / "java/com/smartcare/app"
java.mkdir(parents=True, exist_ok=True)

(java / "Weather.kt").write_text(r'''package com.smartcare.app
import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Weather {
    private const val PREFS = "smartcare_city_weather"
    fun city(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("city", "") ?: ""
    fun summary(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("summary", "") ?: ""

    private fun request(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 12000
        return try { JSONObject(c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }) }
        finally { c.disconnect() }
    }

    fun configureCity(context: Context, requested: String): String {
        val app = context.applicationContext
        val name = requested.trim()
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (name.isEmpty()) {
            p.edit().clear().apply()
            return "Cidade removida. O clima ficará oculto."
        }
        return try {
            val q = URLEncoder.encode(name, "UTF-8")
            val geo = request("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=pt&format=json")
            val results = geo.optJSONArray("results")
            if (results == null || results.length() == 0) {
                p.edit().remove("summary").apply()
                return "Não encontrei essa cidade. Confira o nome e tente novamente."
            }
            val place = results.getJSONObject(0)
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val resolved = place.optString("name", name) + place.optString("country", "").let { if (it.isBlank()) "" else ", $it" }
            p.edit().putString("city", name).putString("resolved_city", resolved)
                .putString("latitude", lat.toString()).putString("longitude", lon.toString()).apply()
            val report = forecast(lat, lon)
            p.edit().putString("summary", report).apply()
            "Cidade configurada: $resolved. Clima atualizado."
        } catch (_: Exception) {
            p.edit().remove("summary").apply()
            "Não foi possível consultar o clima. Verifique a internet e tente novamente."
        }
    }

    fun refresh(context: Context, onComplete: (() -> Unit)? = null) {
        val app = context.applicationContext
        Thread {
            val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lat = p.getString("latitude", null)?.toDoubleOrNull()
            val lon = p.getString("longitude", null)?.toDoubleOrNull()
            val value = if (city(app).isBlank() || lat == null || lon == null) "" else try { forecast(lat, lon) } catch (_: Exception) { "" }
            p.edit().putString("summary", value).apply()
            android.os.Handler(android.os.Looper.getMainLooper()).post { onComplete?.invoke() }
        }.start()
    }

    private fun forecast(lat: Double, lon: Double): String {
        val j = request("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto")
        val c = j.getJSONObject("current")
        val d = j.getJSONObject("daily")
        val temp = c.getDouble("temperature_2m").toInt()
        val code = c.getInt("weather_code")
        val max = d.getJSONArray("temperature_2m_max").getDouble(0).toInt()
        val min = d.getJSONArray("temperature_2m_min").getDouble(0).toInt()
        val condition = when (code) {
            0 -> "Céu limpo"; 1, 2 -> "Parcialmente nublado"; 3 -> "Nublado"
            45, 48 -> "Neblina"; 51, 53, 55, 56, 57 -> "Garoa"
            61, 63, 65, 66, 67, 80, 81, 82 -> "Chuva"
            71, 73, 75, 77, 85, 86 -> "Neve"; 95, 96, 99 -> "Trovoadas"
            else -> "Condição variável"
        }
        return "$condition • $temp°C • Máx. $max° / Mín. $min°"
    }
}
''', encoding="utf-8")

(java / "RestDream.kt").write_text(r'''package com.smartcare.app

import android.service.dreams.DreamService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

class RestDream : DreamService() {
    private var dreamView: RestDreamView? = null
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        dreamView = RestDreamView(this)
        setContentView(dreamView)
    }
    override fun onDreamingStarted() {
        super.onDreamingStarted()
        Weather.refresh(this) { dreamView?.invalidate() }
    }
    override fun onDetachedFromWindow() {
        dreamView = null
        super.onDetachedFromWindow()
    }
}

private class RestDreamView(context: Context) : View(context) {
    private var batteryPercent = 0
    private var charging = false
    private var receiverRegistered = false
    private val filter = IntentFilter().apply {
        addAction(Intent.ACTION_BATTERY_CHANGED)
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val event = intent ?: return
            when (event.action) {
                Intent.ACTION_POWER_CONNECTED -> charging = true
                Intent.ACTION_POWER_DISCONNECTED -> charging = false
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = event.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = event.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    if (level >= 0) batteryPercent = (level * 100f / scale).roundToInt().coerceIn(0, 100)
                    charging = event.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                }
            }
            invalidate()
        }
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val time = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
    }
    private val date = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFD5E4F4.toInt(); textAlign = Paint.Align.CENTER
    }
    private val info = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE7F1FC.toInt(); textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val sticky = if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
        sticky?.let { receiver.onReceive(context, it) }
    }
    override fun onDetachedFromWindow() {
        if (receiverRegistered) {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
            receiverRegistered = false
        }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(3, 8, 18))
        val step = (SystemClock.elapsedRealtime() / 30_000L) % 9L
        val dx = ((step % 3L) - 1L).toFloat() * dp(5f)
        val dy = ((step / 3L) - 1L).toFloat() * dp(5f)
        val cx = width / 2f + dx
        val cy = height * 0.43f + dy
        val radius = min(width * 0.39f, height * 0.255f).coerceAtLeast(dp(90f))
        val bounds = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val segments = 64
        val filled = (batteryPercent * segments / 100f).roundToInt()
        val neon = when {
            !charging -> Color.rgb(57, 255, 136)
            batteryPercent < 20 -> Color.rgb(255, 55, 75)
            batteryPercent <= 50 -> Color.rgb(255, 213, 64)
            else -> Color.rgb(57, 255, 136)
        }
        ring.strokeWidth = dp(7f)
        ring.color = 0x263E5974
        ring.clearShadowLayer()
        for (i in 0 until segments) canvas.drawArc(bounds, -90f + i * 360f / segments, 3.7f, false, ring)
        ring.color = neon
        ring.setShadowLayer(dp(11f), 0f, 0f, neon)
        for (i in 0 until filled) canvas.drawArc(bounds, -90f + i * 360f / segments, 3.7f, false, ring)
        ring.clearShadowLayer()

        time.textSize = dp(66f)
        canvas.drawText(SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date()), cx, cy + dp(10f), time)
        date.textSize = dp(17f)
        val dateText = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("pt", "BR")).format(Date())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("pt", "BR")) else it.toString() }
        canvas.drawText(dateText, cx, cy + dp(43f), date)
        info.textSize = dp(15f)
        canvas.drawText("$batteryPercent% de bateria", cx, cy + radius + dp(34f), info)
        val weather = Weather.summary(context)
        if (weather.isNotBlank()) {
            info.textSize = dp(14f)
            canvas.drawText(weather, cx, cy + radius + dp(62f), info)
        }
        postInvalidateDelayed(1000L)
    }
    private fun dp(value: Float) = value * resources.displayMetrics.density
}
''', encoding="utf-8")

(java / "ClockWeatherManager.kt").write_text(r'''package com.smartcare.app
import android.content.Context
object ClockWeatherManager {
    fun summary(context: Context): String = Weather.summary(context)
    fun refresh(context: Context) = Weather.refresh(context)
}
''', encoding="utf-8")

manifest = base / "AndroidManifest.xml"
if not manifest.exists():
    raise SystemExit("AndroidManifest.xml não encontrado.")
ms = manifest.read_text(encoding="utf-8")
if "android.permission.INTERNET" not in ms:
    ms = ms.replace('<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
                    '<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n    <uses-permission android:name="android.permission.INTERNET" />')
ms = re.sub(r'\s*<service\b(?=[^>]*SmartCareDreamService)[\s\S]*?</service>', '', ms)
if 'android:name=".RestDream"' not in ms:
    declaration = '''
        <service
            android:name=".RestDream"
            android:exported="true"
            android:permission="android.permission.BIND_DREAM_SERVICE"
            android:label="Smart Care - tela de descanso">
            <intent-filter>
                <action android:name="android.service.dreams.DreamService" />
            </intent-filter>
        </service>
'''
    ms = ms.replace('</application>', declaration + '    </application>')
manifest.write_text(ms, encoding="utf-8")

main = java / "MainActivity.kt"
if not main.exists():
    raise SystemExit("MainActivity.kt não encontrado no projeto extraído.")
src = main.read_text(encoding="utf-8")
src = src.replace('6 -> ClockWeatherPanel()', '6 -> RestDreamSettingsPanel()')
if 'fun RestDreamSettingsPanel()' not in src:
    panel = r'''
@Composable
fun RestDreamSettingsPanel() {
    val context = LocalContext.current
    var city by remember { mutableStateOf(Weather.city(context)) }
    var status by remember { mutableStateOf(Weather.summary(context).ifBlank { "Informe uma cidade para exibir o clima." }) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TELA DE DESCANSO", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = BlueMid)
        Text("Relógio estilo Motorola com data, clima da cidade escolhida e anel néon segmentado que acompanha a bateria.", color = Color.Gray)
        androidx.compose.material3.OutlinedTextField(
            value = city, onValueChange = { city = it },
            label = { Text("Cidade para o clima") },
            placeholder = { Text("Ex.: Rio de Janeiro") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                status = "Buscando a cidade e consultando o clima..."
                val requested = city
                kotlin.concurrent.thread {
                    val result = Weather.configureCity(context.applicationContext, requested)
                    android.os.Handler(android.os.Looper.getMainLooper()).post { status = result }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Salvar cidade e atualizar clima") }
        Text(status, color = Color.Gray)
        Button(
            onClick = {
                try {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DREAM_SETTINGS))
                } catch (_: Exception) {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ativar tela de descanso nas configurações") }
        Text("Nas configurações do Android, escolha “Smart Care - tela de descanso”. O caminho e o nome do menu podem variar conforme o aparelho.", color = Color.Gray, fontSize = 12.sp)
        Text("Sem cidade salva ou sem internet, o clima fica oculto. A bateria é atualizada por eventos do Android, sem consulta periódica.", color = Color.Gray, fontSize = 12.sp)
    }
}
'''
    marker = '@Composable\nfun Placeholder'
    if marker not in src:
        raise SystemExit("Não encontrei o ponto de inserção dos painéis em MainActivity.kt.")
    src = src.replace(marker, panel + '\n' + marker, 1)
if '6 -> RestDreamSettingsPanel()' not in src:
    raise SystemExit("Não encontrei a rota do módulo Relógio e clima.")
main.write_text(src, encoding="utf-8")
print("Módulo RestDream, Canvas, bateria por BroadcastReceiver, clima por cidade e configurações aplicados.")
