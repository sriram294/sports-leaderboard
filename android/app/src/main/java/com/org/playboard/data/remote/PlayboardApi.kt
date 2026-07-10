package com.org.playboard.data.remote

import com.org.playboard.data.remote.dto.GoogleSignInRequestDto
import com.org.playboard.data.remote.dto.GroupsResponseDto
import com.org.playboard.data.remote.dto.LeaderboardResponseDto
import com.org.playboard.data.remote.dto.RefreshRequestDto
import com.org.playboard.data.remote.dto.TokenResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Mirrors `docs/backend/api-contracts.md`. Grows with each page slice. */
interface PlayboardApi {

    @POST("api/v1/auth/google")
    suspend fun signInWithGoogle(@Body request: GoogleSignInRequestDto): TokenResponseDto

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequestDto): TokenResponseDto

    @GET("api/v1/groups")
    suspend fun getGroups(): GroupsResponseDto

    @GET("api/v1/groups/{groupId}/leaderboard")
    suspend fun getLeaderboard(@Path("groupId") groupId: String): LeaderboardResponseDto
}
