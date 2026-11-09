package com.smartcare.app

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LocationWeatherPanel() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("smartcare_weather", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var cityInput by remember { mutableStateOf(prefs.getString("city_input", "") ?: "") }
    var city by remember { mutableStateOf(prefs.getString("city", "Cidade não configurada") ?: "") }
    var temperature by remember { mutableStateOf(prefs.getString("temperature", "--°C") ?: "--°C") }
    var description by remember { mutableStateOf(prefs.getString("description", "Informe sua cidade para consultar o clima") ?: "") }
    var status by remember { mutableStateOf("O clima é consultado pela internet, sem precisar ativar o GPS.") }
    var loading by remember { mutableStateOf(false) }

    suspend fun loadWeather(label: String, latitude: Double, longitude: Double) {
        val now = withContext(Dispatchers.IO) { Weather.fetchNow(latitude, longitude) }
        if (now == null) {
            status = "Não foi possível consultar o clima. Verifique a internet e tente novamente."
            return
        }
        city = label
        temperature = "${now.temp}°C"
        description = WeatherArt.label(now.code)
        prefs.edit()
            .putString("city", label)
            .putString("city_input", cityInput.trim())
            .putString("latitude", latitude.toString())
            .putString("longitude", longitude.toString())
            .putString("temperature", temperature)
            .putString("description", description)
            .putInt("weather_code", now.code)
            .putBoolean("is_day", now.day)
            .putLong("updated_at", System.currentTimeMillis())
            .apply()
        NeonClockWeatherWidgetProvider.updateAll(context)
        status = "Clima atualizado. O widget da tela inicial já pode ser adicionado."
    }

    LaunchedEffect(Unit) {
        val lat = prefs.getString("latitude", null)?.toDoubleOrNull()
        val lon = prefs.getString("longitude", null)?.toDoubleOrNull()
        if (lat != null && lon != null) {
            val savedCity = prefs.getString("city", city)
            if (!savedCity.isNullOrBlank()) city = savedCity
            val now = withContext(Dispatchers.IO) { Weather.fetchNow(lat, lon) }
            if (now != null) {
                temperature = "${now.temp}°C"
                description = WeatherArt.label(now.code)
                prefs.edit().putString("temperature", temperature)
                    .putString("description", description)
                    .putInt("weather_code", now.code)
                    .putBoolean("is_day", now.day).apply()
                NeonClockWeatherWidgetProvider.updateAll(context)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("RELÓGIO E CLIMA", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E6FD9))
        Text(
            "Escolha a cidade que aparecerá no widget neon. Não é necessário liberar localização do celular.",
            color = Color.Gray,
            fontSize = 13.sp
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF08162F))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text("◷", fontSize = 36.sp, color = Color(0xFF28E7F2))
                Text(temperature, fontSize = 46.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text(description, color = Color(0xFFD7F9FF), fontSize = 15.sp)
                Text(city, color = Color(0xFF65E9E0), fontSize = 13.sp)
            }
        }
        OutlinedTextField(
            value = cityInput,
            onValueChange = { cityInput = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cidade e estado") },
            placeholder = { Text("Ex.: Rio de Janeiro, RJ") },
            singleLine = true
        )
        Button(
            onClick = {
                if (cityInput.isBlank()) {
                    status = "Digite uma cidade antes de atualizar."
                } else {
                    scope.launch {
                        loading = true
                        status = "Localizando a cidade e consultando o clima..."
                        try {
                            val location = withContext(Dispatchers.IO) { Weather.geocode(cityInput) }
                            if (location == null) {
                                status = "Cidade não encontrada. Tente informar também o estado."
                            } else {
                                loadWeather(location.first, location.second, location.third)
                            }
                        } catch (_: Exception) {
                            status = "Falha na consulta. Confira sua conexão e tente novamente."
                        } finally {
                            loading = false
                        }
                    }
                }
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Text("Salvar cidade e atualizar clima")
            }
        }
        Text(status, color = Color.Gray, fontSize = 12.sp)
        Text(
            "Para colocar o relógio na tela inicial: mantenha pressionado um espaço vazio da tela, abra Widgets e escolha Smart Care – Relógio e clima. Depois, ajuste o tamanho se desejar.",
            color = Color.Gray,
            fontSize = 12.sp
        )
        Text(
            "A previsão é fornecida pela Open-Meteo. A atualização automática do widget segue o intervalo permitido pelo Android.",
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}
