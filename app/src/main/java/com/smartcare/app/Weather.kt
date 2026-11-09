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
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        return try {
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Retorna Triple(rótulo "Cidade - UF", latitude, longitude) ou null. */
    fun geocode(city: String): Triple<String, Double, Double>? = runCatching {
        val q = URLEncoder.encode(city, "UTF-8")
        val r = JSONObject(
            get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=pt")
        ).optJSONArray("results")?.getJSONObject(0) ?: return null
        val uf = if (r.optString("country_code") == "BR") UF[r.optString("admin1")] else null
        val label = if (uf != null) "${r.getString("name")} - $uf" else r.getString("name")
        Triple(label, r.getDouble("latitude"), r.getDouble("longitude"))
    }.getOrNull()

    fun fetchNow(lat: Double, lon: Double): Now? = runCatching {
        val cur = JSONObject(
            get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code,is_day&timezone=auto"
            )
        ).getJSONObject("current")
        Now(
            Math.round(cur.getDouble("temperature_2m")).toInt(),
            cur.getInt("weather_code"),
            cur.getInt("is_day") == 1
        )
    }.getOrNull()

    /** Usado pela tela de descanso. */
    fun fetch(lat: Double, lon: Double): String? =
        fetchNow(lat, lon)?.let { "${it.temp}°C · ${WeatherArt.label(it.code)}" }
}
