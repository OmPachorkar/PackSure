package com.example.packsure.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class OcrProcessor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val fragments = text.textBlocks.flatMap { block ->
            block.lines.map { line -> OcrFragment(line.text, null, line.boundingBox) }
        }
        return OcrResult(text.text.trim(), fragments, bitmap.width, bitmap.height)
    }

    fun close() = recognizer.close()
}
