package com.org.playboard.data.remote

import com.org.playboard.data.remote.dto.DeleteAccountRequestDto
import retrofit2.http.Body
import retrofit2.http.HTTP

/** Narrow authenticated API for the irreversible account lifecycle. */
interface AccountApi {

    @HTTP(method = "DELETE", path = "api/v1/users/me", hasBody = true)
    suspend fun deleteAccount(@Body request: DeleteAccountRequestDto)
}
