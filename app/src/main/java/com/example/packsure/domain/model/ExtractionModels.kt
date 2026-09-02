package com.example.packsure.domain.model

import android.graphics.Rect

enum class VerificationState { DETECTED, NOT_DETECTED, VERIFICATION_REQUIRED }
enum class ExtractionMethod { PATTERN, CONTEXT, PREFIX_LOOKUP, LOCATION_REFERENCE, NONE }
enum class DetectionConfidence { HIGH, MEDIUM, LOW, NONE }

data class FieldEvidence(
    val sourceText: String,
    val boundingBox: Rect?
)

/** A value is never inferred from an absent OCR declaration. */
data class ExtractedField<T>(
    val value: T? = null,
    val state: VerificationState = VerificationState.NOT_DETECTED,
    val confidence: DetectionConfidence = DetectionConfidence.NONE,
    val evidence: List<FieldEvidence> = emptyList(),
    val method: ExtractionMethod = ExtractionMethod.NONE,
    val locationHint: String? = null
)

data class PackageInfo(
    val productName: ExtractedField<String> = ExtractedField(),
    val brandName: ExtractedField<String> = ExtractedField(),
    val commonGenericName: ExtractedField<String> = ExtractedField(),
    val netQuantity: ExtractedField<String> = ExtractedField(),
    val mrp: ExtractedField<String> = ExtractedField(),
    val mrpCurrency: String? = null,
    val mrpTaxInclusive: Boolean? = null,
    val mrpLocationHint: String? = null,
    val manufacturer: ExtractedField<String> = ExtractedField(),
    val manufacturerAddress: ExtractedField<String> = ExtractedField(),
    val packer: ExtractedField<String> = ExtractedField(),
    val packerAddress: ExtractedField<String> = ExtractedField(),
    val importer: ExtractedField<String> = ExtractedField(),
    val importerAddress: ExtractedField<String> = ExtractedField(),
    val manufacturingDate: ExtractedField<String> = ExtractedField(),
    val packingDate: ExtractedField<String> = ExtractedField(),
    val expiryDate: ExtractedField<String> = ExtractedField(),
    val bestBefore: ExtractedField<String> = ExtractedField(),
    val batchNumber: ExtractedField<String> = ExtractedField(),
    val lotNumber: ExtractedField<String> = ExtractedField(),
    val productionCode: ExtractedField<String> = ExtractedField(),
    val consumerCarePhone: ExtractedField<String> = ExtractedField(),
    val consumerCareEmail: ExtractedField<String> = ExtractedField(),
    val consumerCareAddress: ExtractedField<String> = ExtractedField(),
    val countryOfOrigin: ExtractedField<String> = ExtractedField(),
    val qrCodePresent: Boolean = false,
    val barcodePresent: Boolean = false,
    val manufacturerVerificationRequired: Boolean = false,
    val mrpVerificationRequired: Boolean = false,
    val dateVerificationRequired: Boolean = false,
    val extractionWarnings: List<String> = emptyList()
)
