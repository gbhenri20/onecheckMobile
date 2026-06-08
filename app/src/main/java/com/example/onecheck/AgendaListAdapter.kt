package com.example.onecheck

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.example.onecheck.databinding.ItemAgendaSectionHeaderBinding
import com.example.onecheck.databinding.ItemVistoriaBinding
import com.example.onecheck.model.Inspection
import com.example.onecheck.model.InspectionStatus
import com.example.onecheck.model.InspectionType
import com.example.onecheck.model.isConcluidaNoServidor

sealed class AgendaRow {
    data class SectionHeader(val title: String, val isEmptyHint: Boolean = false) : AgendaRow()
    data class InspectionItem(val inspection: Inspection, val openable: Boolean) : AgendaRow()
}

object AgendaListBuilder {

    fun build(context: Context, all: List<Inspection>): List<AgendaRow> {
        val pendentes = all.filter { !it.status.isConcluidaNoServidor() }
        val enviados = all.filter { it.status.isConcluidaNoServidor() }
        val rows = mutableListOf<AgendaRow>()
        rows.add(
            AgendaRow.SectionHeader(
                context.getString(R.string.agenda_secao_pendentes, pendentes.size),
            ),
        )
        if (pendentes.isEmpty()) {
            rows.add(
                AgendaRow.SectionHeader(
                    context.getString(R.string.agenda_secao_pendentes_vazio),
                    isEmptyHint = true,
                ),
            )
        } else {
            pendentes.forEach { rows.add(AgendaRow.InspectionItem(it, openable = true)) }
        }
        rows.add(
            AgendaRow.SectionHeader(
                context.getString(R.string.agenda_secao_enviados, enviados.size),
            ),
        )
        if (enviados.isEmpty()) {
            rows.add(
                AgendaRow.SectionHeader(
                    context.getString(R.string.agenda_secao_enviados_vazio),
                    isEmptyHint = true,
                ),
            )
        } else {
            enviados.forEach { rows.add(AgendaRow.InspectionItem(it, openable = false)) }
        }
        return rows
    }
}

