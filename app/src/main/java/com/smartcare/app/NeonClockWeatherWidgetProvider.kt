package com.smartcare.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

class NeonClockWeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, NeonClockWeatherWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) updateWidgets(context, manager, ids)
        }

        private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.getSharedPreferences("smartcare_weather", Context.MODE_PRIVATE)
            val temperature = prefs.getString("temperature", "--") ?: "--"
            val description = prefs.getString("description", "Ative a localização no Smart Care") ?: ""
            val icon = prefs.getString("icon", "🌤️") ?: "🌤️"
            val city = prefs.getString("city", "Localização não configurada") ?: ""
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.neon_clock_weather_widget)
                views.setTextViewText(R.id.neon_weather_icon, icon)
                views.setTextViewText(R.id.neon_temperature, temperature)
                views.setTextViewText(R.id.neon_description, description)
                views.setTextViewText(R.id.neon_city, city)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
