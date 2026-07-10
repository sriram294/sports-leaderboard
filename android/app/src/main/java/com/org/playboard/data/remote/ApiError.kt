package com.org.playboard.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

/**
 * The stable `code` extension field from the backend's RFC 7807 error bodies
 * (docs/backend/api-contracts.md § Conventions). Only the field we branch on
 * is modelled; everything else is ignored.
 */
@Serializable
private data class ApiErrorBody(val code: String? = null)

/**
 * The backend error `code` for a failed call, or `null` if this isn't an HTTP
 * error or the body has no code. Lets callers switch on a code (e.g.
 * `GROUP_INVITE_INVALID`) instead of parsing human-readable messages.
 */
fun Throwable.apiErrorCode(json: Json): String? {
    val body = (this as? HttpException)?.response()?.errorBody()?.string() ?: return null
    return runCatching { json.decodeFromString<ApiErrorBody>(body).code }.getOrNull()
}

/** A `POST /groups/join` failed because the invite code was wrong, expired, or exhausted. */
class InvalidInviteCodeException : Exception("Invite code is invalid, expired, or exhausted")
