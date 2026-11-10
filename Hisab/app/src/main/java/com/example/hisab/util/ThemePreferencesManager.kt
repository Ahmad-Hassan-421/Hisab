package com.example.hisab.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_preferences")

// SharedPreferences name + key used for splash-screen reading (before Hilt is ready)
const val SPLASH_PREFS_NAME = "splash_prefs"
const val SPLASH_THEME_KEY  = "theme_mode"

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

@Singleton
class ThemePreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.themeDataStore.data
        .map { preferences ->
            when (preferences[themeKey]) {
                "light"  -> ThemeMode.LIGHT
                "dark"   -> ThemeMode.DARK
                "system" -> ThemeMode.SYSTEM
                else     -> ThemeMode.SYSTEM
            }
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        val modeStr = when (mode) {
            ThemeMode.LIGHT  -> "light"
            ThemeMode.DARK   -> "dark"
            ThemeMode.SYSTEM -> "system"
        }
        // Write to DataStore (reactive, for in-app use)
        context.themeDataStore.edit { preferences ->
            preferences[themeKey] = modeStr
        }
        // Mirror to SharedPreferences so MainActivity can read it synchronously
        // before super.onCreate() (before Hilt injection is available)
        context.getSharedPreferences(SPLASH_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(SPLASH_THEME_KEY, modeStr)
            .apply()
    }

    suspend fun setThemeModeFromString(theme: String) {
        val mode = when (theme.lowercase()) {
            "light"                -> ThemeMode.LIGHT
            "dark"                 -> ThemeMode.DARK
            "system", "system default" -> ThemeMode.SYSTEM
            else                   -> ThemeMode.SYSTEM
        }
        setThemeMode(mode)
    }
}
