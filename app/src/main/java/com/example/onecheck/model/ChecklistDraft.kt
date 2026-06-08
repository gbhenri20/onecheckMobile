package com.example.onecheck.model

data class ChecklistDraft(
    val inspectionId: String,
    val rooms: List<RoomDraft>,
) {
    companion object {
        fun fromInspection(inspection: Inspection): ChecklistDraft =
            ChecklistDraft(
                inspectionId = inspection.id,
                rooms = inspection.rooms.map { room ->
                    RoomDraft(
                        id = room.id,
                        name = room.name,
                        items = room.items.map { item ->
                            ItemDraft(
                                id = item.id,
                                label = item.label,
                                required = item.required,
                                condition = item.condition,
                                note = item.note,
                                photos = item.photos,
                            )
                        },
                    )
                },
            )
    }

    fun updateItem(roomId: String, itemId: String, condition: Condition?, note: String): ChecklistDraft =
        copy(
            rooms = rooms.map { room ->
                if (room.id != roomId) return@map room
                room.copy(
                    items = room.items.map { item ->
                        if (item.id != itemId) item else item.copy(condition = condition, note = note)
                    },
                )
            },
        )

    fun updateItemPreservingCondition(
        roomId: String,
        itemId: String,
        condition: Condition?,
        note: String,
    ): ChecklistDraft =
        copy(
            rooms = rooms.map { room ->
                if (room.id != roomId) return@map room
                room.copy(
                    items = room.items.map { item ->
                        if (item.id != itemId) item
                        else item.copy(
                            condition = condition ?: item.condition,
                            note = note,
                        )
                    },
                )
            },
        )

    fun replaceItemId(roomId: String, oldItemId: String, newItemId: String): ChecklistDraft =
        copy(
            rooms = rooms.map { room ->
                if (room.id != roomId) return@map room
                room.copy(
                    items = room.items.map { item ->
                        if (item.id != oldItemId) item else item.copy(id = newItemId)
                    },
                )
            },
        )

    fun setItemPhotos(roomId: String, itemId: String, photos: List<ItemPhoto>): ChecklistDraft =
        copy(
            rooms = rooms.map { room ->
                if (room.id != roomId) return@map room
                room.copy(
                    items = room.items.map { item ->
                        if (item.id != itemId) item else item.copy(photos = photos)
                    },
                )
            },
        )

    fun addPhoto(roomId: String, itemId: String, photo: ItemPhoto): ChecklistDraft =
        setItemPhotos(roomId, itemId, photosFor(roomId, itemId) + photo)

    fun removePhoto(roomId: String, itemId: String, photoId: String): ChecklistDraft =
        setItemPhotos(
            roomId,
            itemId,
            photosFor(roomId, itemId).filter { it.id != photoId },
        )

    private fun photosFor(roomId: String, itemId: String): List<ItemPhoto> =
        rooms.firstOrNull { it.id == roomId }
            ?.items?.firstOrNull { it.id == itemId }
            ?.photos
            .orEmpty()

    fun nextRoomAfter(currentRoomId: String): RoomDraft? {
        val idx = rooms.indexOfFirst { it.id == currentRoomId }
        if (idx == -1) return rooms.firstOrNull()
        return rooms.getOrNull(idx + 1)
    }

    /** Preserva preenchimento local mesmo após recarregar da API (ex.: ao voltar da câmera). */
    fun mergeWithLocal(local: ChecklistDraft?): ChecklistDraft {
        if (local == null || local.inspectionId != inspectionId) return this
        val apiRoomsById = rooms.associateBy { it.id }
        val localRoomIds = local.rooms.map { it.id }.toSet()

        val mergedFromLocal = local.rooms.map { localRoom ->
            val apiRoom = apiRoomsById[localRoom.id]
            if (apiRoom == null) localRoom else mergeRoom(apiRoom, localRoom)
        }

        val apiOnlyRooms = rooms.filter { it.id !in localRoomIds }
        return copy(rooms = mergedFromLocal + apiOnlyRooms)
    }

    private fun mergeRoom(apiRoom: RoomDraft, localRoom: RoomDraft): RoomDraft {
        val apiItemsById = apiRoom.items.associateBy { it.id }
        val localItemIds = localRoom.items.map { it.id }.toSet()

        val mergedFromLocal = localRoom.items.map { localItem ->
            val apiItem = apiItemsById[localItem.id]
                ?: apiRoom.items.find { samePendingItem(localItem.id, it.id) }
                ?: apiRoom.items.find { apiMatchesLocalPending(it.id, localItem.id) }
            if (apiItem == null) {
                localItem
            } else {
                mergeItem(apiItem, localItem)
            }
        }

        val apiOnlyItems = apiRoom.items.filter { apiItem ->
            localRoom.items.none { localItem ->
                localItem.id == apiItem.id ||
                    samePendingItem(localItem.id, apiItem.id) ||
                    apiMatchesLocalPending(apiItem.id, localItem.id)
            }
        }

        return localRoom.copy(
            name = localRoom.name.ifBlank { apiRoom.name },
            items = mergedFromLocal + apiOnlyItems,
        )
    }

    private fun mergeItem(apiItem: ItemDraft, localItem: ItemDraft): ItemDraft =
        apiItem.copy(
            label = localItem.label.ifBlank { apiItem.label },
            condition = localItem.condition ?: apiItem.condition,
            note = localItem.note.ifBlank { apiItem.note },
            photos = if (localItem.photos.isNotEmpty()) localItem.photos else apiItem.photos,
        )

    private fun apiMatchesLocalPending(apiItemId: String, localItemId: String): Boolean {
        val pending = localItemId.parsePendingItem() ?: return false
        val apiPending = apiItemId.parsePendingItem() ?: return false
        return pending == apiPending
    }

    private fun samePendingItem(a: String, b: String): Boolean {
        if (a == b) return true
        val pendingA = a.parsePendingItem() ?: return false
        val pendingB = b.parsePendingItem() ?: return false
        return pendingA == pendingB
    }
}

private fun String.parsePendingItem(): Pair<String, String>? {
    if (!startsWith("pending:")) return null
    val rest = removePrefix("pending:")
    val splitAt = rest.lastIndexOf(':')
    if (splitAt <= 0 || splitAt >= rest.length - 1) return null
    return rest.substring(0, splitAt) to rest.substring(splitAt + 1)
}

data class RoomDraft(
    val id: String,
    val name: String,
    val items: List<ItemDraft>,
)

data class ItemDraft(
    val id: String,
    val label: String,
    val required: Boolean,
    val condition: Condition?,
    val note: String,
    val photos: List<ItemPhoto> = emptyList(),
)
