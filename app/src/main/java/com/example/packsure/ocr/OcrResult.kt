package com.example.packsure.ocr

import android.graphics.Rect

/** Text evidence returned by ML Kit. Confidence is null because Android Text Recognition does not expose it. */
data class OcrFragment(
    val text: String,
    val confidence: Float?,
    val boundingBox: Rect?
)

data class OcrResult(
    val fullText: String,
    val fragments: List<OcrFragment>,
    /** Dimensions of the bitmap passed to ML Kit; bounding boxes use this coordinate space. */
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val rotationDegrees: Int = 0
)
