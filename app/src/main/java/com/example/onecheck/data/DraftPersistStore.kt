package com.example.onecheck.data

import android.content.Context
import androidx.core.content.edit
import com.example.onecheck.model.ChecklistDraft
import com.example.onecheck.model.Inspection
import com.google.gson.Gson

class DraftPersistStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun save(checklistId: String, inspection: Inspection, draft: ChecklistDraft) {
        val payload = PersistedInspectionSession(inspection = inspection, draft = draft)
        prefs.edit { putString(key(checklistId), gson.toJson(payload)) }
    }

    fun load(checklistId: String): PersistedInspectionSession? =
        runCatching {
            val json = prefs.getString(key(checklistId), null) ?: return null
            gson.fromJson(json, PersistedInspectionSession::class.java)
        }.getOrNull()

    fun clear(checklistId: String) {
        prefs.edit { remove(key(checklistId)) }
    }

    data class PersistedInspectionSession(
        val inspection: Inspection,
        val draft: ChecklistDraft,
    )

    companion object {
        private const val PREFS_NAME = "onecheck_draft_persist"
        private fun key(checklistId: String) = "draft_$checklistId"
    }
}
