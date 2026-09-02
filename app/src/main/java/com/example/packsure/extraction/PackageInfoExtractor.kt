package com.example.packsure.extraction

import com.example.packsure.domain.model.DetectionConfidence
import com.example.packsure.domain.model.ExtractedField
import com.example.packsure.domain.model.ExtractionMethod
import com.example.packsure.domain.model.PackageInfo
import com.example.packsure.domain.model.VerificationState
import com.example.packsure.ocr.OcrResult

class PackageInfoExtractor(private val normalizer: TextNormalizer = TextNormalizer()) {
    fun extract(result: OcrResult): PackageInfo {
        val lines = normalizer.lines(result)
        val ctx = ExtractionContext(lines)
        val quantity = QuantityExtractor.extract(ctx)
        val price = PriceExtractor.extract(ctx)
        val party = PartyExtractor.extract(ctx)
        val dates = DateExtractor.extract(ctx)
        val contact = ContactExtractor.extract(ctx)
        val origin = OriginExtractor.extract(ctx)
        val product = ProductExtractor.extract(ctx)
        val warnings = buildList {
            addAll(party.warnings)
            if (price.field.state == VerificationState.VERIFICATION_REQUIRED) add("MRP is referenced outside the scanned area.")
            if (dates.verificationRequired) add("One or more dates are referenced outside the scanned area.")
        }.distinct()
        return PackageInfo(
            productName = product.name, brandName = product.brand, commonGenericName = product.generic,
            netQuantity = quantity, mrp = price.field, mrpCurrency = if (price.field.value != null) "INR" else null,
            mrpTaxInclusive = price.taxInclusive, mrpLocationHint = price.field.locationHint,
            manufacturer = party.manufacturer, manufacturerAddress = party.manufacturerAddress,
            packer = party.packer, packerAddress = party.packerAddress, importer = party.importer, importerAddress = party.importerAddress,
            manufacturingDate = if (dates.manufacturing.state == VerificationState.NOT_DETECTED && dates.reference.state == VerificationState.VERIFICATION_REQUIRED) dates.reference else dates.manufacturing,
            packingDate = dates.packing, expiryDate = dates.expiry, bestBefore = dates.bestBefore,
            batchNumber = ctx.labeledValue("(?:BATCH(?:\\s*(?:NO|NUMBER))?|B\\.?NO)", code = true),
            lotNumber = ctx.labeledValue("LOT(?:\\s*(?:NO|NUMBER))?", code = true), productionCode = party.productionCode,
            consumerCarePhone = contact.phone, consumerCareEmail = contact.email, consumerCareAddress = contact.address,
            countryOfOrigin = origin, qrCodePresent = ctx.any("\\bQR\\s*CODE\\b"), barcodePresent = false,
            manufacturerVerificationRequired = party.manufacturer.state == VerificationState.VERIFICATION_REQUIRED,
            mrpVerificationRequired = price.field.state == VerificationState.VERIFICATION_REQUIRED,
            dateVerificationRequired = dates.verificationRequired, extractionWarnings = warnings
        )
    }
}

internal class ExtractionContext(val lines: List<NormalizedLine>) {
    fun any(pattern: String) = lines.any { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(it.normalized) }
    fun first(pattern: String): NormalizedLine? = lines.firstOrNull { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(it.normalized) }
    fun nextAfter(line: NormalizedLine): NormalizedLine? = lines.dropWhile { it !== line }.drop(1).firstOrNull { it.normalized.isNotBlank() }
    fun field(value: String, line: NormalizedLine, method: ExtractionMethod = ExtractionMethod.PATTERN, confidence: DetectionConfidence = DetectionConfidence.HIGH) = ExtractedField(value.trim(), VerificationState.DETECTED, confidence, listOf(line.evidence), method)
    fun required(line: NormalizedLine, hint: String? = null) = ExtractedField<String>(null, VerificationState.VERIFICATION_REQUIRED, DetectionConfidence.LOW, listOf(line.evidence), ExtractionMethod.LOCATION_REFERENCE, hint ?: line.raw)
    fun labeledValue(label: String, code: Boolean = false): ExtractedField<String> {
        val regex = Regex("(?:$label)\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9./-]{1,})", RegexOption.IGNORE_CASE)
        val line = lines.firstOrNull { regex.containsMatchIn(it.normalized) } ?: return ExtractedField()
        val value = regex.find(line.normalized)?.groupValues?.get(1) ?: return ExtractedField()
        return field(value, line, confidence = if (code) DetectionConfidence.MEDIUM else DetectionConfidence.HIGH)
    }
}

