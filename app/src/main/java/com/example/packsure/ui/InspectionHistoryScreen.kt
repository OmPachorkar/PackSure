package com.example.packsure.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.packsure.history.InspectionRecord
import com.example.packsure.legal.model.ComplianceStatus
import com.example.packsure.ui.theme.Alert
import com.example.packsure.ui.theme.Amber
import com.example.packsure.ui.theme.Forest
import com.example.packsure.ui.theme.Mint
import java.text.DateFormat
import java.util.Date

@Composable
fun InspectionHistoryScreen(records: List<InspectionRecord>, onBack: () -> Unit, onScan: () -> Unit, onOpen: (InspectionRecord) -> Unit, onDelete: (InspectionRecord) -> Unit, onReport: (InspectionRecord) -> Unit) {
    var pendingDelete by remember { mutableStateOf<InspectionRecord?>(null) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inset ->
        LazyColumn(Modifier.fillMaxSize().padding(inset), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { StageTopBar("Inspection history", onBack, MaterialTheme.colorScheme.onSurface) }
            item { Text("Saved locally on this device. Reports are indicative screening summaries, not legal certificates.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (records.isEmpty()) item { EmptyHistory(onScan) } else items(records, key = { it.id }) { record -> HistoryCard(record, { onOpen(record) }, { onReport(record) }, { pendingDelete = record }) }
        }
    }
    pendingDelete?.let { record -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Delete inspection?") }, text = { Text("Its saved snapshot and private package image will be removed from this device.") }, confirmButton = { Button(onClick = { onDelete(record); pendingDelete = null }) { Text("DELETE") } }, dismissButton = { OutlinedButton(onClick = { pendingDelete = null }) { Text("CANCEL") } }) }
}

@Composable private fun EmptyHistory(onScan: () -> Unit) = Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(top = 34.dp)) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Outlined.History, null, tint = Forest, modifier = Modifier.size(44.dp)); Spacer(Modifier.height(14.dp)); Text("No saved inspections", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium); Text("Complete a package inspection to save its local record and report.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)); Button(onClick = onScan, modifier = Modifier.padding(top = 18.dp)) { Text("SCAN PACKAGE") } } }

@Composable private fun HistoryCard(record: InspectionRecord, onOpen: () -> Unit, onReport: () -> Unit, onDelete: () -> Unit) = Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.Top) { StatusDot(record.complianceResult.overallStatus); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(record.displayName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.createdAtMillis)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(record.complianceResult.overallStatus.name.replace('_', ' '), style = MaterialTheme.typography.labelSmall, color = statusColor(record.complianceResult.overallStatus), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }; IconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, "Delete inspection", tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.End) { OutlinedButton(onClick = onReport) { Icon(Icons.Outlined.PictureAsPdf, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp)); Text("REPORT") } }
} }

@Composable private fun StatusDot(status: ComplianceStatus) = Icon(Icons.Outlined.WarningAmber, null, tint = statusColor(status), modifier = Modifier.size(23.dp))
private fun statusColor(status: ComplianceStatus) = when (status) { ComplianceStatus.VERIFIED -> Forest; ComplianceStatus.VERIFICATION_REQUIRED -> Amber; ComplianceStatus.POTENTIAL_NON_COMPLIANCE -> Alert; ComplianceStatus.NOT_APPLICABLE -> Mint }
