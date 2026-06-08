package com.example.onecheck.data.api

import com.example.onecheck.data.api.dto.AgendamentoDto
import com.example.onecheck.data.api.dto.ChecklistDto
import com.example.onecheck.data.api.dto.ChecklistItemDto
import com.example.onecheck.data.api.dto.ComodoDto
import com.example.onecheck.data.api.dto.ContratoDto
import com.example.onecheck.data.api.dto.EnderecoDto
import com.example.onecheck.data.api.dto.ImovelDto
import com.example.onecheck.data.api.dto.ItemVistoriaDto
import com.example.onecheck.model.ChecklistDraft
import com.example.onecheck.model.ChecklistItem
import com.example.onecheck.model.Condition
import com.example.onecheck.model.Inspection
import com.example.onecheck.model.InspectionStatus
import com.example.onecheck.model.InspectionType
import com.example.onecheck.model.ItemDraft
import com.example.onecheck.model.ItemPhoto
import com.example.onecheck.model.PropertySummary
import com.example.onecheck.model.Room
import com.example.onecheck.model.RoomDraft

fun Condition.toApiValue(): String? = when (this) {
    Condition.GREAT -> "otimo"
    Condition.GOOD -> "bom"
    Condition.REGULAR -> "regular"
    Condition.BAD -> "ruim"
}

fun String?.toCondition(): Condition? = when (this?.lowercase()) {
    "otimo", "ótimo" -> Condition.GREAT
    "bom" -> Condition.GOOD
    "regular" -> Condition.REGULAR
    "ruim" -> Condition.BAD
    else -> null
}

fun InspectionType.toApiTipo(): String = when (this) {
    InspectionType.INITIAL -> "inicial"
    InspectionType.FINAL -> "encerramento"
}

fun String?.toInspectionType(): InspectionType = when (this?.lowercase()) {
    "encerramento", "final" -> InspectionType.FINAL
    else -> InspectionType.INITIAL
}

fun String?.toInspectionStatus(): InspectionStatus = when (this?.lowercase()?.trim()) {
    "aceito", "aceita", "aprovado", "aprovada", "concluido", "concluída", "concluida",
    "finalizado", "finalizada" -> InspectionStatus.ACCEPTED
    "submetido", "submetida", "enviado", "enviada", "pendente_aceite",
    "pendente_revisao", "rejeitado", "rejeitada" -> InspectionStatus.SUBMITTED
    "em_preenchimento" -> InspectionStatus.IN_PROGRESS
    else -> InspectionStatus.SCHEDULED
}

fun EnderecoDto.formatLine(): String {
    val ruaNome = rua ?: logradouro
    return listOfNotNull(ruaNome, numero, bairro, cidade, estado)
        .filter { it.isNotBlank() }
        .joinToString(", ")
}

fun ImovelDto.displayTitle(): String {
    val parts = listOfNotNull(tipo, tamanho?.let { "($it)" })
    return parts.joinToString(" ").ifBlank { "Imóvel" }
}

fun AgendamentoDto.toInspectionSummary(
    contrato: ContratoDto? = null,
    property: PropertySummary? = null,
): Inspection {
    val agendamentoId = id.asApiId()
    val contratoId = contratoId?.asApiId()?.takeIf { it.isNotBlank() }
        ?: contrato?.id?.asApiId()
    val prop = property ?: PropertySummary(
        id = contrato?.imovelId?.asApiId() ?: agendamentoId,
        title = "Imóvel",
        addressLine = "—",
    )
    return Inspection(
        id = agendamentoId.ifBlank { "sem-id" },
        type = tipo.toInspectionType(),
        status = InspectionStatus.SCHEDULED,
        scheduledAtIso = dataAgendada.orEmpty(),
        property = prop,
        rooms = emptyList(),
        checklistId = null,
        agendamentoId = agendamentoId.takeIf { it.isNotBlank() },
        contratoId = contratoId,
    )
}

fun ChecklistDto.resolvedId(): String = id.asApiId()

