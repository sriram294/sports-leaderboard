package com.org.playboard.ui.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.org.playboard.data.settings.ThemeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel(): SettingsViewModel {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("theme-${System.nanoTime()}.preferences_pb") },
        )
        return SettingsViewModel(ThemeStore(dataStore))
    }

    @Test
    fun `defaults to dark theme`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        val job = launch { viewModel.isDarkTheme.collect {} }
        advanceUntilIdle()
        assertTrue(viewModel.isDarkTheme.value)
        job.cancel()
    }

    @Test
    fun `setDarkTheme updates the exposed state`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        val job = launch { viewModel.isDarkTheme.collect {} }
        advanceUntilIdle()

        viewModel.setDarkTheme(false)
        advanceUntilIdle()
        assertFalse(viewModel.isDarkTheme.value)

        viewModel.setDarkTheme(true)
        advanceUntilIdle()
        assertTrue(viewModel.isDarkTheme.value)
        job.cancel()
    }
}
