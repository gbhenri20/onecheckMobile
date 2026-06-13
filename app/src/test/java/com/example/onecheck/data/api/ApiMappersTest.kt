package com.example.onecheck.data.api

import com.example.onecheck.data.api.dto.ChecklistDto
import com.example.onecheck.model.Condition
import com.example.onecheck.model.InspectionStatus
import com.example.onecheck.model.InspectionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Testes unitários de mapeamento entre API e domínio. */
class ApiMappersTest {

    @Test
    fun toCondition_mapeiaValoresPtBr() {
        assertEquals(Condition.GREAT, "otimo".toCondition())
        assertEquals(Condition.GREAT, "ótimo".toCondition())
        assertEquals(Condition.GOOD, "bom".toCondition())
        assertEquals(Condition.REGULAR, "regular".toCondition())
        assertEquals(Condition.BAD, "ruim".toCondition())
        assertNull("desconhecido".toCondition())
    }

    @Test
    fun toInspectionStatus_diferenciaAceitoDeSubmetido() {
        assertEquals(InspectionStatus.ACCEPTED, "aceito".toInspectionStatus())
        assertEquals(InspectionStatus.ACCEPTED, "concluida".toInspectionStatus())
        assertEquals(InspectionStatus.SUBMITTED, "submetido".toInspectionStatus())
        assertEquals(InspectionStatus.SUBMITTED, "enviado".toInspectionStatus())
        assertEquals(InspectionStatus.IN_PROGRESS, "em_preenchimento".toInspectionStatus())
        assertEquals(InspectionStatus.SCHEDULED, "outro".toInspectionStatus())
    }

    @Test
    fun toInspectionType_mapeiaInicialEEncerramento() {
        assertEquals(InspectionType.INITIAL, "inicial".toInspectionType())
        assertEquals(InspectionType.FINAL, "encerramento".toInspectionType())
        assertEquals(InspectionType.FINAL, "final".toInspectionType())
    }

    @Test
    fun matchesTipo_reconheceVariacoesDeInicial() {
        val dto = ChecklistDto(tipo = "entrada")
        assertTrue(dto.matchesTipo(InspectionType.INITIAL))
        assertFalse(dto.matchesTipo(InspectionType.FINAL))
    }

    @Test
    fun resolveForTipo_prefereChecklistDoVistoriador() {
        val list = listOf(
            ChecklistDto(id = 1, tipo = "inicial", vistoriadorId = "v1"),
            ChecklistDto(id = 2, tipo = "inicial", vistoriadorId = "v2"),
        )
        assertEquals("2", list.resolveForTipo(InspectionType.INITIAL, "v2")?.id?.asApiId())
    }

    @Test
    fun pendingItemId_usaFormatoEstavel() {
        assertEquals("pending:sala:item-7", pendingItemId("sala", "item-7"))
    }

    @Test
    fun parsePendingItem_extraiComodoEItem() {
        assertEquals("sala" to "item-7", "pending:sala:item-7".parsePendingItem())
        assertNull("item-7".parsePendingItem())
    }

    @Test
    fun asApiId_normalizaNumeros() {
        assertEquals("42", 42.asApiId())
        assertEquals("42", 42.0.asApiId())
        assertEquals("", null.asApiId())
    }

    @Test
    fun conditionToApiValue_retornaChavesEsperadas() {
        assertEquals("otimo", Condition.GREAT.toApiValue())
        assertEquals("bom", Condition.GOOD.toApiValue())
        assertEquals("regular", Condition.REGULAR.toApiValue())
        assertEquals("ruim", Condition.BAD.toApiValue())
    }
}
