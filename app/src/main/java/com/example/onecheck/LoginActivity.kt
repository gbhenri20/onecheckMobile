package com.example.onecheck

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.onecheck.data.OneCheckSession
import com.example.onecheck.databinding.ActivityLoginBinding
import com.example.onecheck.ui.ErrorScreen
import com.example.onecheck.ui.showError
import com.example.onecheck.ui.showMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var loginInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnLoginEntrar.setOnClickListener { fazerLogin() }
    }

    private fun fazerLogin() {
        if (loginInProgress) return

        val email = binding.txtLoginEmail.text.toString().trim()
        val senha = binding.txtLoginSenha.text.toString()

        if (email.isEmpty() || senha.isEmpty()) {
            showMessage(getString(R.string.error_login_fields))
            return
        }

        loginInProgress = true
        binding.btnLoginEntrar.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    OneCheckSession.repository.login(email, senha)
                }
                withContext(NonCancellable) {
                    if (result.mfaRequired) {
                        OneCheckSession.mfaToken = result.mfaToken
                        startActivity(
                            Intent(this@LoginActivity, MfaActivity::class.java).apply {
                                putExtra(MfaActivity.EXTRA_EMAIL, email)
                            },
                        )
                    } else {
                        OneCheckSession.accessToken = result.accessToken
                        OneCheckSession.refreshToken = result.refreshToken
                        startActivity(Intent(this@LoginActivity, AgendaActivity::class.java))
                        finish()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e, ErrorScreen.LOGIN)
            } finally {
                if (!isFinishing) {
                    loginInProgress = false
                    binding.btnLoginEntrar.isEnabled = true
                }
            }
        }
    }
}
