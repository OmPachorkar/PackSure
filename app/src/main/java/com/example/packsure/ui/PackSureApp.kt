package com.example.packsure.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.packsure.history.InspectionHistoryRepository
import com.example.packsure.history.InspectionReportGenerator
import com.example.packsure.history.InspectionRecord
import com.example.packsure.ui.theme.Amber
import com.example.packsure.ui.theme.Canvas
import com.example.packsure.ui.theme.Forest
import com.example.packsure.ui.theme.Ink
import com.example.packsure.ui.theme.Leaf
import com.example.packsure.ui.theme.Mint
import com.example.packsure.ui.theme.PackSureTheme

private enum class Destination { HOME, SCAN, PREVIEW, PROCESSING, OCR_RESULT, PACKAGE_INFO, COMPLIANCE, EVIDENCE, OCR_FAILURE, HISTORY, HOW_IT_WORKS }
private enum class EvidenceOrigin { PACKAGE_INFO, COMPLIANCE }

@Composable
fun PackSureApp() {
    val context = LocalContext.current
    val historyRepository = remember(context) { InspectionHistoryRepository(context.applicationContext) }
    var history by remember { mutableStateOf(historyRepository.load()) }
    var destination by remember { mutableStateOf(Destination.HOME) }
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var ocrResult by remember { mutableStateOf<com.example.packsure.ocr.OcrResult?>(null) }
    var packageInfo by remember { mutableStateOf<com.example.packsure.domain.model.PackageInfo?>(null) }
    var complianceResult by remember { mutableStateOf<com.example.packsure.legal.model.ComplianceResult?>(null) }
    var evidenceRequest by remember { mutableStateOf<com.example.packsure.evidence.EvidenceRequest?>(null) }
    var evidenceOrigin by remember { mutableStateOf(EvidenceOrigin.PACKAGE_INFO) }
    var activeRecordId by remember { mutableStateOf<String?>(null) }
    var viewingHistory by remember { mutableStateOf(false) }
    AnimatedContent(targetState = destination, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "screen") { page ->
        when (page) {
            Destination.HOME -> HomeScreen(historyCount = history.size, onNavigate = { destination = it })
            Destination.SCAN -> ScannerScreen(onBack = { destination = Destination.HOME }, onImageSelected = { uri -> imageUri = uri; activeRecordId = null; viewingHistory = false; destination = Destination.PREVIEW })
            Destination.PREVIEW -> imageUri?.let { uri -> ImagePreviewScreen(uri, onBack = { destination = Destination.SCAN }, onAnalyze = { destination = Destination.PROCESSING }, onChooseAnother = { destination = Destination.SCAN }) }
            Destination.PROCESSING -> imageUri?.let { uri -> ProcessingScreen(uri, onBack = { destination = Destination.PREVIEW }, onSuccess = { result -> ocrResult = result; destination = Destination.OCR_RESULT }, onFailure = { destination = Destination.OCR_FAILURE }) }
            Destination.OCR_RESULT -> ocrResult?.let { result -> OcrResultScreen(result, onBack = { destination = Destination.PREVIEW }, onContinue = { packageInfo = com.example.packsure.extraction.PackageInfoExtractor().extract(result); destination = Destination.PACKAGE_INFO }) }
            Destination.PACKAGE_INFO -> if (packageInfo != null && ocrResult != null) PackageInformationScreen(packageInfo!!, ocrResult!!, onBack = { destination = if (viewingHistory) Destination.HISTORY else Destination.OCR_RESULT }, onInspect = {
                complianceResult = com.example.packsure.legal.engine.ComplianceRuleEngine().evaluate(packageInfo!!)
                if (!viewingHistory && activeRecordId == null) { activeRecordId = historyRepository.save(imageUri, ocrResult!!, packageInfo!!, complianceResult!!).id; history = historyRepository.load() }
                destination = Destination.COMPLIANCE
            }, onEvidence = { request -> evidenceRequest = request; evidenceOrigin = EvidenceOrigin.PACKAGE_INFO; destination = Destination.EVIDENCE })
            Destination.COMPLIANCE -> complianceResult?.let { result -> ComplianceDashboardScreen(result, onBack = { destination = Destination.PACKAGE_INFO }, onEvidence = { request -> evidenceRequest = request; evidenceOrigin = EvidenceOrigin.COMPLIANCE; destination = Destination.EVIDENCE }, onGenerateReport = { history.firstOrNull { it.id == activeRecordId }?.let { shareReport(context, it) } }) }
            Destination.EVIDENCE -> if (ocrResult != null && evidenceRequest != null) {
                val back = { destination = if (evidenceOrigin == EvidenceOrigin.COMPLIANCE) Destination.COMPLIANCE else Destination.PACKAGE_INFO }
                if (imageUri != null) EvidenceViewerScreen(imageUri!!, ocrResult!!, evidenceRequest!!, onBack = back, onCaptureAnother = { viewingHistory = false; activeRecordId = null; destination = Destination.SCAN })
                else EvidenceUnavailableScreen(onBack = back, onCaptureAnother = { viewingHistory = false; activeRecordId = null; destination = Destination.SCAN })
            }
            Destination.OCR_FAILURE -> OcrFailureScreen(onTryAgain = { destination = Destination.SCAN }, onChooseAnother = { destination = Destination.SCAN })
            Destination.HISTORY -> InspectionHistoryScreen(history, onBack = { destination = Destination.HOME }, onScan = { viewingHistory = false; activeRecordId = null; destination = Destination.SCAN }, onOpen = { record -> imageUri = record.imageUri?.let(android.net.Uri::parse); ocrResult = record.ocrResult; packageInfo = record.packageInfo; complianceResult = record.complianceResult; activeRecordId = record.id; viewingHistory = true; destination = Destination.PACKAGE_INFO }, onDelete = { record -> historyRepository.delete(record.id); history = historyRepository.load(); if (activeRecordId == record.id) activeRecordId = null }, onReport = { record -> shareReport(context, record) })
            Destination.HOW_IT_WORKS -> HowItWorksScreen(onBack = { destination = Destination.HOME })
        }
    }
}

