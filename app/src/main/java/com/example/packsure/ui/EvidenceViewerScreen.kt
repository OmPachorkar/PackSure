package com.example.packsure.ui

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import com.example.packsure.evidence.DisplayRect
import com.example.packsure.evidence.EvidenceCoordinateTransformer
import com.example.packsure.evidence.EvidenceRequest
import com.example.packsure.evidence.ImageViewport
import com.example.packsure.legal.model.ComplianceStatus
import com.example.packsure.ocr.ImagePreprocessor
import com.example.packsure.ocr.OcrResult
import com.example.packsure.ui.theme.Alert
import com.example.packsure.ui.theme.Amber
import com.example.packsure.ui.theme.Forest

@Composable
fun EvidenceViewerScreen(uri: Uri, ocr: OcrResult, request: EvidenceRequest, onBack: () -> Unit, onCaptureAnother: () -> Unit) {
    val context = LocalContext.current
    val imageState by produceState<EvidenceImageState>(initialValue = EvidenceImageState.Loading, uri) {
        value = runCatching { EvidenceImageState.Ready(ImagePreprocessor(context.contentResolver).prepare(uri).bitmap) }
            .getOrElse { EvidenceImageState.Unavailable }
    }
    var showRaw by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp)) {
        StageTopBar("Evidence", onBack, MaterialTheme.colorScheme.onSurface)
        Text("Package declaration evidence", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 14.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
            when (val state = imageState) {
                EvidenceImageState.Loading -> CircularProgressIndicator(color = Forest)
                is EvidenceImageState.Ready -> EvidenceImage(state.bitmap, ocr, request)
                EvidenceImageState.Unavailable -> EvidenceImageUnavailable(onCaptureAnother)
            }
        }
        Spacer(Modifier.height(14.dp)); EvidencePanel(request)
        if (!request.hasVisualEvidence) {
            Text("No matching declaration was detected in the scanned image.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            OutlinedButton(onClick = onCaptureAnother, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(14.dp)) { Text("CAPTURE ANOTHER PANEL") }
        }
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(13.dp)) {
                Text(if (showRaw) "Hide scanned text" else "View all scanned text", fontWeight = FontWeight.Bold)
                Button(onClick = { showRaw = !showRaw }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(12.dp)) { Text(if (showRaw) "HIDE SCANNED TEXT" else "VIEW ALL SCANNED TEXT") }
                if (showRaw) Text(ocr.fullText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp).verticalScroll(rememberScrollState()))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

private sealed interface EvidenceImageState {
    data object Loading : EvidenceImageState
    data class Ready(val bitmap: Bitmap) : EvidenceImageState
    data object Unavailable : EvidenceImageState
}

@Composable
fun EvidenceUnavailableScreen(onBack: () -> Unit, onCaptureAnother: () -> Unit) = Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(20.dp)) {
    StageTopBar("Evidence", onBack, MaterialTheme.colorScheme.onSurface)
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { EvidenceImageUnavailable(onCaptureAnother) }
}

