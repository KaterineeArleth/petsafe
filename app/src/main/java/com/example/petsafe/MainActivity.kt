package com.example.petsafe

import android.Manifest
import android.R
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.petsafe.ui.theme.PetSafeTheme
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.wearable.*
import com.google.maps.android.compose.*

class MainActivity : ComponentActivity() {
    private lateinit var googleApiClient: GoogleApiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PetSafeTheme {
                PetSafeMainScreen()
            }
        }
        initializeWearableClient()
    }

    private fun initializeWearableClient() {
        googleApiClient = GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .addConnectionCallbacks(object : GoogleApiClient.ConnectionCallbacks {
                override fun onConnected(bundle: Bundle?) {
                    Log.d("Wearable", "✅ Conectado a Wearable API")
                    setupDataListener()
                }

                override fun onConnectionSuspended(cause: Int) {
                    Log.w("Wearable", "⚠️ Conexión suspendida: $cause")
                }
            })
            .build()
        googleApiClient.connect()
    }

    private fun setupDataListener() {
        Wearable.DataApi.addListener(googleApiClient) { dataEvents ->
            Log.d("Wearable", "📩 Eventos recibidos: ${dataEvents.count}")
            for (event in dataEvents) {
                if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == "/alert") {
                    val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                    val message = dataMap.getString("message")
                    Log.d("Wearable", "📨 Mensaje recibido: $message")
                    showNotification()
                }
            }
        }
    }

    fun showNotification() {
        val channelId = "wear_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alertas Wear",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_dialog_alert)
            .setContentTitle("Alerta del Reloj")
            .setContentText("¡Zona no segura detectada!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(1, notification)
        Log.d("Wearable", "🔔 Notificación mostrada")
    }
}

@Composable
fun PetSafeMainScreen() {
    val context = LocalContext.current
    var ubicacion by remember { mutableStateOf<Location?>(null) }

    val locationPermissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            obtenerUbicacion(context) { loc -> ubicacion = loc }
        }
    }

    LaunchedEffect(Unit) {
        locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val latLng = ubicacion?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(0.0, 0.0)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(latLng, 16f)
    }

    Scaffold(
        topBar = { Text("PetSafe") }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Safe Zones", style = MaterialTheme.typography.headlineSmall)

            Box(
                modifier = Modifier
                    .height(300.dp)
                    .fillMaxWidth()
            ) {
                GoogleMap(
                    modifier = Modifier.matchParentSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    Marker(
                        state = MarkerState(position = latLng),
                        title = "Mi ubicación"
                    )
                }
            }

            Button(
                onClick = { Log.d("ZONA", "Zona añadida") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("+ ADD ZONE")
            }

            Text(
                text = "Estado del dispositivo:",
                style = MaterialTheme.typography.titleMedium
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🦴 Collar conectado vía Bluetooth")
                    Text("📍 Zona segura: Activada")
                    Text("📡 GPS disponible: Sí")
                    ubicacion?.let {
                        Text("🌐 Lat: ${it.latitude.format(5)}")
                        Text("🌐 Lon: ${it.longitude.format(5)}")
                    } ?: Text("🌐 Ubicación no disponible")
                }
            }

            Button(
                onClick = {
                    obtenerUbicacion(context) {
                        it?.let { loc ->
                            Log.d("GPS", "Lat: ${loc.latitude}, Lon: ${loc.longitude}")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Probar alerta al wearable")
            }

            Button(
                onClick = {
                    locationPermissionRequest.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Configurar zonas seguras")
            }
        }
    }
}

@SuppressLint("MissingPermission")
fun obtenerUbicacion(context: Context, callback: (Location?) -> Unit) {
    val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    if (ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        callback(null)
        return
    }

    fusedLocationClient.lastLocation
        .addOnSuccessListener { location -> callback(location) }
        .addOnFailureListener { callback(null) }
}

@Preview(showBackground = true)
@Composable
fun PreviewPetSafeMainScreen() {
    PetSafeTheme {
        PetSafeMainScreen()
    }
}

fun Double.format(digits: Int) = "%.${digits}f".format(this)