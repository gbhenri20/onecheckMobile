package com.example.onecheck.model

data class ItemPhoto(
    val id: String,
    val remoteUrl: String? = null,
    val localUri: String? = null,
) {
    fun resolveDisplayUrl(apiBaseUrl: String): String? {
        localUri?.takeIf { it.isNotBlank() }?.let { return it }
        val path = remoteUrl?.takeIf { it.isNotBlank() } ?: return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = apiBaseUrl.trimEnd('/')
        return if (path.startsWith("/")) "$base$path" else "$base/$path"
    }
}
