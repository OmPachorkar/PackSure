package com.example.packsure.extraction

import com.example.packsure.domain.model.VerificationState
import com.example.packsure.ocr.OcrFragment
import com.example.packsure.ocr.OcrResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageInfoExtractorTest {
    private val extractor = PackageInfoExtractor()
    private fun extract(text: String) = extractor.extract(OcrResult(text, text.lines().filter(String::isNotBlank).map { OcrFragment(it, null, null) }))

    @Test fun beverage_fields_are_extracted_without_product_specific_rules() {
        val info = extract("""PRODUCT: ORANGE FRUIT DRINK
NET QUANTITY: 500 ml
MRP ₹40 (INCL. OF ALL TAXES)
MFD: 08/2026
BEST BEFORE 6 MONTHS FROM MFD
MANUFACTURED BY: ABC BEVERAGES PVT LTD
CONSUMER CARE: 1800-000-0000
MADE IN INDIA""")
        assertEquals("ORANGE FRUIT DRINK", info.productName.value)
        assertEquals("500 mL", info.netQuantity.value)
        assertEquals("₹40", info.mrp.value)
        assertTrue(info.mrpTaxInclusive == true)
        assertEquals("ABC BEVERAGES PVT LTD", info.manufacturer.value)
        assertEquals("08/2026", info.manufacturingDate.value)
        assertEquals("6 MONTHS FROM MFD", info.bestBefore.value)
        assertEquals("1800-000-0000", info.consumerCarePhone.value)
        assertEquals("INDIA", info.countryOfOrigin.value)
    }

    @Test fun food_product_detects_packer_email_and_origin() {
        val info = extract("""WHOLE WHEAT BISCUITS
NET WT. 200 g
MRP ₹60
PACKED BY: XYZ FOODS LTD
PKD: 08/2026
BEST BEFORE 9 MONTHS FROM PACKING
CONSUMER CARE: care@example.com
COUNTRY OF ORIGIN: INDIA""")
        assertEquals("WHOLE WHEAT BISCUITS", info.productName.value)
        assertEquals("200 g", info.netQuantity.value)
        assertEquals("XYZ FOODS LTD", info.packer.value)
        assertEquals("08/2026", info.packingDate.value)
        assertEquals("care@example.com", info.consumerCareEmail.value)
        assertEquals("INDIA", info.countryOfOrigin.value)
        assertNull(info.manufacturer.value)
    }

    @Test fun imported_product_keeps_importer_and_address_separate() {
        val info = extract("""PRODUCT: FACE SERUM
NET CONTENT 100 ml
MRP ₹499 INCLUSIVE OF ALL TAXES
IMPORTED BY: GLOBAL IMPORTS PVT LTD
ADDRESS: MUMBAI, MAHARASHTRA - 400001
COUNTRY OF ORIGIN: JAPAN
CONSUMER CARE: 1800-000-1111""")
        assertEquals("GLOBAL IMPORTS PVT LTD", info.importer.value)
        assertTrue(info.importerAddress.value?.contains("400001") == true)
        assertEquals("JAPAN", info.countryOfOrigin.value)
        assertNull(info.manufacturer.value)
    }

    @Test fun household_and_personal_care_formats_use_the_same_rules() {
        val household = extract("""LIQUID DETERGENT
NET CONTENT: 1 L
MRP: ₹180
MANUFACTURED BY: CLEAN PRODUCTS LTD
BATCH NO: CP0826
MFG: 08/2026
CONSUMER CARE: 1800-000-2222""")
        val care = extract("""SHAMPOO
NET CONTENT: 180 ml
MRP ₹249
MANUFACTURED BY: CARE COSMETICS LTD
MFG DATE: 08/2026
BEST BEFORE: 24 MONTHS FROM MFG
CONSUMER CARE: care@example.com""")
        assertEquals("1 L", household.netQuantity.value)
        assertEquals("CP0826", household.batchNumber.value)
        assertEquals("CLEAN PRODUCTS LTD", household.manufacturer.value)
        assertEquals("180 mL", care.netQuantity.value)
        assertEquals("CARE COSMETICS LTD", care.manufacturer.value)
        assertEquals("24 MONTHS FROM MFG", care.bestBefore.value)
    }

    @Test fun mrp_referenced_elsewhere_is_not_invented() {
        val info = extract("MRP (INCL. OF ALL TAXES): SEE BOTTOM OF CAN")
        assertNull(info.mrp.value)
        assertEquals(VerificationState.VERIFICATION_REQUIRED, info.mrp.state)
        assertTrue(info.mrpVerificationRequired)
    }

    @Test fun known_batch_prefix_resolves_manufacturer() {
        val info = extract("""KCN: MFG BY: MOON BEVERAGES LTD
TRP: MFG BY: SLMG BEVERAGES PVT LTD
PRODUCTION CODE KCN12345""")
        assertEquals("MOON BEVERAGES LTD", info.manufacturer.value)
        assertEquals("KCN12345", info.productionCode.value)
        assertFalse(info.manufacturerVerificationRequired)
    }

    @Test fun unknown_batch_prefix_never_guesses_manufacturer() {
        val info = extract("""KCN: MFG BY: MOON BEVERAGES LTD
TRP: MFG BY: SLMG BEVERAGES PVT LTD
PRODUCTION CODE 92025""")
        assertNull(info.manufacturer.value)
        assertEquals(VerificationState.VERIFICATION_REQUIRED, info.manufacturer.state)
        assertTrue(info.manufacturerVerificationRequired)
    }

    @Test fun qr_address_instruction_requires_verification() {
        val info = extract("SCAN QR CODE FOR MANUFACTURER'S ADDRESS")
        assertEquals(VerificationState.VERIFICATION_REQUIRED, info.manufacturerAddress.state)
        assertTrue(info.qrCodePresent)
    }

    @Test fun diet_coke_regression_preserves_unknowns() {
        val info = extract("""FOR MANUFACTURER'S NAME AND LIC NO., REFER THE FIRST THREE CHARACTERS OF THE BATCH NO. AND SEE BELOW. SCAN QR CODE FOR MANUFACTURER'S ADDRESS.
KCN: MFG BY: MOON BEVERAGES LTD
TRP: MFG BY: SLMG BEVERAGES PVT LTD
LBL: MFG BY: LUDHIANA BEVERAGES PVT LTD
NET QUANTITY: 330 ml
PRODUCTION CODE 92025
FOR DATE OF MANUFACTURE, EXPIRY, BATCH NO., USP & MRP (INCL. OF ALL TAXES): SEE BOTTOM OF CAN.""")
        assertEquals("330 mL", info.netQuantity.value)
        assertNull(info.manufacturer.value)
        assertTrue(info.manufacturerVerificationRequired)
        assertTrue(info.mrpVerificationRequired)
        assertTrue(info.dateVerificationRequired)
        assertEquals(VerificationState.VERIFICATION_REQUIRED, info.manufacturerAddress.state)
    }

    @Test fun spacing_normalization_handles_split_mrp_label() {
        val info = extract("M R P : Rs. 50\nNET WEIGHT : 250 g")
        assertEquals("₹50", info.mrp.value)
        assertEquals("250 g", info.netQuantity.value)
    }

    @Test fun partial_ocr_keeps_missing_fields_missing_instead_of_fabricating_them() {
        val info = extract("NET CONTENT: 75 g\nMRP ₹35")
        assertEquals("75 g", info.netQuantity.value)
        assertEquals("₹35", info.mrp.value)
        assertEquals(VerificationState.NOT_DETECTED, info.manufacturer.state)
        assertEquals(VerificationState.NOT_DETECTED, info.countryOfOrigin.state)
        assertNull(info.expiryDate.value)
    }

    @Test fun difficult_ocr_spacing_and_partial_labels_remain_conservative() {
        val info = extract("""S H A M P O O
N E T  C O N T E N T : 180 m l
M R P : R s . 249
MFG : 08/2026
CONSUMER CARE care @ example . com""")
        assertEquals("180 mL", info.netQuantity.value)
        assertEquals("₹249", info.mrp.value)
        assertEquals("08/2026", info.manufacturingDate.value)
        assertNull(info.manufacturer.value)
        assertNull(info.consumerCareEmail.value)
    }
}
