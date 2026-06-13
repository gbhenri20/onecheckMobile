package com.example.onecheck.data.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Testes de integração do repositório com API simulada (MockWebServer).
 * Validam contrato HTTP sem depender do servidor de produção.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class ApiOneCheckRepositoryIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ApiOneCheckRepository
    private lateinit var tokenStore: TokenStore

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val context: Context = ApplicationProvider.getApplicationContext()
        tokenStore = TokenStore(context)

        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        repository = ApiOneCheckRepository(
            retrofit.create(OneCheckApiService::class.java),
            tokenStore,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        tokenStore.clear()
    }

    @Test
    fun login_quandoMfaObrigatorio_retornaFlagEMfaToken() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {"sucesso":true,"dados":{"mfa_required":true,"temp_token":"temp-mfa-abc"}}
                    """.trimIndent(),
                ),
        )

        val result = repository.login("vistoriador@test.com", "senha123")

        assertTrue(result.mfaRequired)
        assertEquals("temp-mfa-abc", result.mfaToken)
        assertEquals("temp-mfa-abc", tokenStore.mfaToken)
        assertEquals("/api/v1/auth/login", server.takeRequest().path)
    }

    @Test
    fun login_quandoCredenciaisValidas_salvaTokens() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "sucesso": true,
                      "dados": {
                        "access_token": "access-xyz",
                        "refresh_token": "refresh-xyz",
                        "usuario": { "id": 10, "email": "vistoriador@test.com" }
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.login("vistoriador@test.com", "senha123")

        assertFalse(result.mfaRequired)
        assertEquals("access-xyz", result.accessToken)
        assertEquals("access-xyz", tokenStore.accessToken)
        assertEquals("refresh-xyz", tokenStore.refreshToken)
        assertEquals("10", tokenStore.currentUserId)
    }

    @Test
    fun login_quandoCredenciaisInvalidas_lancaApiException() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"sucesso":false,"erro":"Credenciais inválidas"}"""),
        )

        val error = runCatching {
            repository.login("errado@test.com", "123")
        }.exceptionOrNull()

        assertTrue(error is ApiException)
        assertEquals(401, (error as ApiException).httpCode)
    }
}