@Composable
private fun HomeScreen(historyCount: Int, onNavigate: (Destination) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding() + 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { AppHeader(onHistory = { onNavigate(Destination.HISTORY) }) }
            item { HeroCard(onScan = { onNavigate(Destination.SCAN) }) }
            item { PrivacyNote() }
            item { Dashboard(historyCount, onHistory = { onNavigate(Destination.HISTORY) }) }
            item { SectionTitle("Quick actions") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ActionTile("Upload image", "Choose a label", Icons.Outlined.Upload, Modifier.weight(1f)) { onNavigate(Destination.SCAN) }
                    ActionTile("How it works", "See the flow", Icons.Outlined.Info, Modifier.weight(1f)) { onNavigate(Destination.HOW_IT_WORKS) }
                }
            }
        }
    }
}

@Composable private fun AppHeader(onHistory: () -> Unit) = Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    BrandMark()
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
        Text("PackSure", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.6).sp)
        Text("AI-powered package inspector", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    IconButton(onClick = onHistory, modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)) {
        Icon(Icons.Outlined.History, "Inspection history", tint = Forest)
    }
}

@Composable private fun BrandMark() = Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp).background(Forest, RoundedCornerShape(15.dp))) {
    Icon(Icons.Outlined.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(26.dp))
}

@Composable private fun HeroCard(onScan: () -> Unit) = Card(
    shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Forest),
    modifier = Modifier.fillMaxWidth()
) {
    Box(Modifier.fillMaxWidth().padding(24.dp)) {
        Box(Modifier.align(Alignment.TopEnd).size(115.dp).rotate(18f).background(Color.White.copy(alpha = .08f), CircleShape))
        Column {
            Surface(color = Mint.copy(alpha = .16f), shape = RoundedCornerShape(50)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Mint, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp)); Text("AI-ASSISTED SCREENING", color = Mint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text("Scan. Analyze.\nVerify.", color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, lineHeight = 38.sp)
            Spacer(Modifier.height(10.dp))
            Text("Inspect package declarations and surface potential compliance findings in seconds.", color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp, modifier = Modifier.fillMaxWidth(.82f))
            Spacer(Modifier.height(24.dp))
            Button(onClick = onScan, shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 15.dp)) {
                Icon(Icons.Outlined.CameraAlt, null); Spacer(Modifier.width(9.dp)); Text("SCAN PACKAGE", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable private fun PrivacyNote() = Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)).padding(13.dp)) {
    Icon(Icons.Outlined.PrivacyTip, null, tint = Leaf, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
    Text("Your package images stay on your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun Dashboard(historyCount: Int, onHistory: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Recent inspections", Modifier.weight(1f))
            Text("VIEW HISTORY", color = Forest, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onHistory).padding(8.dp))
        }
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onHistory)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(46.dp).background(Mint.copy(alpha = .45f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Collections, null, tint = Forest) }
                Spacer(Modifier.height(10.dp)); Text(if (historyCount == 0) "No inspections yet" else "$historyCount saved inspection${if (historyCount == 1) "" else "s"}", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp)); Text(if (historyCount == 0) "Your inspection history will appear here." else "Open history to review package details and reports.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable private fun SectionTitle(text: String, modifier: Modifier = Modifier) = Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = modifier)

@Composable private fun ActionTile(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) = Card(
    modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)
) { Column(Modifier.padding(17.dp)) { Box(Modifier.size(38.dp).background(Canvas, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Forest) }; Spacer(Modifier.height(14.dp)); Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable private fun HowItWorksScreen(onBack: () -> Unit) = EmptyScreen(onBack, "How PackSure works", "Your inspection, explained", "Capture a package. PackSure will read, organize and screen its declarations entirely on-device.", Icons.Outlined.AutoAwesome, "GOT IT", onBack)

private fun shareReport(context: android.content.Context, record: InspectionRecord) {
    runCatching {
        val generator = InspectionReportGenerator(context.applicationContext)
        val uri = generator.contentUri(generator.create(record))
        ContextCompat.startActivity(context, Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share inspection report"), null)
    }.onFailure {
        Toast.makeText(context, "Couldn't generate the inspection report. Please try again.", Toast.LENGTH_LONG).show()
    }
}

@Composable private fun EmptyScreen(onBack: () -> Unit, title: String, heading: String, message: String, icon: androidx.compose.ui.graphics.vector.ImageVector, action: String, onAction: () -> Unit) = Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back") }; Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.weight(1f)); Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) { Box(Modifier.size(76.dp).background(Mint.copy(alpha = .5f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Forest, modifier = Modifier.size(37.dp)) }; Spacer(Modifier.height(20.dp)); Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(22.dp)); OutlinedButton(onClick = onAction, shape = RoundedCornerShape(14.dp)) { Text(action, fontWeight = FontWeight.Bold) } }; Spacer(Modifier.weight(1f))
    }
}

@Preview(showBackground = true) @Composable private fun HomePreview() = PackSureTheme { HomeScreen(0, { }) }
