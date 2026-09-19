package com.example.moducommerce.core.di

import android.content.Context
import coil.ImageLoader
import com.example.moducommerce.BuildConfig
import com.example.moducommerce.core.network.ApiConfig
import com.example.moducommerce.core.network.AuthInterceptor
import com.example.moducommerce.core.network.TokenAuthenticator
import com.example.moducommerce.data.api.AuthApi
import com.example.moducommerce.data.api.CatalogApi
import com.example.moducommerce.data.api.OrderApi
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * 두 서버를 부른다: 게이트웨이(auth-service 토큰·userinfo)와 커머스 서비스(카탈로그).
 * 토큰 갱신 호출만 인터셉터·Authenticator 없는 "plain" 클라이언트로 한다(갱신이 자기 자신을 부르면 안 된다).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 10L

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setLenient().create()

    @Provides
    @Singleton
    @Named("plain")
    fun providePlainClient(): OkHttpClient = baseClientBuilder().build()

    @Provides
    @Singleton
    @Named("api")
    fun provideApiClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = baseClientBuilder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    /** [TokenAuthenticator] 가 쓰는 갱신 전용 AuthApi. */
    @Provides
    @Singleton
    @Named("plain")
    fun providePlainAuthApi(@Named("plain") client: OkHttpClient, gson: Gson): AuthApi =
        retrofit(ApiConfig.GATEWAY_URL, client, gson).create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideAuthApi(@Named("api") client: OkHttpClient, gson: Gson): AuthApi =
        retrofit(ApiConfig.GATEWAY_URL, client, gson).create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideCatalogApi(@Named("api") client: OkHttpClient, gson: Gson): CatalogApi =
        retrofit(ApiConfig.COMMERCE_URL, client, gson).create(CatalogApi::class.java)

    @Provides
    @Singleton
    fun provideOrderApi(@Named("api") client: OkHttpClient, gson: Gson): OrderApi =
        retrofit(ApiConfig.COMMERCE_URL, client, gson).create(OrderApi::class.java)

    /** 상품 사진은 공개 URL(picsum 등)이라 인증이 필요 없지만, 클라이언트를 하나로 쓰는 편이 단순하다. */
    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @Named("plain") client: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(client)
        .crossfade(true)
        .build()

    private fun baseClientBuilder(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return builder
    }

    private fun retrofit(baseUrl: String, client: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
}
