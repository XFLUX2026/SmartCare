package com.smartcare.app

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class WeatherSnapshot(
    val city: String,
    val temperature: Int,
    val description: String,
    val icon: String
)

object WeatherLocation {
    fun fetch(context: Context, location: Location): WeatherSnapshot {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=temperature_2m,weather_code,is_day&timezone=auto"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
        }
        val json = try {
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
        val current = json.getJSONObject("current")
        val temperature = current.getDouble("temperature_2m").toInt()
        val code = current.getInt("weather_code")
        val isDay = current.optInt("is_day", 1) == 1
        val (description, icon) = condition(code, isDay)
        val city = try {
            @Suppress("DEPRECATION")
            val address = Geocoder(context, Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
            address?.locality ?: address?.subAdminArea ?: address?.adminArea ?: "Localização atual"
        } catch (_: Exception) {
            "Localização atual"
        }
        return WeatherSnapshot(city, temperature, description, icon)
    }

    private fun condition(code: Int, isDay: Boolean): Pair<String, String> = when (code) {
        0 -> if (isDay) "Céu limpo" to "☀️" else "Céu limpo" to "🌙"
        1 -> if (isDay) "Predomínio de sol" to "🌤️" else "Poucas nuvens" to "☁️"
        2 -> "Parcialmente nublado" to "⛅"
        3 -> "Nublado" to "☁️"
        45, 48 -> "Neblina" to "🌫️"
        in 51..57 -> "Garoa" to "🌦️"
        in 61..67 -> "Chuva" to "🌧️"
        in 71..77 -> "Neve" to "🌨️"
        in 80..82 -> "Pancadas de chuva" to "🌦️"
        in 85..86 -> "Neve" to "🌨️"
        95, 96, 99 -> "Trovoadas" to "⛈️"
        else -> "Condição variável" to if (isDay) "🌤️" else "🌙"
    }
}
