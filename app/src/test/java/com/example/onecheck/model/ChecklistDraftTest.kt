package com.example.onecheck.model

import com.example.onecheck.testutil.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testes unitários da lógica de rascunho do checklist (sem Android). */
class ChecklistDraftTest {

    @Test
    fun updateItem_alteraEstadoENotaDoItemCorreto() {
        val draft = TestFixtures.draft()
        val updated = draft.updateItem("sala", "pending:sala:item-1", Condition.GOOD, "Riscos leves")

        val item = updated.rooms.first().items.first()
        assertEquals(Condition.GOOD, item.condition)
        assertEquals("Riscos leves", item.note)
    }

    @Test
    fun updateItemPreservingCondition_mantemEstadoQuandoNovoEhNulo() {
        val draft = TestFixtures.draftWithCondition(Condition.REGULAR, note = "Antes")
        val updated = draft.updateItemPreservingCondition(
            roomId = "sala",
            itemId = "pending:sala:item-1",
            condition = null,
            note = "Depois",
        )

        val item = updated.rooms.first().items.first()
        assertEquals(Condition.REGULAR, item.condition)
        assertEquals("Depois", item.note)
    }

    @Test
    fun replaceItemId_substituiIdPendentePeloIdReal() {
        val draft = TestFixtures.draft()
        val updated = draft.replaceItemId("sala", "pending:sala:item-1", "item-real-99")

        assertEquals("item-real-99", updated.rooms.first().items.first().id)
    }

    @Test
    fun mergeWithLocal_preservaPreenchimentoLocalAposRecargaDaApi() {
        val apiDraft = TestFixtures.draft()
        val localDraft = apiDraft
            .updateItem("sala", "pending:sala:item-1", Condition.BAD, "Quebrado")
            .setItemPhotos(
                "sala",
                "pending:sala:item-1",
                listOf(ItemPhoto(id = "local-1", localUri = "file:///tmp/foto.jpg")),
            )

        val merged = apiDraft.mergeWithLocal(localDraft)
        val item = merged.rooms.first().items.first()

        assertEquals(Condition.BAD, item.condition)
        assertEquals("Quebrado", item.note)
        assertEquals(1, item.photos.size)
    }

    @Test
    fun mergeWithLocal_incluiComodosNovosDaApi() {
        val apiDraft = TestFixtures.draft().copy(
            rooms = TestFixtures.draft().rooms +
                RoomDraft(id = "banheiro", name = "Banheiro", items = emptyList()),
        )
        val localDraft = TestFixtures.draft()
            .updateItem("sala", "pending:sala:item-1", Condition.GOOD, "Ok")

        val merged = apiDraft.mergeWithLocal(localDraft)

        assertEquals(3, merged.rooms.size)
        assertEquals(Condition.GOOD, merged.rooms.first { it.id == "sala" }.items.first().condition)
    }

    @Test
    fun nextRoomAfter_retornaProximoComodo() {
        val draft = TestFixtures.draft()
        assertEquals("quarto", draft.nextRoomAfter("sala")?.id)
    }

    @Test
    fun nextRoomAfter_ultimoComodoRetornaNulo() {
        val draft = TestFixtures.draft()
        assertNull(draft.nextRoomAfter("quarto"))
    }

    @Test
    fun removePhoto_removeApenasFotoInformada() {
        val photos = listOf(
            ItemPhoto(id = "f1"),
            ItemPhoto(id = "f2"),
        )
        val draft = TestFixtures.draftWithCondition(photos = photos)
        val updated = draft.removePhoto("sala", "pending:sala:item-1", "f1")

        assertEquals(listOf(ItemPhoto(id = "f2")), updated.rooms.first().items.first().photos)
    }

    @Test
    fun fromInspection_criaDraftComTodosComodos() {
        val inspection = Inspection(
            id = "c1",
            type = InspectionType.INITIAL,
            status = InspectionStatus.SCHEDULED,
            scheduledAtIso = "2026-01-01T09:00:00",
            property = TestFixtures.property(),
            rooms = listOf(
                Room(
                    id = "cozinha",
                    name = "Cozinha",
                    items = listOf(
                        ChecklistItem(id = "i1", label = "Pia", required = true),
                    ),
                ),
            ),
        )

        val draft = ChecklistDraft.fromInspection(inspection)
        assertEquals("c1", draft.inspectionId)
        assertEquals(1, draft.rooms.size)
        assertEquals("Pia", draft.rooms.first().items.first().label)
    }

    @Test
    fun isConcluidaNoServidor_trueParaSubmetidoEAceito() {
        assertTrue(InspectionStatus.SUBMITTED.isConcluidaNoServidor())
        assertTrue(InspectionStatus.ACCEPTED.isConcluidaNoServidor())
    }
}
