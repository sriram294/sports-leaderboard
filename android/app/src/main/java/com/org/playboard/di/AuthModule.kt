package com.org.playboard.di

import com.org.playboard.data.auth.CredentialManagerGoogleAuthClient
import com.org.playboard.data.auth.GoogleAuthClient
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
}
