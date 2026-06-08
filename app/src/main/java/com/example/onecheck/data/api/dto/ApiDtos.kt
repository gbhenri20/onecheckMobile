package com.example.onecheck.data.api.dto

import com.google.gson.annotations.SerializedName

data class ApiEnvelope<T>(
    @SerializedName("data") val data: T? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("detail") val detail: String? = null,
)

/** Formato oficial: `{ "sucesso": true, "dados": { ... } }` */
data class ApiResult<T>(
    @SerializedName("sucesso") val sucesso: Boolean? = null,
    @SerializedName("success") val success: Boolean? = null,
    @SerializedName("data") val data: T? = null,
    @SerializedName("dados") val dados: T? = null,
    @SerializedName("erro") val erro: String? = null,
    @SerializedName("erros") val erros: Map<String, Any>? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("detail") val detail: String? = null,
    @SerializedName("paginacao") val paginacao: PaginacaoDto? = null,
)

data class PaginacaoDto(
    @SerializedName("total") val total: Int? = null,
    @SerializedName("pagina") val pagina: Int? = null,
    @SerializedName("itensPorPagina") val itensPorPagina: Int? = null,
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("senha") val senha: String,
)

data class LoginResponse(
    @SerializedName("mfa_required") val mfaRequired: Boolean? = null,
    @SerializedName("temp_token") val tempToken: String? = null,
    @SerializedName("mfa_token") val mfaToken: String? = null,
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
    @SerializedName("usuario") val usuario: UsuarioDto? = null,
)

data class UsuarioDto(
    @SerializedName("id") val id: Any? = null,
    @SerializedName("nome") val nome: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("role") val role: String? = null,
)

data class MfaVerifyRequest(
    @SerializedName("temp_token") val tempToken: String,
    @SerializedName("codigo") val codigo: String,
)

data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String,
)

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String? = null,
    @SerializedName("refresh_token") val refreshToken: String? = null,
)

data class AgendamentoDto(
    @SerializedName("id") val id: Any? = null,
    @SerializedName("contrato_id") val contratoId: Any? = null,
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName("data_agendada") val dataAgendada: String? = null,
    @SerializedName("observacao") val observacao: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
)

data class ContratoDto(
    @SerializedName("id") val id: Any? = null,
    @SerializedName("imovel_id") val imovelId: Any? = null,
    @SerializedName("locatario_id") val locatarioId: Any? = null,
    @SerializedName("status") val status: String? = null,
)

data class ImovelDto(
    @SerializedName("id") val id: Any? = null,
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName("tamanho") val tamanho: String? = null,
    @SerializedName("garagem") val garagem: Boolean? = null,
    @SerializedName("status") val status: String? = null,
)

data class EnderecoDto(
    @SerializedName("rua") val rua: String? = null,
    @SerializedName("logradouro") val logradouro: String? = null,
    @SerializedName("numero") val numero: String? = null,
    @SerializedName("complemento") val complemento: String? = null,
    @SerializedName("bairro") val bairro: String? = null,
    @SerializedName("cidade") val cidade: String? = null,
    @SerializedName("estado") val estado: String? = null,
    @SerializedName("cep") val cep: String? = null,
)

data class ComodoDto(
    @SerializedName("id") val id: Any? = null,
    @SerializedName("imovel_id") val imovelId: Any? = null,
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName("descricao") val descricao: String? = null,
    @SerializedName("nome") val nome: String? = null,
)

data class ItemVistoriaDto(
    @SerializedName("id") val id: Any? = null,
    @SerializedName("nome") val nome: String? = null,
    @SerializedName("categoria") val categoria: String? = null,
)

/** Checklist resumido (lista por contrato). */
data class ChecklistDto(
    @SerializedName("id") val id: Any? = null,
    @SerializedName("contrato_id") val contratoId: Any? = null,
    @SerializedName("vistoriador_id") val vistoriadorId: Any? = null,
    @SerializedName("tipo") val tipo: String? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("data_vistoria") val dataVistoria: String? = null,
    @SerializedName("itens") val itens: List<ChecklistItemDto>? = null,
    @SerializedName("comodos") val comodos: List<ComodoDto>? = null,
)

data class ChecklistItemDto(
    @SerializedName("id") val id: Any? = null,
    @SerializedName("checklist_id") val checklistId: Any? = null,
    @SerializedName("comodo_id") val comodoId: Any? = null,
    @SerializedName("item_vistoria_id") val itemVistoriaId: Any? = null,
    @SerializedName("estado") val estado: String? = null,
    @SerializedName("observacao") val observacao: String? = null,
    @SerializedName("fotos") val fotos: List<FotoDto>? = null,
)

data class FotoDto(
    @SerializedName("id") val id: Any? = null,
    @SerializedName("url") val url: String? = null,
)

data class FotoUploadResponse(
    @SerializedName("id") val id: Any? = null,
    @SerializedName("url") val url: String? = null,
)

data class ItemUpdateRequest(
    @SerializedName("estado") val estado: String? = null,
    @SerializedName("observacao") val observacao: String? = null,
)

data class AddChecklistItemRequest(
    @SerializedName("comodo_id") val comodoId: String,
    @SerializedName("item_vistoria_id") val itemVistoriaId: String,
    @SerializedName("estado") val estado: String,
    @SerializedName("observacao") val observacao: String? = null,
)
