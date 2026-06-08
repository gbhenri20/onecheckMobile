package com.example.onecheck.data.api

import com.example.onecheck.data.api.dto.ApiEnvelope
import com.example.onecheck.data.api.dto.ApiResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.Response

private val gson = Gson()

fun <T> ApiResult<T>.payload(): T? = data ?: dados

fun <T> ApiResult<T>.isSuccessFlag(): Boolean =
    sucesso == true || success == true

fun <T> ApiResult<T>.isFailureFlag(): Boolean =
    sucesso == false || success == false

fun <T> ApiResult<T>.errorMessage(): String {
    erro?.takeIf { it.isNotBlank() }?.let { return it }
    message?.takeIf { it.isNotBlank() }?.let { return it }
    detail?.takeIf { it.isNotBlank() }?.let { return it }
    erros?.takeIf { it.isNotEmpty() }?.let { map ->
        return map.entries.joinToString("\n") { (field, value) ->
            "$field: ${value.toString().trim('[', ']', '"')}"
        }
    }
    return "Erro na API"
}

fun <T> Response<ApiResult<T>>.unwrapApiResult(): T {
    if (!isSuccessful) throw ApiException(apiErrorMessage(), code())
    val body = body() ?: throw ApiException("Resposta vazia", code())
    if (body.isFailureFlag()) throw ApiException(body.errorMessage(), code())
    return body.payload() ?: throw ApiException(
        body.errorMessage().ifBlank { "Resposta sem dados" },
        code(),
    )
}

fun <T> Response<T>.unwrapDirect(): T {
    if (!isSuccessful) throw ApiException(apiErrorMessage(), code())
    return body() ?: throw ApiException("Resposta vazia", code())
}

fun <T> Response<ApiResult<T>>.unwrapApiResultOrNull(): T? {
    if (!isSuccessful) return null
    val body = body() ?: return null
    if (body.isFailureFlag()) return null
    return body.payload()
}

/**
 * Tenta ler lista no formato `{ sucesso, data: [...] }`, depois `data` envelope e lista direta.
 */
suspend fun <T> fetchApiList(
    apiResult: suspend () -> Response<ApiResult<List<T>>>,
    envelope: suspend () -> Response<ApiEnvelope<List<T>>>,
    direct: suspend () -> Response<List<T>>,
): List<T> {
    val wrapped = apiResult()
    if (wrapped.isSuccessful) {
        wrapped.body()?.let { result ->
            if (result.isFailureFlag()) throw ApiException(result.errorMessage(), wrapped.code())
            result.payload()?.let { return it }
        }
    } else if (wrapped.code() != 404 && wrapped.code() != 405) {
        throw ApiException(wrapped.apiErrorMessage(), wrapped.code())
    }

    val env = envelope()
    if (env.isSuccessful) {
        env.body()?.data?.let { return it }
    } else if (env.code() != 404 && env.code() != 405) {
        throw ApiException(env.apiErrorMessage(), env.code())
    }

    val flat = direct()
    if (flat.isSuccessful) {
        return flat.body().orEmpty()
    }
    if (flat.code() != 404 && flat.code() != 405) {
        throw ApiException(flat.apiErrorMessage(), flat.code())
    }
    return emptyList()
}

suspend fun <T> fetchApiData(
    apiResult: suspend () -> Response<ApiResult<T>>,
    envelope: suspend () -> Response<ApiEnvelope<T>>,
    direct: suspend () -> Response<T>,
): T? {
    val wrapped = apiResult()
    if (wrapped.isSuccessful) {
        wrapped.body()?.let { result ->
            if (result.isFailureFlag()) throw ApiException(result.errorMessage(), wrapped.code())
            result.payload()?.let { return it }
        }
    } else if (wrapped.code() != 404 && wrapped.code() != 405) {
        throw ApiException(wrapped.apiErrorMessage(), wrapped.code())
    }

    val env = envelope()
    if (env.isSuccessful) {
        env.body()?.data?.let { return it }
    } else if (env.code() != 404 && env.code() != 405) {
        throw ApiException(env.apiErrorMessage(), env.code())
    }

    val flat = direct()
    if (flat.isSuccessful) {
        return flat.body()
    }
    if (flat.code() != 404 && flat.code() != 405) {
        throw ApiException(flat.apiErrorMessage(), flat.code())
    }
    return null
}

fun <T> Response<T>.apiErrorMessage(): String {
    val raw = errorBody()?.string().orEmpty()
    if (raw.isBlank()) return message().ifBlank { "Erro HTTP ${code()}" }
    return parseApiErrorJson(raw)
}

fun parseApiErrorJson(raw: String): String {
    return runCatching {
        val type = object : TypeToken<ApiResult<Any>>() {}.type
        val parsed: ApiResult<Any> = gson.fromJson(extractJsonFragment(raw), type)
        parsed.errorMessage()
    }.getOrElse {
        raw
    }
}

fun extractJsonFragment(raw: String): String {
    val markers = listOf("{\"sucesso\"", "{\"erro\"", "{\"success\"")
    val start = markers.map { raw.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return raw
    val end = raw.lastIndexOf('}')
    return if (end > start) raw.substring(start, end + 1) else raw
}

fun <T> Response<ApiResult<T>>.parseApiResultFromRaw(): ApiResult<T>? {
    val raw = errorBody()?.string()?.takeIf { it.isNotBlank() }
        ?: runCatching { raw().peekBody(512 * 1024).string() }.getOrNull()
        ?: return null
    return runCatching {
        val type = object : TypeToken<ApiResult<T>>() {}.type
        gson.fromJson<ApiResult<T>>(extractJsonFragment(raw), type)
    }.getOrNull()
}

fun Response<*>.rawBodyErrorMessage(): String {
    val raw = errorBody()?.string()?.takeIf { it.isNotBlank() }
        ?: runCatching { raw().peekBody(512 * 1024).string() }.getOrNull()
        .orEmpty()
    if (raw.isBlank()) return "Resposta de foto vazia"
    return parseApiErrorJson(raw)
}

fun Any?.asApiId(): String = when (this) {
    null -> ""
    is Number -> {
        val n = this.toDouble()
        if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()
    }
    else -> toString()
}
