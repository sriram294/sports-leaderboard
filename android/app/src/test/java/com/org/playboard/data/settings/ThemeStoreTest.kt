package com.org.playboard.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    private fun newStore(): ThemeStore {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("theme-${System.nanoTime()}.preferences_pb") },
        )
        return ThemeStore(dataStore)
    }

    @Test
    fun `defaults to dark`() = runTest(testDispatcher) {
        assertTrue(newStore().isDarkTheme.first())
    }

    @Test
    fun `setDarkTheme persists the choice`() = runTest(testDispatcher) {
        val store = newStore()
        store.setDarkTheme(false)
        assertFalse(store.isDarkTheme.first())
        store.setDarkTheme(true)
        assertTrue(store.isDarkTheme.first())
    }
}
