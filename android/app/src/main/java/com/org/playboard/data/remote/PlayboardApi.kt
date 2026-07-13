package com.org.playboard.data.remote

import com.org.playboard.data.remote.dto.AddMemberRequestDto
import com.org.playboard.data.remote.dto.CreateGroupRequestDto
import com.org.playboard.data.remote.dto.CreateInviteRequestDto
import com.org.playboard.data.remote.dto.GoogleSignInRequestDto
import com.org.playboard.data.remote.dto.GroupDto
import com.org.playboard.data.remote.dto.GroupsResponseDto
import com.org.playboard.data.remote.dto.InviteResponseDto
import com.org.playboard.data.remote.dto.JoinGroupRequestDto
import com.org.playboard.data.remote.dto.LeaderboardResponseDto
import com.org.playboard.data.remote.dto.MatchDetailDto
import com.org.playboard.data.remote.dto.MatchListResponseDto
import com.org.playboard.data.remote.dto.MemberDto
import com.org.playboard.data.remote.dto.MembersResponseDto
import com.org.playboard.data.remote.dto.PlayerStatsDto
import com.org.playboard.data.remote.dto.RecordMatchRequestDto
import com.org.playboard.data.remote.dto.RecordMatchResponseDto
import com.org.playboard.data.remote.dto.RefreshRequestDto
import com.org.playboard.data.remote.dto.RegisterDeviceRequestDto
import com.org.playboard.data.remote.dto.RenameGroupRequestDto
import com.org.playboard.data.remote.dto.TokenResponseDto
import com.org.playboard.data.remote.dto.UnregisterDeviceRequestDto
import com.org.playboard.data.remote.dto.UpdateUserRequestDto
import com.org.playboard.data.remote.dto.UserSummaryDto
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/** Mirrors `docs/backend/api-contracts.md`. Grows with each page slice. */
interface PlayboardApi {

    @POST("api/v1/auth/google")
    suspend fun signInWithGoogle(@Body request: GoogleSignInRequestDto): TokenResponseDto

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequestDto): TokenResponseDto

    /** Rename the signed-in user (docs/requirements/05-profile.md req #3). */
    @PATCH("api/v1/users/me")
    suspend fun updateDisplayName(@Body request: UpdateUserRequestDto): UserSummaryDto

    /**
     * Upload/replace the signed-in user's avatar photo (multipart, field `file`).
     * Returns the user with the new `photoUrl` per docs/backend/api-contracts.md.
     */
    @Multipart
    @POST("api/v1/users/me/photo")
    suspend fun uploadUserPhoto(@Part file: MultipartBody.Part): UserSummaryDto

    @GET("api/v1/groups")
    suspend fun getGroups(): GroupsResponseDto

    @POST("api/v1/groups")
    suspend fun createGroup(@Body request: CreateGroupRequestDto): GroupDto

    @POST("api/v1/groups/join")
    suspend fun joinGroup(@Body request: JoinGroupRequestDto): GroupDto

    @PATCH("api/v1/groups/{groupId}")
    suspend fun renameGroup(
        @Path("groupId") groupId: String,
        @Body request: RenameGroupRequestDto,
    ): GroupDto

    @POST("api/v1/groups/{groupId}/invites")
    suspend fun createInvite(
        @Path("groupId") groupId: String,
        @Body request: CreateInviteRequestDto,
    ): InviteResponseDto

    @GET("api/v1/groups/{groupId}/leaderboard")
    suspend fun getLeaderboard(@Path("groupId") groupId: String): LeaderboardResponseDto

    @GET("api/v1/groups/{groupId}/members")
    suspend fun getMembers(@Path("groupId") groupId: String): MembersResponseDto

    @POST("api/v1/groups/{groupId}/members")
    suspend fun addMember(
        @Path("groupId") groupId: String,
        @Body request: AddMemberRequestDto,
    ): MemberDto

    @GET("api/v1/groups/{groupId}/members/{userId}/stats")
    suspend fun getPlayerStats(
        @Path("groupId") groupId: String,
        @Path("userId") userId: String,
    ): PlayerStatsDto

    @POST("api/v1/groups/{groupId}/matches")
    suspend fun recordMatch(
        @Path("groupId") groupId: String,
        @Body request: RecordMatchRequestDto,
    ): RecordMatchResponseDto

    @GET("api/v1/groups/{groupId}/matches")
    suspend fun getMatches(
        @Path("groupId") groupId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int? = null,
    ): MatchListResponseDto

    @GET("api/v1/groups/{groupId}/matches/{matchId}")
    suspend fun getMatchDetail(
        @Path("groupId") groupId: String,
        @Path("matchId") matchId: String,
    ): MatchDetailDto

    @PATCH("api/v1/groups/{groupId}/matches/{matchId}")
    suspend fun editMatch(
        @Path("groupId") groupId: String,
        @Path("matchId") matchId: String,
        @Body request: RecordMatchRequestDto,
    ): MatchDetailDto

    @DELETE("api/v1/groups/{groupId}/matches/{matchId}")
    suspend fun deleteMatch(
        @Path("groupId") groupId: String,
        @Path("matchId") matchId: String,
    )

    /** Register this device's FCM token for push notifications (idempotent upsert). */
    @POST("api/v1/devices")
    suspend fun registerDevice(@Body request: RegisterDeviceRequestDto)

    /** Unregister this device's FCM token on sign-out. DELETE carries a body by design. */
    @HTTP(method = "DELETE", path = "api/v1/devices", hasBody = true)
    suspend fun unregisterDevice(@Body request: UnregisterDeviceRequestDto)
}
