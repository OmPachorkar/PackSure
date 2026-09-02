package com.example.packsure.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.packsure.domain.model.ExtractedField
import com.example.packsure.domain.model.PackageInfo
import com.example.packsure.domain.model.VerificationState
import com.example.packsure.evidence.EvidenceMapper
import com.example.packsure.evidence.EvidenceRequest
import com.example.packsure.ocr.OcrResult
import com.example.packsure.ui.theme.Amber
import com.example.packsure.ui.theme.Forest
import com.example.packsure.ui.theme.Mint

@Composable
fun PackageInformationScreen(info: PackageInfo, ocr: OcrResult, onBack: () -> Unit, onInspect: () -> Unit, onEvidence: (EvidenceRequest) -> Unit) {
    var showRaw by remember { mutableStateOf(false) }
    val detected = listOf(info.productName, info.netQuantity, info.mrp, info.manufacturer, info.packer, info.importer, info.manufacturingDate, info.packingDate, info.expiryDate, info.bestBefore, info.consumerCarePhone, info.countryOfOrigin).count { it.state == VerificationState.DETECTED }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inset ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(inset), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { StageTopBar("Package information", onBack, MaterialTheme.colorScheme.onSurface) }
            item {
                Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Forest), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        Text("EXTRACTION SUMMARY", color = Mint, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp)); Text("Package details identified", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                        Text("$detected declarations detected from the scanned label", color = Color.White.copy(alpha = .76f), modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            item { InfoSection("Package overview", listOf("Product name" to info.productName, "Brand" to info.brandName, "Common / generic name" to info.commonGenericName, "Net quantity" to info.netQuantity), onEvidence) }
            item { InfoSection("Pricing", listOf("MRP" to info.mrp, "Tax information" to taxField(info)), onEvidence) }
            item { InfoSection("Responsible party", listOf("Manufacturer" to info.manufacturer, "Manufacturer address" to info.manufacturerAddress, "Packer" to info.packer, "Importer" to info.importer), onEvidence) }
            item { InfoSection("Dates & identifiers", listOf("Manufacturing date" to info.manufacturingDate, "Packing date" to info.packingDate, "Best before" to info.bestBefore, "Expiry" to info.expiryDate, "Batch number" to info.batchNumber, "Production code" to info.productionCode), onEvidence) }
            item { InfoSection("Consumer & origin", listOf("Consumer care" to info.consumerCarePhone, "Email" to info.consumerCareEmail, "Country of origin" to info.countryOfOrigin), onEvidence) }
            if (info.extractionWarnings.isNotEmpty()) item { WarningCard(info.extractionWarnings) }
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().clickable { showRaw = !showRaw }) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("View scanned text", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Icon(if (showRaw) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) }
                        if (showRaw) { Spacer(Modifier.height(12.dp)); Text(ocr.fullText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            item { Button(onClick = onInspect, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) { Text("RUN COMPLIANCE INSPECTION", fontWeight = FontWeight.Bold) } }
            item { Text("Detected values are extracted from OCR evidence and are not compliance decisions.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) }
        }
    }
}

@Composable private fun InfoSection(title: String, rows: List<Pair<String, ExtractedField<String>>>, onEvidence: (EvidenceRequest) -> Unit) = Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.padding(17.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Forest, fontWeight = FontWeight.Bold)
        rows.forEachIndexed { index, (label, field) -> if (index > 0) Spacer(Modifier.height(13.dp)); FieldRow(label, field, onEvidence) }
    }
}

@Composable private fun FieldRow(label: String, field: ExtractedField<String>, onEvidence: (EvidenceRequest) -> Unit) = Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
    FieldStatus(field.state); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(field.value ?: when (field.state) { VerificationState.VERIFICATION_REQUIRED -> "Needs verification${field.locationHint?.let { " · $it" }.orEmpty()}"; else -> "Not detected" }, fontWeight = if (field.value != null) FontWeight.SemiBold else FontWeight.Normal, color = if (field.state == VerificationState.NOT_DETECTED) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        if (field.value != null && field.confidence.name != "NONE") Text(if (field.confidence.name == "LOW") "Detected" else "High confidence", style = MaterialTheme.typography.labelSmall, color = Forest)
        if (field.evidence.any { it.boundingBox != null }) Text("VIEW EVIDENCE", style = MaterialTheme.typography.labelSmall, color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp).clickable { onEvidence(EvidenceMapper.fromField(label, field)) }) else Text("Evidence unavailable", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable private fun FieldStatus(state: VerificationState) = when (state) {
    VerificationState.DETECTED -> Icon(Icons.Outlined.CheckCircle, "Detected", tint = Forest)
    VerificationState.VERIFICATION_REQUIRED -> Icon(Icons.Outlined.WarningAmber, "Needs verification", tint = Amber)
    VerificationState.NOT_DETECTED -> Icon(Icons.Outlined.RemoveCircleOutline, "Not detected", tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun WarningCard(warnings: List<String>) = Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4DF)), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Outlined.WarningAmber, null, tint = Amber); Spacer(Modifier.width(10.dp)); Column { Text("Verification", fontWeight = FontWeight.Bold); warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) } } } }

private fun taxField(info: PackageInfo): ExtractedField<String> = when (info.mrpTaxInclusive) {
    true -> ExtractedField("Inclusive of all taxes", VerificationState.DETECTED)
    false -> ExtractedField("Tax statement not detected", VerificationState.NOT_DETECTED)
    null -> ExtractedField()
}
