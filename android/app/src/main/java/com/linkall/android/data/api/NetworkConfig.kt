package com.linkall.android.data.api

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络层配置：统一 Json 解析器、OkHttp 客户端、Retrofit 实例工厂
 */
object NetworkConfig {

    /** 统一 JSON 解析器（忽略未知字段、宽松编码） */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        coerceInputValues = true
    }

    /** 默认官方服务器地址 */
    const val DEFAULT_SERVER = "https://linkall.app/"

    /**
     * 创建 OkHttp 客户端
     */
    fun createOkHttpClient(tokenProvider: () -> String?): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .addInterceptor { chain ->
                val token = tokenProvider()
                val req = chain.request().newBuilder()
                    .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    /**
     * 构造 Retrofit 实例
     */
    fun createRetrofit(baseUrl: String, client: OkHttpClient): Retrofit {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
