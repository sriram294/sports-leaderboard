package com.org.playboard.data.update

import com.org.playboard.BuildConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
    @Test
    fun equalOrOlderVersionsAreNotUpdates() {
        assertFalse(AppUpdateRepository.isNewerThanCurrent(BuildConfig.VERSION_CODE))
        assertFalse(AppUpdateRepository.isNewerThanCurrent(BuildConfig.VERSION_CODE - 1))
    }

    @Test
    fun newerVersionIsAnUpdate() {
        assertTrue(AppUpdateRepository.isNewerThanCurrent(BuildConfig.VERSION_CODE + 1))
    }
}
