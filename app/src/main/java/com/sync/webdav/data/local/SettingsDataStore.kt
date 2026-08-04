package com.sync.webdav.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "webdav_settings")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class SettingsDataStore(private val context: Context) {

    companion object {
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USER = stringPreferencesKey("webdav_user")
        val WEBDAV_PASS = stringPreferencesKey("webdav_pass")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
    }

    val webDavUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[WEBDAV_URL] ?: ""
    }

    val webDavUser: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[WEBDAV_USER] ?: ""
    }

    val webDavPass: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[WEBDAV_PASS] ?: ""
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[THEME_MODE]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[WIFI_ONLY] ?: false
    }

    suspend fun saveWebDavAccount(url: String, user: String, pass: String) {
        context.dataStore.edit { prefs ->
            prefs[WEBDAV_URL] = url
            prefs[WEBDAV_USER] = user
            prefs[WEBDAV_PASS] = pass
        }
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.name
        }
    }

    suspend fun saveWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[WIFI_ONLY] = wifiOnly
        }
    }
}
