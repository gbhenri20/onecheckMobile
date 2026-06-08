package com.example.onecheck

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.onecheck.data.OneCheckSession
import com.example.onecheck.databinding.ActivityMfaBinding
import com.example.onecheck.ui.ErrorScreen
import com.example.onecheck.ui.showError
import com.example.onecheck.ui.showMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MfaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMfaBinding
    private var verifyInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMfaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val email = intent.getStringExtra(EXTRA_EMAIL).orEmpty()
        binding.txtMfaEmail.text = email

        binding.btnMfaConfirmar.setOnClickListener { confirmarMfa() }
    }

    private fun confirmarMfa() {
        if (verifyInProgress) return

        val codigo = binding.txtMfaCodigo.text.toString().trim()
        val mfaToken = OneCheckSession.mfaToken

        if (codigo.length != 6 || mfaToken == null) {
            showMessage(getString(R.string.error_mfa_code_length))
            return
        }

        verifyInProgress = true
        binding.btnMfaConfirmar.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    OneCheckSession.repository.verifyMfa(mfaToken, codigo)
                }
                withContext(NonCancellable) {
                    OneCheckSession.accessToken = result.accessToken
                    OneCheckSession.refreshToken = result.refreshToken
                    OneCheckSession.mfaToken = null
                    startActivity(Intent(this@MfaActivity, AgendaActivity::class.java))
                    finish()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e, ErrorScreen.MFA)
            } finally {
                if (!isFinishing) {
                    verifyInProgress = false
                    binding.btnMfaConfirmar.isEnabled = true
                }
            }
        }
    }

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }
}
