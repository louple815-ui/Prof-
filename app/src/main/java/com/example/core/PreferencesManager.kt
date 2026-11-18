package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "profplus_prefs")

class PreferencesManager(private val context: Context) {
    private val keyLanguage = stringPreferencesKey("preferred_language")
    private val keyDarkMode = booleanPreferencesKey("dark_mode")
    private val keyPushAlerts = booleanPreferencesKey("local_push_alerts")

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[keyLanguage] ?: "fr"
    }

    val darkModeFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyDarkMode] ?: false
    }

    val localAlertsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyPushAlerts] ?: true
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { prefs ->
            prefs[keyLanguage] = if (lang == "en") "en" else "fr"
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keyDarkMode] = enabled
        }
    }

    suspend fun setLocalAlerts(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keyPushAlerts] = enabled
        }
    }
}