fun ChecklistDto.toStandaloneInspection(
    contratoId: String,
    property: PropertySummary,
): Inspection = Inspection(
    id = resolvedId().ifBlank { "checklist-sem-id" },
    type = tipo.toInspectionType(),
    status = status.toInspectionStatus(),
    scheduledAtIso = dataVistoria.orEmpty(),
    property = property,
    rooms = emptyList(),
    checklistId = resolvedId().takeIf { it.isNotBlank() },
    agendamentoId = null,
    contratoId = contratoId,
)

fun ChecklistDto.matchesTipo(inspectionType: InspectionType): Boolean {
    val t = tipo?.lowercase()?.trim() ?: return false
    return when (inspectionType) {
        InspectionType.INITIAL ->
            t == "inicial" || t == "entrada" || t.contains("inicial")
        InspectionType.FINAL ->
            t == "encerramento" || t == "final" || t.contains("encer") || t.contains("final")
    }
}

fun List<ChecklistDto>.resolveForTipo(
    tipo: InspectionType,
    vistoriadorId: String? = null,
): ChecklistDto? {
    val matching = filter { it.matchesTipo(tipo) }
    if (matching.isEmpty()) return null
    if (!vistoriadorId.isNullOrBlank()) {
        matching.firstOrNull { it.vistoriadorId.asApiId() == vistoriadorId }?.let { return it }
    }
    return matching.firstOrNull()
}

fun mergeRoomsWithImovelComodos(
    rooms: List<Room>,
    allComodos: List<ComodoDto>,
    itensVistoriaById: Map<String, ItemVistoriaDto>,
): List<Room> {
    if (allComodos.isEmpty()) return rooms
    val roomsById = rooms.associateBy { it.id }
    return allComodos.map { comodo ->
        val comodoId = comodo.id.asApiId().ifBlank { "comodo" }
        roomsById[comodoId] ?: roomFromComodo(comodo, itensVistoriaById)
    }
}

fun ChecklistDto.toInspection(
    summary: Inspection,
    comodosById: Map<String, ComodoDto>,
    itensVistoriaById: Map<String, ItemVistoriaDto>,
): Inspection {
    val checklistId = resolvedId()
    val allComodos = comodosById.values.toList().ifEmpty { comodos.orEmpty() }
    val roomsFromItens = buildRoomsFromItens(itens, comodosById, itensVistoriaById)
    val rooms = mergeRoomsWithImovelComodos(
        roomsFromItens.ifEmpty { buildRoomsFromComodos(allComodos, itensVistoriaById) },
        allComodos,
        itensVistoriaById,
    )

    val status = status.toInspectionStatus().let { mapped ->
        if (mapped == InspectionStatus.SCHEDULED) summary.status else mapped
    }

    return summary.copy(
        id = checklistId.ifBlank { summary.id },
        checklistId = checklistId,
        type = tipo.toInspectionType(),
        status = status,
        scheduledAtIso = dataVistoria ?: summary.scheduledAtIso,
        rooms = rooms,
    )
}

fun buildRoomsFromComodos(
    comodos: List<ComodoDto>?,
    itensVistoriaById: Map<String, ItemVistoriaDto> = emptyMap(),
): List<Room> = comodos.orEmpty().map { roomFromComodo(it, itensVistoriaById) }

private fun roomFromComodo(
    comodo: ComodoDto,
    itensVistoriaById: Map<String, ItemVistoriaDto>,
): Room {
    val comodoId = comodo.id.asApiId().ifBlank { "comodo" }
    val catalog = itensVistoriaById.values.toList()
    val items = if (catalog.isEmpty()) {
        emptyList()
    } else {
        catalog.mapIndexed { index, catalogItem ->
            val itemVistoriaId = catalogItem.id.asApiId()
            ChecklistItem(
                id = pendingItemId(comodoId, itemVistoriaId),
                label = catalogItem.nome ?: "Item de vistoria",
                required = index == 0,
            )
        }
    }
    return Room(
        id = comodoId,
        name = comodo.tipo ?: comodo.nome ?: comodo.descricao ?: "Cômodo",
        items = items,
    )
}

