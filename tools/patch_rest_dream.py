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

settings_file = base / "java/com/smartcare/app/ui/screens/Settings.kt"
if not settings_file.exists():
    raise SystemExit("Settings.kt não encontrado no projeto extraído.")
settings = settings_file.read_text(encoding="utf-8")

imports = [
    "import android.content.Intent",
    "import android.os.Handler",
    "import android.os.Looper",
    "import android.provider.Settings as AndroidSettings",
    "import androidx.compose.material3.OutlinedTextField",
    "import androidx.compose.runtime.mutableStateOf",
    "import androidx.compose.runtime.remember",
    "import com.smartcare.app.Weather",
    "import kotlin.concurrent.thread",
]
for line in imports:
    if line not in settings:
        first_import = settings.find("\nimport ")
        if first_import >= 0:
            settings = settings[:first_import + 1] + line + "\n" + settings[first_import + 1:]
        else:
            package_end = settings.find("\n")
            settings = settings[:package_end + 1] + "\n" + line + settings[package_end + 1:]

function = re.search(r'@Composable\s*\nfun\s+SettingsScreen\s*\(\s*vm\s*:\s*AppVM\s*,\s*nav\s*:\s*NavController\s*\)\s*\{', settings)
if not function:
    raise SystemExit("Não encontrei SettingsScreen(vm: AppVM, nav: NavController) em Settings.kt.")

body_start = function.end()
ctx_line = settings.find("val ctx = LocalContext.current", body_start)
if ctx_line < 0:
    raise SystemExit("Não encontrei o contexto Android dentro de SettingsScreen.")
ctx_end = settings.find("\n", ctx_line)
state_code = '''
    val weatherCityState = remember { mutableStateOf(Weather.city(ctx)) }
    val weatherStatusState = remember {
        mutableStateOf(Weather.summary(ctx).ifBlank { "Informe uma cidade para exibir o clima." })
    }'''
settings = settings[:ctx_end] + state_code + settings[ctx_end:]

lazy_start = settings.find("LazyColumn(", body_start)
if lazy_start < 0:
    raise SystemExit("Não encontrei a lista de configurações em SettingsScreen.")
depth = 0
open_paren = settings.find("(", lazy_start)
close_paren = -1
for index in range(open_paren, len(settings)):
    if settings[index] == "(":
        depth += 1
    elif settings[index] == ")":
        depth -= 1
        if depth == 0:
            close_paren = index
            break
if close_paren < 0:
    raise SystemExit("Não consegui localizar o fechamento de LazyColumn.")
lazy_brace = settings.find("{", close_paren)
if lazy_brace < 0:
    raise SystemExit("Não encontrei o início do conteúdo de LazyColumn.")

settings_card = r'''
            item {
                SmartCard {
                    CardTitle(Icons.Rounded.Palette, "Tela de descanso e clima")
                    Text(
                        "Relógio grande estilo Motorola, data em português e anel néon segmentado com o nível da bateria.",
                        color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)
                    )
                    OutlinedTextField(
                        value = weatherCityState.value,
                        onValueChange = { weatherCityState.value = it },
                        label = { Text("Cidade para o clima") },
                        placeholder = { Text("Ex.: Rio de Janeiro") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        weatherStatusState.value = "Buscando a cidade e consultando o clima..."
                        val requestedCity = weatherCityState.value
                        thread {
                            val result = Weather.configureCity(ctx.applicationContext, requestedCity)
                            Handler(Looper.getMainLooper()).post { weatherStatusState.value = result }
                        }
                    }) { Text("Salvar cidade e atualizar clima") }
                    Text(weatherStatusState.value, color = c.muted, fontSize = 12.sp)
                    OutlinedButton(onClick = {
                        try {
                            ctx.startActivity(Intent(AndroidSettings.ACTION_DREAM_SETTINGS))
                        } catch (_: Exception) {
                            ctx.startActivity(Intent(AndroidSettings.ACTION_DISPLAY_SETTINGS))
                        }
                    }) { Text("Abrir configurações da tela de descanso") }
                    Text(
                        "Nas configurações do Android, selecione “Smart Care - tela de descanso”. Sem cidade salva ou sem internet, o clima fica oculto. A bateria é atualizada por BroadcastReceiver, sem polling.",
                        color = c.muted, fontSize = 12.sp
                    )
                }
            }
'''
if "Tela de descanso e clima" not in settings:
    settings = settings[:lazy_brace + 1] + "\n" + settings_card + settings[lazy_brace + 1:]
settings_file.write_text(settings, encoding="utf-8")
print("DreamService, clima por cidade e painel adicionados às Configurações existentes.")