private object ProductExtractor {
    private val banned = Regex("MRP|NET QUANTITY|INGREDIENT|MANUFACTURED|MFG|PACKED|IMPORTED|BATCH|DATE|CONSUMER|COUNTRY|ADDRESS|BEST BEFORE", RegexOption.IGNORE_CASE)
    fun extract(ctx: ExtractionContext): ProductFields {
        fun labeled(label: String) = ctx.first("$label\\s*[:\\-]")?.let { line ->
            val value = line.normalized.replaceFirst(Regex("^.*?[:\\-]"), "").trim(); if (value.isNotBlank()) ctx.field(value, line) else ctx.nextAfter(line)?.let { ctx.field(it.raw, it, ExtractionMethod.CONTEXT, DetectionConfidence.MEDIUM) }
        }
        val name = labeled("PRODUCT(?:\\s+NAME)?") ?: ctx.lines.take(8).firstOrNull { line ->
            line.normalized.length in 3..60 && !banned.containsMatchIn(line.normalized) && !Regex("[₹]|\\b(?:Rs|INR)\\b|\\d{2,}", RegexOption.IGNORE_CASE).containsMatchIn(line.normalized)
        }?.let { ctx.field(it.raw, it, ExtractionMethod.CONTEXT, DetectionConfidence.LOW) } ?: ExtractedField()
        return ProductFields(name, labeled("BRAND") ?: ExtractedField(), labeled("(?:COMMON|GENERIC)(?:\\s+NAME)?|PRODUCT DESCRIPTION") ?: ExtractedField())
    }
    data class ProductFields(val name: ExtractedField<String>, val brand: ExtractedField<String>, val generic: ExtractedField<String>)
}

private object QuantityExtractor {
    private val quantity = Regex("(?:NET\\s+(?:QUANTITY|CONTENT)|NET\\s*(?:WT|WEIGHT)|QUANTITY)?\\s*[:\\-]?\\s*(\\d+(?:\\.\\d+)?\\s*(?:x\\s*\\d+(?:\\.\\d+)?\\s*)?(?:mg|g|kg|ml|l|litre|liter)s?)\\b", RegexOption.IGNORE_CASE)
    fun extract(ctx: ExtractionContext): ExtractedField<String> {
        val line = ctx.lines.firstOrNull { quantity.containsMatchIn(it.normalized) && (it.normalized.contains("NET", true) || quantity.find(it.normalized)!!.groupValues[1].isNotBlank()) } ?: return ExtractedField()
        val value = quantity.find(line.normalized)!!.groupValues[1].replace(Regex("(?i)\\bml\\b"), "mL").replace(Regex("(?i)\\blitre?s?\\b"), "L")
        return ctx.field(value, line)
    }
}

private object PriceExtractor {
    private val price = Regex("(?:MRP|MAXIMUM RETAIL PRICE)\\s*(?:\\([^)]*\\))?\\s*[:\\-]?\\s*(₹|Rs\\.?|INR)\\s*(\\d+(?:[.,]\\d{1,2})?)", RegexOption.IGNORE_CASE)
    private val elsewhere = Regex("(?:MRP|PRICE).*(?:SEE|PRINTED).*(?:BOTTOM|BACK|SIDE|BELOW|PANEL)|(?:SEE|PRINTED).*(?:BOTTOM|BACK|SIDE|BELOW|PANEL).*(?:MRP|PRICE)", RegexOption.IGNORE_CASE)
    fun extract(ctx: ExtractionContext): PriceFields {
        val line = ctx.lines.firstOrNull { price.containsMatchIn(it.normalized) }
        if (line != null) {
            val match = price.find(line.normalized)!!
            return PriceFields(ctx.field("₹${match.groupValues[2].replace(',', '.')}", line), line.normalized.contains("INCL", true) || line.normalized.contains("INCLUSIVE", true))
        }
        val reference = ctx.lines.firstOrNull { elsewhere.containsMatchIn(it.normalized) }
        return PriceFields(reference?.let { ctx.required(it) } ?: ExtractedField(), null)
    }
    data class PriceFields(val field: ExtractedField<String>, val taxInclusive: Boolean?)
}

private object PartyExtractor {
    private val table = Regex("\\b([A-Z0-9]{2,4})\\s*:\\s*(?:MFG|MANUFACTURED)\\s+BY\\s*:?\\s*(.+)", RegexOption.IGNORE_CASE)
    private val production = Regex("(?:PRODUCTION\\s*CODE|BATCH(?:\\s*(?:NO|NUMBER))?|LOT(?:\\s*(?:NO|NUMBER))?)\\s*[:#-]?\\s*([A-Z0-9][A-Z0-9./-]{1,})", RegexOption.IGNORE_CASE)
    fun extract(ctx: ExtractionContext): PartyFields {
        val directManufacturer = party(ctx, "(?:MANUFACTURED|MFG)\\s+BY|MANUFACTURER")
        val packer = party(ctx, "(?:PACKED\\s+BY|PACKER)")
        val importer = party(ctx, "(?:IMPORTED\\s+BY|IMPORTER)")
        val mappings = ctx.lines.mapNotNull { line -> table.find(line.normalized)?.let { match -> match.groupValues[1].uppercase() to Pair(match.groupValues[2].trim(), line) } }.toMap()
        val codeLine = ctx.lines.firstOrNull { production.containsMatchIn(it.normalized) }
        val code: ExtractedField<String> = codeLine?.let { line ->
            production.find(line.normalized)?.groupValues?.get(1)?.let { ctx.field(it, line, confidence = DetectionConfidence.MEDIUM) }
        } ?: ExtractedField()
        val lookupManufacturer: ExtractedField<String> = if (directManufacturer.value == null && mappings.isNotEmpty() && code.value != null) {
            val matched = mappings.entries.firstOrNull { code.value.uppercase().startsWith(it.key) }
            matched?.let { (_, valueLine) -> ctx.field(valueLine.first, valueLine.second, ExtractionMethod.PREFIX_LOOKUP, DetectionConfidence.MEDIUM) }
                ?: codeLine?.let { ctx.required(it, "Manufacturer lookup table detected; code prefix was not matched.") }
                ?: ExtractedField()
        } else ExtractedField()
        val manufacturer = if (directManufacturer.value != null) directManufacturer else lookupManufacturer
        val qrInstruction = ctx.first("QR\\s*CODE.*(?:MANUFACTURER|ADDRESS)|(?:MANUFACTURER|ADDRESS).*QR\\s*CODE")
        val warnings = buildList {
            if (manufacturer.state == VerificationState.VERIFICATION_REQUIRED) add("A manufacturer lookup table was detected, but the production code could not be matched.")
            if (qrInstruction != null) add("Manufacturer address is indicated through a QR code and requires verification.")
        }
        val qrAddress = qrInstruction?.let { ctx.required(it) } ?: ExtractedField()
        val directAddress = address(ctx, directManufacturer)
        return PartyFields(manufacturer, if (directAddress.value != null) directAddress else qrAddress, packer, address(ctx, packer), importer, address(ctx, importer), code, warnings, qrAddress)
    }

    private fun party(ctx: ExtractionContext, label: String): ExtractedField<String> {
        val pattern = Regex("(?:$label)\\s*[:\\-]?\\s*(.*)", RegexOption.IGNORE_CASE)
        val tableEntry = Regex("^\\s*[A-Z0-9]{2,4}\\s*:\\s*(?:MFG|MANUFACTURED)\\s+BY", RegexOption.IGNORE_CASE)
        val instruction = Regex("(?:FOR\\s+MANUFACTURER|REFER\\s+THE\\s+FIRST|LOOKUP)", RegexOption.IGNORE_CASE)
        val line = ctx.lines.firstOrNull { pattern.containsMatchIn(it.normalized) && !tableEntry.containsMatchIn(it.normalized) && !instruction.containsMatchIn(it.normalized) } ?: return ExtractedField()
        val remainder = pattern.find(line.normalized)?.groupValues?.get(1)?.trim().orEmpty()
        val candidate = remainder.ifBlank { ctx.nextAfter(line)?.raw.orEmpty() }
        if (candidate.isBlank() || candidate.startsWith("ADDRESS", true)) return ExtractedField()
        return ctx.field(candidate, line, if (remainder.isBlank()) ExtractionMethod.CONTEXT else ExtractionMethod.PATTERN, if (remainder.isBlank()) DetectionConfidence.MEDIUM else DetectionConfidence.HIGH)
    }

    private fun address(ctx: ExtractionContext, party: ExtractedField<String>): ExtractedField<String> {
        if (party.value == null) return ExtractedField()
        val partyLine = ctx.lines.firstOrNull { it.evidence == party.evidence.firstOrNull() } ?: return ExtractedField()
        val following = ctx.lines.dropWhile { it !== partyLine }.drop(1).take(3)
        val address = following.takeWhile { line -> !Regex("(?:MRP|NET|MFG|PACKED|IMPORTED|BATCH|DATE|CONSUMER|BEST BEFORE)", RegexOption.IGNORE_CASE).containsMatchIn(line.normalized) }
            .takeIf { block -> block.any { it.normalized.contains("ADDRESS", true) || Regex("\\b\\d{6}\\b").containsMatchIn(it.normalized) || it.normalized.contains(",") } }
            ?.joinToString(" ") { it.raw }
        return if (address != null) ctx.field(address, following.first(), ExtractionMethod.CONTEXT, DetectionConfidence.LOW) else ExtractedField()
    }

    data class PartyFields(
        val manufacturer: ExtractedField<String>, val manufacturerAddress: ExtractedField<String>,
        val packer: ExtractedField<String>, val packerAddress: ExtractedField<String>,
        val importer: ExtractedField<String>, val importerAddress: ExtractedField<String>,
        val productionCode: ExtractedField<String>, val warnings: List<String>, val qrAddress: ExtractedField<String>
    )
}

private object DateExtractor {
    private val date = "(?:\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}[/-]\\d{2,4}|(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|SEPT|OCT|NOV|DEC)[A-Z]*\\s*[-/]?\\s*\\d{2,4})"
    private fun field(ctx: ExtractionContext, label: String): ExtractedField<String> {
        val pattern = Regex("(?:$label)\\s*(?:DATE)?\\s*[:\\-]?\\s*($date)", RegexOption.IGNORE_CASE)
        val line = ctx.lines.firstOrNull { pattern.containsMatchIn(it.normalized) } ?: return ExtractedField()
        return ctx.field(pattern.find(line.normalized)!!.groupValues[1], line)
    }
    fun extract(ctx: ExtractionContext): DateFields {
        val mfg = field(ctx, "MFG|MFD|MANUFACTURED|DATE OF MANUFACTURE")
        val packed = field(ctx, "PKD|PACKED|DATE OF PACKING")
        val expiry = field(ctx, "EXP|EXPIRY|USE BY")
        val bestLine = ctx.first("BEST\\s+BEFORE")
        val best = bestLine?.let { line ->
            val phrase = line.normalized.replaceFirst(Regex(".*?BEST\\s+BEFORE\\s*[:\\-]?", RegexOption.IGNORE_CASE), "").trim()
            if (phrase.isNotBlank()) ctx.field(phrase, line, confidence = DetectionConfidence.MEDIUM) else ctx.nextAfter(line)?.let { ctx.field(it.raw, it, ExtractionMethod.CONTEXT, DetectionConfidence.MEDIUM) }
        } ?: ExtractedField()
        val elsewhere = ctx.first("(?:DATE OF MANUFACTURE|MFG|MFD|EXPIRY|EXP|BATCH).*?(?:SEE|PRINTED).*?(?:BOTTOM|BACK|SIDE|BELOW)|(?:SEE|PRINTED).*?(?:BOTTOM|BACK|SIDE|BELOW).*?(?:DATE|MFG|EXPIRY)")
        val reference = elsewhere?.let { ctx.required(it) } ?: ExtractedField()
        return DateFields(mfg, packed, expiry, best, elsewhere != null, reference)
    }
    data class DateFields(val manufacturing: ExtractedField<String>, val packing: ExtractedField<String>, val expiry: ExtractedField<String>, val bestBefore: ExtractedField<String>, val verificationRequired: Boolean, val reference: ExtractedField<String>)
}

private object ContactExtractor {
    private val phone = Regex("(?<!\\d)(?:\\+91[ -]?)?[6-9]\\d{9}|(?<!\\d)1[ -]?800[ -]?\\d{3}[ -]?\\d{3,4}(?!\\d)")
    private val email = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    fun extract(ctx: ExtractionContext): ContactFields {
        val phoneLine = ctx.lines.firstOrNull { it.normalized.contains("CARE", true) || it.normalized.contains("HELPLINE", true) || it.normalized.contains("TOLL", true) }
            ?.takeIf { phone.containsMatchIn(it.normalized) }
        val emailLine = ctx.lines.firstOrNull { email.containsMatchIn(it.normalized) }
        val addressLine = ctx.first("(?:CONSUMER|CUSTOMER)\\s+(?:CARE|SERVICE).*ADDRESS")
        return ContactFields(phoneLine?.let { ctx.field(phone.find(it.normalized)!!.value, it) } ?: ExtractedField(), emailLine?.let { ctx.field(email.find(it.normalized)!!.value, it) } ?: ExtractedField(), addressLine?.let { ctx.nextAfter(it)?.let { next -> ctx.field(next.raw, next, ExtractionMethod.CONTEXT, DetectionConfidence.LOW) } } ?: ExtractedField())
    }
    data class ContactFields(val phone: ExtractedField<String>, val email: ExtractedField<String>, val address: ExtractedField<String>)
}

private object OriginExtractor {
    private val origin = Regex("(?:MADE\\s+IN|PRODUCT\\s+OF|COUNTRY\\s+OF\\s+ORIGIN\\s*[:\\-]?|IMPORTED\\s+FROM)\\s*([A-Z][A-Z ]{1,})", RegexOption.IGNORE_CASE)
    fun extract(ctx: ExtractionContext): ExtractedField<String> {
        val line = ctx.lines.firstOrNull { origin.containsMatchIn(it.normalized) } ?: return ExtractedField()
        return ctx.field(origin.find(line.normalized)!!.groupValues[1].trim(), line)
    }
}
