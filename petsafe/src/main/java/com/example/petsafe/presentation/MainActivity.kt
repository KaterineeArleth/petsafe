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
import androidx.wear.tooling.preview.devices.WearDevices
import androidx.activity.compose.rememberLauncherForActivityResult


class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            PetSafeTheme {
                val ubicacion = remember { mutableStateOf<Location?>(null) }

                val locationPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        startLocationUpdates(ubicacion)
                    } else {
                        Log.e("WearGPS", "Permiso de ubicación denegado")
                    }
                }

                LaunchedEffect(Unit) {
                    val permiso = Manifest.permission.ACCESS_FINE_LOCATION
                    if (ContextCompat.checkSelfPermission(this@MainActivity, permiso)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        locationPermissionLauncher.launch(permiso)
                    } else {
                        startLocationUpdates(ubicacion)
                    }
                }

                WearAppUI(ubicacion.value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(ubicacion: MutableState<Location?>) {
        val locationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
            interval = 5000
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location != null) {
                    ubicacion.value = location
                    Log.d("WearGPS", "Lat: ${location.latitude}, Lon: ${location.longitude}")
                } else {
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
}
@Composable
fun WearAppUI(location: Location?) {
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
                    onClick = { Log.d("Wear", "Alerta enviada al teléfono") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Alerta"
                    )
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
                }
            }
        }
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)
