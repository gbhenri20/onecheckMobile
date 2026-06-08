package com.example.onecheck.data

import com.example.onecheck.model.ChecklistDraft
import com.example.onecheck.model.Inspection

data class LoadedInspection(
    val inspection: Inspection,
    val draft: ChecklistDraft,
)
