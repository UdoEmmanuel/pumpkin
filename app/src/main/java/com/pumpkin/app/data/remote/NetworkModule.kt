package com.pumpkin.app.data.remote

import com.pumpkin.app.data.remote.api.ChatApi
import com.pumpkin.app.data.remote.socket.PumpkinSocket
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Process-wide singletons for the self-hosted server connection. */
object NetworkModule {
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
    }

    val chatApi: ChatApi by lazy {
        Retrofit.Builder()
            .baseUrl(ServerConfig.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatApi::class.java)
    }

    val socket: PumpkinSocket by lazy { PumpkinSocket() }
}
