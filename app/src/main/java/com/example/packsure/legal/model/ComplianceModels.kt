package com.example.packsure.legal.model

import com.example.packsure.domain.model.FieldEvidence

enum class ComplianceStatus { VERIFIED, VERIFICATION_REQUIRED, POTENTIAL_NON_COMPLIANCE, NOT_APPLICABLE }
enum class ComplianceSeverity { INFORMATIONAL, MODERATE, HIGH }
enum class EvidenceCoverage { COMPLETE, INCOMPLETE, UNKNOWN }
enum class ProductCategory { BEVERAGE, FOOD, PERSONAL_CARE, HOUSEHOLD, IMPORTED_GOOD, OTHER, UNKNOWN }

data class ComplianceCheck(
    val ruleId: String,
    val field: String,
    val title: String,
    val status: ComplianceStatus,
    val explanation: String,
    val legalReference: String,
    val evidence: List<FieldEvidence> = emptyList(),
    val severity: ComplianceSeverity = ComplianceSeverity.MODERATE,
    val verificationAction: String? = null,
    val detectedValue: String? = null
)

data class ComplianceResult(
    val overallStatus: ComplianceStatus,
    val category: ProductCategory,
    val checks: List<ComplianceCheck>,
    val verifiedCount: Int,
    val verificationRequiredCount: Int,
    val potentialNonComplianceCount: Int,
    val notApplicableCount: Int,
    val screeningScore: Int?,
    val disclaimer: String = "PackSure provides an automated screening based on scanned package evidence. Results are indicative and should be verified by an authorized authority where required."
)

/** Optional context lets later image-coverage or size analysis make a screening decision more precise. */
data class ComplianceContext(
    val evidenceCoverage: EvidenceCoverage = EvidenceCoverage.UNKNOWN,
    val smallPackageMayApply: Boolean = false
)
