package com.example.onecheck.data

import android.content.Context
import androidx.core.content.edit
import com.example.onecheck.model.SubmissionLogEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

class SubmissionLogStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<SubmissionLogEntry>>() {}.type

    fun list(): List<SubmissionLogEntry> =
        runCatching {
            val json = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
            gson.fromJson<List<SubmissionLogEntry>>(json, listType).orEmpty()
        }.getOrDefault(emptyList())
            .sortedByDescending { it.timestampMs }

    fun append(entry: SubmissionLogEntry) {
        val updated = listOf(entry) + list()
        prefs.edit {
            putString(KEY_ENTRIES, gson.toJson(updated.take(MAX_ENTRIES)))
        }
    }

    fun appendChecklist(
        checklistId: String,
        propertyTitle: String,
        success: Boolean,
        message: String,
    ) {
        append(
            SubmissionLogEntry(
                id = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis(),
                checklistId = checklistId,
                propertyTitle = propertyTitle,
                type = SubmissionLogEntry.LogType.CHECKLIST,
                success = success,
                message = message,
            ),
        )
    }

    fun appendPhoto(
        checklistId: String,
        propertyTitle: String,
        success: Boolean,
        message: String,
    ) {
        append(
            SubmissionLogEntry(
                id = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis(),
                checklistId = checklistId,
                propertyTitle = propertyTitle,
                type = SubmissionLogEntry.LogType.PHOTO,
                success = success,
                message = message,
            ),
        )
    }

    fun clear() {
        prefs.edit { remove(KEY_ENTRIES) }
    }

    companion object {
        private const val PREFS_NAME = "onecheck_submission_log"
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 200
    }
}
