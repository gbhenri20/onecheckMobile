package com.example.onecheck.data.api

import com.example.onecheck.data.api.dto.ApiResult
import com.example.onecheck.data.api.dto.RefreshRequest
import com.example.onecheck.data.api.dto.TokenResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApiService {
    @POST("api/v1/auth/refresh")
    fun refreshSync(@Body body: RefreshRequest): Call<ApiResult<TokenResponse>>
}
