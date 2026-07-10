package com.org.playboard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Body for `POST /groups/{groupId}/invites`. Both fields are optional —
 * `null` means unlimited uses / no expiry, which is what the app requests for
 * a simple shareable invite. With `encodeDefaults = false` (the app's [Json]
 * config), leaving both null serializes to `{}`.
 */
@Serializable
data class CreateInviteRequestDto(
    val maxUses: Int? = null,
    val expiresInHours: Int? = null,
)
