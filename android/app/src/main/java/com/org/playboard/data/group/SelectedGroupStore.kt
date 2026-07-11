package com.org.playboard.data.group

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-backed persistence for the active group id, so the switcher lands
 * on the same group across app relaunches instead of resetting to the first
 * one. Shares the "session" DataStore with [com.org.playboard.data.auth.TokenStore],
 * so a sign-out ([TokenStore.clear]) also drops the saved selection — the next
 * user starts fresh.
 */
@Singleton
class SelectedGroupStore @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val SELECTED_GROUP_ID = stringPreferencesKey("selected_group_id")
    }

    /** The persisted active group id, or `null` if none has been saved yet. */
    val selectedGroupId: Flow<String?> = dataStore.data.map { it[Keys.SELECTED_GROUP_ID] }

    suspend fun set(groupId: String) {
        dataStore.edit { it[Keys.SELECTED_GROUP_ID] = groupId }
    }
}
