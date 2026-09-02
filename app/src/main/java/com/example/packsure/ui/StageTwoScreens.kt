package com.example.packsure.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.ImageView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.packsure.ocr.ImagePreprocessor
import com.example.packsure.ocr.ImageQualityReport
import com.example.packsure.ocr.OcrProcessor
import com.example.packsure.ocr.OcrResult
import com.example.packsure.ui.theme.Amber
import com.example.packsure.ui.theme.Forest
import com.example.packsure.ui.theme.Mint
import java.io.File

@Composable
fun ScannerScreen(onBack: () -> Unit, onImageSelected: (Uri) -> Unit) {
    val context = LocalContext.current
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var showDenied by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraGranted = granted
        showDenied = !granted
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(onImageSelected) }

    when {
        cameraGranted -> CameraScanner(onBack, onImageSelected, onGallery = { gallery.launch("image/*") })
        showDenied -> CameraPermissionScreen(onBack, onRetry = { permission.launch(Manifest.permission.CAMERA) }, onOpenSettings = { openAppSettings(context) }, onGallery = { gallery.launch("image/*") })
        else -> CameraPermissionScreen(onBack, onRetry = { permission.launch(Manifest.permission.CAMERA) }, onOpenSettings = null, onGallery = { gallery.launch("image/*") })
    }
}

@Composable
private fun CameraScanner(onBack: () -> Unit, onImageSelected: (Uri) -> Unit, onGallery: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember { LifecycleCameraController(context).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) } }
    var flashOn by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }
    Scaffold(containerColor = Color(0xFF0D1511)) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).padding(horizontal = 20.dp)) {
            StageTopBar("Scan package", onBack, Color.White, right = {
                IconButton(onClick = { flashOn = !flashOn; controller.enableTorch(flashOn) }) { Icon(Icons.Outlined.FlashlightOn, if (flashOn) "Flash enabled" else "Enable flash", tint = if (flashOn) Mint else Color.White) }
            })
            Text("Align the package declarations inside the frame.", color = Color.White.copy(alpha = .78f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 18.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xFF23372D))) {
                AndroidView(factory = { viewContext -> PreviewView(viewContext).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; this.controller = controller } }, modifier = Modifier.fillMaxSize())
                ScanCorners(Modifier.fillMaxSize().padding(22.dp))
                Surface(color = Color.Black.copy(alpha = .45f), shape = RoundedCornerShape(12.dp), modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
                    Text("Include MRP, quantity & manufacturer details", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                captureError?.let { message -> Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.align(Alignment.TopCenter).padding(16.dp)) { Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(12.dp)) } }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CaptureAction(Icons.Outlined.Image, "Gallery", onGallery)
                Button(onClick = {
                    if (capturing) return@Button
                    capturing = true; captureError = null
                    val destination = File(context.cacheDir, "packsure-${System.currentTimeMillis()}.jpg")
                    val options = ImageCapture.OutputFileOptions.Builder(destination).build()
                    controller.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) { capturing = false; onImageSelected(Uri.fromFile(destination)) }
                        override fun onError(exception: ImageCaptureException) { capturing = false; captureError = "We couldn't capture that image. Please try again." }
                    })
                }, enabled = !capturing, shape = CircleShape, contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Forest), modifier = Modifier.size(74.dp)) {
                    if (capturing) CircularProgressIndicator(Modifier.size(28.dp), color = Forest, strokeWidth = 3.dp) else Icon(Icons.Outlined.CameraAlt, "Capture package", modifier = Modifier.size(30.dp))
                }
                CaptureAction(Icons.Outlined.PrivacyTip, "On-device", {})
            }
        }
    }
}

@Composable private fun CaptureAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) = Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(width = 58.dp, height = 54.dp).clickable(onClick = onClick)) { Icon(icon, null, tint = Color.White); Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall) }

@Composable
private fun CameraPermissionScreen(onBack: () -> Unit, onRetry: () -> Unit, onOpenSettings: (() -> Unit)?, onGallery: () -> Unit) = Scaffold(containerColor = MaterialTheme.colorScheme.background) { inset ->
    Column(Modifier.fillMaxSize().padding(inset).padding(20.dp)) {
        StageTopBar("Scan package", onBack, MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(76.dp).background(Mint.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CameraAlt, null, tint = Forest, modifier = Modifier.size(38.dp)) }
            Spacer(Modifier.height(20.dp)); Text("Camera access is required to scan a package.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp)); Text("You can also choose an existing package image from your gallery.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(22.dp)); Button(onClick = onRetry, shape = RoundedCornerShape(14.dp)) { Text("TRY AGAIN") }
            if (onOpenSettings != null) { Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = onOpenSettings, shape = RoundedCornerShape(14.dp)) { Text("OPEN SETTINGS") } }
            Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = onGallery, shape = RoundedCornerShape(14.dp)) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text("CHOOSE IMAGE") }
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun ImagePreviewScreen(uri: Uri, onBack: () -> Unit, onAnalyze: () -> Unit, onChooseAnother: () -> Unit) {
    val context = LocalContext.current
    var invalidImage by remember(uri) { mutableStateOf(false) }
    val loaded by produceState<com.example.packsure.ocr.PreparedImage?>(initialValue = null, uri) {
        val outcome = runCatching { ImagePreprocessor(context.contentResolver).prepare(uri) }
        invalidImage = outcome.isFailure
        value = outcome.getOrNull()
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).padding(horizontal = 20.dp)) {
            StageTopBar("Package image", onBack, MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                loaded?.let { prepared -> AndroidView(factory = { imageContext -> ImageView(imageContext).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageBitmap(prepared.bitmap) } }, modifier = Modifier.fillMaxSize()) }
                    ?: if (invalidImage) Text("This image could not be opened.", color = MaterialTheme.colorScheme.error) else CircularProgressIndicator(color = Forest)
            }
            loaded?.quality?.takeIf { it.needsAttention }?.let { QualityCard(it) }
            Text("Your package image is processed on-device.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 14.dp), textAlign = TextAlign.Center)
            Button(onClick = onAnalyze, enabled = loaded != null, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp)) { Text("ANALYZE PACKAGE", fontWeight = FontWeight.Bold) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("RETAKE") }; OutlinedButton(onClick = onChooseAnother, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("CHOOSE ANOTHER") } }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable private fun QualityCard(report: ImageQualityReport) = Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4DF)), shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Row(Modifier.padding(13.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Outlined.WarningAmber, null, tint = Amber); Spacer(Modifier.width(9.dp)); Column { Text("Image quality may affect results", fontWeight = FontWeight.Bold, color = Forest); report.messages.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = Forest) }; Text("You can still continue.", style = MaterialTheme.typography.bodySmall, color = Forest, modifier = Modifier.padding(top = 4.dp)) } } }

