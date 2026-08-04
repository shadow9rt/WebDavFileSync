package com.sync.webdav

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.sync.webdav.data.local.SettingsDataStore
import com.sync.webdav.data.local.SyncDatabase
import com.sync.webdav.data.local.ThemeMode
import com.sync.webdav.data.sync.SyncEngine
import com.sync.webdav.ui.MainScreen
import com.sync.webdav.ui.theme.WebDavFileSyncTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable true Edge-to-Edge for Android 16 / HyperOS 3
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        val settingsDataStore = SettingsDataStore(this)
        val syncDatabase = SyncDatabase.getInstance(this)
        val syncEngine = SyncEngine(this)

        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsState(initial = ThemeMode.SYSTEM)

            WebDavFileSyncTheme(themeMode = themeMode) {
                MainScreen(
                    syncDatabase = syncDatabase,
                    settingsDataStore = settingsDataStore,
                    syncEngine = syncEngine
                )
            }
        }
    }
}
