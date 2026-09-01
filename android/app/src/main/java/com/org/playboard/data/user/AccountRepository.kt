package com.org.playboard.data.user

import com.org.playboard.data.auth.TokenStore
import com.org.playboard.data.group.GroupRepository
import com.org.playboard.data.remote.AccountApi
import com.org.playboard.data.remote.dto.DeleteAccountRequestDto
import com.org.playboard.di.AuthenticatedApi
import javax.inject.Inject
import javax.inject.Singleton

/** Deletes the remote account and purges every account-scoped local value. */
interface AccountRepository {
    suspend fun deleteAccount(confirmation: String): Result<Unit>
}

@Singleton
class RemoteAccountRepository @Inject constructor(
    @AuthenticatedApi private val api: AccountApi,
    private val tokenStore: TokenStore,
    private val groupRepository: GroupRepository,
) : AccountRepository {

    override suspend fun deleteAccount(confirmation: String): Result<Unit> = runCatching {
        api.deleteAccount(DeleteAccountRequestDto(confirmation))
        groupRepository.clearSession()
        tokenStore.clear()
    }
}
