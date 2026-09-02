package com.example.packsure.history

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.example.packsure.domain.model.DetectionConfidence
import com.example.packsure.domain.model.ExtractedField
import com.example.packsure.domain.model.ExtractionMethod
import com.example.packsure.domain.model.FieldEvidence
import com.example.packsure.domain.model.PackageInfo
import com.example.packsure.domain.model.VerificationState
import com.example.packsure.legal.model.ComplianceCheck
import com.example.packsure.legal.model.ComplianceResult
import com.example.packsure.legal.model.ComplianceSeverity
import com.example.packsure.legal.model.ComplianceStatus
import com.example.packsure.legal.model.ProductCategory
import com.example.packsure.ocr.OcrFragment
import com.example.packsure.ocr.OcrResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A durable, evidence-preserving snapshot of one completed on-device inspection. */
data class InspectionRecord(
    val id: String,
    val createdAtMillis: Long,
    val imageUri: String?,
    val ocrResult: OcrResult,
    val packageInfo: PackageInfo,
    val complianceResult: ComplianceResult
) {
    val displayName: String get() = packageInfo.productName.value ?: packageInfo.brandName.value ?: "Unnamed package"
}

class InspectionHistoryRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): List<InspectionRecord> {
        val raw = preferences.getString(RECORDS, "[]") ?: "[]"
        val json = runCatching { JSONArray(raw) }.getOrElse { return emptyList() }
        // A partially corrupted item must not hide otherwise valid inspections.
        return buildList { for (index in 0 until json.length()) { runCatching { decodeRecord(json.getJSONObject(index)) }.getOrNull()?.let(::add) } }
            .sortedByDescending { it.createdAtMillis }
    }

    fun save(sourceUri: Uri?, ocr: OcrResult, info: PackageInfo, compliance: ComplianceResult): InspectionRecord {
        val id = UUID.randomUUID().toString()
        // Never retain an expiring gallery/camera URI as a historical image fallback.
        // A null image is surfaced as an explicit unavailable-evidence state in the UI.
        val storedImageUri = sourceUri?.let { copyImage(it, id)?.toString() }
        val record = InspectionRecord(id, System.currentTimeMillis(), storedImageUri, ocr, info, compliance)
        persist(load() + record)
        return record
    }

    fun delete(id: String) {
        val record = load().firstOrNull { it.id == id }
        record?.imageUri?.let { uri ->
            val file = runCatching { File(Uri.parse(uri).path.orEmpty()) }.getOrNull()
            if (file?.parentFile == File(context.filesDir, IMAGES_DIR)) file.delete()
        }
        persist(load().filterNot { it.id == id })
    }

    private fun persist(records: List<InspectionRecord>) {
        val array = JSONArray()
        records.sortedByDescending { it.createdAtMillis }.forEach { array.put(encodeRecord(it)) }
        preferences.edit().putString(RECORDS, array.toString()).apply()
    }

    private fun copyImage(source: Uri, id: String): Uri? = runCatching {
        val directory = File(context.filesDir, IMAGES_DIR).also { it.mkdirs() }
        val destination = File(directory, "$id.jpg")
        context.contentResolver.openInputStream(source)?.use { input -> destination.outputStream().use(input::copyTo) }
            ?: return null
        Uri.fromFile(destination)
    }.getOrNull()

    private fun encodeRecord(record: InspectionRecord) = JSONObject().apply {
        put("id", record.id); put("createdAt", record.createdAtMillis); put("imageUri", record.imageUri)
        put("ocr", encodeOcr(record.ocrResult)); put("info", encodeInfo(record.packageInfo)); put("compliance", encodeCompliance(record.complianceResult))
    }

    private fun decodeRecord(json: JSONObject) = InspectionRecord(
        json.getString("id"), json.getLong("createdAt"), json.optString("imageUri").takeIf { it.isNotBlank() },
        decodeOcr(json.getJSONObject("ocr")), decodeInfo(json.getJSONObject("info")), decodeCompliance(json.getJSONObject("compliance"))
    )

    private fun encodeOcr(result: OcrResult) = JSONObject().apply {
        put("text", result.fullText); put("width", result.imageWidth); put("height", result.imageHeight); put("rotation", result.rotationDegrees)
        put("fragments", JSONArray().apply { result.fragments.forEach { put(JSONObject().apply { put("text", it.text); put("confidence", it.confidence); put("box", encodeRect(it.boundingBox)) }) } })
    }
    private fun decodeOcr(json: JSONObject) = OcrResult(json.getString("text"), json.getJSONArray("fragments").let { array -> List(array.length()) { i -> array.getJSONObject(i).let { OcrFragment(it.getString("text"), if (it.isNull("confidence")) null else it.getDouble("confidence").toFloat(), decodeRect(it.optJSONObject("box"))) } } }, if (json.isNull("width")) null else json.getInt("width"), if (json.isNull("height")) null else json.getInt("height"), json.optInt("rotation"))

    private fun encodeInfo(info: PackageInfo) = JSONObject().apply {
        fields(info).forEach { (key, value) -> put(key, encodeField(value)) }
        put("currency", info.mrpCurrency); put("tax", info.mrpTaxInclusive); put("mrpHint", info.mrpLocationHint)
        put("qr", info.qrCodePresent); put("barcode", info.barcodePresent); put("manufacturerReview", info.manufacturerVerificationRequired); put("mrpReview", info.mrpVerificationRequired); put("dateReview", info.dateVerificationRequired)
        put("warnings", JSONArray(info.extractionWarnings))
    }
    private fun decodeInfo(json: JSONObject): PackageInfo {
        fun field(key: String) = decodeField(json.optJSONObject(key))
        return PackageInfo(field("productName"), field("brandName"), field("commonGenericName"), field("netQuantity"), field("mrp"), json.optString("currency").takeIf { it.isNotBlank() }, if (json.isNull("tax")) null else json.getBoolean("tax"), json.optString("mrpHint").takeIf { it.isNotBlank() }, field("manufacturer"), field("manufacturerAddress"), field("packer"), field("packerAddress"), field("importer"), field("importerAddress"), field("manufacturingDate"), field("packingDate"), field("expiryDate"), field("bestBefore"), field("batchNumber"), field("lotNumber"), field("productionCode"), field("consumerCarePhone"), field("consumerCareEmail"), field("consumerCareAddress"), field("countryOfOrigin"), json.optBoolean("qr"), json.optBoolean("barcode"), json.optBoolean("manufacturerReview"), json.optBoolean("mrpReview"), json.optBoolean("dateReview"), json.optJSONArray("warnings")?.let { List(it.length()) { index -> it.getString(index) } }.orEmpty())
    }
    private fun fields(info: PackageInfo) = listOf("productName" to info.productName, "brandName" to info.brandName, "commonGenericName" to info.commonGenericName, "netQuantity" to info.netQuantity, "mrp" to info.mrp, "manufacturer" to info.manufacturer, "manufacturerAddress" to info.manufacturerAddress, "packer" to info.packer, "packerAddress" to info.packerAddress, "importer" to info.importer, "importerAddress" to info.importerAddress, "manufacturingDate" to info.manufacturingDate, "packingDate" to info.packingDate, "expiryDate" to info.expiryDate, "bestBefore" to info.bestBefore, "batchNumber" to info.batchNumber, "lotNumber" to info.lotNumber, "productionCode" to info.productionCode, "consumerCarePhone" to info.consumerCarePhone, "consumerCareEmail" to info.consumerCareEmail, "consumerCareAddress" to info.consumerCareAddress, "countryOfOrigin" to info.countryOfOrigin)

    private fun encodeField(field: ExtractedField<String>) = JSONObject().apply { put("value", field.value); put("state", field.state.name); put("confidence", field.confidence.name); put("method", field.method.name); put("hint", field.locationHint); put("evidence", encodeEvidence(field.evidence)) }
    private fun decodeField(json: JSONObject?) = json?.let { ExtractedField(it.optString("value").takeIf(String::isNotBlank), VerificationState.valueOf(it.optString("state", VerificationState.NOT_DETECTED.name)), DetectionConfidence.valueOf(it.optString("confidence", DetectionConfidence.NONE.name)), decodeEvidence(it.optJSONArray("evidence")), ExtractionMethod.valueOf(it.optString("method", ExtractionMethod.NONE.name)), it.optString("hint").takeIf(String::isNotBlank)) } ?: ExtractedField()

    private fun encodeCompliance(result: ComplianceResult) = JSONObject().apply { put("status", result.overallStatus.name); put("category", result.category.name); put("verified", result.verifiedCount); put("review", result.verificationRequiredCount); put("issues", result.potentialNonComplianceCount); put("na", result.notApplicableCount); put("score", result.screeningScore); put("disclaimer", result.disclaimer); put("checks", JSONArray().apply { result.checks.forEach { check -> put(JSONObject().apply { put("rule", check.ruleId); put("field", check.field); put("title", check.title); put("status", check.status.name); put("explanation", check.explanation); put("reference", check.legalReference); put("evidence", encodeEvidence(check.evidence)); put("severity", check.severity.name); put("action", check.verificationAction); put("value", check.detectedValue) }) } }) }
    private fun decodeCompliance(json: JSONObject): ComplianceResult = ComplianceResult(ComplianceStatus.valueOf(json.getString("status")), ProductCategory.valueOf(json.getString("category")), json.getJSONArray("checks").let { array -> List(array.length()) { index -> array.getJSONObject(index).let { check -> ComplianceCheck(check.getString("rule"), check.getString("field"), check.getString("title"), ComplianceStatus.valueOf(check.getString("status")), check.getString("explanation"), check.getString("reference"), decodeEvidence(check.optJSONArray("evidence")), ComplianceSeverity.valueOf(check.optString("severity", ComplianceSeverity.MODERATE.name)), check.optString("action").takeIf(String::isNotBlank), check.optString("value").takeIf(String::isNotBlank)) } } }, json.getInt("verified"), json.getInt("review"), json.getInt("issues"), json.getInt("na"), if (json.isNull("score")) null else json.getInt("score"), json.optString("disclaimer"))

    private fun encodeEvidence(evidence: List<FieldEvidence>) = JSONArray().apply { evidence.forEach { put(JSONObject().apply { put("text", it.sourceText); put("box", encodeRect(it.boundingBox)) }) } }
    private fun decodeEvidence(array: JSONArray?) = array?.let { List(it.length()) { index -> it.getJSONObject(index).let { item -> FieldEvidence(item.getString("text"), decodeRect(item.optJSONObject("box"))) } } }.orEmpty()
    private fun encodeRect(rect: Rect?) = rect?.let { JSONObject().apply { put("left", it.left); put("top", it.top); put("right", it.right); put("bottom", it.bottom) } }
    private fun decodeRect(json: JSONObject?) = json?.let { Rect(it.getInt("left"), it.getInt("top"), it.getInt("right"), it.getInt("bottom")) }

    companion object { const val PREFERENCES = "packsure.inspections"; const val RECORDS = "records"; const val IMAGES_DIR = "inspection-images" }
}
