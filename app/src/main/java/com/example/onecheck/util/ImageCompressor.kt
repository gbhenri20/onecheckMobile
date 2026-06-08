package com.example.onecheck.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageCompressor {

    /** Reduz tamanho para upload em rede móvel / API com limite de corpo. */
    fun compressForUpload(
        bytes: ByteArray,
        maxDimension: Int = 1920,
        jpegQuality: Int = 82,
    ): ByteArray {
        if (bytes.isEmpty()) return bytes
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes

        var sampleSize = 1
        val largestSide = max(bounds.outWidth, bounds.outHeight)
        while (largestSide / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return bytes
        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)
                out.toByteArray().takeIf { it.isNotEmpty() } ?: bytes
            }
        } finally {
            bitmap.recycle()
        }
    }
}
