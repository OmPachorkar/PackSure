package com.example.packsure.evidence

import android.graphics.Rect
import com.example.packsure.domain.model.ExtractedField
import com.example.packsure.legal.model.ComplianceCheck

/** Converts existing extraction/compliance evidence into a UI-neutral selection. */
object EvidenceMapper {
    fun fromField(title: String, field: ExtractedField<String>) = EvidenceRequest(
        title = title,
        status = field.state.toComplianceStatus(),
        detectedValue = field.value,
        explanation = field.locationHint?.let { "This declaration requires verification: $it" }
            ?: if (field.value != null) "This value was detected in the scanned package evidence." else "No matching declaration was detected in the scanned image.",
        evidence = field.evidence
    )

    fun fromCheck(check: ComplianceCheck) = EvidenceRequest(
        title = check.field,
        status = check.status,
        detectedValue = check.detectedValue,
        explanation = check.explanation,
        legalReference = check.legalReference,
        verificationAction = check.verificationAction,
        evidence = check.evidence
    )
}

private fun com.example.packsure.domain.model.VerificationState.toComplianceStatus() = when (this) {
    com.example.packsure.domain.model.VerificationState.DETECTED -> com.example.packsure.legal.model.ComplianceStatus.VERIFIED
    com.example.packsure.domain.model.VerificationState.VERIFICATION_REQUIRED -> com.example.packsure.legal.model.ComplianceStatus.VERIFICATION_REQUIRED
    // A missing extraction is not a legal finding. The compliance engine may later
    // elevate it only when it has explicit complete-coverage context.
    com.example.packsure.domain.model.VerificationState.NOT_DETECTED -> com.example.packsure.legal.model.ComplianceStatus.VERIFICATION_REQUIRED
}
