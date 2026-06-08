package com.example.onecheck

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.onecheck.data.OneCheckSession
import com.example.onecheck.databinding.ActivitySubmissionLogBinding
import com.example.onecheck.databinding.ItemSubmissionLogBinding
import com.example.onecheck.model.SubmissionLogEntry
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SubmissionLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubmissionLogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubmissionLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarLog)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        atualizarLista()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_submission_log, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.menu_limpar_log -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.log_limpar_titulo)
                    .setMessage(R.string.log_limpar_mensagem)
                    .setPositiveButton(R.string.menu_limpar_log) { _, _ ->
                        OneCheckSession.submissionLogStore.clear()
                        atualizarLista()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun atualizarLista() {
        val entries = OneCheckSession.submissionLogStore.list()
        binding.txtLogVazio.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        binding.listLogs.visibility = if (entries.isEmpty()) View.GONE else View.VISIBLE
        binding.listLogs.adapter = LogAdapter(this, entries)
    }

    private class LogAdapter(
        private val context: Context,
        private val entries: List<SubmissionLogEntry>,
    ) : BaseAdapter() {

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): SubmissionLogEntry = entries[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val row = if (convertView == null) {
                ItemSubmissionLogBinding.inflate(LayoutInflater.from(context), parent, false)
            } else {
                ItemSubmissionLogBinding.bind(convertView)
            }
            val entry = entries[position]
            row.txtLogTitulo.text = entry.propertyTitle
            val tipo = when (entry.type) {
                SubmissionLogEntry.LogType.CHECKLIST -> context.getString(R.string.log_tipo_checklist)
                SubmissionLogEntry.LogType.PHOTO -> context.getString(R.string.log_tipo_foto)
            }
            val status = if (entry.success) {
                context.getString(R.string.log_status_sucesso)
            } else {
                context.getString(R.string.log_status_falha)
            }
            row.txtLogMeta.text = buildString {
                append(dateFormat.format(Date(entry.timestampMs)))
                append(" · ")
                append(tipo)
                append(" · ")
                append(status)
            }
            row.txtLogMensagem.text = entry.message
            val color = if (entry.success) R.color.accent_green else R.color.error_red
            row.txtLogMeta.setTextColor(ContextCompat.getColor(context, color))
            return row.root
        }
    }
}