@Composable private fun EvidenceImageUnavailable(onCaptureAnother: () -> Unit) = Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
    Icon(Icons.Outlined.ErrorOutline, "Package image unavailable", tint = Alert, modifier = Modifier.size(34.dp))
    Text("Package image unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
    Text("The saved image could not be opened. OCR text and declaration details remain available.", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    OutlinedButton(onClick = onCaptureAnother, modifier = Modifier.padding(top = 14.dp)) { Text("CAPTURE ANOTHER PANEL") }
}

@Composable
private fun EvidenceImage(bitmap: Bitmap, ocr: OcrResult, request: EvidenceRequest) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember { mutableStateOf(1f) }
    var translateX by remember { mutableStateOf(0f) }
    var translateY by remember { mutableStateOf(0f) }
    var fittedToEvidence by remember { mutableStateOf(false) }
    val sourceWidth = ocr.imageWidth ?: bitmap.width
    val sourceHeight = ocr.imageHeight ?: bitmap.height
    val boxes = request.evidence.mapNotNull { it.boundingBox }
    val description = if (boxes.isEmpty()) "No highlighted ${request.title} evidence" else "Highlighted ${statusLabel(request.status)} ${request.title} evidence"
    LaunchedEffect(viewport, boxes) {
        if (!fittedToEvidence && viewport != IntSize.Zero && boxes.isNotEmpty()) {
            val mapped = EvidenceCoordinateTransformer.mapAll(boxes, sourceWidth, sourceHeight, ImageViewport(viewport.width.toFloat(), viewport.height.toFloat()), ocr.rotationDegrees)
            mapped.evidenceBounds()?.let { bounds ->
                val target = minOf(viewport.width * .48f / (bounds.right - bounds.left).coerceAtLeast(1f), viewport.height * .32f / (bounds.bottom - bounds.top).coerceAtLeast(1f)).coerceIn(1f, 2.2f)
                val cx = viewport.width / 2f; val cy = viewport.height / 2f
                scale = target
                translateX = cx - (cx + (bounds.left + bounds.right) / 2f - cx) * target
                translateY = cy - (cy + (bounds.top + bounds.bottom) / 2f - cy) * target
                fittedToEvidence = true
            }
        }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { viewport = it }.semantics { contentDescription = description }.pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            translateX += pan.x; translateY += pan.y
        }
    }.clip(RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = translateX; translationY = translateY }) {
            AndroidView(factory = { imageContext -> ImageView(imageContext).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageBitmap(bitmap) } }, modifier = Modifier.fillMaxSize())
            if (viewport != IntSize.Zero && boxes.isNotEmpty()) {
                val mapped = EvidenceCoordinateTransformer.mapAll(boxes, sourceWidth, sourceHeight, ImageViewport(viewport.width.toFloat(), viewport.height.toFloat()), ocr.rotationDegrees)
                HighlightOverlay(mapped, request.status)
            }
        }
    }
}

@Composable private fun HighlightOverlay(rectangles: List<DisplayRect>, status: ComplianceStatus?) = Canvas(Modifier.fillMaxSize()) {
    val color = statusColor(status)
    rectangles.forEach { rect ->
        drawRoundRect(color.copy(alpha = .18f), topLeft = androidx.compose.ui.geometry.Offset(rect.left, rect.top), size = androidx.compose.ui.geometry.Size(rect.right - rect.left, rect.bottom - rect.top), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()))
        drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(rect.left, rect.top), size = androidx.compose.ui.geometry.Size(rect.right - rect.left, rect.bottom - rect.top), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx()), style = Stroke(width = 3.dp.toPx()))
    }
}

@Composable private fun EvidencePanel(request: EvidenceRequest) = Card(shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) { EvidenceStatusIcon(request.status); Text(request.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 9.dp)) }
    request.detectedValue?.let { Text("Detected value: $it", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
    request.evidence.firstOrNull()?.let { Text("Scanned evidence: “${it.sourceText}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) }
    Text(request.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    request.legalReference?.let { Text("Legal reference: $it", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
    request.verificationAction?.let { Text("Recommended action: $it", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp)) }
} }

@Composable private fun EvidenceStatusIcon(status: ComplianceStatus?) = when (status) {
    ComplianceStatus.VERIFIED -> Icon(Icons.Outlined.CheckCircle, "Verified evidence", tint = Forest)
    ComplianceStatus.VERIFICATION_REQUIRED -> Icon(Icons.Outlined.WarningAmber, "Verification-required evidence", tint = Amber)
    ComplianceStatus.POTENTIAL_NON_COMPLIANCE -> Icon(Icons.Outlined.ErrorOutline, "Potential issue evidence", tint = Alert)
    else -> Icon(Icons.Outlined.Info, "OCR evidence", tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun statusColor(status: ComplianceStatus?) = when (status) { ComplianceStatus.VERIFIED -> Forest; ComplianceStatus.VERIFICATION_REQUIRED -> Amber; ComplianceStatus.POTENTIAL_NON_COMPLIANCE -> Alert; else -> Color.Gray }
private fun statusLabel(status: ComplianceStatus?) = when (status) { ComplianceStatus.VERIFIED -> "verified"; ComplianceStatus.VERIFICATION_REQUIRED -> "verification-required"; ComplianceStatus.POTENTIAL_NON_COMPLIANCE -> "potential issue"; else -> "OCR" }
private fun List<DisplayRect>.evidenceBounds(): DisplayRect? = if (isEmpty()) null else DisplayRect(minOf { it.left }, minOf { it.top }, maxOf { it.right }, maxOf { it.bottom })
