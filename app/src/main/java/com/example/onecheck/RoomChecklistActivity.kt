package com.example.onecheck

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.onecheck.BuildConfig
import com.example.onecheck.data.OneCheckSession
import com.example.onecheck.databinding.ActivityRoomChecklistBinding
import com.example.onecheck.databinding.ItemChecklistRowBinding
import com.example.onecheck.model.ChecklistDraft
import com.example.onecheck.model.Condition
import com.example.onecheck.model.Inspection
import com.example.onecheck.model.ItemDraft
import com.example.onecheck.model.ItemPhoto
import com.example.onecheck.model.RoomDraft
import com.example.onecheck.ui.ErrorScreen
import com.example.onecheck.ui.PhotoImageLoader
import com.example.onecheck.ui.showError
import com.example.onecheck.ui.showMessage
import com.example.onecheck.util.ImageCompressor
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class RoomChecklistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRoomChecklistBinding
    private lateinit var inspectionId: String
    private var roomId: String = ""
    private lateinit var inspection: Inspection
    private lateinit var draft: ChecklistDraft

    private var pendingPhotoRoomId: String? = null
    private var pendingPhotoItemId: String? = null
    private var cameraPhotoFile: File? = null
    private var cameraCaptureUri: Uri? = null
    private var substituirFotoExistente: Boolean = false
    private var awaitingCameraUpload: Boolean = false
    private var uiPronta: Boolean = false

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {
                // alguns provedores não suportam persistência
            }
            enviarFoto(it)
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        awaitingCameraUpload = false
        val file = cameraPhotoFile
        val uri = cameraCaptureUri
        if (success && file != null && file.exists() && uri != null) {
            enviarFoto(uri, captureFile = file)
        } else {
            cameraPhotoFile = null
            cameraCaptureUri = null
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            when (pendingPhotoAction) {
                PhotoAction.CAMERA -> abrirCamera()
                PhotoAction.GALLERY -> abrirGaleria()
                null -> Unit
            }
        } else {
            showMessage(getString(R.string.error_permission_media))
        }
        pendingPhotoAction = null
    }

    private enum class PhotoAction { CAMERA, GALLERY }
    private var pendingPhotoAction: PhotoAction? = null

    private val conditionLabels by lazy {
        listOf(
            getString(R.string.estado_selecione) to null,
            getString(R.string.estado_otimo) to Condition.GREAT,
            getString(R.string.estado_bom) to Condition.GOOD,
            getString(R.string.estado_regular) to Condition.REGULAR,
            getString(R.string.estado_ruim) to Condition.BAD,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRoomChecklistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarChecklist)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        inspectionId = intent.getStringExtra(EXTRA_INSPECTION_ID) ?: run {
            finish()
            return
        }
        roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty()

        if (savedInstanceState != null) {
            restaurarEstado(savedInstanceState)
        } else {
            lifecycleScope.launch { carregarChecklist() }
        }
        binding.btnChecklistProximo.setOnClickListener { irProximoComodo() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (uiPronta) salvarItensAtuais()
        outState.putString(STATE_ROOM_ID, roomId)
        outState.putString(STATE_PENDING_ROOM, pendingPhotoRoomId)
        outState.putString(STATE_PENDING_ITEM, pendingPhotoItemId)
        outState.putBoolean(STATE_SUBSTITUIR_FOTO, substituirFotoExistente)
        outState.putBoolean(STATE_AWAITING_CAMERA, awaitingCameraUpload)
        cameraPhotoFile?.absolutePath?.let { outState.putString(STATE_CAMERA_FILE, it) }
        cameraCaptureUri?.toString()?.let { outState.putString(STATE_CAMERA_URI, it) }
    }

    private fun restaurarEstado(state: Bundle) {
        roomId = state.getString(STATE_ROOM_ID, roomId)
        pendingPhotoRoomId = state.getString(STATE_PENDING_ROOM)
        pendingPhotoItemId = state.getString(STATE_PENDING_ITEM)
        substituirFotoExistente = state.getBoolean(STATE_SUBSTITUIR_FOTO, false)
        awaitingCameraUpload = state.getBoolean(STATE_AWAITING_CAMERA, false)
        state.getString(STATE_CAMERA_FILE)?.let { path ->
            cameraPhotoFile = File(path).takeIf { it.exists() }
        }
        state.getString(STATE_CAMERA_URI)?.let { uriString ->
            cameraCaptureUri = Uri.parse(uriString)
        }

        if (restaurarDaSessaoOuDisco()) {
            maybeRetomarFotoDaCamera()
            return
        }
        lifecycleScope.launch { carregarChecklist() }
    }

    private fun restaurarDaSessaoOuDisco(): Boolean {
        val cachedDraft = OneCheckSession.drafts[inspectionId]
        val cachedInspection = OneCheckSession.inspections[inspectionId]
        if (cachedDraft != null && cachedInspection != null) {
            draft = cachedDraft
            inspection = cachedInspection
            exibirChecklist()
            return true
        }
        val persisted = OneCheckSession.draftPersistStore.load(inspectionId) ?: return false
        draft = persisted.draft
        inspection = persisted.inspection
        OneCheckSession.drafts[inspectionId] = draft
        OneCheckSession.inspections[inspectionId] = inspection
        exibirChecklist()
        return true
    }

    private fun maybeRetomarFotoDaCamera() {
        if (!awaitingCameraUpload) return
        val file = cameraPhotoFile ?: return
        val room = pendingPhotoRoomId ?: return
        val item = pendingPhotoItemId ?: return
        if (!file.exists()) return

        awaitingCameraUpload = false
        val uri = cameraCaptureUri ?: FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            file,
        )
        binding.root.post {
            if (!isFinishing && !isDestroyed) {
                enviarFoto(uri, captureFile = file)
            }
        }
    }

    private suspend fun carregarChecklist() {
        if (restaurarDaSessaoOuDisco()) return

        try {
            val localDraft = OneCheckSession.drafts[inspectionId]
                ?: OneCheckSession.draftPersistStore.load(inspectionId)?.draft

            val summary = OneCheckSession.inspections[inspectionId]
                ?: OneCheckSession.summaries[inspectionId]
                ?: OneCheckSession.repository.findInspectionSummary(inspectionId)

            val loaded = OneCheckSession.repository.loadInspection(summary)
            inspection = loaded.inspection
            draft = loaded.draft.mergeWithLocal(localDraft)
            persistirSessao()
            exibirChecklist()
        } catch (e: Exception) {
            if (restaurarDaSessaoOuDisco()) {
                showMessage(getString(R.string.checklist_restaurado_local))
                return
            }
            showError(e, ErrorScreen.CHECKLIST)
            finish()
        }
    }

    private fun exibirChecklist() {
        if (draft.rooms.isEmpty()) {
            if (!restaurarDaSessaoOuDisco()) {
                showMessage(getString(R.string.error_checklist_load))
                finish()
            }
            return
        }
        if (roomId.isBlank()) roomId = draft.rooms.first().id
        binding.txtChecklistImovel.text = inspection.property.title
        binding.txtChecklistEndereco.text = inspection.property.addressLine
        setupRoomSpinner()
        renderItems()
        uiPronta = true
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_checklist, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.menu_proximo_comodo -> {
                irProximoComodo()
                true
            }
            R.id.menu_revisao -> {
                salvarItensAtuais()
                startActivity(
                    Intent(this, ReviewActivity::class.java).apply {
                        putExtra(ReviewActivity.EXTRA_INSPECTION_ID, inspectionId)
                    },
                )
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    private fun setupRoomSpinner() {
        val roomNames = draft.rooms.map { it.name }
        binding.spinnerComodos.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            roomNames,
        )

        val index = draft.rooms.indexOfFirst { it.id == roomId }.coerceAtLeast(0)
        binding.spinnerComodos.setSelection(index)
        roomId = draft.rooms[index].id

        binding.spinnerComodos.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                salvarItensAtuais()
                roomId = draft.rooms[position].id
                renderItems()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun renderItems() {
        salvarItensAtuais()
        binding.layoutItens.removeAllViews()
        val room = draft.rooms.firstOrNull { it.id == roomId } ?: return
        room.items.forEach { item ->
            val row = ItemChecklistRowBinding.inflate(LayoutInflater.from(this), binding.layoutItens, false)
            bindItemRow(row, room, item)
            binding.layoutItens.addView(row.root)
        }
    }

    private fun bindItemRow(row: ItemChecklistRowBinding, room: RoomDraft, item: ItemDraft) {
        row.root.tag = item.id
        row.txtItemLabel.text = if (item.required) "${item.label} *" else item.label
        row.txtItemObservacao.setText(item.note)

        setupConditionSpinner(row.spinnerEstado, item.condition) { condition, fromUser ->
            if (fromUser && condition != null) {
                atualizarItem(room.id, item.id, condition, row.txtItemObservacao.text.toString())
            }
        }

        row.txtItemObservacao.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val condition = readConditionFromSpinner(row.spinnerEstado)
                if (condition != null) {
                    atualizarItem(room.id, item.id, condition, row.txtItemObservacao.text.toString())
                }
            }
        }

        bindFotoUi(row, room, item)
    }

    private fun bindFotoUi(row: ItemChecklistRowBinding, room: RoomDraft, item: ItemDraft) {
        val photo = item.photos.firstOrNull()
        if (photo != null) {
            row.layoutFotoPreview.visibility = View.VISIBLE
            row.btnAdicionarFoto.visibility = View.GONE
            PhotoImageLoader.load(row.imgFotoPreview, photo, BuildConfig.API_BASE_URL, this)
            row.btnTrocarFoto.setOnClickListener {
                pendingPhotoRoomId = room.id
                pendingPhotoItemId = item.id
                substituirFotoExistente = true
                mostrarEscolhaFoto()
            }
            row.btnRemoverFoto.setOnClickListener {
                removerFoto(room.id, item.id, photo.id)
            }
        } else {
            row.layoutFotoPreview.visibility = View.GONE
            row.btnAdicionarFoto.visibility = View.VISIBLE
            row.imgFotoPreview.setImageDrawable(null)
            row.btnAdicionarFoto.setOnClickListener {
                pendingPhotoRoomId = room.id
                pendingPhotoItemId = item.id
                substituirFotoExistente = false
                mostrarEscolhaFoto()
            }
        }
    }

    private fun atualizarLinhaItem(roomIdLocal: String, itemId: String) {
        val room = draft.rooms.firstOrNull { it.id == roomIdLocal } ?: return
        val item = room.items.firstOrNull { it.id == itemId } ?: return
        for (i in 0 until binding.layoutItens.childCount) {
            val child = binding.layoutItens.getChildAt(i)
            if (child.tag == itemId) {
                bindItemRow(ItemChecklistRowBinding.bind(child), room, item)
                break
            }
        }
    }

    private fun mostrarEscolhaFoto() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.photo_source_title)
            .setItems(
                arrayOf(
                    getString(R.string.photo_source_camera),
                    getString(R.string.photo_source_gallery),
                ),
            ) { _, which ->
                when (which) {
                    0 -> solicitarPermissoesE(PhotoAction.CAMERA)
                    1 -> solicitarPermissoesE(PhotoAction.GALLERY)
                }
            }
            .show()
    }

    private fun solicitarPermissoesE(action: PhotoAction) {
        val needed = mutableListOf<String>()
        if (action == PhotoAction.CAMERA) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.CAMERA)
            }
        }
        if (action == PhotoAction.GALLERY) {
            val readPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            if (ContextCompat.checkSelfPermission(this, readPerm) != PackageManager.PERMISSION_GRANTED) {
                needed.add(readPerm)
            }
        }
        if (needed.isEmpty()) {
            when (action) {
                PhotoAction.CAMERA -> abrirCamera()
                PhotoAction.GALLERY -> abrirGaleria()
            }
        } else {
            pendingPhotoAction = action
            requestPermissionsLauncher.launch(needed.toTypedArray())
        }
    }

    private fun abrirGaleria() {
        salvarItensAtuais()
        pickImageLauncher.launch("image/*")
    }

    private fun abrirCamera() {
        salvarItensAtuais()
        persistirSessao()
        val photoFile = File(cacheDir, "camera/capture_${System.currentTimeMillis()}.jpg")
        photoFile.parentFile?.mkdirs()
        cameraPhotoFile = photoFile
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        cameraCaptureUri = uri
        awaitingCameraUpload = true
        takePictureLauncher.launch(uri)
    }

    private fun enviarFoto(uri: Uri, captureFile: File? = null) {
        val roomIdLocal = pendingPhotoRoomId ?: return
        var itemId = pendingPhotoItemId ?: return

        lifecycleScope.launch {
            try {
                salvarItensAtuais()
                showMessage(getString(R.string.photo_enviando))

                val room = draft.rooms.firstOrNull { it.id == roomIdLocal }
                val item = room?.items?.firstOrNull { it.id == itemId }
                val condition = item?.condition
                val note = item?.note.orEmpty()

                val rawBytes = withContext(Dispatchers.IO) {
                    when {
                        captureFile != null && captureFile.exists() -> captureFile.readBytes()
                        else -> contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IOException("photo_read_failed")
                    }
                }
                val bytes = withContext(Dispatchers.IO) {
                    ImageCompressor.compressForUpload(rawBytes)
                }

                val mimeType = if (captureFile != null) {
                    "image/jpeg"
                } else {
                    contentResolver.getType(uri) ?: "image/jpeg"
                }
                val ext = "jpg"

                val fotoAntiga = draft.rooms
                    .firstOrNull { it.id == roomIdLocal }
                    ?.items?.firstOrNull { it.id == itemId }
                    ?.photos?.firstOrNull()

                if (substituirFotoExistente && fotoAntiga != null && !fotoAntiga.id.startsWith("local-")) {
                    runCatching {
                        OneCheckSession.repository.deleteItemPhoto(
                            checklistId = inspectionId,
                            itemId = itemId,
                            photoId = fotoAntiga.id,
                        )
                    }
                }

                val localPreview = ItemPhoto(
                    id = "local-${System.currentTimeMillis()}",
                    localUri = uri.toString(),
                )
                salvarDraft(draft.setItemPhotos(roomIdLocal, itemId, listOf(localPreview)))
                atualizarLinhaItem(roomIdLocal, itemId)

                val uploadResult = OneCheckSession.repository.uploadItemPhoto(
                    checklistId = inspectionId,
                    itemId = itemId,
                    imageBytes = bytes,
                    fileName = "foto_${System.currentTimeMillis()}.$ext",
                    condition = condition,
                    note = note,
                    mimeType = mimeType,
                )
                if (uploadResult.itemId != itemId) {
                    salvarDraft(draft.replaceItemId(roomIdLocal, itemId, uploadResult.itemId))
                    itemId = uploadResult.itemId
                }

                salvarDraft(draft.setItemPhotos(roomIdLocal, itemId, listOf(uploadResult.photo)))
                atualizarLinhaItem(roomIdLocal, itemId)
                OneCheckSession.submissionLogStore.appendPhoto(
                    checklistId = inspectionId,
                    propertyTitle = inspection.property.title,
                    success = true,
                    message = getString(R.string.success_photo_upload),
                )
                showMessage(getString(R.string.success_photo_upload))
            } catch (e: Exception) {
                OneCheckSession.submissionLogStore.appendPhoto(
                    checklistId = inspectionId,
                    propertyTitle = inspection.property.title,
                    success = false,
                    message = e.message ?: getString(R.string.error_photo_upload),
                )
                showError(e, ErrorScreen.PHOTO)
                atualizarLinhaItem(roomIdLocal, itemId)
            } finally {
                pendingPhotoRoomId = null
                pendingPhotoItemId = null
                substituirFotoExistente = false
                cameraPhotoFile = null
            }
        }
    }

    private fun removerFoto(roomIdLocal: String, itemId: String, photoId: String) {
        lifecycleScope.launch {
            try {
                salvarItensAtuais()
                if (!photoId.startsWith("local-")) {
                    OneCheckSession.repository.deleteItemPhoto(
                        checklistId = inspectionId,
                        itemId = itemId,
                        photoId = photoId,
                    )
                }
                salvarDraft(draft.removePhoto(roomIdLocal, itemId, photoId))
                atualizarLinhaItem(roomIdLocal, itemId)
                showMessage(getString(R.string.photo_removida))
            } catch (e: Exception) {
                showError(e, ErrorScreen.PHOTO)
            }
        }
    }

    private fun setupConditionSpinner(
        spinner: Spinner,
        current: Condition?,
        onChange: (Condition?, fromUser: Boolean) -> Unit,
    ) {
        val labels = conditionLabels.map { it.first }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val index = conditionLabels.indexOfFirst { it.second == current }.coerceAtLeast(0)

        var suppressCallback = true
        spinner.setSelection(index, false)
        spinner.post { suppressCallback = false }

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (suppressCallback) return
                onChange(conditionLabels[position].second, position > 0)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun readConditionFromSpinner(spinner: Spinner): Condition? {
        val position = spinner.selectedItemPosition
        return conditionLabels.getOrNull(position)?.second
    }

    private fun atualizarItem(roomId: String, itemId: String, condition: Condition?, note: String) {
        salvarDraft(draft.updateItem(roomId, itemId, condition, note))
        lifecycleScope.launch {
            runCatching {
                val resolvedId = OneCheckSession.repository.saveChecklistItem(
                    checklistId = inspectionId,
                    itemId = itemId,
                    condition = condition,
                    note = note,
                )
                if (resolvedId != itemId) {
                    salvarDraft(draft.replaceItemId(roomId, itemId, resolvedId))
                    atualizarLinhaItem(roomId, resolvedId)
                }
            }.onFailure { e ->
                showError(e, ErrorScreen.ITEM_SAVE)
            }
        }
    }

    private fun salvarDraft(updated: ChecklistDraft) {
        draft = updated
        OneCheckSession.drafts[inspectionId] = updated
        if (::inspection.isInitialized) {
            persistirSessao()
        }
    }

    private fun persistirSessao() {
        OneCheckSession.inspections[inspectionId] = inspection
        OneCheckSession.drafts[inspectionId] = draft
        OneCheckSession.draftPersistStore.save(inspectionId, inspection, draft)
    }

    private fun salvarItensAtuais() {
        val room = draft.rooms.firstOrNull { it.id == roomId } ?: return
        var updated = draft
        for (i in 0 until binding.layoutItens.childCount) {
            val child = binding.layoutItens.getChildAt(i)
            val itemId = child.tag as? String ?: continue
            val row = ItemChecklistRowBinding.bind(child)
            val condition = readConditionFromSpinner(row.spinnerEstado)
            updated = updated.updateItemPreservingCondition(
                room.id,
                itemId,
                condition,
                row.txtItemObservacao.text.toString(),
            )
        }
        salvarDraft(updated)
    }

    private fun irProximoComodo() {
        salvarItensAtuais()
        val next = draft.nextRoomAfter(roomId)
        if (next != null) {
            roomId = next.id
            val index = draft.rooms.indexOfFirst { it.id == roomId }
            binding.spinnerComodos.setSelection(index)
            renderItems()
        } else {
            startActivity(
                Intent(this, ReviewActivity::class.java).apply {
                    putExtra(ReviewActivity.EXTRA_INSPECTION_ID, inspectionId)
                },
            )
        }
    }

    companion object {
        const val EXTRA_INSPECTION_ID = "extra_inspection_id"
        const val EXTRA_ROOM_ID = "extra_room_id"
        private const val STATE_ROOM_ID = "state_room_id"
        private const val STATE_PENDING_ROOM = "state_pending_room"
        private const val STATE_PENDING_ITEM = "state_pending_item"
        private const val STATE_SUBSTITUIR_FOTO = "state_substituir_foto"
        private const val STATE_CAMERA_FILE = "state_camera_file"
        private const val STATE_CAMERA_URI = "state_camera_uri"
        private const val STATE_AWAITING_CAMERA = "state_awaiting_camera"
    }
}
