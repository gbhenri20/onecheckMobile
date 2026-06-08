package com.example.onecheck.ui

import android.content.Context
import com.example.onecheck.R
import com.example.onecheck.data.api.ApiException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

enum class ErrorScreen {
    LOGIN,
    MFA,
    AGENDA,
    AGENDA_OPEN,
    CHECKLIST,
    PHOTO,
    SUBMIT,
    ITEM_SAVE,
}

fun Throwable.userMessage(context: Context, screen: ErrorScreen): String {
    if (this is CancellationException) {
        return context.getString(R.string.error_login_generic)
    }
    val raw = message.orEmpty()
    val lower = raw.lowercase()
    val httpCode = (this as? ApiException)?.httpCode

    if (lower.contains("job was cancelled") || lower.contains("job cancellation")) {
        return context.getString(R.string.error_login_generic)
    }

    if (this is ApiException && screen == ErrorScreen.SUBMIT) {
        if (raw.isNotBlank() && raw.length <= 280 && !raw.trimStart().startsWith("{")) {
            return raw
        }
    }

    if (isNetworkProblem()) {
        return context.getString(R.string.error_network)
    }
    if (this is SocketTimeoutException || lower.contains("timeout") || lower.contains("timed out")) {
        return context.getString(R.string.error_timeout)
    }
    if (httpCode != null && httpCode >= 500) {
        return context.getString(R.string.error_server)
    }
    if (httpCode == 403 || lower.contains("permiss") || lower.contains("forbidden")) {
        return context.getString(R.string.error_forbidden)
    }
    if (httpCode == 401 || matchesLoginFailure(lower)) {
        return when (screen) {
            ErrorScreen.LOGIN -> context.getString(R.string.error_login_invalid)
            ErrorScreen.MFA -> context.getString(R.string.error_mfa_invalid)
            else -> context.getString(R.string.error_session)
        }
    }
    if (lower.contains("checklist não encontrado") || lower.contains("checklist nao encontrado")) {
        return context.getString(R.string.error_agenda_open)
    }
    if (httpCode == 404 || lower.contains("não encontrado") || lower.contains("nao encontrado") || lower.contains("not found")) {
        return screenSpecificNotFound(context, screen)
    }
    if (screen == ErrorScreen.SUBMIT && (
            lower.contains("ao menos um item") ||
                lower.contains("nenhum item foi salvo") ||
                lower.contains("não registrou os itens") ||
                lower.contains("nao registrou os itens") ||
                lower.contains("já foi aceito") ||
                lower.contains("aceito")
            )
    ) {
        return if (raw.isNotBlank() && raw.length < 200) raw else context.getString(R.string.error_submit)
    }
    if (screen == ErrorScreen.PHOTO && lower.contains("item do checklist não encontrado")) {
        return raw
    }
    if (httpCode == 422 || lower.contains("obrigatório") || lower.contains("obrigatorio") || lower.contains("inválido")) {
        return if (raw.isNotBlank() && raw.length < 200 && screen != ErrorScreen.LOGIN) raw
        else context.getString(R.string.error_validation)
    }
    if (lower.contains("mfa") && (lower.contains("token") || lower.contains("expirad") || lower.contains("inválid"))) {
        return when (screen) {
            ErrorScreen.MFA -> context.getString(R.string.error_mfa_invalid)
            ErrorScreen.LOGIN -> context.getString(R.string.error_mfa_session)
            else -> context.getString(R.string.error_mfa_generic)
        }
    }
    if (lower.contains("token") && lower.contains("não retornado")) {
        return when (screen) {
            ErrorScreen.LOGIN -> context.getString(R.string.error_mfa_session)
            ErrorScreen.MFA -> context.getString(R.string.error_mfa_generic)
            else -> context.getString(R.string.error_session)
        }
    }
    if (lower.contains("credenciais")) {
        return context.getString(R.string.error_login_invalid)
    }
    if (lower.contains("photo_read_failed")) {
        return context.getString(R.string.error_photo_read)
    }
    if (screen == ErrorScreen.PHOTO && (
            lower.contains("armazenamento") ||
                lower.contains("storage") ||
                lower.contains("diretório") ||
                lower.contains("diretorio") ||
                lower.contains("mkdir")
            )
    ) {
        return context.getString(R.string.error_photo_storage)
    }
    if (lower.contains("selecione o estado") && screen == ErrorScreen.PHOTO) {
        return context.getString(R.string.error_photo_estado_first)
    }
    if (httpCode == 403 && screen == ErrorScreen.ITEM_SAVE) {
        return context.getString(R.string.error_checklist_save)
    }
    if (httpCode == 403 && screen == ErrorScreen.PHOTO) {
        return context.getString(R.string.error_photo_upload)
    }
    if (lower.contains("vistoriador deste checklist")) {
        return raw
    }

    return screenSpecificDefault(context, screen, raw)
}

private fun Throwable.isNetworkProblem(): Boolean =
    this is UnknownHostException ||
        this is ConnectException ||
        (this is IOException && message.orEmpty().lowercase().let {
            it.contains("unable to resolve") ||
                it.contains("failed to connect") ||
                it.contains("network is unreachable")
        })

private fun matchesLoginFailure(lower: String): Boolean =
    lower.contains("credenciais") ||
        lower.contains("unauthorized") ||
        lower.contains("invalid credentials") ||
        lower.contains("senha") && lower.contains("incorret")

private fun screenSpecificNotFound(context: Context, screen: ErrorScreen): String =
    when (screen) {
        ErrorScreen.AGENDA -> context.getString(R.string.error_agenda_load)
        ErrorScreen.AGENDA_OPEN -> context.getString(R.string.error_agenda_open)
        ErrorScreen.CHECKLIST -> context.getString(R.string.error_checklist_load)
        ErrorScreen.LOGIN,
        ErrorScreen.MFA,
        ErrorScreen.PHOTO,
        ErrorScreen.SUBMIT,
        ErrorScreen.ITEM_SAVE,
        -> context.getString(R.string.error_not_found)
    }

private fun screenSpecificDefault(context: Context, screen: ErrorScreen, raw: String): String {
    if (raw.isNotBlank() && raw.length < 120 && !raw.trimStart().startsWith("{")) {
        return raw
    }
    return when (screen) {
        ErrorScreen.LOGIN -> context.getString(R.string.error_login_generic)
        ErrorScreen.MFA -> context.getString(R.string.error_mfa_generic)
        ErrorScreen.AGENDA -> context.getString(R.string.error_agenda_load)
        ErrorScreen.AGENDA_OPEN -> context.getString(R.string.error_agenda_open)
        ErrorScreen.CHECKLIST -> context.getString(R.string.error_checklist_load)
        ErrorScreen.PHOTO -> context.getString(R.string.error_photo_upload)
        ErrorScreen.SUBMIT -> context.getString(R.string.error_submit)
        ErrorScreen.ITEM_SAVE -> context.getString(R.string.error_checklist_save)
    }
}
