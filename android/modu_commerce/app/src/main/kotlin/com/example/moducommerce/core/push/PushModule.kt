package com.example.moducommerce.core.push

import com.example.moducommerce.core.network.ApiConfig
import com.google.gson.Gson
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {

    @Binds
    @Singleton
    abstract fun bindPushTokenSource(impl: FirebasePushTokenSource): PushTokenSource

    companion object {
        /** commerce-service 는 게이트웨이가 아니라 웹과 같은 출처(nginx·Vite 의 /api 프록시)로 부른다. 토큰·갱신은 "api" 클라이언트가. */
        @Provides
        @Singleton
        fun providePushApi(@Named("api") client: OkHttpClient, gson: Gson): PushApi = Retrofit.Builder()
            .baseUrl(ApiConfig.WEB_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(PushApi::class.java)
    }
}
