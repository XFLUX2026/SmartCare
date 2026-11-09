package com.smartcare.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LocationWeatherPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("smartcare_weather", Context.MODE_PRIVATE) }
    var city by remember { mutableStateOf(prefs.getString("city", "Ainda não configurada") ?: "Ainda não configurada") }
    var temperature by remember { mutableStateOf(prefs.getString("temperature", "--") ?: "--") }
    var description by remember { mutableStateOf(prefs.getString("description", "Ative a localização para consultar o clima") ?: "") }
    var icon by remember { mutableStateOf(prefs.getString("icon", "🌤️") ?: "🌤️") }
    var status by remember { mutableStateOf("A localização só será usada após sua autorização.") }
    var loading by remember { mutableStateOf(false) }

    fun updateFromLocation() {
        loading = true
        status = "Obtendo localização e consultando o clima..."
        requestCurrentLocation(context) { location ->
            if (location == null) {
                loading = false
                status = "Não foi possível obter a localização. Verifique se o serviço de localização está ligado."
            } else {
                scope.launch {
                    try {
                        val weather = withContext(Dispatchers.IO) {
                            WeatherLocation.fetch(context, location)
                        }
                        city = weather.city
                        temperature = "${weather.temperature}°C"
                        description = weather.description
                        icon = weather.icon
                        prefs.edit()
                            .putString("city", weather.city)
                            .putString("temperature", "${weather.temperature}°C")
                            .putString("description", weather.description)
                            .putString("icon", weather.icon)
                            .putLong("updated_at", System.currentTimeMillis())
                            .apply()
                        NeonClockWeatherWidgetProvider.updateAll(context)
                        status = "Clima atualizado. A localização não fica sendo rastreada em segundo plano."
                    } catch (_: Exception) {
                        status = "Não foi possível consultar o clima. Confira sua conexão com a internet."
                    } finally {
                        loading = false
                    }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) updateFromLocation()
        else status = "Permissão não concedida. Você pode autorizar quando quiser."
    }

    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("LOCALIZAÇÃO E CLIMA", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E6FD9))
        Text("Use a localização do próprio celular para mostrar o clima da região onde você está.", color = Color.Gray, fontSize = 13.sp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF092C43))
        ) {
            Column(
                Modifier.fillMaxWidth().padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(icon, fontSize = 48.sp)
                Text(temperature, fontSize = 42.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text(description, color = Color.White, fontSize = 15.sp)
                Text(city, color = Color(0xFF65E9E0), fontSize = 13.sp)
            }
        }
        Button(
            onClick = {
                val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) updateFromLocation()
                else permissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ))
            },
            enabled = !loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (loading) "Atualizando..." else "Ativar / atualizar localização")
        }
        Text(status, color = Color.Gray, fontSize = 12.sp)
        Text(
            "Privacidade: o Smart Care pede permissão enquanto você usa o aplicativo. Não precisa de localização permanente nem de GPS ligado o tempo todo. O clima é consultado pela internet usando coordenadas aproximadas/precisas conforme a permissão concedida.",
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}

private fun requestCurrentLocation(context: Context, callback: (Location?) -> Unit) {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!fine && !coarse) {
        callback(null)
        return
    }
    val providers = manager.getProviders(true)
    val provider = when {
        fine && LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
        LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
        providers.isNotEmpty() -> providers.first()
        else -> null
    }
    if (provider == null) {
        callback(null)
        return
    }
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getCurrentLocation(provider, null, context.mainExecutor) { location ->
                callback(location)
            }
        } else {
            @Suppress("DEPRECATION")
            manager.requestSingleUpdate(provider, object : LocationListener {
                override fun onLocationChanged(location: Location) = callback(location)
                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) { callback(null) }
            }, Looper.getMainLooper())
        }
    } catch (_: SecurityException) {
        callback(null)
    }
}
