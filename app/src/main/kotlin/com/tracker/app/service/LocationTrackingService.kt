package com.tracker.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.tracker.app.R
import com.tracker.app.data.db.AppDatabase
import com.tracker.app.data.model.LocationEntry
import com.tracker.app.data.repository.LocationRepository
import com.tracker.app.ui.MainActivity
import com.tracker.app.util.DeviceIdHelper
import kotlinx.coroutines.*

private const val TAG = "LocationTrackingService"

// Constantes de configuração
private const val NOTIFICATION_ID = 1001
private const val CHANNEL_ID = "tracker_channel"
private const val SEND_INTERVAL_MS = 15_000L
private const val INACTIVITY_THRESHOLD_MS = 5 * 60 * 1000L
private const val MOVEMENT_THRESHOLD_METERS = 10f
private const val LOCATION_REQUEST_INTERVAL_MS = 2_000L
private const val FLUSH_INTERVAL_MS = 10_000L

class LocationTrackingService : LifecycleService() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_TARGET_URL = "EXTRA_TARGET_URL"

        fun startService(context: Context, targetUrl: String) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TARGET_URL, targetUrl)
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }


    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRepository: LocationRepository
    private lateinit var deviceId: String


    private var targetUrl: String = ""
    private var currentLocation: Location? = null
    private var lastMovementTime: Long = System.currentTimeMillis()
    private var isTracking: Boolean = true
    private var isPaused: Boolean = false

    private var sendingJob: Job? = null
    private var flushJob: Job? = null
    private var movementMonitorJob: Job? = null


    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            val previous = currentLocation

            if (previous != null) {
                val distance = previous.distanceTo(location)
                if (distance >= MOVEMENT_THRESHOLD_METERS) {
                    lastMovementTime = System.currentTimeMillis()
                    Log.d(TAG, "Movimento detectado: ${distance}m. Retomando envio.")
                    resumeIfPaused()
                }
            } else {
                lastMovementTime = System.currentTimeMillis()
            }

            currentLocation = location
            Log.v(TAG, "Localização atualizada: ${location.latitude}, ${location.longitude}")
        }
    }

    

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service criado.")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val db = AppDatabase.getInstance(this)
        locationRepository = LocationRepository(this, db.locationDao())
        deviceId = DeviceIdHelper.getDeviceId(this)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                targetUrl = intent.getStringExtra(EXTRA_TARGET_URL) ?: ""
                Log.d(TAG, "Iniciando rastreamento para: $targetUrl")
                startForeground(NOTIFICATION_ID, buildNotification("Rastreamento ativo"))
                startTracking()
            }
            ACTION_STOP -> {
                Log.d(TAG, "Parando serviço.")
                stopTracking()
                stopSelf()
            }
        }

        return START_STICKY 
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        Log.d(TAG, "Service destruído.")
    }

    

    private fun startTracking() {
        isTracking = true
        isPaused = false
        startLocationUpdates()
        startSendingLoop()
        startFlushLoop()
        startMovementMonitor()
    }

    private fun stopTracking() {
        isTracking = false
        sendingJob?.cancel()
        flushJob?.cancel()
        movementMonitorJob?.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_REQUEST_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(1_000L)
            .setMinUpdateDistanceMeters(0f) 
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Permissão de localização negada: ${e.message}")
        }
    }

    
    private fun startSendingLoop() {
        sendingJob?.cancel()
        sendingJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isTracking) {
                delay(SEND_INTERVAL_MS)

                if (isPaused) {
                    Log.d(TAG, "Loop pausado (inatividade). Aguardando movimento...")
                    continue
                }

                val location = currentLocation
                if (location == null) {
                    Log.d(TAG, "Aguardando primeira localização...")
                    continue
                }

                if (targetUrl.isBlank()) {
                    Log.w(TAG, "URL de destino não configurada.")
                    continue
                }

                val entry = LocationEntry(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis(),
                )

                locationRepository.sendOrQueue(entry, targetUrl)
                updateNotification(entry)
            }
        }
    }

    
    private fun startFlushLoop() {
        flushJob?.cancel()
        flushJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isTracking) {
                delay(FLUSH_INTERVAL_MS)

                val pending = locationRepository.getPendingCount()
                if (pending > 0 && locationRepository.isNetworkAvailable()) {
                    Log.d(TAG, "Tentando reenviar $pending registros da fila offline...")
                    locationRepository.flushQueue(targetUrl)
                }
            }
        }
    }

   
    private fun startMovementMonitor() {
        movementMonitorJob?.cancel()
        movementMonitorJob = lifecycleScope.launch(Dispatchers.Default) {
            while (isTracking) {
                delay(30_000L) 

                val timeSinceLastMovement = System.currentTimeMillis() - lastMovementTime

                if (timeSinceLastMovement >= INACTIVITY_THRESHOLD_MS && !isPaused) {
                    Log.d(TAG, "Inatividade por ${timeSinceLastMovement / 60000} min. Pausando envio.")
                    pauseTracking()
                }
            }
        }
    }

    
    private fun pauseTracking() {
        isPaused = true
        updateForegroundNotification("Pausado (sem movimento)")
    }

    
    private fun resumeIfPaused() {
        if (isPaused) {
            isPaused = false
            Log.d(TAG, "Retomando envio após movimento detectado.")
            updateForegroundNotification("Rastreamento ativo")
        }
    }

    

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rastreamento de Localização",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Serviço de rastreamento GPS em execução"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, LocationTrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Tracker GPS")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Parar", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateForegroundNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun updateNotification(entry: LocationEntry) {
        val status = "Lat: %.5f | Lon: %.5f".format(entry.latitude, entry.longitude)
        updateForegroundNotification(status)
    }
}
