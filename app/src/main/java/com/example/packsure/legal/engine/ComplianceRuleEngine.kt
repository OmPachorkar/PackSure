package com.example.packsure.legal.engine

import com.example.packsure.domain.model.ExtractedField
import com.example.packsure.domain.model.PackageInfo
import com.example.packsure.domain.model.VerificationState
import com.example.packsure.legal.model.ComplianceCheck
import com.example.packsure.legal.model.ComplianceContext
import com.example.packsure.legal.model.ComplianceResult
import com.example.packsure.legal.model.ComplianceStatus
import com.example.packsure.legal.model.EvidenceCoverage
import com.example.packsure.legal.model.ProductCategory
import com.example.packsure.legal.registry.LegalRule
import com.example.packsure.legal.registry.LegalRuleRegistry

class ComplianceRuleEngine {
    fun evaluate(info: PackageInfo, context: ComplianceContext = ComplianceContext()): ComplianceResult {
        val category = ProductCategoryClassifier.classify(info)
        val checks = buildList {
            add(responsibleParty(info, context))
            add(fieldCheck(LegalRuleRegistry.commonName, "Common / generic name", info.commonGenericName, context, "Common/generic name was detected in the scanned package area.", "The common/generic name was not detected in the scanned package area."))
            add(fieldCheck(LegalRuleRegistry.quantity, "Net quantity", info.netQuantity, context, "${info.netQuantity.value} was detected on the package.", "Net quantity was not detected in the scanned package area."))
            add(dateCheck(info, context))
            add(mrpCheck(info, context))
            add(consumerCheck(info, context))
            add(importerCheck(info, category, context))
            add(smallPackageCheck(context))
        }
        val evaluated = checks.filter { it.status != ComplianceStatus.NOT_APPLICABLE }
        val verified = checks.count { it.status == ComplianceStatus.VERIFIED }
        val review = checks.count { it.status == ComplianceStatus.VERIFICATION_REQUIRED }
        val potential = checks.count { it.status == ComplianceStatus.POTENTIAL_NON_COMPLIANCE }
        val notApplicable = checks.count { it.status == ComplianceStatus.NOT_APPLICABLE }
        val overall = when { potential > 0 -> ComplianceStatus.POTENTIAL_NON_COMPLIANCE; review > 0 -> ComplianceStatus.VERIFICATION_REQUIRED; evaluated.isNotEmpty() -> ComplianceStatus.VERIFIED; else -> ComplianceStatus.NOT_APPLICABLE }
        val score = evaluated.takeIf { it.isNotEmpty() }?.let { (verified * 100) / it.size }
        return ComplianceResult(overall, category, checks, verified, review, potential, notApplicable, score)
    }

    private fun responsibleParty(info: PackageInfo, context: ComplianceContext): ComplianceCheck {
        val detected = listOf(info.manufacturer, info.packer).firstOrNull { it.state == VerificationState.DETECTED }
        val requiresVerification = listOf(info.manufacturer, info.packer).firstOrNull { it.state == VerificationState.VERIFICATION_REQUIRED }
        return when {
            detected != null -> check(LegalRuleRegistry.responsibleParty, "Manufacturer / packer", ComplianceStatus.VERIFIED, "${detected.value} was detected as the responsible party.", detected)
            requiresVerification != null -> check(LegalRuleRegistry.responsibleParty, "Manufacturer / packer", ComplianceStatus.VERIFICATION_REQUIRED, "Responsible-party identification requires verification from another package area, QR code, or batch code.", requiresVerification, "Check the referenced package panel, QR code, or batch/production code.")
            else -> missing(LegalRuleRegistry.responsibleParty, "Manufacturer / packer", context, "Manufacturer or packer identification was not detected in the scanned package area.")
        }
    }

    private fun dateCheck(info: PackageInfo, context: ComplianceContext): ComplianceCheck {
        val date = listOf(info.manufacturingDate, info.packingDate).firstOrNull { it.state == VerificationState.DETECTED }
        return when {
            date != null -> check(LegalRuleRegistry.date, "Manufacturing / packing date", ComplianceStatus.VERIFIED, "${date.value} was detected as a manufacturing or packing date.", date)
            info.dateVerificationRequired -> check(LegalRuleRegistry.date, "Manufacturing / packing date", ComplianceStatus.VERIFICATION_REQUIRED, "The package refers to date information outside the scanned area.", evidence = listOf(info.manufacturingDate, info.packingDate, info.expiryDate).firstOrNull { it.evidence.isNotEmpty() } ?: ExtractedField(), action = "Check the referenced package panel for the date declaration.")
            else -> missing(LegalRuleRegistry.date, "Manufacturing / packing date", context, "Manufacturing or packing date was not detected in the scanned package area.")
        }
    }

    private fun mrpCheck(info: PackageInfo, context: ComplianceContext): ComplianceCheck = when (info.mrp.state) {
        VerificationState.DETECTED -> {
            val tax = if (info.mrpTaxInclusive == true) " The declaration indicates that the price includes applicable taxes." else " Tax-inclusive wording was not detected."
            check(LegalRuleRegistry.mrp, "MRP", ComplianceStatus.VERIFIED, "MRP ${info.mrp.value} was detected on the package.$tax", info.mrp)
        }
        VerificationState.VERIFICATION_REQUIRED -> check(LegalRuleRegistry.mrp, "MRP", ComplianceStatus.VERIFICATION_REQUIRED, "The package instructs the user to verify MRP in another package area.", info.mrp, "Check ${info.mrp.locationHint ?: "the referenced package panel"}.")
        VerificationState.NOT_DETECTED -> missing(LegalRuleRegistry.mrp, "MRP", context, "MRP was not detected in the scanned package area.")
    }

