package com.smartcare.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Now(val temp: Int, val code: Int, val day: Boolean)

object Weather {
    private val UF = mapOf(
        "Acre" to "AC", "Alagoas" to "AL", "Amapá" to "AP", "Amazonas" to "AM",
        "Bahia" to "BA", "Ceará" to "CE", "Distrito Federal" to "DF",
        "Espírito Santo" to "ES", "Goiás" to "GO", "Maranhão" to "MA",
        "Mato Grosso" to "MT", "Mato Grosso do Sul" to "MS", "Minas Gerais" to "MG",
        "Pará" to "PA", "Paraíba" to "PB", "Paraná" to "PR", "Pernambuco" to "PE",
        "Piauí" to "PI", "Rio de Janeiro" to "RJ", "Rio Grande do Norte" to "RN",
        "Rio Grande do Sul" to "RS", "Rondônia" to "RO", "Roraima" to "RR",
        "Santa Catarina" to "SC", "São Paulo" to "SP", "Sergipe" to "SE", "Tocantins" to "TO"
    )

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.requestMethod = "GET"
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Retorna Triple(rótulo "Cidade - UF", latitude, longitude) ou null. */
    fun geocode(city: String): Triple<String, Double, Double>? = runCatching {
        val query = URLEncoder.encode(city.trim(), "UTF-8")
        val result = JSONObject(
            get("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=1&language=pt")
        ).optJSONArray("results")?.optJSONObject(0) ?: return null
        val uf = if (result.optString("country_code") == "BR") UF[result.optString("admin1")] else null
        val label = if (uf != null) "${result.getString("name")} - $uf" else result.getString("name")
        Triple(label, result.getDouble("latitude"), result.getDouble("longitude"))
    }.getOrNull()

    fun fetchNow(latitude: Double, longitude: Double): Now? = runCatching {
        val current = JSONObject(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                    "&current=temperature_2m,weather_code,is_day&timezone=auto"
            )
        ).getJSONObject("current")
        Now(
            Math.round(current.getDouble("temperature_2m")).toInt(),
            current.getInt("weather_code"),
            current.getInt("is_day") == 1
        )
    }.getOrNull()

    /** Usado pela tela de descanso. */
    fun fetch(latitude: Double, longitude: Double): String? =
        fetchNow(latitude, longitude)?.let {
            "${it.temp}°C · ${WeatherArt.label(it.code)}"
        }
}
