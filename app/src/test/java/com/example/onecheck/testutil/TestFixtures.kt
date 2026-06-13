package com.example.onecheck.testutil

import com.example.onecheck.model.ChecklistDraft
import com.example.onecheck.model.Condition
import com.example.onecheck.model.Inspection
import com.example.onecheck.model.InspectionStatus
import com.example.onecheck.model.InspectionType
import com.example.onecheck.model.ItemDraft
import com.example.onecheck.model.ItemPhoto
import com.example.onecheck.model.PropertySummary
import com.example.onecheck.model.RoomDraft

object TestFixtures {

    fun property(title: String = "Apartamento Centro") = PropertySummary(
        id = "imovel-1",
        title = title,
        addressLine = "Rua A, 100",
    )

    fun inspection(
        id: String = "checklist-1",
        status: InspectionStatus = InspectionStatus.SCHEDULED,
        type: InspectionType = InspectionType.INITIAL,
        checklistId: String? = id,
    ) = Inspection(
        id = id,
        type = type,
        status = status,
        scheduledAtIso = "2026-06-01T10:00:00",
        property = property(),
        rooms = emptyList(),
        checklistId = checklistId,
        agendamentoId = "ag-1",
        contratoId = "contrato-1",
    )

    fun draft(
        inspectionId: String = "checklist-1",
        roomId: String = "sala",
        itemId: String = "pending:sala:item-1",
    ): ChecklistDraft {
        val item = ItemDraft(
            id = itemId,
            label = "Piso",
            required = true,
            condition = null,
            note = "",
        )
        return ChecklistDraft(
            inspectionId = inspectionId,
            rooms = listOf(
                RoomDraft(id = roomId, name = "Sala", items = listOf(item)),
                RoomDraft(
                    id = "quarto",
                    name = "Quarto",
                    items = listOf(
                        item.copy(id = "pending:quarto:item-2", label = "Parede"),
                    ),
                ),
            ),
        )
    }

    fun draftWithCondition(
        condition: Condition = Condition.GOOD,
        note: String = "Ok",
        photos: List<ItemPhoto> = emptyList(),
    ): ChecklistDraft {
        val base = draft()
        val room = base.rooms.first()
        val item = room.items.first().copy(condition = condition, note = note, photos = photos)
        return base.copy(
            rooms = listOf(room.copy(items = listOf(item))) + base.rooms.drop(1),
        )
    }
}
