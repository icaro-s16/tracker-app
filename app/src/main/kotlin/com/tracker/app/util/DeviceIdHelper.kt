package com.tracker.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat

private const val TAG = "DeviceIdHelper"


object DeviceIdHelper {

    
    fun getDeviceId(context: Context): String {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            tryGetImei(context) ?: getAndroidId(context)
        } else {
            Log.d(TAG, "Android 10+: usando ANDROID_ID como fallback do IMEI.")
            getAndroidId(context)
        }
    }

    
    private fun tryGetImei(context: Context): String? {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "Permissão READ_PHONE_STATE não concedida. Usando ANDROID_ID.")
            return null
        }

        return try {
            val telephonyManager =
                context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("HardwareIds")
            val imei = telephonyManager.imei
            if (!imei.isNullOrBlank()) {
                Log.d(TAG, "IMEI obtido com sucesso.")
                imei
            } else {
                Log.w(TAG, "IMEI retornou vazio. Usando ANDROID_ID.")
                null
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException ao obter IMEI: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter IMEI: ${e.message}")
            null
        }
    }

    /**
     * Retorna o ANDROID_ID do dispositivo.
     * Este ID é único por dispositivo + usuário + instalação de sistema.
     */
    private fun getAndroidId(context: Context): String {
        @Suppress("HardwareIds")
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return androidId ?: "UNKNOWN"
    }

    
    fun getDeviceIdForDisplay(context: Context): String {
        return getDeviceId(context)
    }
}
