package com.example.packsure.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

data class ImageQualityReport(val messages: List<String>) {
    val needsAttention get() = messages.isNotEmpty()
}

data class PreparedImage(val bitmap: Bitmap, val quality: ImageQualityReport)

/** Keeps the original Uri untouched and creates a bounded bitmap only for local OCR. */
class ImagePreprocessor(private val resolver: ContentResolver) {
    suspend fun prepare(uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image" }

        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / sample > MAX_OCR_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Unable to open image")
        PreparedImage(bitmap, assess(bitmap))
    }

    private fun assess(bitmap: Bitmap): ImageQualityReport {
        val notes = mutableListOf<String>()
        if (bitmap.width < 640 || bitmap.height < 640) notes += "Image is small; move closer to the label."
        val stride = max(1, max(bitmap.width, bitmap.height) / 80)
        var luminanceTotal = 0L
        var count = 0
        for (y in 0 until bitmap.height step stride) for (x in 0 until bitmap.width step stride) {
            val pixel = bitmap.getPixel(x, y)
            luminanceTotal += ((pixel shr 16 and 0xff) * 299 + (pixel shr 8 and 0xff) * 587 + (pixel and 0xff) * 114) / 1000
            count++
        }
        if (count > 0 && luminanceTotal / count < 42) notes += "Image appears very dark; improve lighting if possible."
        return ImageQualityReport(notes)
    }

    private companion object { const val MAX_OCR_EDGE = 2048 }
}
