package com.org.playboard.data.auth

import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.org.playboard.BuildConfig
import javax.inject.Inject

sealed interface GoogleAuthResult {
    data class Success(val idToken: String) : GoogleAuthResult
    data object Cancelled : GoogleAuthResult
    data object NoCredentialAvailable : GoogleAuthResult
    data class Failed(val message: String) : GoogleAuthResult
}

interface GoogleAuthClient {
    suspend fun signIn(): GoogleAuthResult
}

/**
 * Wraps Credential Manager's Google Sign-In flow. Deliberately thin and not
 * unit-tested — this is a platform API needing a real Android context +
 * Play Services. [com.org.playboard.ui.login.LoginViewModel] only depends
 * on the [GoogleAuthClient] interface, which a fake implements for tests.
 */
class CredentialManagerGoogleAuthClient @Inject constructor() : GoogleAuthClient {

    override suspend fun signIn(): GoogleAuthResult {
        // Credential Manager must launch its account-picker UI from an
        // Activity-based context; the application context throws
        // "Failed to launch the selector UI" on stricter OEM builds. Grab the
        // foreground Activity (see [ActivityProvider]).
        val activity = ActivityProvider.currentActivity
            ?: return GoogleAuthResult.Failed("No foreground activity available for sign-in")

        // GetSignInWithGoogleOption is the explicit "Sign in with Google" button
        // flow: it always shows the account picker. GetGoogleIdOption is the
        // silent/One-Tap flow for returning users and throws NoCredentialException
        // when there's no already-authorized credential to return, even with
        // Google accounts present on the device.
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                GoogleAuthResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleAuthResult.Failed("Unexpected credential type: ${credential.type}")
            }
        } catch (e: GetCredentialCancellationException) {
            GoogleAuthResult.Cancelled
        } catch (e: NoCredentialException) {
            GoogleAuthResult.NoCredentialAvailable
        } catch (e: GetCredentialException) {
            // Log the real cause (type + message) so a field sign-in failure is
            // diagnosable in Logcat rather than silently generic.
            Log.e("PlayboardAuth", "getCredential failed: ${e.type} / ${e.message}", e)
            GoogleAuthResult.Failed(e.message ?: "Sign-in failed")
        }
    }
}
