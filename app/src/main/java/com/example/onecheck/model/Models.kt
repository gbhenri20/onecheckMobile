package com.example.onecheck.model

data class Inspection(
    /** ID usado nas rotas de checklist (salvar item, foto, enviar). */
    val id: String,
    val type: InspectionType,
    val status: InspectionStatus,
    val scheduledAtIso: String,
    val property: PropertySummary,
    val rooms: List<Room>,
    val checklistId: String? = null,
    val agendamentoId: String? = null,
    val contratoId: String? = null,
)

data class PropertySummary(
    val id: String,
    val title: String,
    val addressLine: String,
)

data class Room(
    val id: String,
    val name: String,
    val items: List<ChecklistItem>,
)

data class ChecklistItem(
    val id: String,
    val label: String,
    val required: Boolean,
    val condition: Condition? = null,
    val note: String = "",
    val photos: List<ItemPhoto> = emptyList(),
)

enum class InspectionType { INITIAL, FINAL }
enum class InspectionStatus { SCHEDULED, IN_PROGRESS, SUBMITTED, ACCEPTED }

fun InspectionStatus.isConcluidaNoServidor(): Boolean =
    this == InspectionStatus.SUBMITTED || this == InspectionStatus.ACCEPTED

enum class Condition { GREAT, GOOD, REGULAR, BAD }
