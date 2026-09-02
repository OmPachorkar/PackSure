package com.example.packsure.history

import com.example.packsure.domain.model.ExtractedField
import com.example.packsure.domain.model.PackageInfo
import com.example.packsure.domain.model.VerificationState
import com.example.packsure.legal.model.ComplianceResult
import com.example.packsure.legal.model.ComplianceStatus
import com.example.packsure.legal.model.ProductCategory
import com.example.packsure.ocr.OcrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InspectionReportContentTest {
    private val detected = ExtractedField("Sample product", VerificationState.DETECTED)
    private val record = InspectionRecord("test", 1L, null, OcrResult("raw OCR", emptyList()), PackageInfo(productName = detected, mrp = ExtractedField("₹50", VerificationState.DETECTED)), ComplianceResult(ComplianceStatus.VERIFICATION_REQUIRED, ProductCategory.OTHER, emptyList(), 0, 1, 0, 0, 0))

    @Test fun report_content_includes_only_available_declarations_and_raw_ocr_reference() {
        val fields = InspectionReportContent.summaryFields(record)
        assertEquals(listOf("Product", "MRP"), fields.map { it.first })
        assertTrue(InspectionReportContent.rawOcrReference(record).contains("7 recognized characters"))
    }

    @Test fun history_snapshot_name_has_a_safe_fallback() {
        assertEquals("Sample product", record.displayName)
        assertEquals("Unnamed package", record.copy(packageInfo = PackageInfo()).displayName)
    }
}