fun pendingItemId(comodoId: String, itemVistoriaId: String): String =
    "pending:$comodoId:$itemVistoriaId"

fun String.parsePendingItem(): Pair<String, String>? {
    if (!startsWith("pending:")) return null
    val rest = removePrefix("pending:")
    val splitAt = rest.lastIndexOf(':')
    if (splitAt <= 0 || splitAt >= rest.length - 1) return null
    return rest.substring(0, splitAt) to rest.substring(splitAt + 1)
}

private fun buildRoomsFromItens(
    itens: List<ChecklistItemDto>?,
    comodosById: Map<String, ComodoDto>,
    itensVistoriaById: Map<String, ItemVistoriaDto>,
): List<Room> {
    if (itens.isNullOrEmpty()) return emptyList()

    return itens
        .groupBy { it.comodoId.asApiId().ifBlank { "sem-comodo" } }
        .map { (comodoId, roomItems) ->
            val comodo = comodosById[comodoId]
            val roomName = comodo?.tipo ?: comodo?.descricao ?: comodo?.nome ?: "Cômodo"
            Room(
                id = comodoId,
                name = roomName,
                items = roomItems.map { item ->
                    val itemVistoriaId = item.itemVistoriaId.asApiId()
                    val label = itensVistoriaById[itemVistoriaId]?.nome ?: "Item de vistoria"
                    ChecklistItem(
                        id = item.id.asApiId().ifBlank {
                            pendingItemId(comodoId, itemVistoriaId)
                        },
                        label = label,
                        required = true,
                        condition = item.estado.toCondition(),
                        note = item.observacao.orEmpty(),
                        photos = item.fotos.orEmpty().map { foto ->
                            ItemPhoto(
                                id = foto.id.asApiId(),
                                remoteUrl = foto.url,
                            )
                        },
                    )
                },
            )
        }
}

fun ChecklistDto.toDraft(
    comodosById: Map<String, ComodoDto>,
    itensVistoriaById: Map<String, ItemVistoriaDto>,
): ChecklistDraft {
    val checklistId = resolvedId()
    val allComodos = comodosById.values.toList().ifEmpty { comodos.orEmpty() }
    val roomsFromItens = buildRoomsFromItens(itens, comodosById, itensVistoriaById)
    val rooms = mergeRoomsWithImovelComodos(
        roomsFromItens.ifEmpty { buildRoomsFromComodos(allComodos, itensVistoriaById) },
        allComodos,
        itensVistoriaById,
    )

    return ChecklistDraft(
        inspectionId = checklistId,
        rooms = rooms.map { room ->
            val itemsInRoom = itens.orEmpty().filter {
                it.comodoId.asApiId() == room.id
            }
            RoomDraft(
                id = room.id,
                name = room.name,
                items = room.items.map { checklistItem ->
                    val apiItem = itemsInRoom.find { it.id.asApiId() == checklistItem.id }
                        ?: checklistItem.id.parsePendingItem()?.let { (comodoId, itemVistoriaId) ->
                            itemsInRoom.find {
                                it.comodoId.asApiId() == comodoId &&
                                    it.itemVistoriaId.asApiId() == itemVistoriaId
                            }
                        }
                    ItemDraft(
                        id = checklistItem.id,
                        label = checklistItem.label,
                        required = checklistItem.required,
                        condition = apiItem?.estado.toCondition(),
                        note = apiItem?.observacao.orEmpty(),
                        photos = apiItem?.fotos.orEmpty().map { foto ->
                            ItemPhoto(
                                id = foto.id.asApiId(),
                                remoteUrl = foto.url,
                            )
                        } ?: emptyList(),
                    )
                },
            )
        },
    )
}

fun Inspection.mergeWithLoaded(loaded: Inspection): Inspection = loaded.copy(
    scheduledAtIso = if (scheduledAtIso.isNotBlank()) scheduledAtIso else loaded.scheduledAtIso,
    property = if (property.title != "Imóvel") property else loaded.property,
    agendamentoId = agendamentoId ?: loaded.agendamentoId,
    contratoId = contratoId ?: loaded.contratoId,
)
