package com.example.onecheck.data

import com.example.onecheck.model.ChecklistDraft
import com.example.onecheck.model.ItemPhoto
import com.example.onecheck.model.Condition
import com.example.onecheck.model.Inspection

data class LoginResult(
    val mfaRequired: Boolean,
    val mfaToken: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

data class VerifyMfaResult(
    val accessToken: String,
    val refreshToken: String,
)

data class PhotoUploadResult(
    val photo: ItemPhoto,
    val itemId: String,
)

interface OneCheckRepository {
    suspend fun login(email: String, password: String): LoginResult
    suspend fun verifyMfa(mfaToken: String, totp: String): VerifyMfaResult

    suspend fun listScheduledInspections(forceRefresh: Boolean = false): List<Inspection>
    suspend fun loadInspection(summary: Inspection): LoadedInspection

    /** Recupera resumo da vistoria quando a sessão em memória foi perdida (ex.: após câmera). */
    suspend fun findInspectionSummary(checklistOrAgendamentoId: String): Inspection

    fun clearCaches()

    /** @return ID real do item no servidor (pode diferir do [itemId] quando era pendente). */
    suspend fun saveChecklistItem(
        checklistId: String,
        itemId: String,
        condition: Condition?,
        note: String,
    ): String

    suspend fun uploadItemPhoto(
        checklistId: String,
        itemId: String,
        imageBytes: ByteArray,
        fileName: String = "foto.jpg",
        condition: Condition? = null,
        note: String = "",
        mimeType: String = "image/jpeg",
    ): PhotoUploadResult

    suspend fun deleteItemPhoto(
        checklistId: String,
        itemId: String,
        photoId: String,
    )

    /**
     * Salva itens preenchidos no servidor e substitui IDs pendentes pelos IDs reais.
     * @return rascunho atualizado e quantidade de itens gravados no servidor nesta chamada.
     */
    suspend fun syncDraftToServer(checklistId: String, draft: ChecklistDraft): Pair<ChecklistDraft, Int>

    suspend fun submitChecklist(checklistId: String, draft: ChecklistDraft)

    suspend fun logout()
}
