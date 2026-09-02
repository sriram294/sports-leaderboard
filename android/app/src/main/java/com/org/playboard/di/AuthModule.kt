package com.org.playboard.di

import com.org.playboard.data.auth.CredentialManagerGoogleAuthClient
import com.org.playboard.data.auth.GoogleAuthClient
import com.org.playboard.data.user.AccountRepository
import com.org.playboard.data.user.RemoteAccountRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindGoogleAuthClient(impl: CredentialManagerGoogleAuthClient): GoogleAuthClient

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: RemoteAccountRepository): AccountRepository
}
