package com.example.packsure.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import com.example.packsure.evidence.EvidenceMapper
import com.example.packsure.evidence.EvidenceRequest
import com.example.packsure.legal.model.ComplianceCheck
import com.example.packsure.legal.model.ComplianceResult
import com.example.packsure.legal.model.ComplianceStatus
import com.example.packsure.ui.theme.Alert
import com.example.packsure.ui.theme.Amber
import com.example.packsure.ui.theme.Forest
import com.example.packsure.ui.theme.Mint

@Composable
fun ComplianceDashboardScreen(result: ComplianceResult, onBack: () -> Unit, onEvidence: (EvidenceRequest) -> Unit, onGenerateReport: () -> Unit) = Scaffold(containerColor = MaterialTheme.colorScheme.background) { inset ->
    LazyColumn(modifier = Modifier.fillMaxSize().padding(inset), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { StageTopBar("Compliance inspection", onBack, MaterialTheme.colorScheme.onSurface) }
        item { ComplianceHero(result) }
        item { Summary(result) }
        item { Button(onClick = onGenerateReport, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) { Text("GENERATE INSPECTION REPORT", fontWeight = FontWeight.Bold) } }
        item { Text("Declaration checks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(result.checks.size) { ComplianceCard(result.checks[it], onEvidence) }
        item { Text(result.disclaimer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) }
    }
}

@Composable private fun ComplianceHero(result: ComplianceResult) {
    val (title, detail, color) = when (result.overallStatus) {
        ComplianceStatus.VERIFIED -> Triple("VERIFIED", "Available package evidence supports the screened declarations.", Forest)
        ComplianceStatus.VERIFICATION_REQUIRED -> Triple("NEEDS VERIFICATION", "Some declarations require checking another package area.", Amber)
        ComplianceStatus.POTENTIAL_NON_COMPLIANCE -> Triple("POTENTIAL ISSUE", "Some expected declarations were not detected in sufficiently complete scan evidence.", Alert)
        ComplianceStatus.NOT_APPLICABLE -> Triple("NOT APPLICABLE", "No applicable declaration checks were available.", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
        Text("AI-ASSISTED COMPLIANCE SCREENING", color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text(title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
        Text(detail, color = Color.White.copy(alpha = .9f), modifier = Modifier.padding(top = 5.dp))
        result.screeningScore?.let { Text("Declaration Verification Score  $it / 100", color = Mint, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 15.dp)) }
    } }
}

@Composable private fun Summary(result: ComplianceResult) = Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) { SummaryItem("Verified", result.verifiedCount, Forest); SummaryItem("Need review", result.verificationRequiredCount, Amber); SummaryItem("Potential issues", result.potentialNonComplianceCount, Alert) } }
@Composable private fun RowScope.SummaryItem(label: String, value: Int, color: Color) = Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) { Text(value.toString(), color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text(label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }

@Composable private fun ComplianceCard(check: ComplianceCheck, onEvidence: (EvidenceRequest) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) { Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) { StatusIcon(check.status); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(check.field, fontWeight = FontWeight.Bold); Text(statusLabel(check.status), style = MaterialTheme.typography.labelMedium, color = statusColor(check.status), fontWeight = FontWeight.SemiBold) }; Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, "Show details") }
        Spacer(Modifier.height(10.dp)); Text(check.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (expanded) { Spacer(Modifier.height(13.dp)); Text("Legal reference", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(check.legalReference, fontWeight = FontWeight.Bold); check.evidence.firstOrNull()?.let { evidence -> Spacer(Modifier.height(10.dp)); Text("Detected evidence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("“${evidence.sourceText}”", style = MaterialTheme.typography.bodySmall) }; check.verificationAction?.let { action -> Spacer(Modifier.height(10.dp)); Text("Recommended action", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(action, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) }; if (check.evidence.any { it.boundingBox != null }) Text("VIEW EVIDENCE", style = MaterialTheme.typography.labelSmall, color = Forest, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp).clickable { onEvidence(EvidenceMapper.fromCheck(check)) }) else Text("No image evidence", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)) }
    } }
}

@Composable private fun StatusIcon(status: ComplianceStatus) = when (status) { ComplianceStatus.VERIFIED -> Icon(Icons.Outlined.CheckCircle, "Verified", tint = Forest); ComplianceStatus.VERIFICATION_REQUIRED -> Icon(Icons.Outlined.WarningAmber, "Verification required", tint = Amber); ComplianceStatus.POTENTIAL_NON_COMPLIANCE -> Icon(Icons.Outlined.ErrorOutline, "Potential non-compliance", tint = Alert); ComplianceStatus.NOT_APPLICABLE -> Icon(Icons.Outlined.Info, "Not applicable", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
private fun statusLabel(status: ComplianceStatus) = when (status) { ComplianceStatus.VERIFIED -> "Verified"; ComplianceStatus.VERIFICATION_REQUIRED -> "Verification required"; ComplianceStatus.POTENTIAL_NON_COMPLIANCE -> "Potential non-compliance"; ComplianceStatus.NOT_APPLICABLE -> "Not applicable" }
private fun statusColor(status: ComplianceStatus) = when (status) { ComplianceStatus.VERIFIED -> Forest; ComplianceStatus.VERIFICATION_REQUIRED -> Amber; ComplianceStatus.POTENTIAL_NON_COMPLIANCE -> Alert; ComplianceStatus.NOT_APPLICABLE -> Color.Gray }
