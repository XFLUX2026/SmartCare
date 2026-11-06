package com.smartcare.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class HomeCareWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED -> updateAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.smartcare.app.ACTION_REFRESH_HOME_WIDGET"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, HomeCareWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) updateWidgets(context, manager, ids)
        }

        private fun updateWidgets(
            context: Context,
            manager: AppWidgetManager,
            ids: IntArray
        ) {
            val snapshot = Metrics.read(context)
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.home_care_widget)
                val theme = context.getSharedPreferences("smartcare_settings", Context.MODE_PRIVATE)
                    .getString("theme", "classic") ?: "classic"
                val dark = theme == "black"
                val cardBackground = when (theme) {
                    "black" -> R.drawable.widget_card_black
                    "petrol" -> R.drawable.widget_card_petrol
                    else -> R.drawable.widget_card_classic
                }
                val widgetBackground = when (theme) {
                    "black" -> R.drawable.home_care_widget_bg_black
                    "petrol" -> R.drawable.home_care_widget_bg_petrol
                    else -> R.drawable.home_care_widget_bg
                }
                val mutedText = if (dark) android.graphics.Color.rgb(164, 181, 196) else android.graphics.Color.rgb(83, 101, 122)
                val accent = when (theme) {
                    "petrol" -> android.graphics.Color.rgb(15, 100, 112)
                    "black" -> android.graphics.Color.rgb(105, 200, 255)
                    else -> android.graphics.Color.rgb(30, 111, 217)
                }
                views.setInt(R.id.home_care_widget_root, "setBackgroundResource", widgetBackground)
                listOf(R.id.widget_health_card, R.id.widget_battery_card, R.id.widget_ram_card, R.id.widget_storage_card)
                    .forEach { views.setInt(it, "setBackgroundResource", cardBackground) }
                views.setTextColor(R.id.widget_title, accent)
                views.setTextColor(R.id.widget_refresh, accent)
                listOf(R.id.widget_health_detail, R.id.widget_battery_detail, R.id.widget_ram_detail, R.id.widget_storage_detail)
                    .forEach { views.setTextColor(it, mutedText) }
                listOf(R.id.widget_health_value, R.id.widget_battery_value, R.id.widget_ram_value, R.id.widget_storage_value)
                    .forEach { views.setTextColor(it, accent) }
                views.setTextViewText(R.id.widget_health_value, "${snapshot.health.score}/100")
                views.setTextViewText(
                    R.id.widget_health_detail,
                    if (snapshot.health.reasons.isEmpty()) "Sem alertas" else snapshot.health.reasons.first()
                )

                val battery = if (snapshot.battery.percent >= 0) "${snapshot.battery.percent}%" else "—"
                val batteryDetail = buildString {
                    append(snapshot.battery.status)
                    if (!snapshot.battery.tempC.isNaN()) append(" • %.1f °C".format(snapshot.battery.tempC))
                }
                views.setTextViewText(R.id.widget_battery_value, battery)
                views.setTextViewText(R.id.widget_battery_detail, batteryDetail)

                views.setTextViewText(R.id.widget_ram_value, "${snapshot.ram.percent.toInt()}%")
                views.setTextViewText(R.id.widget_ram_detail, "${fmt(snapshot.ram.available)} livres")

                views.setTextViewText(R.id.widget_storage_value, "${snapshot.storage.percent.toInt()}%")
                views.setTextViewText(R.id.widget_storage_detail, "${fmt(snapshot.storage.free)} livres")

                val openIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPending = PendingIntent.getActivity(
                    context, 4100 + id, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.home_care_widget_root, openPending)

                val refreshIntent = Intent(context, HomeCareWidgetProvider::class.java).apply {
                    action = ACTION_REFRESH
                }
                val refreshPending = PendingIntent.getBroadcast(
                    context, 5100 + id, refreshIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)

                manager.updateAppWidget(id, views)
            }
        }
    }
}
