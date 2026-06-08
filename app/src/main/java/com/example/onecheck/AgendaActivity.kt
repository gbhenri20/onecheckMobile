package com.example.onecheck

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.onecheck.data.OneCheckSession
import com.example.onecheck.databinding.ActivityAgendaBinding
import com.example.onecheck.model.Inspection
import com.example.onecheck.model.InspectionType
import com.example.onecheck.model.isConcluidaNoServidor
import com.example.onecheck.ui.ErrorScreen
import com.example.onecheck.ui.showError
import com.example.onecheck.ui.showMessage
import com.example.onecheck.ui.userMessage
import kotlinx.coroutines.launch

class AgendaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgendaBinding
    private var todasVistorias: List<Inspection> = emptyList()
    private var agendaRows: List<AgendaRow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgendaBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarAgenda)

        binding.listVistorias.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val row = agendaRows.getOrNull(position) as? AgendaRow.InspectionItem ?: return@OnItemClickListener
            if (row.openable) {
                abrirVistoria(row.inspection)
            } else {
                showMessage(getString(R.string.agenda_item_enviado_nao_abre))
            }
        }

        binding.swipeAgenda.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.primary_blue),
        )
        binding.swipeAgenda.setOnRefreshListener { carregarAgenda(forceRefresh = true) }

        carregarAgenda(
            forceRefresh = intent.getBooleanExtra(EXTRA_REFRESH, false),
            preserveDrafts = intent.getBooleanExtra(EXTRA_REFRESH, false),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_REFRESH, false)) {
            carregarAgenda(forceRefresh = true, preserveDrafts = true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!intent.getBooleanExtra(EXTRA_REFRESH, false) && !binding.swipeAgenda.isRefreshing) {
            carregarAgenda(forceRefresh = true, preserveDrafts = true)
        }
        intent.removeExtra(EXTRA_REFRESH)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_agenda, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.menu_atualizar -> {
                carregarAgenda(forceRefresh = true)
                true
            }
            R.id.menu_log_envios -> {
                startActivity(Intent(this, SubmissionLogActivity::class.java))
                true
            }
            R.id.menu_sair -> {
                lifecycleScope.launch {
                    runCatching { OneCheckSession.repository.logout() }
                    OneCheckSession.clear()
                    startActivity(Intent(this@AgendaActivity, LoginActivity::class.java))
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun carregarAgenda(forceRefresh: Boolean = false, preserveDrafts: Boolean = false) {
        lifecycleScope.launch {
            binding.swipeAgenda.isRefreshing = true
            binding.txtAgendaErro.visibility = View.GONE
            try {
                if (forceRefresh) {
                    OneCheckSession.repository.clearCaches()
                    if (!preserveDrafts) {
                        OneCheckSession.inspections.clear()
                        OneCheckSession.drafts.clear()
                    }
                }
                todasVistorias = OneCheckSession.repository.listScheduledInspections(forceRefresh)
                OneCheckSession.summaries.clear()
                todasVistorias.forEach { v ->
                    OneCheckSession.summaries[v.agendamentoId ?: v.id] = v
                    v.checklistId?.let { OneCheckSession.summaries[it] = v }
                }
                agendaRows = AgendaListBuilder.build(this@AgendaActivity, todasVistorias)
                val temItens = todasVistorias.isNotEmpty()
                binding.txtAgendaVazio.visibility = if (temItens) View.GONE else View.VISIBLE
                binding.listVistorias.visibility = if (temItens) View.VISIBLE else View.GONE
                binding.listVistorias.adapter = AgendaListAdapter(this@AgendaActivity, agendaRows)
            } catch (e: Exception) {
                todasVistorias = emptyList()
                agendaRows = emptyList()
                binding.listVistorias.visibility = View.GONE
                binding.txtAgendaVazio.visibility = View.GONE
                binding.txtAgendaErro.text = e.userMessage(this@AgendaActivity, ErrorScreen.AGENDA)
                binding.txtAgendaErro.visibility = View.VISIBLE
                showError(e, ErrorScreen.AGENDA)
            } finally {
                binding.swipeAgenda.isRefreshing = false
            }
        }
    }

    private fun abrirVistoria(inspection: Inspection) {
        if (inspection.checklistId.isNullOrBlank()) {
            val tipoLabel = when (inspection.type) {
                InspectionType.INITIAL -> getString(R.string.tipo_inicial)
                InspectionType.FINAL -> getString(R.string.tipo_encerramento)
            }
            showMessage(getString(R.string.error_checklist_missing, tipoLabel))
            return
        }
        if (inspection.status.isConcluidaNoServidor()) {
            showMessage(getString(R.string.error_checklist_already_done))
            return
        }
        lifecycleScope.launch {
            try {
                val loaded = OneCheckSession.repository.loadInspection(inspection)
                val full = loaded.inspection
                val checklistId = full.checklistId?.takeIf { it.isNotBlank() } ?: full.id
                OneCheckSession.inspections[checklistId] = full
                inspection.agendamentoId?.let { OneCheckSession.inspections[it] = full }
                OneCheckSession.drafts[checklistId] = loaded.draft
                val draft = loaded.draft
                if (draft.rooms.isEmpty() || draft.rooms.all { it.items.isEmpty() }) {
                    showMessage(getString(R.string.error_checklist_load))
                    return@launch
                }
                val roomId = draft.rooms.firstOrNull { room ->
                    room.items.any { it.condition == null }
                }?.id ?: draft.rooms.first().id

                startActivity(
                    Intent(this@AgendaActivity, RoomChecklistActivity::class.java).apply {
                        putExtra(RoomChecklistActivity.EXTRA_INSPECTION_ID, checklistId)
                        putExtra(RoomChecklistActivity.EXTRA_ROOM_ID, roomId)
                    },
                )
            } catch (e: Exception) {
                showError(e, ErrorScreen.AGENDA_OPEN)
            }
        }
    }

    companion object {
        const val EXTRA_REFRESH = "extra_refresh_agenda"
    }
}
