package com.tracker.app.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.tracker.app.data.db.LocationDao
import com.tracker.app.data.model.LocationEntry
import com.tracker.app.data.model.LocationPayload
import kotlinx.coroutines.flow.Flow

private const val TAG = "LocationRepository"


class LocationRepository(
    private val context: Context,
    private val locationDao: LocationDao,
    private val apiService: ApiService = ApiService.create()
) {

    val pendingCount: Flow<Int> = locationDao.getPendingCount()

    
    suspend fun sendOrQueue(entry: LocationEntry, targetUrl: String) {
        if (!isNetworkAvailable()) {
            Log.d(TAG, "Sem conexão. Salvando na fila: ${entry.timestamp}")
            locationDao.insert(entry)
            return
        }

        try {
            val payload = entry.toPayload()
            val response = apiService.sendLocation(targetUrl, payload)

            if (response.isSuccessful) {
                Log.d(TAG, "Enviado com sucesso: ${entry.timestamp}")
                locationDao.deleteSentEntries()
            } else {
                Log.w(TAG, "Resposta de erro ${response.code()}. Salvando na fila.")
                locationDao.insert(entry)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha no envio: ${e.message}. Salvando na fila.")
            locationDao.insert(entry)
        }
    }

    
    suspend fun flushQueue(targetUrl: String) {
        if (!isNetworkAvailable()) return

        val pendingEntries = locationDao.getPendingEntries()
        if (pendingEntries.isEmpty()) return

        Log.d(TAG, "Reenvio da fila: ${pendingEntries.size} registros pendentes.")

        var successCount = 0
        for (entry in pendingEntries) {
            try {
                val payload = entry.toPayload()
                val response = apiService.sendLocation(targetUrl, payload)

                if (response.isSuccessful) {
                    locationDao.markAsSent(entry.id)
                    successCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao reenviar entrada ${entry.id}: ${e.message}")
                
                if (!isNetworkAvailable()) break
            }
        }

        Log.d(TAG, "Reenvio concluído: $successCount/${pendingEntries.size} enviados.")

        
        if (successCount > 0) {
            locationDao.deleteSentEntries()
        }
    }

    
    suspend fun getPendingCount(): Int = locationDao.getPendingCountOnce()

    
    fun isNetworkAvailable(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    
    private fun LocationEntry.toPayload() = LocationPayload(
        latitude = latitude,
        longitude = longitude,
        timestamp = timestamp,
    )
}