@Composable
fun ProcessingScreen(uri: Uri, onBack: () -> Unit, onSuccess: (OcrResult) -> Unit, onFailure: () -> Unit) {
    val context = LocalContext.current
    var prepared by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(false) }
    LaunchedEffect(uri) {
        val image = runCatching { ImagePreprocessor(context.contentResolver).prepare(uri) }.getOrElse { onFailure(); return@LaunchedEffect }
        prepared = true; reading = true
        val processor = OcrProcessor()
        val result = runCatching { processor.recognize(image.bitmap) }.also { processor.close() }.getOrElse { onFailure(); return@LaunchedEffect }
        if (result.fullText.length < 3) onFailure() else onSuccess(result)
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inset -> Column(Modifier.fillMaxSize().padding(inset).padding(20.dp)) {
        StageTopBar("Analyzing package", onBack, MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f)); Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(72.dp), color = Forest, strokeWidth = 6.dp); Spacer(Modifier.height(26.dp)); Text("Analyzing Package", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); Text("Reading text locally on your device", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 7.dp))
            Spacer(Modifier.height(30.dp)); Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) { ProcessStep("Image received", true); ProcessStep("Preparing image", prepared); ProcessStep("Reading package text", reading, active = reading); ProcessStep("Extracting information", false); ProcessStep("Checking declarations", false) } }
        }; Spacer(Modifier.weight(1f))
    } }
}

@Composable private fun ProcessStep(label: String, complete: Boolean, active: Boolean = false) = Row(verticalAlignment = Alignment.CenterVertically) { when { complete && !active -> Icon(Icons.Outlined.CheckCircle, null, tint = Forest); active -> CircularProgressIndicator(Modifier.size(20.dp), color = Forest, strokeWidth = 2.dp); else -> Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)) }; Spacer(Modifier.width(12.dp)); Text(label, color = if (complete || active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal) }

@Composable
fun OcrResultScreen(result: OcrResult, onBack: () -> Unit, onContinue: () -> Unit) = Scaffold(containerColor = MaterialTheme.colorScheme.background) { inset ->
    Column(Modifier.fillMaxSize().padding(inset).padding(horizontal = 20.dp)) {
        StageTopBar("Text detected", onBack, MaterialTheme.colorScheme.onSurface)
        Text("Recognized text from this package", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 18.dp, bottom = 16.dp)) { Text(result.fullText, modifier = Modifier.padding(18.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodyMedium) }
        Text("OCR runs locally and is shown here for Stage 2 testing.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Button(onClick = onContinue, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text("CONTINUE TO PACKAGE ANALYSIS", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun OcrFailureScreen(onTryAgain: () -> Unit, onChooseAnother: () -> Unit) = Scaffold(containerColor = MaterialTheme.colorScheme.background) { inset -> Column(Modifier.fillMaxSize().padding(inset).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Spacer(Modifier.weight(1f)); Box(Modifier.size(76.dp).background(Color(0xFFFFE8E5), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(38.dp)) }; Spacer(Modifier.height(20.dp)); Text("We couldn't read this package", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(9.dp)); Text("Try a clearer image with the label filling the frame.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(18.dp)); Text("• Move closer to the label\n• Improve lighting\n• Avoid glare\n• Keep text inside the scan frame", color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(24.dp)); Button(onClick = onTryAgain, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Text("TRY AGAIN") }; OutlinedButton(onClick = onChooseAnother, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) { Text("CHOOSE ANOTHER IMAGE") }; Spacer(Modifier.weight(1f))
} }

@Composable fun StageTopBar(title: String, onBack: () -> Unit, color: Color, right: @Composable () -> Unit = {}) = Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBack, "Back", tint = color) }; Text(title, color = color, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); right() }

@Composable private fun ScanCorners(modifier: Modifier) = Box(modifier = modifier.border(2.dp, Mint, RoundedCornerShape(24.dp)))

private fun openAppSettings(context: Context) = context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
