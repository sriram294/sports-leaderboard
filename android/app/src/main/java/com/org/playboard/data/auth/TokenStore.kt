package com.org.playboard.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.org.playboard.data.model.SessionState
import com.org.playboard.data.model.UserSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed persistence for the current session: access/refresh
 * tokens plus the signed-in user's identity. A row is either fully present
 * (signed in) or fully absent (signed out) — [clear] and [save] are the
 * only ways in/out of "signed in".
 */
@Singleton
class TokenStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_PHOTO_URL = stringPreferencesKey("user_photo_url")
        val USER_AVATAR_COLOR = stringPreferencesKey("user_avatar_color")
    }

    /** Never emits [SessionState.Loading] — that's [com.org.playboard.data.auth.AuthRepository]'s bootstrap concern. */
    val sessionState: Flow<SessionState> = dataStore.data.map(::toSessionState)

    suspend fun currentAccessToken(): String? = dataStore.data.first()[Keys.ACCESS_TOKEN]

    suspend fun currentRefreshToken(): String? = dataStore.data.first()[Keys.REFRESH_TOKEN]

    suspend fun save(accessToken: String, refreshToken: String, user: UserSession) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
            prefs[Keys.USER_ID] = user.id
            prefs[Keys.USER_DISPLAY_NAME] = user.displayName
            prefs[Keys.USER_EMAIL] = user.email
            if (user.photoUrl != null) {
                prefs[Keys.USER_PHOTO_URL] = user.photoUrl
            } else {
                prefs.remove(Keys.USER_PHOTO_URL)
            }
            prefs[Keys.USER_AVATAR_COLOR] = user.avatarColor
        }
    }

    /** Called after a successful refresh — the user identity doesn't change, only the tokens. */
    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            prefs[Keys.REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private fun toSessionState(prefs: Preferences): SessionState {
        val accessToken = prefs[Keys.ACCESS_TOKEN]
        val userId = prefs[Keys.USER_ID]
        val displayName = prefs[Keys.USER_DISPLAY_NAME]
        val email = prefs[Keys.USER_EMAIL]
        val avatarColor = prefs[Keys.USER_AVATAR_COLOR]

        return if (accessToken != null && userId != null && displayName != null && email != null && avatarColor != null) {
            SessionState.SignedIn(
                UserSession(
                    id = userId,
                    displayName = displayName,
                    email = email,
                    photoUrl = prefs[Keys.USER_PHOTO_URL],
                    avatarColor = avatarColor,
                )
            )
        } else {
            SessionState.SignedOut
        }
    }
}
