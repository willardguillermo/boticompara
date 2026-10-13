package com.boticompara.app.data.remote

import android.content.Context
import com.boticompara.app.data.local.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val tokenManager = TokenManager(context)
        val token = runBlocking { tokenManager.tokenFlow.first() }
        val requestBuilder = chain.request().newBuilder()
        if (!token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(requestBuilder.build())
    }
}

object RetrofitClient {
    private var retrofit: Retrofit? = null

    fun getInstance(context: Context): ApiService {
        if (retrofit == null) {
            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(context))
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl("http://10.0.2.2:8080/api/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }
}