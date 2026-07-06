package com.tracker.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tracker.app.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private const val TAG = "BootReceiver"


class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Boot detectado. Verificando se deve retomar o rastreamento.")

        
        val pendingResult = goAsync()
        val prefsManager = PreferencesManager(context)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val savedUrl = prefsManager.targetUrlFlow.firstOrNull()
                if (!savedUrl.isNullOrBlank()) {
                    Log.d(TAG, "URL encontrada. Reiniciando serviço de rastreamento.")
                    LocationTrackingService.startService(context, savedUrl)
                } else {
                    Log.d(TAG, "Nenhuma URL salva. Serviço não será reiniciado.")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
