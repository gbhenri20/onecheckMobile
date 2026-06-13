package com.example.onecheck.util

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/** Testes instrumentados da compressão de imagens (requer Android Bitmap). */
@RunWith(AndroidJUnit4::class)
class ImageCompressorInstrumentedTest {

    @Test
    fun compressForUpload_reduzBytesDeImagemGrande() {
        val original = createLargeJpeg(width = 4000, height = 3000)
        val compressed = ImageCompressor.compressForUpload(original)

        assertTrue(compressed.isNotEmpty())
        assertTrue(compressed.size < original.size)
    }

    @Test
    fun compressForUpload_mantemBytesQuandoEntradaVazia() {
        assertTrue(ImageCompressor.compressForUpload(ByteArray(0)).isEmpty())
    }

    private fun createLargeJpeg(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }
}
