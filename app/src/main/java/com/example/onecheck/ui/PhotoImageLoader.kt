package com.example.onecheck.ui

import android.net.Uri
import android.widget.ImageView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.onecheck.model.ItemPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

object PhotoImageLoader {
    fun load(imageView: ImageView, photo: ItemPhoto, apiBaseUrl: String, owner: LifecycleOwner) {
        val displayUrl = photo.resolveDisplayUrl(apiBaseUrl) ?: run {
            imageView.setImageDrawable(null)
            return
        }
        if (displayUrl.startsWith("content:") || displayUrl.startsWith("file:")) {
            imageView.setImageURI(Uri.parse(displayUrl))
            return
        }
        owner.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    URL(displayUrl).openStream().use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
            }
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            }
        }
    }
}
