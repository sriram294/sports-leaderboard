package com.org.playboard.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.org.playboard.di.SettingsPrefs
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed persistence for the user's theme choice. Lives in the
 * [SettingsPrefs] store rather than the "session" store so the choice survives
 * a sign-out. Defaults to dark — the app's original, only look — so existing
 * users and fresh installs are unchanged until they opt into light.
 */
@Singleton
class ThemeStore @Inject constructor(
    @SettingsPrefs private val dataStore: DataStore<Preferences>,
) {
    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
    }

    /** `true` for the dark theme (the default), `false` for light. */
    val isDarkTheme: Flow<Boolean> = dataStore.data.map { it[Keys.DARK_THEME] ?: true }

    suspend fun setDarkTheme(dark: Boolean) {
        dataStore.edit { it[Keys.DARK_THEME] = dark }
    }
}
