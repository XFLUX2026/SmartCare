package com.smartcare.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import kotlin.concurrent.thread

class NeonClockWeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids)
        refreshWeather(context)
    }

    companion object {
        private const val PREFS = "smartcare_weather"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, NeonClockWeatherWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) updateWidgets(context, manager, ids)
        }

        fun refreshWeather(context: Context) {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lat = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return
            val lon = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return
            thread(name = "SmartCareWeatherWidget") {
                val now = Weather.fetchNow(lat, lon) ?: return@thread
                prefs.edit()
                    .putString("temperature", "${now.temp}°C")
                    .putString("description", WeatherArt.label(now.code))
                    .putInt("weather_code", now.code)
                    .putBoolean("is_day", now.day)
                    .putLong("updated_at", System.currentTimeMillis())
                    .apply()
                updateAll(app)
            }
        }

        private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val temperature = prefs.getString("temperature", "--°C") ?: "--°C"
            val description = prefs.getString("description", "Configure uma cidade no Smart Care") ?: ""
            val city = prefs.getString("city", "Cidade não configurada") ?: ""
            val code = prefs.getInt("weather_code", 2)
            val day = prefs.getBoolean("is_day", true)

            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.neon_clock_weather_widget)
                views.setImageViewBitmap(R.id.neon_ring, WeatherArt.ring(320))
                views.setImageViewBitmap(R.id.neon_weather_icon, WeatherArt.icon(WeatherArt.kind(code), day, 128))
                views.setTextViewText(R.id.neon_temperature, temperature)
                views.setTextViewText(R.id.neon_description, description)
                views.setTextViewText(R.id.neon_city, city)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
