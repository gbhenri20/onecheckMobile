package com.example.onecheck.data.api

import com.example.onecheck.data.api.dto.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testes unitários do parser de respostas e erros da API. */
class ApiResponseParserTest {

    @Test
    fun payload_prefereDataDepoisDados() {
        val withData = ApiResult<String>(sucesso = true, data = "via-data")
        val withDados = ApiResult<String>(sucesso = true, dados = "via-dados")
        assertEquals("via-data", withData.payload())
        assertEquals("via-dados", withDados.payload())
    }

    @Test
    fun isSuccessFlag_reconheceSucessoOuSuccess() {
        assertTrue(ApiResult<Any>(sucesso = true).isSuccessFlag())
        assertTrue(ApiResult<Any>(success = true).isSuccessFlag())
        assertFalse(ApiResult<Any>(sucesso = false).isSuccessFlag())
    }

    @Test
    fun errorMessage_priorizaErroMessageDetail() {
        assertEquals("Falha X", ApiResult<Any>(erro = "Falha X").errorMessage())
        assertEquals("Msg Y", ApiResult<Any>(message = "Msg Y").errorMessage())
        assertEquals("Det Z", ApiResult<Any>(detail = "Det Z").errorMessage())
    }

    @Test
    fun extractJsonFragment_recortaJsonEmRespostaHtml() {
        val raw = "<html>erro {\"sucesso\":false,\"erro\":\"Credenciais inválidas\"}</html>"
        val json = extractJsonFragment(raw)
        assertTrue(json.startsWith("{\"sucesso\""))
        assertTrue(json.endsWith("}"))
    }

    @Test
    fun parseApiErrorJson_lêErroDoEnvelope() {
        val raw = "{\"sucesso\":false,\"erro\":\"Senha incorreta\"}"
        assertEquals("Senha incorreta", parseApiErrorJson(raw))
    }
}
