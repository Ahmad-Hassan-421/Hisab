package com.example.hisab.di

import android.content.Context
import androidx.room.Room
import com.example.hisab.data.db.HisabDatabase
import com.example.hisab.data.db.HisabEntryDao
import com.example.hisab.data.db.UserDao
import com.example.hisab.features.groups.data.ApiGroupRepository
import com.example.hisab.features.ledger.data.ApiHisabRepository
import com.example.hisab.features.invitations.data.ApiRequestRepository
import com.example.hisab.features.profile.data.ApiUserRepository
import com.example.hisab.data.remote.TokenInterceptor
import com.example.hisab.data.remote.api.HisabApiService
import com.example.hisab.data.remote.api.WebSocketClient
import com.example.hisab.features.groups.domain.GroupRepository
import com.example.hisab.features.ledger.domain.HisabRepository
import com.example.hisab.features.invitations.domain.RequestRepository
import com.example.hisab.features.profile.domain.UserRepository
import com.example.hisab.util.Constants
import com.example.hisab.util.TokenManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HisabDatabase {
        return Room.databaseBuilder(
            context,
            HisabDatabase::class.java,
            "hisab.db"
        )
        .addMigrations(HisabDatabase.MIGRATION_1_2, HisabDatabase.MIGRATION_2_3)
        .build()
    }

    @Provides
    fun provideUserDao(database: HisabDatabase): UserDao = database.userDao()

    @Provides
    fun provideHisabEntryDao(database: HisabDatabase): HisabEntryDao = database.hisabEntryDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(tokenInterceptor: TokenInterceptor): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(tokenInterceptor)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideHisabApiService(retrofit: Retrofit): HisabApiService {
        return retrofit.create(HisabApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideWebSocketClient(tokenManager: TokenManager, okHttpClient: OkHttpClient): WebSocketClient {
        return WebSocketClient(tokenManager, okHttpClient)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: ApiUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun bindHisabRepository(impl: ApiHisabRepository): HisabRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(impl: ApiGroupRepository): GroupRepository

    @Binds
    @Singleton
    abstract fun bindRequestRepository(impl: ApiRequestRepository): RequestRepository
}
