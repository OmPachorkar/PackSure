package com.example.packsure.evidence

import android.graphics.Rect
import com.example.packsure.extraction.PackageInfoExtractor
import com.example.packsure.legal.engine.ComplianceRuleEngine
import com.example.packsure.legal.model.ComplianceContext
import com.example.packsure.legal.model.EvidenceCoverage
import com.example.packsure.legal.model.ComplianceStatus
import com.example.packsure.ocr.OcrFragment
import com.example.packsure.ocr.OcrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceMappingTest {
    private fun rect(l: Int, t: Int, r: Int, b: Int) = Rect().apply { left = l; top = t; right = r; bottom = b }

    @Test fun missing_field_evidence_is_verification_required_not_a_violation() {
        val request = EvidenceMapper.fromField("MRP", com.example.packsure.domain.model.ExtractedField())
        assertEquals(ComplianceStatus.VERIFICATION_REQUIRED, request.status)
        assertTrue(!request.hasVisualEvidence)
    }

    @Test fun unknown_prefix_manufacturer_check_maps_to_production_code_box() {
        val codeBox = rect(50, 300, 350, 340)
        val info = PackageInfoExtractor().extract(OcrResult("", listOf(
            OcrFragment("KCN: MFG BY: MOON BEVERAGES LTD", null, rect(40, 100, 400, 140)),
            OcrFragment("PRODUCTION CODE 92025", null, codeBox)
        ), 1000, 800))
        val check = ComplianceRuleEngine().evaluate(info, ComplianceContext(EvidenceCoverage.COMPLETE)).checks.first { it.field == "Manufacturer / packer" }
        val request = EvidenceMapper.fromCheck(check)
        assertTrue(request.hasVisualEvidence)
        val actual = request.evidence.single().boundingBox!!
        assertEquals(codeBox.left, actual.left)
        assertEquals(codeBox.top, actual.top)
        assertEquals(codeBox.right, actual.right)
        assertEquals(codeBox.bottom, actual.bottom)
    }

    @Test fun location_instruction_retains_its_own_evidence_box_without_an_mrp_value() {
        val instructionBox = rect(20, 500, 600, 560)
        val info = PackageInfoExtractor().extract(OcrResult("", listOf(OcrFragment("MRP (INCL. OF ALL TAXES): SEE BOTTOM OF CAN", null, instructionBox)), 800, 1000))
        val check = ComplianceRuleEngine().evaluate(info, ComplianceContext(EvidenceCoverage.COMPLETE)).checks.first { it.field == "MRP" }
        val request = EvidenceMapper.fromCheck(check)
        assertTrue(request.hasVisualEvidence)
        assertEquals(null, request.detectedValue)
        val actual = request.evidence.single().boundingBox!!
        assertEquals(instructionBox.left, actual.left)
        assertEquals(instructionBox.top, actual.top)
        assertEquals(instructionBox.right, actual.right)
        assertEquals(instructionBox.bottom, actual.bottom)
    }
}
