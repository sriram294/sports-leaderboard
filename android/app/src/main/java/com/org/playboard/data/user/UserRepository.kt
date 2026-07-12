package com.org.playboard.data.user

import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.model.UserSession
import com.org.playboard.data.remote.PlayboardApi
import com.org.playboard.data.remote.dto.UpdateUserRequestDto
import com.org.playboard.data.remote.dto.UserSummaryDto
import com.org.playboard.di.AuthenticatedApi
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Edits to the signed-in user's own identity — display name and avatar photo
 * (docs/requirements/05-profile.md req #3, docs/backend/api-contracts.md
 * `PATCH /users/me` + `POST /users/me/photo`).
 *
 * On success the returned identity is written straight back into [TokenStore],
 * so the persisted session — and every screen observing it — reflects the change
 * without a re-login.
 */
@Singleton
class UserRepository @Inject constructor(
    @AuthenticatedApi private val api: PlayboardApi,
    private val tokenStore: TokenStore,
) {
    /** Renames the signed-in user and updates the stored session. */
    suspend fun updateDisplayName(displayName: String): Result<UserSession> =
        runCatching { api.updateDisplayName(UpdateUserRequestDto(displayName.trim())).toUserSession() }
            .onSuccess { tokenStore.updateUser(it) }

    /**
     * Uploads [bytes] as the signed-in user's new avatar. The server reuses a
     * per-user storage path, so a re-upload can return the *same* `photoUrl`; we
     * append a version param so the URL-keyed image cache (Coil) reloads the new
     * bytes rather than showing the old photo. Spring serves the static file
     * ignoring the query string, so the busted URL still resolves.
     */
    suspend fun updatePhoto(bytes: ByteArray, mimeType: String): Result<UserSession> =
        runCatching {
            val part = MultipartBody.Part.createFormData(
                "file",
                "avatar",
                bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
            )
            val updated = api.uploadUserPhoto(part).toUserSession()
            updated.copy(photoUrl = updated.photoUrl?.let(::cacheBust))
        }.onSuccess { tokenStore.updateUser(it) }

    private fun cacheBust(url: String): String {
        val separator = if (url.contains('?')) '&' else '?'
        return "$url${separator}v=${System.currentTimeMillis()}"
    }
}

private fun UserSummaryDto.toUserSession() = UserSession(
    id = id,
    displayName = displayName,
    email = email,
    photoUrl = photoUrl,
    avatarColor = avatarColor,
)
