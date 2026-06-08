package com.example.onecheck.model

data class SubmissionLogEntry(
    val id: String,
    val timestampMs: Long,
    val checklistId: String,
    val propertyTitle: String,
    val type: LogType,
    val success: Boolean,
    val message: String,
) {
    enum class LogType { CHECKLIST, PHOTO }
}
