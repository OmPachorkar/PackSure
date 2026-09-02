package com.example.packsure.legal

import com.example.packsure.domain.model.ExtractedField
import com.example.packsure.domain.model.PackageInfo
import com.example.packsure.domain.model.VerificationState
import com.example.packsure.extraction.PackageInfoExtractor
import com.example.packsure.legal.engine.ComplianceRuleEngine
import com.example.packsure.legal.model.ComplianceContext
import com.example.packsure.legal.model.ComplianceStatus
import com.example.packsure.legal.model.EvidenceCoverage
import com.example.packsure.ocr.OcrFragment
import com.example.packsure.ocr.OcrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComplianceRuleEngineTest {
    private val engine = ComplianceRuleEngine()
    private val complete = ComplianceContext(EvidenceCoverage.COMPLETE)
    private fun detected(value: String) = ExtractedField(value, VerificationState.DETECTED)
    private fun extract(text: String) = PackageInfoExtractor().extract(OcrResult(text, text.lines().filter(String::isNotBlank).map { OcrFragment(it, null, null) }))
    private fun status(result: com.example.packsure.legal.model.ComplianceResult, field: String) = result.checks.first { it.field == field }.status

    @Test fun all_screened_declarations_detected_are_verified() {
        val info = PackageInfo(commonGenericName = detected("Fruit drink"), netQuantity = detected("500 mL"), mrp = detected("₹40"), mrpTaxInclusive = true, manufacturer = detected("ABC Beverages"), manufacturingDate = detected("08/2026"), consumerCarePhone = detected("18000000000"))
        val result = engine.evaluate(info, complete)
        assertEquals(ComplianceStatus.VERIFIED, result.overallStatus)
        assertEquals(ComplianceStatus.VERIFIED, status(result, "MRP"))
    }

    @Test fun missing_responsible_party_is_only_potential_with_complete_coverage() {
        val result = engine.evaluate(PackageInfo(commonGenericName = detected("Biscuits")), complete)
        assertEquals(ComplianceStatus.POTENTIAL_NON_COMPLIANCE, status(result, "Manufacturer / packer"))
        assertEquals(ComplianceStatus.POTENTIAL_NON_COMPLIANCE, result.overallStatus)
    }

    @Test fun incomplete_evidence_makes_missing_fields_verification_required() {
        val result = engine.evaluate(PackageInfo())
        assertEquals(ComplianceStatus.VERIFICATION_REQUIRED, status(result, "MRP"))
        assertEquals(ComplianceStatus.VERIFICATION_REQUIRED, result.overallStatus)
    }

    @Test fun quantity_mrp_date_and_consumer_care_checks_are_independent() {
        val info = PackageInfo(netQuantity = detected("1 L"), mrp = detected("₹180"), packingDate = detected("08/2026"), consumerCareEmail = detected("care@example.com"))
        val result = engine.evaluate(info, complete)
        assertEquals(ComplianceStatus.VERIFIED, status(result, "Net quantity"))
        assertEquals(ComplianceStatus.VERIFIED, status(result, "MRP"))
        assertEquals(ComplianceStatus.VERIFIED, status(result, "Manufacturing / packing date"))
        assertEquals(ComplianceStatus.VERIFIED, status(result, "Consumer care"))
    }

    @Test fun mrp_and_date_references_are_verification_required() {
        val info = PackageInfo(mrp = ExtractedField(state = VerificationState.VERIFICATION_REQUIRED, locationHint = "SEE BOTTOM OF CAN"), manufacturingDate = ExtractedField(state = VerificationState.VERIFICATION_REQUIRED, locationHint = "SEE BOTTOM OF CAN"), dateVerificationRequired = true)
        val result = engine.evaluate(info, complete)
        assertEquals(ComplianceStatus.VERIFICATION_REQUIRED, status(result, "MRP"))
        assertEquals(ComplianceStatus.VERIFICATION_REQUIRED, status(result, "Manufacturing / packing date"))
    }

    @Test fun imported_goods_require_importer_and_origin_together() {
        val verified = engine.evaluate(PackageInfo(importer = detected("Global Imports"), countryOfOrigin = detected("Japan")), complete)
        val originMissing = engine.evaluate(PackageInfo(importer = detected("Global Imports"), countryOfOrigin = ExtractedField()), complete)
        assertEquals(ComplianceStatus.VERIFIED, status(verified, "Importer & origin"))
        assertEquals(ComplianceStatus.VERIFICATION_REQUIRED, status(originMissing, "Importer & origin"))
    }

    @Test fun generic_category_classification_handles_beverage_food_household_and_personal_care() {
        assertEquals("BEVERAGE", engine.evaluate(PackageInfo(productName = detected("Orange Fruit Drink"))).category.name)
        assertEquals("FOOD", engine.evaluate(PackageInfo(productName = detected("Whole Wheat Biscuits"))).category.name)
        assertEquals("HOUSEHOLD", engine.evaluate(PackageInfo(productName = detected("Liquid Detergent"))).category.name)
        assertEquals("PERSONAL_CARE", engine.evaluate(PackageInfo(productName = detected("Shampoo"))).category.name)
    }

    @Test fun unknown_batch_prefix_from_generic_extractor_requires_manufacturer_verification() {
        val info = extract("""KCN: MFG BY: MOON BEVERAGES LTD
TRP: MFG BY: SLMG BEVERAGES PVT LTD
PRODUCTION CODE 92025""")
        val result = engine.evaluate(info, complete)
        assertEquals(ComplianceStatus.VERIFICATION_REQUIRED, status(result, "Manufacturer / packer"))
    }

    @Test fun known_batch_prefix_is_verified_without_brand_assumption() {
        val info = extract("KCN: MFG BY: MOON BEVERAGES LTD\nPRODUCTION CODE KCN123")
        assertEquals(ComplianceStatus.VERIFIED, status(engine.evaluate(info, complete), "Manufacturer / packer"))
    }

    @Test fun small_package_rule_needs_measurement_only_when_signalled() {
        val result = engine.evaluate(PackageInfo(), ComplianceContext(EvidenceCoverage.UNKNOWN, smallPackageMayApply = true))
        assertEquals(ComplianceStatus.VERIFICATION_REQUIRED, status(result, "Small-package exemption"))
    }

    @Test fun diet_coke_regression_keeps_mrp_date_and_manufacturer_in_review() {
        val info = extract("""KCN: MFG BY: MOON BEVERAGES LTD
TRP: MFG BY: SLMG BEVERAGES PVT LTD
NET QUANTITY: 330 ml
PRODUCTION CODE 92025
FOR DATE OF MANUFACTURE, EXPIRY, BATCH NO., USP & MRP (INCL. OF ALL TAXES): SEE BOTTOM OF CAN.""")
        val result = engine.evaluate(info, complete)
        assertEquals(ComplianceStatus.VERIFICATION_REQUIRED, status(result, "Manufacturer / packer"))
        assertEquals(ComplianceStatus.VERIFICATION_REQUIRED, status(result, "MRP"))
        assertEquals(ComplianceStatus.VERIFICATION_REQUIRED, status(result, "Manufacturing / packing date"))
        assertEquals(ComplianceStatus.VERIFIED, status(result, "Net quantity"))
        assertTrue(result.verificationRequiredCount >= 3)
    }
}
