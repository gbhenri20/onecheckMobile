package com.example.onecheck.data

import android.content.Context
import com.example.onecheck.data.api.ApiOneCheckRepository
import com.example.onecheck.data.api.RetrofitProvider
import com.example.onecheck.data.api.TokenStore
import com.example.onecheck.model.ChecklistDraft
import com.example.onecheck.model.Inspection

object OneCheckSession {
    private var initialized = false
    lateinit var tokenStore: TokenStore
        private set

    lateinit var submissionLogStore: SubmissionLogStore
        private set

    lateinit var draftPersistStore: DraftPersistStore
        private set

    lateinit var repository: OneCheckRepository
        private set

    var mfaToken: String?
        get() = tokenStore.mfaToken
        set(value) { tokenStore.mfaToken = value }

    var accessToken: String?
        get() = tokenStore.accessToken
        set(value) { tokenStore.accessToken = value }

    var refreshToken: String?
        get() = tokenStore.refreshToken
        set(value) { tokenStore.refreshToken = value }

    val inspections = mutableMapOf<String, Inspection>()
    /** Resumo da agenda (ids de agendamento/checklist) para abrir a vistoria. */
    val summaries = mutableMapOf<String, Inspection>()
    val drafts = mutableMapOf<String, ChecklistDraft>()

    fun init(context: Context) {
        if (initialized) return
        tokenStore = TokenStore(context.applicationContext)
        submissionLogStore = SubmissionLogStore(context.applicationContext)
        draftPersistStore = DraftPersistStore(context.applicationContext)
        val api = RetrofitProvider.createApi(tokenStore)
        repository = ApiOneCheckRepository(api, tokenStore)
        initialized = true
    }

    fun clear() {
        if (initialized) {
            tokenStore.clear()
            submissionLogStore.clear()
        }
        inspections.clear()
        summaries.clear()
        drafts.clear()
    }

    fun releaseInspectionLocal(checklistId: String) {
        drafts.remove(checklistId)
        inspections.remove(checklistId)
        if (initialized) {
            draftPersistStore.clear(checklistId)
        }
        val keysToRemove = summaries.filter { (_, v) ->
            v.checklistId == checklistId || v.id == checklistId
        }.keys
        keysToRemove.forEach { summaries.remove(it) }
    }
}
