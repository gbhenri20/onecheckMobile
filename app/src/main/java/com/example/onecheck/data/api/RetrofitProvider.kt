package com.example.onecheck.data.api

import com.example.onecheck.BuildConfig
import com.example.onecheck.data.api.dto.LoginResponse
import com.example.onecheck.data.api.dto.RefreshRequest
import com.example.onecheck.data.api.dto.TokenResponse
import com.example.onecheck.data.api.payload
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitProvider {

    fun createApi(tokenStore: TokenStore): OneCheckApiService {
        val refreshRetrofit = createRetrofitBuilder().build()
        val authApi = refreshRetrofit.create(AuthApiService::class.java)

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor(tokenStore))
            .addInterceptor(loggingInterceptor())
            .authenticator { _, response ->
                if (response.code != 401) return@authenticator null
                val refreshToken = tokenStore.refreshToken ?: return@authenticator null

                val refreshResponse = authApi.refreshSync(RefreshRequest(refreshToken)).execute()
                if (!refreshResponse.isSuccessful) {
                    tokenStore.clear()
                    return@authenticator null
                }

                val tokenBody = refreshResponse.body()?.payload() ?: return@authenticator null
                val newAccess = tokenBody.resolveAccessToken() ?: return@authenticator null
                tokenStore.accessToken = newAccess
                tokenBody.resolveRefreshToken()?.let { newRefresh ->
                    tokenStore.refreshToken = newRefresh
                }

                response.request.newBuilder()
                    .header("Authorization", "Bearer $newAccess")
                    .build()
            }
            .build()

        return createRetrofitBuilder()
            .client(client)
            .build()
            .create(OneCheckApiService::class.java)
    }

    private fun createRetrofitBuilder(): Retrofit.Builder {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
    }

    private fun authInterceptor(tokenStore: TokenStore): Interceptor = Interceptor { chain ->
        val request = chain.request()
        val token = tokenStore.accessToken
        val path = request.url.encodedPath
        val isPublicAuth = path.contains("/auth/login") ||
            path.contains("/auth/mfa/verify") ||
            path.contains("/auth/refresh")

        val newRequest = if (!token.isNullOrBlank() && !isPublicAuth) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        chain.proceed(newRequest)
    }

    private fun loggingInterceptor(): HttpLoggingInterceptor {
        val logging = HttpLoggingInterceptor()
        logging.level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.BASIC
        }
        return logging
    }
}

fun LoginResponse.resolveMfaRequired(): Boolean = mfaRequired == true

fun LoginResponse.resolveMfaToken(): String? = tempToken ?: mfaToken

fun LoginResponse.needsMfaStep(): Boolean {
    val hasMfaToken = !resolveMfaToken().isNullOrBlank()
    return resolveMfaRequired() || (hasMfaToken && resolveAccessToken().isNullOrBlank())
}

fun LoginResponse.resolveAccessToken(): String? = accessToken

fun LoginResponse.resolveRefreshToken(): String? = refreshToken

fun TokenResponse.resolveAccessToken(): String? = accessToken

fun TokenResponse.resolveRefreshToken(): String? = refreshToken
