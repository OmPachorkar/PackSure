package com.example.packsure.history

/** Pure report preparation kept separate from PDF drawing so it remains easy to regression-test. */
internal object InspectionReportContent {
    fun summaryFields(record: InspectionRecord) = listOf(
        "Product" to record.packageInfo.productName.value, "Brand" to record.packageInfo.brandName.value,
        "Net quantity" to record.packageInfo.netQuantity.value, "MRP" to record.packageInfo.mrp.value,
        "Manufacturer" to record.packageInfo.manufacturer.value, "Packer" to record.packageInfo.packer.value,
        "Importer" to record.packageInfo.importer.value, "Manufacturing date" to record.packageInfo.manufacturingDate.value,
        "Expiry / best before" to (record.packageInfo.expiryDate.value ?: record.packageInfo.bestBefore.value),
        "Batch" to record.packageInfo.batchNumber.value, "Country of origin" to record.packageInfo.countryOfOrigin.value
    ).filter { it.second != null }.map { it.first to it.second!! }

    fun rawOcrReference(record: InspectionRecord) = "Raw OCR text is retained in the PackSure inspection record (${record.ocrResult.fullText.length} recognized characters)."
}
