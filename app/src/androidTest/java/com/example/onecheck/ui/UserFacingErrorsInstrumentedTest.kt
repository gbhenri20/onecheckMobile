package com.example.onecheck.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.onecheck.R
import com.example.onecheck.data.api.ApiException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.net.UnknownHostException

/** Testes de mensagens amigáveis exibidas ao vistoriador. */
@RunWith(AndroidJUnit4::class)
class UserFacingErrorsInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun userMessage_semInternet_retornaErroDeRede() {
        val msg = UnknownHostException("host").userMessage(context, ErrorScreen.AGENDA)
        assertEquals(context.getString(R.string.error_network), msg)
    }

    @Test
    fun userMessage_login401_retornaCredenciaisInvalidas() {
        val msg = ApiException("Unauthorized", 401).userMessage(context, ErrorScreen.LOGIN)
        assertEquals(context.getString(R.string.error_login_invalid), msg)
    }

    @Test
    fun userMessage_sessaoExpirada_foraDoLogin() {
        val msg = ApiException("Unauthorized", 401).userMessage(context, ErrorScreen.CHECKLIST)
        assertEquals(context.getString(R.string.error_session), msg)
    }

    @Test
    fun userMessage_mfaInvalido_naTelaMfa() {
        val msg = ApiException("MFA inválido", 401).userMessage(context, ErrorScreen.MFA)
        assertEquals(context.getString(R.string.error_mfa_invalid), msg)
    }
}
