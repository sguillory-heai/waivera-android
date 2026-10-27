package com.waivera.waivera.core.network.di

import com.waivera.waivera.core.network.AuthTokenInterceptor
import com.waivera.waivera.core.network.AuthTokenProvider
import com.waivera.waivera.core.network.BuildConfig
import com.waivera.waivera.core.network.NetworkConfig
import com.waivera.waivera.core.network.SessionRejections
import com.waivera.waivera.core.network.WaiveraConverterFactory
import com.waivera.waivera.core.network.WaiveraJson
import com.waivera.waivera.core.network.api.AuthenticationApi
import com.waivera.waivera.core.network.api.CalendarApi
import com.waivera.waivera.core.network.api.ChatApi
import com.waivera.waivera.core.network.api.EmployeeApi
import com.waivera.waivera.core.network.api.FileApi
import com.waivera.waivera.core.network.api.MobilePushApi
import com.waivera.waivera.core.network.api.TimeOffApi
import com.waivera.waivera.core.network.api.VideoApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit

/** The OkHttp client that adds the Firebase token for sprout_api requests (REST, attachments, Coil). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedHttpClient

/**
 * Network graph. [AuthTokenProvider] is bound by `core:data` (Firebase) or `core:demo`.
 * Base URLs come from BuildConfig fields fed by Gradle properties.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
object NetworkModule {
    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    fun networkConfig(): NetworkConfig = NetworkConfig(
        apiBaseUrl = BuildConfig.API_BASE_URL,
        webBaseUrl = BuildConfig.WEB_BASE_URL,
        allowLocalCleartext = BuildConfig.DEBUG,
    )

    @Provides
    @Singleton
    fun json(): Json = WaiveraJson

    @Provides
    @Singleton
    @AuthenticatedHttpClient
    fun authenticatedClient(
        config: NetworkConfig,
        tokens: AuthTokenProvider,
        rejections: SessionRejections,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(AuthTokenInterceptor(tokens, config.retrofitBaseUrl, rejections::report))
        .build()

    @Provides
    @Singleton
    fun retrofit(config: NetworkConfig, @AuthenticatedHttpClient client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(config.retrofitBaseUrl)
            .client(client)
            .addConverterFactory(WaiveraConverterFactory(json))
            .build()

    @Provides
    @Singleton
    fun authenticationApi(retrofit: Retrofit): AuthenticationApi = retrofit.create(AuthenticationApi::class.java)

    @Provides
    @Singleton
    fun chatApi(retrofit: Retrofit): ChatApi = retrofit.create(ChatApi::class.java)

    @Provides
    @Singleton
    fun calendarApi(retrofit: Retrofit): CalendarApi = retrofit.create(CalendarApi::class.java)

    @Provides
    @Singleton
    fun employeeApi(retrofit: Retrofit): EmployeeApi = retrofit.create(EmployeeApi::class.java)

    @Provides
    @Singleton
    fun timeOffApi(retrofit: Retrofit): TimeOffApi = retrofit.create(TimeOffApi::class.java)

    @Provides
    @Singleton
    fun fileApi(retrofit: Retrofit): FileApi = retrofit.create(FileApi::class.java)

    @Provides
    @Singleton
    fun videoApi(retrofit: Retrofit): VideoApi = retrofit.create(VideoApi::class.java)

    @Provides
    @Singleton
    fun mobilePushApi(retrofit: Retrofit): MobilePushApi = retrofit.create(MobilePushApi::class.java)
}
