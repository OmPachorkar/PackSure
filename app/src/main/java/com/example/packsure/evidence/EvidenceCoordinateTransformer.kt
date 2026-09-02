package com.example.packsure.evidence

import android.graphics.Rect
import kotlin.math.min

/** Maps ML Kit image-space rectangles into a fit-center displayed image, never guessing rectangles. */
object EvidenceCoordinateTransformer {
    fun map(rect: Rect, sourceWidth: Int, sourceHeight: Int, viewport: ImageViewport, rotationDegrees: Int = 0): DisplayRect? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || viewport.width <= 0f || viewport.height <= 0f || rect.left < 0 || rect.top < 0 || rect.right > sourceWidth || rect.bottom > sourceHeight || rect.right <= rect.left || rect.bottom <= rect.top) return null
        val rotated = rotate(rect, sourceWidth, sourceHeight, rotationDegrees) ?: return null
        val rotatedWidth = if (rotationDegrees.normalized() % 180 == 0) sourceWidth.toFloat() else sourceHeight.toFloat()
        val rotatedHeight = if (rotationDegrees.normalized() % 180 == 0) sourceHeight.toFloat() else sourceWidth.toFloat()
        val scale = min(viewport.width / rotatedWidth, viewport.height / rotatedHeight)
        val offsetX = (viewport.width - rotatedWidth * scale) / 2f
        val offsetY = (viewport.height - rotatedHeight * scale) / 2f
        return DisplayRect(offsetX + rotated.left * scale, offsetY + rotated.top * scale, offsetX + rotated.right * scale, offsetY + rotated.bottom * scale)
    }

    fun mapAll(rectangles: List<Rect>, sourceWidth: Int, sourceHeight: Int, viewport: ImageViewport, rotationDegrees: Int = 0) = rectangles.mapNotNull { map(it, sourceWidth, sourceHeight, viewport, rotationDegrees) }

    private fun rotate(rect: Rect, width: Int, height: Int, degrees: Int): Rect? = when (degrees.normalized()) {
        0 -> Rect().apply { left = rect.left; top = rect.top; right = rect.right; bottom = rect.bottom }
        90 -> Rect().apply { left = height - rect.bottom; top = rect.left; right = height - rect.top; bottom = rect.right }
        180 -> Rect().apply { left = width - rect.right; top = height - rect.bottom; right = width - rect.left; bottom = height - rect.top }
        270 -> Rect().apply { left = rect.top; top = width - rect.right; right = rect.bottom; bottom = width - rect.left }
        else -> null
    }

    private fun Int.normalized() = ((this % 360) + 360) % 360
}
