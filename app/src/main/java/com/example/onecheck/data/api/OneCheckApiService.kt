package com.example.onecheck.data.api

import com.example.onecheck.data.api.dto.AgendamentoDto
import com.example.onecheck.data.api.dto.ApiEnvelope
import com.example.onecheck.data.api.dto.ApiResult
import com.example.onecheck.data.api.dto.ChecklistDto
import com.example.onecheck.data.api.dto.ChecklistItemDto
import com.example.onecheck.data.api.dto.ComodoDto
import com.example.onecheck.data.api.dto.ContratoDto
import com.example.onecheck.data.api.dto.EnderecoDto
import com.example.onecheck.data.api.dto.FotoUploadResponse
import com.example.onecheck.data.api.dto.ImovelDto
import com.example.onecheck.data.api.dto.AddChecklistItemRequest
import com.example.onecheck.data.api.dto.ItemUpdateRequest
import com.example.onecheck.data.api.dto.ItemVistoriaDto
import com.example.onecheck.data.api.dto.LoginRequest
import com.example.onecheck.data.api.dto.LoginResponse
import com.example.onecheck.data.api.dto.MfaVerifyRequest
import com.example.onecheck.data.api.dto.RefreshRequest
import com.example.onecheck.data.api.dto.TokenResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface OneCheckApiService {

    @GET("api/v1/health")
    suspend fun health(): Response<ResponseBody>

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<ApiResult<LoginResponse>>

    @POST("api/v1/auth/mfa/verify")
    suspend fun verifyMfa(@Body body: MfaVerifyRequest): Response<ApiResult<LoginResponse>>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): Response<ApiResult<TokenResponse>>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<ResponseBody>

    @GET("api/v1/usuarios/me")
    suspend fun getCurrentUser(): Response<ApiResult<com.example.onecheck.data.api.dto.UsuarioDto>>

    @GET("api/v1/contratos")
    suspend fun listContratos(
        @Query("pagina") pagina: Int? = 1,
        @Query("por_pagina") porPagina: Int? = 50,
        @Query("status") status: String? = "ativo",
    ): Response<ApiResult<List<ContratoDto>>>

    @GET("api/v1/contratos/{contratoId}/agendamentos")
    suspend fun listAgendamentosByContrato(
        @Path("contratoId") contratoId: String,
    ): Response<ApiResult<List<AgendamentoDto>>>

    @GET("api/v1/contratos/{contratoId}/checklists")
    suspend fun listChecklistsByContrato(
        @Path("contratoId") contratoId: String,
    ): Response<ApiResult<List<ChecklistDto>>>

    @GET("api/v1/checklists/{id}")
    suspend fun getChecklist(@Path("id") id: String): Response<ApiResult<ChecklistDto>>

    @GET("api/v1/imoveis/{id}")
    suspend fun getImovel(@Path("id") id: String): Response<ApiResult<ImovelDto>>

    @GET("api/v1/imoveis/{id}/endereco")
    suspend fun getEndereco(@Path("id") id: String): Response<ApiResult<EnderecoDto>>

    @GET("api/v1/imoveis/{id}/comodos")
    suspend fun listComodos(@Path("id") id: String): Response<ApiResult<List<ComodoDto>>>

    @GET("api/v1/itens-vistoria")
    suspend fun listItensVistoria(): Response<ApiResult<List<ItemVistoriaDto>>>

    @POST("api/v1/checklists/{id}/itens")
    suspend fun addChecklistItem(
        @Path("id") checklistId: String,
        @Body body: AddChecklistItemRequest,
    ): Response<ApiResult<ChecklistItemDto>>

    @PUT("api/v1/checklists/{id}/itens/{itemId}")
    suspend fun updateChecklistItem(
        @Path("id") checklistId: String,
        @Path("itemId") itemId: String,
        @Body body: ItemUpdateRequest,
    ): Response<ApiResult<ChecklistItemDto>>

    @Multipart
    @POST("api/v1/checklists/{id}/itens/{itemId}/fotos")
    suspend fun uploadItemPhoto(
        @Path("id") checklistId: String,
        @Path("itemId") itemId: String,
        @Part foto: MultipartBody.Part,
    ): Response<ApiResult<FotoUploadResponse>>

    @DELETE("api/v1/checklists/{id}/itens/{itemId}/fotos/{fotoId}")
    suspend fun deleteItemPhoto(
        @Path("id") checklistId: String,
        @Path("itemId") itemId: String,
        @Path("fotoId") fotoId: String,
    ): Response<ApiResult<ResponseBody>>

    @PATCH("api/v1/checklists/{id}/submeter")
    suspend fun submitChecklist(@Path("id") id: String): Response<ApiResult<ResponseBody>>
}
