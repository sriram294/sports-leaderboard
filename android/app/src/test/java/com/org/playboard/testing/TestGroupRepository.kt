package com.org.playboard.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.group.SelectedGroupStore
import com.org.playboard.data.remote.PlayboardApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json

/** In-memory [DataStore] so [SelectedGroupStore] works in plain JVM unit tests (no disk/Android). */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

/**
 * Builds a [GroupRepository] wired for unit tests — an in-memory selected-group
 * store and an unconfined app scope (its fire-and-forget persistence / background
 * refreshes run inline) — mirroring the Hilt-provided singleton without pulling in
 * Android or disk dependencies. Keeps the tests off the real 4-arg constructor so a
 * future dependency change only touches this one helper.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun testGroupRepository(
    api: PlayboardApi,
    json: Json = Json { ignoreUnknownKeys = true },
    scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
): GroupRepository = GroupRepository(api, json, SelectedGroupStore(InMemoryPreferencesDataStore()), scope)
