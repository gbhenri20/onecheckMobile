package com.example.onecheck.data.api

import com.example.onecheck.data.LoadedInspection
import com.example.onecheck.data.LoginResult
import com.example.onecheck.data.OneCheckRepository
import com.example.onecheck.data.PhotoUploadResult
import com.example.onecheck.data.VerifyMfaResult
import com.example.onecheck.data.api.dto.AddChecklistItemRequest
import com.example.onecheck.data.api.dto.AgendamentoDto
import com.example.onecheck.data.api.dto.ChecklistDto
import com.example.onecheck.data.api.dto.ComodoDto
import com.example.onecheck.data.api.dto.ContratoDto
import com.example.onecheck.data.api.dto.ItemUpdateRequest
import com.example.onecheck.data.api.dto.ItemVistoriaDto
import com.example.onecheck.data.api.dto.LoginRequest
import com.example.onecheck.data.api.dto.LoginResponse
import com.example.onecheck.data.api.dto.MfaVerifyRequest
import com.example.onecheck.model.ChecklistDraft
import com.example.onecheck.model.Condition
import com.example.onecheck.model.Inspection
import com.example.onecheck.model.ItemPhoto
import com.example.onecheck.model.InspectionType
import com.example.onecheck.model.isConcluidaNoServidor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class ApiOneCheckRepository(
    private val api: OneCheckApiService,
    private val tokenStore: TokenStore,
) : OneCheckRepository {

    private var itensVistoriaCache: Map<String, ItemVistoriaDto>? = null

    override fun clearCaches() {
        itensVistoriaCache = null
    }

    override suspend fun login(email: String, password: String): LoginResult {
        val response = api.login(LoginRequest(email = email, senha = password))
        val body = response.unwrapApiResult()
        val result = handleAuthResponse(body)
        if (!result.mfaRequired) {
            syncCurrentUserId(body)
        }
        return result
    }

    override suspend fun verifyMfa(mfaToken: String, totp: String): VerifyMfaResult {
        val response = api.verifyMfa(
            MfaVerifyRequest(tempToken = mfaToken, codigo = totp),
        )
        val body = response.unwrapApiResult()
        val access = body.resolveAccessToken()
            ?: throw ApiException("Token não retornado após MFA")
        val refresh = body.resolveRefreshToken()
        tokenStore.accessToken = access
        tokenStore.refreshToken = refresh
        tokenStore.mfaToken = null
        syncCurrentUserId(body)
        return VerifyMfaResult(accessToken = access, refreshToken = refresh ?: "")
    }

    private suspend fun syncCurrentUserId(loginBody: LoginResponse) {
        val fromLogin = loginBody.usuario?.id?.asApiId()?.takeIf { it.isNotBlank() }
        if (fromLogin != null) {
            tokenStore.currentUserId = fromLogin
            return
        }
        runCatching {
            api.getCurrentUser().unwrapApiResult().id?.asApiId()?.takeIf { it.isNotBlank() }
        }.getOrNull()?.let { tokenStore.currentUserId = it }
    }

    private fun handleAuthResponse(body: LoginResponse): LoginResult {
        if (body.needsMfaStep()) {
            val mfa = body.resolveMfaToken()
                ?: throw ApiException("Confirme o código MFA no app autenticador")
            tokenStore.mfaToken = mfa
            return LoginResult(mfaRequired = true, mfaToken = mfa)
        }

        val access = body.resolveAccessToken()
            ?: throw ApiException("Token de acesso não retornado")
        val refresh = body.resolveRefreshToken()
        tokenStore.accessToken = access
        tokenStore.refreshToken = refresh
        tokenStore.mfaToken = null
        return LoginResult(
            mfaRequired = false,
            accessToken = access,
            refreshToken = refresh,
        )
    }

    override suspend fun listScheduledInspections(forceRefresh: Boolean): List<Inspection> {
        if (forceRefresh) clearCaches()

        val contratos = loadContratos()
        if (contratos.isEmpty()) return emptyList()

        val vistoriadorId = tokenStore.currentUserId
        val result = mutableListOf<Inspection>()

        for (contrato in contratos) {
            val contratoId = contrato.id.asApiId()
            if (contratoId.isBlank()) continue

            val property = loadPropertyForContrato(contrato)
            val checklists = runCatching {
                api.listChecklistsByContrato(contratoId).unwrapApiResult()
            }.getOrDefault(emptyList())
            val agendamentos = loadAgendamentosDoContrato(contratoId)

            agendamentos.forEach { ag ->
                val tipo = ag.tipo.toInspectionType()
                if (tipo == InspectionType.FINAL) return@forEach
                val checklist = checklists.resolveForTipo(tipo, vistoriadorId)
                    ?.let { refreshChecklistStatus(it, forceRefresh) }
                val checklistId = checklist?.resolvedId()
                result.add(
                    ag.toInspectionSummary(contrato, property).copy(
                        checklistId = checklistId,
                        contratoId = contratoId,
                        status = checklist?.status.toInspectionStatus(),
                    ),
                )
            }

            checklists.forEach { checklistDto ->
                if (checklistDto.tipo.toInspectionType() == InspectionType.FINAL) return@forEach
                val checklistId = checklistDto.resolvedId()
                if (checklistId.isBlank()) return@forEach
                if (result.any { it.checklistId == checklistId }) return@forEach
                val refreshed = refreshChecklistStatus(checklistDto, forceRefresh)
                result.add(
                    refreshed.toStandaloneInspection(contratoId, property),
                )
            }
        }
        return result
            .filter { it.type == InspectionType.INITIAL }
            .distinctBy { "${it.contratoId}_${it.agendamentoId ?: it.id}_${it.type}" }
    }

    override suspend fun loadInspection(summary: Inspection): LoadedInspection {
        val contratoId = summary.contratoId?.takeIf { it.isNotBlank() }
            ?: throw ApiException("Agendamento sem contrato vinculado", 404)

        val tipo = summary.type
        val checklists = api.listChecklistsByContrato(contratoId).unwrapApiResult()
        val checklistFromList = checklists.resolveForTipo(tipo, tokenStore.currentUserId)

        val checklistId = summary.checklistId?.takeIf { it.isNotBlank() }
            ?: checklistFromList?.resolvedId()
            ?: resolveChecklistIdFromList(checklists, tipo)
            ?: throw ApiException(
                "Checklist de ${tipo.toApiTipo()} não encontrado. Peça ao admin criar o checklist de encerramento no contrato.",
                404,
            )

        val checklistDto = loadChecklistDto(contratoId, checklistId, tipo, checklistFromList)

        val imovelId = loadContratos()
            .firstOrNull { it.id.asApiId() == contratoId }
            ?.imovelId?.asApiId()

        val comodosById = if (!imovelId.isNullOrBlank()) {
            loadComodos(imovelId).associateBy { it.id.asApiId() }
        } else {
            emptyMap()
        }

        val itensVistoriaById = loadItensVistoria().associateBy { it.id.asApiId() }
        val inspection = checklistDto.toInspection(summary, comodosById, itensVistoriaById)

        if (inspection.rooms.isEmpty()) {
            throw ApiException(
                "Sem itens no checklist e sem cômodos no imóvel. Cadastre cômodos ou itens no checklist.",
                404,
            )
        }

        val fullInspection = inspection.copy(
            id = checklistId,
            checklistId = checklistId,
            agendamentoId = summary.agendamentoId ?: summary.id,
            contratoId = contratoId,
            type = tipo,
        )

        val draft = checklistDto.toDraft(comodosById, itensVistoriaById).let { apiDraft ->
            if (apiDraft.rooms.any { it.items.isNotEmpty() }) apiDraft
            else ChecklistDraft.fromInspection(fullInspection)
        }

        return LoadedInspection(fullInspection, draft)
    }

    override suspend fun findInspectionSummary(checklistOrAgendamentoId: String): Inspection {
        listScheduledInspections(false).forEach { inspection ->
            if (inspection.checklistId == checklistOrAgendamentoId ||
                inspection.id == checklistOrAgendamentoId ||
                inspection.agendamentoId == checklistOrAgendamentoId
            ) {
                return inspection
            }
        }
        return buildSummaryFromChecklist(checklistOrAgendamentoId)
    }

    private suspend fun buildSummaryFromChecklist(checklistId: String): Inspection {
        val dto = api.getChecklist(checklistId).unwrapApiResult()
        val contratoId = dto.contratoId.asApiId()
        val contrato = loadContratos().firstOrNull { it.id.asApiId() == contratoId }
        val property = contrato?.let { loadPropertyForContrato(it) }
            ?: com.example.onecheck.model.PropertySummary(
                id = contrato?.imovelId?.asApiId() ?: checklistId,
                title = "Imóvel",
                addressLine = "—",
            )
        return Inspection(
            id = checklistId,
            type = dto.tipo.toInspectionType(),
            status = dto.status.toInspectionStatus(),
            scheduledAtIso = dto.dataVistoria.orEmpty(),
            property = property,
            rooms = emptyList(),
            checklistId = checklistId,
            agendamentoId = null,
            contratoId = contratoId.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun saveChecklistItem(
        checklistId: String,
        itemId: String,
        condition: Condition?,
        note: String,
    ): String {
        val estado = condition?.toApiValue()
            ?: return itemId

        val pending = itemId.parsePendingItem()
        if (pending != null) {
            val (comodoId, itemVistoriaId) = pending
            val existingId = findChecklistItemId(checklistId, comodoId, itemVistoriaId)
            if (existingId != null) {
                updateExistingItem(checklistId, existingId, estado, note)
                return existingId
            }
            return createChecklistItem(
                checklistId = checklistId,
                comodoId = comodoId,
                itemVistoriaId = itemVistoriaId,
                estado = estado,
                note = note,
            )
        }

        updateExistingItem(checklistId, itemId, estado, note)
        return itemId
    }

    override suspend fun uploadItemPhoto(
        checklistId: String,
        itemId: String,
        imageBytes: ByteArray,
        fileName: String,
        condition: Condition?,
        note: String,
        mimeType: String,
    ): PhotoUploadResult {
        val realItemId = resolveItemIdForPhoto(checklistId, itemId, condition, note)

        val mediaType = mimeType.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()
        val body = imageBytes.toRequestBody(mediaType)
        val part = MultipartBody.Part.createFormData("foto", fileName, body)

        val response = api.uploadItemPhoto(checklistId, realItemId, part)
        if (!response.isSuccessful) {
            val code = response.code()
            val msg = response.apiErrorMessage()
            if (code == 403) {
                throw ApiException(
                    "Não foi possível enviar a foto. Confirme que você é o vistoriador deste checklist.",
                    403,
                )
            }
            if (code == 404) {
                throw ApiException(
                    "Item do checklist não encontrado. Aguarde o salvamento e tente novamente.",
                    404,
                )
            }
            throw ApiException(msg, code)
        }
        val data = runCatching { response.unwrapApiResult() }.getOrElse {
            val parsed = response.parseApiResultFromRaw()
            if (parsed != null && !parsed.isFailureFlag()) {
                parsed.payload() ?: throw ApiException(parsed.errorMessage(), response.code())
            } else {
                throw ApiException(response.rawBodyErrorMessage(), response.code())
            }
        }
        val photo = ItemPhoto(
            id = data.id.asApiId().takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
            remoteUrl = data.url,
        )
        return PhotoUploadResult(photo = photo, itemId = realItemId)
    }

    override suspend fun deleteItemPhoto(checklistId: String, itemId: String, photoId: String) {
        val response = api.deleteItemPhoto(checklistId, itemId, photoId)
        if (!response.isSuccessful) {
            throw ApiException(response.apiErrorMessage(), response.code())
        }
        response.body()?.let { result ->
            if (result.isFailureFlag()) throw ApiException(result.errorMessage(), response.code())
        }
    }

    private suspend fun resolveItemIdForPhoto(
        checklistId: String,
        itemId: String,
        condition: Condition?,
        note: String,
    ): String {
        if (itemId.startsWith("pending:")) {
            val estadoParaApi = condition ?: Condition.GOOD
            return saveChecklistItem(checklistId, itemId, estadoParaApi, note)
        }
        if (condition != null) {
            return saveChecklistItem(checklistId, itemId, condition, note)
        }
        return itemId
    }

    override suspend fun syncDraftToServer(
        checklistId: String,
        draft: ChecklistDraft,
    ): Pair<ChecklistDraft, Int> {
        var updated = draft
        var savedCount = 0
        var lastError: ApiException? = null

        draft.rooms.forEach { room ->
            room.items.forEach { item ->
                val condition = item.condition ?: return@forEach
                try {
                    val resolvedId = saveChecklistItem(
                        checklistId = checklistId,
                        itemId = item.id,
                        condition = condition,
                        note = item.note,
                    )
                    savedCount++
                    if (resolvedId != item.id) {
                        updated = updated.replaceItemId(room.id, item.id, resolvedId)
                    }
                } catch (e: ApiException) {
                    lastError = e
                }
            }
        }

        val serverItems = runCatching {
            api.getChecklist(checklistId).unwrapApiResult().itens.orEmpty()
        }.getOrDefault(emptyList())

        if (savedCount == 0) {
            throw lastError ?: ApiException(
                "Nenhum item foi salvo no servidor. Volte aos cômodos, escolha o estado de cada item e aguarde alguns segundos antes de enviar.",
                422,
            )
        }

        if (serverItems.isEmpty()) {
            throw lastError ?: ApiException(
                "O servidor ainda não registrou os itens do checklist. Aguarde alguns segundos e tente enviar de novo.",
                422,
            )
        }

        return updated to savedCount
    }

    override suspend fun submitChecklist(checklistId: String, draft: ChecklistDraft) {
        val localFilled = draft.rooms.sumOf { room ->
            room.items.count { it.condition != null }
        }
        if (localFilled == 0) {
            throw ApiException(
                "Preencha ao menos um item do checklist antes de enviar.",
                422,
            )
        }

        syncDraftToServer(checklistId, draft)

        val response = api.submitChecklist(checklistId)
        if (!response.isSuccessful) {
            throw ApiException(response.apiErrorMessage(), response.code())
        }
        val result = response.body() ?: response.parseApiResultFromRaw()
            ?: throw ApiException(response.rawBodyErrorMessage(), response.code())
        if (result.isFailureFlag()) {
            throw ApiException(result.errorMessage(), response.code())
        }
    }

    override suspend fun logout() {
        runCatching { api.logout() }
        tokenStore.clear()
    }

    private suspend fun loadChecklistDto(
        contratoId: String,
        checklistId: String,
        tipo: InspectionType,
        fromList: ChecklistDto?,
    ): ChecklistDto {
        val detail = runCatching {
            api.getChecklist(checklistId).unwrapApiResult()
        }.getOrNull()

        when {
            detail != null && !detail.itens.isNullOrEmpty() -> return detail
            detail != null && fromList != null && !fromList.itens.isNullOrEmpty() ->
                return detail.copy(itens = fromList.itens)
            detail != null -> return detail
            fromList != null -> return fromList
            else -> {
                val list = api.listChecklistsByContrato(contratoId).unwrapApiResult()
                val fallback = list.resolveForTipo(tipo, tokenStore.currentUserId)
                    ?: list.firstOrNull { it.resolvedId() == checklistId }
                if (fallback != null) return fallback
                throw ApiException(
                    "Não foi possível carregar o checklist de ${tipo.toApiTipo()}.",
                    404,
                )
            }
        }
    }

    private fun resolveChecklistIdFromList(
        checklists: List<ChecklistDto>,
        type: InspectionType,
    ): String? = checklists.resolveForTipo(type, tokenStore.currentUserId)?.resolvedId()

    private suspend fun createChecklistItem(
        checklistId: String,
        comodoId: String,
        itemVistoriaId: String,
        estado: String,
        note: String,
    ): String {
        val response = api.addChecklistItem(
            checklistId = checklistId,
            body = AddChecklistItemRequest(
                comodoId = comodoId,
                itemVistoriaId = itemVistoriaId,
                estado = estado,
                observacao = note.ifBlank { null },
            ),
        )
        if (!response.isSuccessful) {
            val existingId = findChecklistItemId(checklistId, comodoId, itemVistoriaId)
            if (existingId != null) {
                updateExistingItem(checklistId, existingId, estado, note)
                return existingId
            }
            throw ApiException(response.apiErrorMessage(), response.code())
        }
        val result = response.body() ?: throw ApiException("Resposta vazia ao criar item", response.code())
        if (result.isFailureFlag()) {
            val existingId = findChecklistItemId(checklistId, comodoId, itemVistoriaId)
            if (existingId != null) {
                updateExistingItem(checklistId, existingId, estado, note)
                return existingId
            }
            throw ApiException(result.errorMessage(), response.code())
        }
        val created = result.payload()
        return created?.id?.asApiId()?.takeIf { it.isNotBlank() }
            ?: findChecklistItemId(checklistId, comodoId, itemVistoriaId)
            ?: throw ApiException("Item criado mas ID não retornado", response.code())
    }

    private suspend fun updateExistingItem(
        checklistId: String,
        itemId: String,
        estado: String,
        note: String,
    ) {
        val response = api.updateChecklistItem(
            checklistId = checklistId,
            itemId = itemId,
            body = ItemUpdateRequest(
                estado = estado,
                observacao = note,
            ),
        )
        if (!response.isSuccessful) {
            throw ApiException(response.apiErrorMessage(), response.code())
        }
        response.body()?.let { result ->
            if (result.isFailureFlag()) throw ApiException(result.errorMessage(), response.code())
        }
    }

    private suspend fun findChecklistItemId(
        checklistId: String,
        comodoId: String,
        itemVistoriaId: String,
    ): String? {
        val checklist = runCatching { api.getChecklist(checklistId).unwrapApiResult() }.getOrNull()
            ?: return null
        return checklist.itens.orEmpty().firstOrNull {
            it.comodoId.asApiId() == comodoId && it.itemVistoriaId.asApiId() == itemVistoriaId
        }?.id?.asApiId()?.takeIf { it.isNotBlank() }
    }

    private suspend fun refreshChecklistStatus(
        checklist: ChecklistDto,
        forceRefresh: Boolean,
    ): ChecklistDto {
        val checklistId = checklist.resolvedId()
        if (checklistId.isBlank()) return checklist
        if (!forceRefresh && checklist.status.toInspectionStatus().isConcluidaNoServidor()) {
            return checklist
        }
        return runCatching {
            api.getChecklist(checklistId).unwrapApiResult()
        }.getOrNull()?.let { detail ->
            checklist.copy(
                status = detail.status ?: checklist.status,
                itens = detail.itens ?: checklist.itens,
                dataVistoria = detail.dataVistoria ?: checklist.dataVistoria,
            )
        } ?: checklist
    }

    private suspend fun loadContratos(): List<ContratoDto> {
        val response = api.listContratos(status = "ativo", porPagina = 100)
        return response.unwrapApiResult()
    }

    private suspend fun loadAgendamentosDoContrato(contratoId: String): List<AgendamentoDto> {
        val response = api.listAgendamentosByContrato(contratoId)
        return response.unwrapApiResult()
    }

    private suspend fun loadComodos(imovelId: String): List<ComodoDto> {
        val response = api.listComodos(imovelId)
        return response.unwrapApiResult()
    }

    private suspend fun loadItensVistoria(): List<ItemVistoriaDto> {
        itensVistoriaCache?.let { return it.values.toList() }
        val response = api.listItensVistoria()
        val list = response.unwrapApiResult()
        itensVistoriaCache = list.associateBy { it.id.asApiId() }
        return list
    }

    private suspend fun loadPropertyForContrato(contrato: ContratoDto): com.example.onecheck.model.PropertySummary {
        val imovelId = contrato.imovelId?.asApiId().orEmpty()
        if (imovelId.isBlank()) {
            return com.example.onecheck.model.PropertySummary(
                id = contrato.id.asApiId(),
                title = "Imóvel",
                addressLine = "—",
            )
        }

        val imovel = runCatching { api.getImovel(imovelId).unwrapApiResult() }.getOrNull()
        val endereco = runCatching { api.getEndereco(imovelId).unwrapApiResult() }.getOrNull()

        return com.example.onecheck.model.PropertySummary(
            id = imovelId,
            title = imovel?.displayTitle() ?: "Imóvel",
            addressLine = endereco?.formatLine()?.ifBlank { "—" } ?: "—",
        )
    }
}

class ApiException(
    message: String,
    val httpCode: Int? = null,
) : Exception(message)
