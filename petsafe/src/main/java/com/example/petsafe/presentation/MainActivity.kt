package com.example.petsafe.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.registerForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.*
import com.example.petsafe.presentation.theme.PetSafeTheme
import com.google.android.gms.location.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import com.google.android.gms.wearable.*
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var googleApiClient: GoogleApiClient
    private lateinit var locationCallback: LocationCallback

    // Registro para el permiso de ubicación
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startLocationUpdates()
        } else {
            Log.e("WearGPS", "Permiso de ubicación denegado")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Inicialización del cliente de Wear API
        googleApiClient = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .build()
        googleApiClient.connect()

        setContent {
            PetSafeTheme {
                val ubicacion = remember { mutableStateOf<Location?>(null) }

                LaunchedEffect(Unit) {
                    val permiso = Manifest.permission.ACCESS_FINE_LOCATION
                    if (ContextCompat.checkSelfPermission(this@MainActivity, permiso) != PackageManager.PERMISSION_GRANTED) {
                        locationPermissionRequest.launch(permiso)
                    } else {
                        startLocationUpdates()
                    }
                }

                WearAppUI(ubicacion.value) {
                    sendAlertMessageToPhone()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        googleApiClient.disconnect()
        stopLocationUpdates()
    }

    // Método para enviar mensaje al teléfono
    private fun sendAlertMessageToPhone() {
        if (!googleApiClient.isConnected) {
            Log.e("Wearable", "GoogleApiClient no conectado")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                var dataMap = DataMap().apply {
                    putString("message", "Alerta: Zona no segura")
                    putLong("timestamp", System.currentTimeMillis())
                }

                val putDataRequest = PutDataRequest.create("/alert").apply {
                    dataMap = dataMap
                    setUrgent()
                }

                val result = Wearable.DataApi.putDataItem(googleApiClient, putDataRequest).await()

                if (result.status.isSuccess) {
                    Log.d("Wearable", "Mensaje enviado correctamente al teléfono")
                } else {
                    Log.e("Wearable", "Error al enviar el mensaje: ${result.status}")
                }
            } catch (e: Exception) {
                Log.e("Wearable", "Excepción al enviar mensaje", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
            interval = 5000
            fastestInterval = 3000
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d("WearGPS", "Lat: ${location.latitude}, Lon: ${location.longitude}")
                    // Aquí deberías actualizar tu estado de ubicación
                    // Puedes usar un ViewModel o algún otro mecanismo de estado
                } ?: run {
                    Log.d("WearGPS", "Ubicación null")
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopLocationUpdates() {
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}

@Composable
fun WearAppUI(
    location: Location?,
    onAlertClick: () -> Unit
) {
    val scrollState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = scrollState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "PetSafe",
                    style = MaterialTheme.typography.title1,
                    modifier = Modifier.padding(8.dp)
                )
            }

            item {
                Card(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("🦴 Collar conectado vía Bluetooth")
                        Text("📍 Zona segura: Activada")
                        Text("📡 GPS disponible: Sí")
                        if (location != null) {
                            Text("🌐 Lat: ${location.latitude.format(5)}")
                            Text("🌐 Lon: ${location.longitude.format(5)}")
                        } else {
                            Text("🌐 Ubicación no disponible")
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = onAlertClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Alerta"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Enviar Alerta")
                }
            }

            item {
                Button(
                    onClick = { Log.d("Wear", "Abrir configuración") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configurar"
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Configuración")
                }
            }
        }
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)