class AgendaListAdapter(
    private val context: Context,
    private val rows: List<AgendaRow>,
) : BaseAdapter() {

    private companion object {
        const val VIEW_HEADER = 0
        const val VIEW_INSPECTION = 1
    }

    override fun getCount(): Int = rows.size
    override fun getItem(position: Int): AgendaRow = rows[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun getViewTypeCount(): Int = 2
    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is AgendaRow.SectionHeader -> VIEW_HEADER
        is AgendaRow.InspectionItem -> VIEW_INSPECTION
    }

    override fun isEnabled(position: Int): Boolean =
        (rows[position] as? AgendaRow.InspectionItem)?.openable == true

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val type = getItemViewType(position)
        val reuse = convertView?.takeIf { (it.tag as? Int) == type }
        return when (val row = rows[position]) {
            is AgendaRow.SectionHeader -> bindHeader(reuse, parent, row.title, row.isEmptyHint)
            is AgendaRow.InspectionItem -> bindInspection(reuse, parent, row.inspection, row.openable)
        }
    }

    private fun bindHeader(
        convertView: View?,
        parent: ViewGroup?,
        title: String,
        isEmptyHint: Boolean,
    ): View {
        val binding = if (convertView == null) {
            ItemAgendaSectionHeaderBinding.inflate(LayoutInflater.from(context), parent, false)
        } else {
            ItemAgendaSectionHeaderBinding.bind(convertView)
        }
        binding.root.tag = VIEW_HEADER
        binding.txtSectionTitle.text = title
        val isSubtitle = isEmptyHint
        binding.txtSectionTitle.setTextColor(
            ContextCompat.getColor(
                context,
                if (isSubtitle) R.color.text_secondary else R.color.text_primary,
            ),
        )
        binding.txtSectionTitle.textSize = if (isSubtitle) 13f else 15f
        binding.txtSectionTitle.setTypeface(
            binding.txtSectionTitle.typeface,
            if (isSubtitle) android.graphics.Typeface.NORMAL else android.graphics.Typeface.BOLD,
        )
        return binding.root
    }

    private fun bindInspection(
        convertView: View?,
        parent: ViewGroup?,
        inspection: Inspection,
        openable: Boolean,
    ): View {
        val rowBinding = if (convertView == null) {
            ItemVistoriaBinding.inflate(LayoutInflater.from(context), parent, false)
        } else {
            ItemVistoriaBinding.bind(convertView)
        }
        val card = rowBinding.root as MaterialCardView
        rowBinding.root.alpha = if (openable) 1f else 0.9f
        rowBinding.txtItemTitulo.text = inspection.property.title
        when (inspection.type) {
            InspectionType.INITIAL -> {
                rowBinding.txtItemTipo.text = context.getString(R.string.tipo_inicial)
                rowBinding.txtItemTipo.setBackgroundResource(R.drawable.bg_badge_inicial)
                rowBinding.txtItemTipo.setTextColor(ContextCompat.getColor(context, R.color.badge_inicial))
            }
            InspectionType.FINAL -> {
                rowBinding.txtItemTipo.text = context.getString(R.string.tipo_encerramento)
                rowBinding.txtItemTipo.setBackgroundResource(R.drawable.bg_badge_final)
                rowBinding.txtItemTipo.setTextColor(ContextCompat.getColor(context, R.color.badge_final))
            }
        }
        rowBinding.txtItemEndereco.text = inspection.property.addressLine

        val (statusLabel, statusColor, strokeColor) = when (inspection.status) {
            InspectionStatus.ACCEPTED -> Triple(
                context.getString(R.string.agenda_status_aceita),
                R.color.badge_aceita,
                R.color.card_stroke_aceita,
            )
            InspectionStatus.SUBMITTED -> Triple(
                context.getString(R.string.agenda_status_aguardando),
                R.color.badge_aguardando,
                R.color.card_stroke_aguardando,
            )
            else -> Triple(null, null, R.color.divider)
        }

        if (!openable && statusLabel != null && statusColor != null) {
            rowBinding.txtItemTipo.text = statusLabel
            rowBinding.txtItemTipo.setBackgroundResource(
                when (inspection.status) {
                    InspectionStatus.ACCEPTED -> R.drawable.bg_badge_aceita
                    else -> R.drawable.bg_badge_aguardando
                },
            )
            rowBinding.txtItemTipo.setTextColor(ContextCompat.getColor(context, statusColor))
            card.strokeColor = ContextCompat.getColor(context, strokeColor)
            card.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
        } else {
            card.strokeColor = ContextCompat.getColor(context, R.color.divider)
            card.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
        }

        val dataLinha = buildString {
            append(formatarDataAgenda(inspection.scheduledAtIso))
            when {
                inspection.checklistId.isNullOrBlank() ->
                    append("\n").append(context.getString(R.string.agenda_sem_checklist))
                inspection.status == InspectionStatus.ACCEPTED ->
                    append("\n").append(context.getString(R.string.agenda_aceita))
                inspection.status == InspectionStatus.SUBMITTED ->
                    append("\n").append(context.getString(R.string.agenda_aguardando_aceite))
            }
        }
        rowBinding.txtItemData.text = dataLinha
        if (statusColor != null && !openable) {
            rowBinding.txtItemData.setTextColor(ContextCompat.getColor(context, statusColor))
        } else {
            rowBinding.txtItemData.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
        rowBinding.root.tag = VIEW_INSPECTION
        return rowBinding.root
    }
}

private fun formatarDataAgenda(iso: String): String {
    val data = iso.substringBefore("T")
    val partes = data.split("-")
    if (partes.size != 3) return iso
    val hora = iso.substringAfter("T", "").take(5)
    return if (hora.isNotEmpty()) "${partes[2]}/${partes[1]}/${partes[0]} · $hora" else "${partes[2]}/${partes[1]}/${partes[0]}"
}
