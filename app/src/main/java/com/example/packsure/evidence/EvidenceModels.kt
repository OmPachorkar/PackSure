package com.example.packsure.evidence

import com.example.packsure.domain.model.FieldEvidence
import com.example.packsure.legal.model.ComplianceStatus

data class EvidenceRequest(
    val title: String,
    val status: ComplianceStatus?,
    val detectedValue: String?,
    val explanation: String,
    val legalReference: String? = null,
    val verificationAction: String? = null,
    val evidence: List<FieldEvidence>
) {
    val hasVisualEvidence: Boolean get() = evidence.any { it.boundingBox != null }
}

data class ImageViewport(val width: Float, val height: Float)
data class DisplayRect(val left: Float, val top: Float, val right: Float, val bottom: Float)
