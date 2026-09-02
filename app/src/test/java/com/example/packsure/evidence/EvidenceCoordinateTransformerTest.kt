package com.example.packsure.evidence

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvidenceCoordinateTransformerTest {
    private fun rect(l: Int, t: Int, r: Int, b: Int) = Rect().apply { left = l; top = t; right = r; bottom = b }

    @Test fun same_size_mapping_preserves_coordinates() {
        val mapped = EvidenceCoordinateTransformer.map(rect(100, 200, 300, 400), 1000, 1000, ImageViewport(1000f, 1000f))!!
        assertEquals(100f, mapped.left, .01f); assertEquals(200f, mapped.top, .01f)
        assertEquals(300f, mapped.right, .01f); assertEquals(400f, mapped.bottom, .01f)
    }

    @Test fun scaled_mapping_scales_coordinates() {
        val mapped = EvidenceCoordinateTransformer.map(rect(100, 200, 300, 400), 1000, 1000, ImageViewport(500f, 500f))!!
        assertEquals(50f, mapped.left, .01f); assertEquals(100f, mapped.top, .01f)
        assertEquals(150f, mapped.right, .01f); assertEquals(200f, mapped.bottom, .01f)
    }

    @Test fun fit_center_mapping_accounts_for_aspect_ratio_letterboxing() {
        val mapped = EvidenceCoordinateTransformer.map(rect(100, 100, 200, 200), 1000, 500, ImageViewport(1000f, 1000f))!!
        assertEquals(100f, mapped.left, .01f); assertEquals(350f, mapped.top, .01f)
        assertEquals(200f, mapped.right, .01f); assertEquals(450f, mapped.bottom, .01f)
    }

    @Test fun rotated_mapping_uses_rotated_image_extent() {
        val mapped = EvidenceCoordinateTransformer.map(rect(100, 50, 300, 150), 1000, 500, ImageViewport(1000f, 1000f), 90)!!
        assertEquals(600f, mapped.left, .01f); assertEquals(100f, mapped.top, .01f)
        assertEquals(700f, mapped.right, .01f); assertEquals(300f, mapped.bottom, .01f)
    }

    @Test fun multiple_boxes_are_retained_and_invalid_source_is_rejected() {
        val mapped = EvidenceCoordinateTransformer.mapAll(listOf(rect(0, 0, 10, 10), rect(20, 20, 30, 30)), 100, 100, ImageViewport(100f, 100f))
        assertEquals(2, mapped.size)
        assertNull(EvidenceCoordinateTransformer.map(rect(0, 0, 1, 1), 0, 100, ImageViewport(100f, 100f)))
        assertNull(EvidenceCoordinateTransformer.map(rect(-1, 0, 10, 10), 100, 100, ImageViewport(100f, 100f)))
        assertNull(EvidenceCoordinateTransformer.map(rect(10, 10, 10, 20), 100, 100, ImageViewport(100f, 100f)))
    }
}