    private fun consumerCheck(info: PackageInfo, context: ComplianceContext): ComplianceCheck {
        val contact = listOf(info.consumerCarePhone, info.consumerCareEmail, info.consumerCareAddress).firstOrNull { it.state == VerificationState.DETECTED }
        return if (contact != null) check(LegalRuleRegistry.consumerCare, "Consumer care", ComplianceStatus.VERIFIED, "Consumer care information was detected: ${contact.value}.", contact)
        else missing(LegalRuleRegistry.consumerCare, "Consumer care", context, "Consumer care information was not detected in the scanned package area.")
    }

    private fun importerCheck(info: PackageInfo, category: ProductCategory, context: ComplianceContext): ComplianceCheck {
        if (category != ProductCategory.IMPORTED_GOOD) return check(LegalRuleRegistry.importer, "Importer", ComplianceStatus.NOT_APPLICABLE, "No imported-commodity signal was detected.")
        return when (info.importer.state) {
            VerificationState.DETECTED -> if (info.countryOfOrigin.state == VerificationState.DETECTED) check(LegalRuleRegistry.importer, "Importer & origin", ComplianceStatus.VERIFIED, "Importer ${info.importer.value} and country of origin ${info.countryOfOrigin.value} were detected.", info.importer)
            else check(LegalRuleRegistry.importer, "Importer & origin", ComplianceStatus.VERIFICATION_REQUIRED, "Importer information was detected, but country of origin requires verification.", info.importer, "Check the country-of-origin declaration.")
            VerificationState.VERIFICATION_REQUIRED -> check(LegalRuleRegistry.importer, "Importer", ComplianceStatus.VERIFICATION_REQUIRED, "Importer information requires verification from another package area.", info.importer, "Check the referenced package panel.")
            VerificationState.NOT_DETECTED -> missing(LegalRuleRegistry.importer, "Importer", context, "Importer information was not detected for this imported commodity.")
        }
    }

    private fun smallPackageCheck(context: ComplianceContext): ComplianceCheck = if (context.smallPackageMayApply) check(LegalRuleRegistry.smallPackage, "Small-package exemption", ComplianceStatus.VERIFICATION_REQUIRED, "Package dimensions are needed to assess whether a small-package exemption applies.", action = "Measure the package or verify its labelled dimensions.") else check(LegalRuleRegistry.smallPackage, "Small-package exemption", ComplianceStatus.NOT_APPLICABLE, "No small-package exemption signal is available in this inspection.")

    private fun fieldCheck(rule: LegalRule, field: String, value: ExtractedField<String>, context: ComplianceContext, success: String, absent: String): ComplianceCheck = when (value.state) {
        VerificationState.DETECTED -> check(rule, field, ComplianceStatus.VERIFIED, success, value)
        VerificationState.VERIFICATION_REQUIRED -> check(rule, field, ComplianceStatus.VERIFICATION_REQUIRED, "$field requires verification from another package area.", value, "Check ${value.locationHint ?: "the referenced package panel"}.")
        VerificationState.NOT_DETECTED -> missing(rule, field, context, absent)
    }

    private fun missing(rule: LegalRule, field: String, context: ComplianceContext, text: String): ComplianceCheck = if (context.evidenceCoverage == EvidenceCoverage.COMPLETE) check(rule, field, ComplianceStatus.POTENTIAL_NON_COMPLIANCE, "$text Available scan evidence appears sufficiently complete, but this remains an indicative screening finding.", action = "Inspect all package panels before drawing a conclusion.") else check(rule, field, ComplianceStatus.VERIFICATION_REQUIRED, "$text Scan coverage or OCR evidence is incomplete, so the declaration requires verification.", action = "Inspect additional package panels or capture a clearer image.")

    private fun check(rule: LegalRule, field: String, status: ComplianceStatus, explanation: String, evidence: ExtractedField<String> = ExtractedField(), action: String? = null) = ComplianceCheck(rule.id, field, rule.title, status, explanation, rule.legalReference, evidence.evidence, rule.severity, action, evidence.value)
}

object ProductCategoryClassifier {
    fun classify(info: PackageInfo): ProductCategory {
        if (info.importer.state == VerificationState.DETECTED || info.countryOfOrigin.state == VerificationState.DETECTED && info.importer.state != VerificationState.NOT_DETECTED) return ProductCategory.IMPORTED_GOOD
        val text = listOfNotNull(info.productName.value, info.commonGenericName.value).joinToString(" ").lowercase()
        return when {
            Regex("drink|water|juice|beverage|soda|milk").containsMatchIn(text) -> ProductCategory.BEVERAGE
            Regex("biscuit|flour|rice|spice|noodle|snack|chocolate|cereal|food").containsMatchIn(text) -> ProductCategory.FOOD
            Regex("shampoo|soap|toothpaste|lotion|face wash|cosmetic").containsMatchIn(text) -> ProductCategory.PERSONAL_CARE
            Regex("detergent|dishwash|cleaner|laundry").containsMatchIn(text) -> ProductCategory.HOUSEHOLD
            text.isBlank() -> ProductCategory.UNKNOWN
            else -> ProductCategory.OTHER
        }
    }
}
