package com.org.playboard.di

import javax.inject.Qualifier

/**
 * Qualifies the DataStore that holds long-lived app settings (e.g. the chosen
 * theme). Kept separate from the unqualified "session" DataStore so these
 * preferences survive a sign-out, which clears the session store.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsPrefs
