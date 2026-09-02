package com.example.packsure.extraction

import com.example.packsure.domain.model.FieldEvidence
import com.example.packsure.ocr.OcrFragment
import com.example.packsure.ocr.OcrResult

data class NormalizedLine(val raw: String, val normalized: String, val evidence: FieldEvidence)

/** Context-safe normalization: it standardizes declaration labels without mutating product names globally. */
class TextNormalizer {
    fun lines(result: OcrResult): List<NormalizedLine> = result.fragments
        .filter { it.text.isNotBlank() }
        .map { it.toLine() }
        .ifEmpty { result.fullText.lines().filter(String::isNotBlank).map { raw -> NormalizedLine(raw, normalize(raw), FieldEvidence(raw, null)) } }

    fun normalize(text: String): String = text
        .replace(Regex("\\s+"), " ")
        .replace(Regex("(?i)M\\s*\\.?\\s*R\\s*\\.?\\s*P\\s*\\.?"), "MRP")
        .replace(Regex("(?i)N\\s*E\\s*T\\s*(?:W\\s*T|W\\s*E\\s*I\\s*G\\s*H\\s*T|C\\s*O\\s*N\\s*T\\s*E\\s*N\\s*T)\\.?"), "NET QUANTITY")
        .replace(Regex("(?i)M\\s*F\\s*G\\.?"), "MFG")
        .replace(Regex("(?i)R\\s*S\\s*\\.?"), "Rs")
        .replace(Regex("(?i)\\bM\\s+L\\b"), "mL")
        .trim()

    private fun OcrFragment.toLine() = NormalizedLine(text, normalize(text), FieldEvidence(text, boundingBox))
}
