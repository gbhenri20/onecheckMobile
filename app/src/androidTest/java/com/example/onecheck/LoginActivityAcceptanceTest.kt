package com.example.onecheck

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Testes de aceitação (UI) da tela de login.
 * Validam que o fluxo inicial está acessível ao vistoriador.
 */
@RunWith(AndroidJUnit4::class)
class LoginActivityAcceptanceTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(LoginActivity::class.java)

    @Test
    fun telaLogin_exibeCamposEbotaoEntrar() {
        onView(withId(R.id.txt_login_email)).check(matches(isDisplayed()))
        onView(withId(R.id.txt_login_senha)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_login_entrar)).check(matches(isDisplayed()))
        onView(withId(R.id.btn_login_entrar)).check(matches(isEnabled()))
    }

    @Test
    fun telaLogin_botaoEntrarPermaneceClicavelComCamposVazios() {
        onView(withId(R.id.btn_login_entrar)).perform(click())
        onView(withId(R.id.btn_login_entrar)).check(matches(isEnabled()))
    }
}
