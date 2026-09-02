package com.example.packsure.legal.registry

import com.example.packsure.legal.model.ComplianceSeverity

data class LegalRule(
    val id: String,
    val title: String,
    val description: String,
    val legalReference: String,
    val severity: ComplianceSeverity
)

/** Local MVP registry. No project legal-source files existed when Stage 4 was added. */
object LegalRuleRegistry {
    val responsibleParty = LegalRule("responsible_party", "Responsible party declaration", "Name and address of manufacturer, packer or importer where applicable.", "Rule 6(1)(a)", ComplianceSeverity.HIGH)
    val commonName = LegalRule("common_name", "Common or generic name", "Common or generic name of the commodity.", "Rule 6(1)(b)", ComplianceSeverity.MODERATE)
    val quantity = LegalRule("net_quantity", "Net quantity", "Net quantity declaration; related quantity provisions are referenced for screening.", "Rule 6(1)(c), Rules 12 & 13", ComplianceSeverity.HIGH)
    val date = LegalRule("date", "Manufacturing or packing date", "Manufacturing, packing or applicable import date declaration.", "Rule 6(1)(d)", ComplianceSeverity.MODERATE)
    val mrp = LegalRule("mrp", "Maximum retail price", "Maximum retail price declaration, including tax-inclusive wording where detected.", "Rule 2(m), Rule 6(1)(e)", ComplianceSeverity.HIGH)
    val consumerCare = LegalRule("consumer_care", "Consumer care information", "Consumer care contact details.", "Rule 6(2)", ComplianceSeverity.MODERATE)
    val importer = LegalRule("importer", "Importer declaration", "Importer and country-of-origin information for an imported commodity.", "Rule 6(1)(a)", ComplianceSeverity.HIGH)
    val smallPackage = LegalRule("small_package", "Small-package exemption", "Potential applicability of the small-package exemption requires package-size evidence.", "Rule 26(a)", ComplianceSeverity.INFORMATIONAL)
}
