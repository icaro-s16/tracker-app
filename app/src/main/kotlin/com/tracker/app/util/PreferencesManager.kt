package com.tracker.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tracker_prefs")


class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_TARGET_URL = stringPreferencesKey("target_url")
    }

    
    val targetUrlFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[KEY_TARGET_URL]
    }

    
    suspend fun saveTargetUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TARGET_URL] = url
        }
    }

    
    suspend fun clearTargetUrl() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_TARGET_URL)
        }
    }
}
