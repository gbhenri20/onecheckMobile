package com.example.onecheck.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.onecheck.model.SubmissionLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Testes instrumentados de persistência local do log de envios. */
@RunWith(AndroidJUnit4::class)
class SubmissionLogStoreInstrumentedTest {

    private lateinit var store: SubmissionLogStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        store = SubmissionLogStore(context)
        store.clear()
    }

    @Test
    fun appendChecklist_persisteEntradaOrdenadaPorData() {
        store.appendChecklist("c1", "Imóvel A", success = true, message = "OK")
        store.appendPhoto("c1", "Imóvel A", success = false, message = "Falha foto")

        val entries = store.list()
        assertEquals(2, entries.size)
        assertTrue(entries[0].timestampMs >= entries[1].timestampMs)
    }

    @Test
    fun clear_removeTodoHistorico() {
        store.appendChecklist("c1", "Imóvel A", success = true, message = "OK")
        store.clear()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun append_registraTipoChecklistEFoto() {
        store.appendChecklist("c1", "Imóvel A", success = true, message = "Enviado")
        store.appendPhoto("c1", "Imóvel A", success = true, message = "Foto ok")

        val types = store.list().map { it.type }.toSet()
        assertEquals(
            setOf(SubmissionLogEntry.LogType.CHECKLIST, SubmissionLogEntry.LogType.PHOTO),
            types,
        )
    }
}
