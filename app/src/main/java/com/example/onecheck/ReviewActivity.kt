package com.example.onecheck

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.onecheck.data.OneCheckSession
import com.example.onecheck.databinding.ActivityReviewBinding
import com.example.onecheck.ui.ErrorScreen
import com.example.onecheck.ui.showError
import com.example.onecheck.ui.showMessage
import com.example.onecheck.ui.userMessage
import com.example.onecheck.databinding.ItemComodoResumoBinding
import com.example.onecheck.model.ChecklistDraft
import com.example.onecheck.model.Inspection
import com.example.onecheck.model.RoomDraft
import com.example.onecheck.model.isConcluidaNoServidor
import kotlinx.coroutines.launch

class ReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewBinding
    private lateinit var inspectionId: String
    private lateinit var inspection: Inspection
    private lateinit var draft: ChecklistDraft

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarReview)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        inspectionId = intent.getStringExtra(EXTRA_INSPECTION_ID) ?: run {
            finish()
            return
        }

        inspection = OneCheckSession.inspections[inspectionId] ?: run {
            showMessage(getString(R.string.error_agenda_open))
            finish()
            return
        }
        draft = OneCheckSession.drafts[inspectionId] ?: ChecklistDraft.fromInspection(inspection).also {
            OneCheckSession.drafts[inspectionId] = it
        }

        binding.txtReviewImovel.text = inspection.property.title
        atualizarPendencias()
        binding.listComodos.adapter = ComodoResumoAdapter(this, draft.rooms)

        binding.listComodos.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val room = draft.rooms[position]
            startActivity(
                Intent(this, RoomChecklistActivity::class.java).apply {
                    putExtra(RoomChecklistActivity.EXTRA_INSPECTION_ID, inspectionId)
                    putExtra(RoomChecklistActivity.EXTRA_ROOM_ID, room.id)
                },
            )
        }

        binding.btnReviewEnviar.setOnClickListener { enviarChecklist() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onResume() {
        super.onResume()
        draft = OneCheckSession.drafts[inspectionId] ?: draft
        atualizarPendencias()
        binding.listComodos.adapter = ComodoResumoAdapter(this, draft.rooms)
    }

    private fun atualizarPendencias() {
        val preenchidos = draft.rooms.sumOf { room ->
            room.items.count { it.condition != null }
        }
        val obrigatoriosPendentes = draft.rooms.sumOf { room ->
            room.items.count { it.required && it.condition == null }
        }
        binding.txtReviewPendencias.text = when {
            preenchidos == 0 -> getString(R.string.review_precisa_um_item)
            obrigatoriosPendentes > 0 -> getString(
                R.string.review_obrigatorios_pendentes,
                obrigatoriosPendentes,
            )
            else -> getString(R.string.review_pronto_enviar)
        }
        binding.btnReviewEnviar.isEnabled = preenchidos > 0
        binding.txtReviewPendencias.setTextColor(
            ContextCompat.getColor(
                this,
                if (preenchidos > 0) R.color.accent_green else R.color.error_red,
            ),
        )
    }

    private fun enviarChecklist() {
        if (inspection.status.isConcluidaNoServidor()) {
            showMessage(getString(R.string.error_checklist_already_done))
            return
        }
        val checklistId = inspection.checklistId?.takeIf { it.isNotBlank() } ?: inspectionId
        lifecycleScope.launch {
            binding.btnReviewEnviar.isEnabled = false
            try {
                showMessage(getString(R.string.review_enviando))
                val (synced, _) = OneCheckSession.repository.syncDraftToServer(checklistId, draft)
                draft = synced
                OneCheckSession.drafts[inspectionId] = synced
                OneCheckSession.repository.submitChecklist(checklistId, synced)
                OneCheckSession.submissionLogStore.appendChecklist(
                    checklistId = checklistId,
                    propertyTitle = inspection.property.title,
                    success = true,
                    message = getString(R.string.success_checklist_submit),
                )
                OneCheckSession.releaseInspectionLocal(inspectionId)
                showMessage(getString(R.string.success_checklist_submit))
                val agendaIntent = Intent(this@ReviewActivity, AgendaActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(AgendaActivity.EXTRA_REFRESH, true)
                }
                startActivity(agendaIntent)
                finishAffinity()
            } catch (e: Exception) {
                OneCheckSession.submissionLogStore.appendChecklist(
                    checklistId = checklistId,
                    propertyTitle = inspection.property.title,
                    success = false,
                    message = e.userMessage(this@ReviewActivity, ErrorScreen.SUBMIT),
                )
                showError(e, ErrorScreen.SUBMIT)
                atualizarPendencias()
            } finally {
                binding.btnReviewEnviar.isEnabled = draft.rooms.sumOf { r ->
                    r.items.count { it.condition != null }
                } > 0
            }
        }
    }

    private class ComodoResumoAdapter(
        private val context: Context,
        private val rooms: List<RoomDraft>,
    ) : BaseAdapter() {

        override fun getCount(): Int = rooms.size
        override fun getItem(position: Int): RoomDraft = rooms[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val rowBinding = if (convertView == null) {
                ItemComodoResumoBinding.inflate(LayoutInflater.from(context), parent, false)
            } else {
                ItemComodoResumoBinding.bind(convertView)
            }

            val room = rooms[position]
            val completed = room.items.count { it.condition != null }
            rowBinding.txtComodoNome.text = room.name
            rowBinding.txtComodoProgresso.text = context.getString(
                R.string.progresso_comodo,
                completed,
                room.items.size,
            )
            return rowBinding.root
        }
    }

    companion object {
        const val EXTRA_INSPECTION_ID = "extra_inspection_id"
    }
}
