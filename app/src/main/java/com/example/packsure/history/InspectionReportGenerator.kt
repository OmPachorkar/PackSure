package com.example.packsure.history

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Creates a local screening summary; it is deliberately never an official certificate. */
class InspectionReportGenerator(private val context: Context) {
    fun create(record: InspectionRecord): File {
        val directory = File(context.cacheDir, REPORTS_DIR).also { it.mkdirs() }
        val output = File(directory, "packsure-inspection-${record.id}.pdf")
        val temporary = File(directory, "${output.name}.tmp")
        val document = PdfDocument()
        try {
            val writer = ReportWriter(document)
            writer.title("PackSure inspection report")
            writer.wrapped("Generated locally on ${DateFormat.getDateTimeInstance().format(Date(record.createdAtMillis))}")
            writer.gap()
            writer.section("SCREENING SUMMARY")
            writer.line("Package: ${record.displayName}")
            writer.line("Outcome: ${record.complianceResult.overallStatus.name.replace('_', ' ')}")
            writer.line("Category: ${record.complianceResult.category.name.replace('_', ' ')}")
            record.complianceResult.screeningScore?.let { writer.line("Declaration Verification Score: $it / 100") }
            writer.gap()
            writer.section("DETECTED PACKAGE INFORMATION")
            val fields = InspectionReportContent.summaryFields(record)
            if (fields.isEmpty()) writer.line("No package declarations were confidently extracted.")
            else fields.forEach { (label, value) -> writer.wrapped("$label: $value") }
            writer.gap()
            writer.section("DECLARATION CHECKS")
            record.complianceResult.checks.forEach { check ->
                writer.wrapped("${check.field}: ${check.status.name.replace('_', ' ')} - ${check.explanation}")
                check.detectedValue?.let { writer.wrapped("Evidence text: $it", indent = 12f) }
                check.verificationAction?.let { writer.wrapped("Recommended action: $it", indent = 12f) }
                writer.gap(4f)
            }
            writer.gap()
            writer.section("RAW OCR REFERENCE")
            writer.wrapped(InspectionReportContent.rawOcrReference(record))
            writer.gap()
            writer.section("IMPORTANT")
            writer.wrapped(record.complianceResult.disclaimer)
            writer.finish()
            temporary.outputStream().use(document::writeTo)
            if (output.exists()) output.delete()
            check(temporary.renameTo(output)) { "Unable to finalize inspection report" }
            return output
        } finally {
            document.close()
            if (temporary.exists()) temporary.delete()
        }
    }

    fun contentUri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private class ReportWriter(private val document: PdfDocument) {
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 21f; isFakeBoldText = true; color = 0xFF163D2A.toInt() }
        private val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; isFakeBoldText = true; color = 0xFF163D2A.toInt() }
        private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF202420.toInt() }
        private var pageNumber = 0
        private var page: PdfDocument.Page? = null
        private lateinit var canvas: Canvas
        private var y = TOP

        init { nextPage() }
        fun title(value: String) { canvas.drawText(value, LEFT, y, title); y += 23f }
        fun section(value: String) { ensure(18f); canvas.drawText(value, LEFT, y, heading); y += 17f }
        fun line(value: String) { ensure(15f); canvas.drawText(value, LEFT, y, body); y += 15f }
        fun gap(amount: Float = 12f) { ensure(amount); y += amount }
        fun wrapped(value: String, indent: Float = 0f) {
            var current = ""
            value.trim().split(Regex("\\s+")).filter(String::isNotBlank).forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (body.measureText(candidate) > WIDTH - LEFT * 2 - indent && current.isNotEmpty()) { lineAt(current, indent); current = word } else current = candidate
            }
            if (current.isNotEmpty()) lineAt(current, indent)
        }
        fun finish() { page?.let(document::finishPage); page = null }
        private fun lineAt(value: String, indent: Float) { ensure(14f); canvas.drawText(value, LEFT + indent, y, body); y += 14f }
        private fun ensure(height: Float) { if (y + height > BOTTOM) nextPage() }
        private fun nextPage() {
            page?.let(document::finishPage)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(WIDTH.toInt(), HEIGHT.toInt(), pageNumber).create())
            canvas = page!!.canvas
            y = TOP
            canvas.drawText("PackSure - local inspection screening", LEFT, y, heading)
            y += 18f
        }
        private companion object { const val WIDTH = 595f; const val HEIGHT = 842f; const val LEFT = 40f; const val TOP = 44f; const val BOTTOM = 790f }
    }

    private companion object { const val REPORTS_DIR = "reports" }
}
