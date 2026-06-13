package com.example.onecheck

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.onecheck.model.InspectionStatus
import com.example.onecheck.model.InspectionType
import com.example.onecheck.testutil.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Testes de UI lógica da agenda (seções pendentes/enviados) no dispositivo/emulador. */
@RunWith(AndroidJUnit4::class)
class AgendaListBuilderInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun build_criaSecoesPendentesEEnviados() {
        val inspections = listOf(
            TestFixtures.inspection(id = "p1", status = InspectionStatus.SCHEDULED),
            TestFixtures.inspection(id = "p2", status = InspectionStatus.IN_PROGRESS),
            TestFixtures.inspection(id = "e1", status = InspectionStatus.SUBMITTED),
            TestFixtures.inspection(id = "e2", status = InspectionStatus.ACCEPTED),
        )

        val rows = AgendaListBuilder.build(context, inspections)
        val headers = rows.filterIsInstance<AgendaRow.SectionHeader>()

        assertEquals(2, headers.size)
        assertTrue(headers[0].title.contains("Pendentes"))
        assertTrue(headers[1].title.contains("Enviados"))
    }

    @Test
    fun build_pendentesSaoAbrivels_enviadosNao() {
        val inspections = listOf(
            TestFixtures.inspection(id = "p1", status = InspectionStatus.SCHEDULED),
            TestFixtures.inspection(id = "e1", status = InspectionStatus.ACCEPTED),
        )

        val rows = AgendaListBuilder.build(context, inspections)
        val pendentes = rows.filterIsInstance<AgendaRow.InspectionItem>()
            .filter { it.inspection.status == InspectionStatus.SCHEDULED }
        val enviados = rows.filterIsInstance<AgendaRow.InspectionItem>()
            .filter { it.inspection.status == InspectionStatus.ACCEPTED }

        assertTrue(pendentes.all { it.openable })
        assertFalse(enviados.all { it.openable })
    }

    @Test
    fun build_listaVazia_mantemSecoesComHint() {
        val rows = AgendaListBuilder.build(context, emptyList())
        assertTrue(rows.any { it is AgendaRow.SectionHeader })
    }
}
