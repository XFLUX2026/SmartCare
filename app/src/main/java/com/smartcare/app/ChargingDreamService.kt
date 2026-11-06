package com.smartcare.app

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChargingDreamService : DreamService() {
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var percent: TextView
    private lateinit var status: TextView
    private lateinit var temperature: TextView
    private lateinit var ring: BatteryRingView
    private val handler = Handler(Looper.getMainLooper())
    private var lastBatteryRead = 0L

    private val ticker = object : Runnable {
        override fun run() {
            clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            date.text = SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")).format(Date())
            val now = System.currentTimeMillis()
            if (now - lastBatteryRead >= 30_000L) {
                updateBattery()
                lastBatteryRead = now
            }
            if (::ring.isInitialized) ring.invalidate()
            handler.postDelayed(this, 1_000L)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        buildScreen()
        updateBattery()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(ticker)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(24), dp(28), dp(24))
            setBackgroundColor(0xFF080D14.toInt())
        }
        clock = label(76f, 0xFFF2F7FC.toInt(), true)
        date = label(17f, 0xFF91A6B8.toInt(), false)
        percent = label(42f, 0xFF69C8FF.toInt(), true)
        status = label(18f, 0xFFEAF1F7.toInt(), true)
        temperature = label(15f, 0xFFA8B5C2.toInt(), false)

        root.addView(clock, centeredParams())
        root.addView(date, centeredParams().apply { bottomMargin = dp(26) })
        val ringFrame = FrameLayout(this)
        ring = BatteryRingView(this)
        ringFrame.addView(ring, FrameLayout.LayoutParams(dp(250), dp(250), Gravity.CENTER))
        ringFrame.addView(percent, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        root.addView(ringFrame, LinearLayout.LayoutParams(dp(280), dp(280)))
        root.addView(status, centeredParams().apply { topMargin = dp(20) })
        root.addView(temperature, centeredParams().apply { topMargin = dp(10) })
        val welcome = label(15f, 0xFFEAF1F7.toInt(), true).apply {
            text = "Olá! Seja bem-vindo."
        }
        val signature = label(10f, 0xFF60788D.toInt(), false).apply {
            text = "SMART CARE  •  XFLUX"
        }
        root.addView(welcome, centeredParams().apply { topMargin = dp(28) })
        root.addView(signature, centeredParams().apply { topMargin = dp(8) })
        setContentView(root)
    }

    private fun label(size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun centeredParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun updateBattery() {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (battery == null) {
            percent.text = "—"
            status.text = "Dados da bateria indisponíveis"
            temperature.text = ""
            ring.level = 0
            return
        }
        val level = battery.getIntExtra("level", -1)
        val scale = battery.getIntExtra("scale", 100).coerceAtLeast(1)
        val value = if (level >= 0) (level * 100 / scale).coerceIn(0, 100) else -1
        val tempTenths = battery.getIntExtra("temperature", Int.MIN_VALUE)
        val temp = if (tempTenths != Int.MIN_VALUE) tempTenths / 10f else Float.NaN
        val state = battery.getIntExtra("status", -1)
        val charging = state == 2 || state == 5
        percent.text = if (value >= 0) "$value%" else "—"
        status.text = when {
            charging && value >= 100 -> "Carga completa"
            charging -> "Carregando"
            else -> "Carregador desconectado"
        }
        temperature.text = if (temp.isNaN()) "Temperatura indisponível" else
            "Temperatura da bateria: %.1f °C".format(Locale.getDefault(), temp)
        ring.level = value.coerceAtLeast(0)
        ring.tint = when {
            temp.isNaN() -> 0xFF69C8FF.toInt()
            temp >= 40f -> 0xFFFF665E.toInt()
            temp >= 35f -> 0xFFFFC04D.toInt()
            else -> 0xFF69C8FF.toInt()
        }
        ring.invalidate()
    }
}

private class BatteryRingView(context: android.content.Context) : View(context) {
    var level: Int = 0
    var tint: Int = 0xFF69C8FF.toInt()
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 13f * resources.displayMetrics.density
        color = 0xFF263543.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 13f * resources.displayMetrics.density
        color = 0xFF69C8FF.toInt()
        strokeCap = Paint.Cap.ROUND
    }
    private val movingGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
        color = 0xFFFFFFFF.toInt()
        strokeCap = Paint.Cap.ROUND
        alpha = 170
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 18f * resources.displayMetrics.density
        val rect = RectF(pad, pad, width - pad, height - pad)
        canvas.drawArc(rect, 0f, 360f, false, track)
        progress.color = tint
        canvas.drawArc(rect, -90f, 360f * level.coerceIn(0, 100) / 100f, false, progress)
        val movingStart = (android.os.SystemClock.uptimeMillis() / 18L % 360L).toFloat()
        canvas.drawArc(rect, movingStart, 13f, false, movingGlow)
    }
}
