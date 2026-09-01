package com.org.playboard.ui.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.org.playboard.data.settings.ThemeStore
import com.org.playboard.data.user.AccountRepository
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    private fun newViewModel(
        deleteResult: Result<Unit> = Result.success(Unit),
        onDelete: (String) -> Unit = {},
    ): SettingsViewModel {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher),
            produceFile = { tempFolder.newFile("theme-${System.nanoTime()}.preferences_pb") },
        )
        val accountRepository = object : AccountRepository {
            override suspend fun deleteAccount(confirmation: String): Result<Unit> {
                onDelete(confirmation)
                return deleteResult
            }
        }
        return SettingsViewModel(ThemeStore(dataStore), accountRepository)
    }

    @Test
    fun `defaults to dark theme`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDarkTheme)
        job.cancel()
    }

    @Test
    fun `setDarkTheme updates the exposed state`() = runTest(testDispatcher) {
        val viewModel = newViewModel()
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setDarkTheme(false)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isDarkTheme)

        viewModel.setDarkTheme(true)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDarkTheme)
        job.cancel()
    }

    @Test
    fun `delete requires exact confirmation and submits once`() = runTest(testDispatcher) {
        var calls = 0
        var submitted = ""
        val viewModel = newViewModel(onDelete = {
            calls++
            submitted = it
        })

        viewModel.openDeleteDialog()
        viewModel.setDeleteConfirmation("delete")
        viewModel.deleteAccount()
        advanceUntilIdle()
        assertEquals(0, calls)

        viewModel.setDeleteConfirmation("DELETE")
        assertTrue(viewModel.uiState.value.canDeleteAccount)
        viewModel.deleteAccount()
        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals("DELETE", submitted)
        assertTrue(viewModel.uiState.value.isDeletingAccount)
    }

    @Test
    fun `delete failure is retryable and preserves dialog`() = runTest(testDispatcher) {
        val viewModel = newViewModel(deleteResult = Result.failure(RuntimeException("offline")))
        viewModel.openDeleteDialog()
        viewModel.setDeleteConfirmation("DELETE")

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDeleteDialogOpen)
        assertFalse(viewModel.uiState.value.isDeletingAccount)
        assertNotNull(viewModel.uiState.value.deleteError)
        assertTrue(viewModel.uiState.value.canDeleteAccount)
    }
}
