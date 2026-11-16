package com.omrmaster.app.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.omrmaster.app.ui.theme.PrimaryBlue
import com.omrmaster.app.ui.theme.SuccessGreen
import com.omrmaster.app.viewmodel.OmrViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: OmrViewModel,
    modifier: Modifier = Modifier,
    onGoToExams: () -> Unit,
    onGoToResults: () -> Unit
) {
    val context = LocalContext.current
    val exams by viewModel.exams.collectAsState()
    val selectedExam by viewModel.selectedExam.collectAsState()
    val scanState by viewModel.scanState.collectAsState()

    var useCameraMode by remember { mutableStateOf(false) }
    var examMenuExpanded by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) useCameraMode = true
    }

    val bulkGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 50)
    ) { uris ->
        val exam = selectedExam
        if (exam != null && uris.isNotEmpty()) {
            viewModel.evaluateBulkGalleryUris(uris, exam)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("স্ক্যান ও বাল্ক আপলোড", fontWeight = FontWeight.Bold) },
            actions = {
                if (scanState.lastOutput != null || scanState.statusBanner != null) {
                    TextButton(onClick = { viewModel.clearScanStatus() }) {
                        Text("ক্লিয়ার")
                    }
                }
            }
        )

        if (exams.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("স্ক্যান করার আগে অন্তত একটি এক্সাম তৈরি করুন।")
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onGoToExams) {
                        Text("এক্সাম তৈরি করুন")
                    }
                }
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Active Exam Selector
            ExposedDropdownMenuBox(
                expanded = examMenuExpanded,
                onExpandedChange = { examMenuExpanded = !examMenuExpanded }
            ) {
                OutlinedTextField(
                    value = selectedExam?.let { "${it.name} (${it.questionCount} প্রশ্ন)" } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("সক্রিয় এক্সাম নির্বাচন করুন") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = examMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = examMenuExpanded,
                    onDismissRequest = { examMenuExpanded = false }
                ) {
                    exams.forEach { exam ->
                        DropdownMenuItem(
                            text = { Text("${exam.name} (${exam.questionCount} প্রশ্ন)") },
                            onClick = {
                                viewModel.selectExam(exam.id)
                                examMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Mode Switcher Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        if (hasCameraPermission) {
                            useCameraMode = !useCameraMode
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (useCameraMode) PrimaryBlue else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (useCameraMode) "ক্যামেরা চালু" else "লাইভ ক্যামেরা")
                }

                Button(
                    onClick = {
                        useCameraMode = false
                        bulkGalleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !scanState.isProcessing,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Collections, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("বাল্ক গ্যালারি")
                }
            }

            // Progress / Status Banner
            if (scanState.isProcessing || scanState.statusBanner != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            scanState.statusBanner ?: "",
                            fontWeight = FontWeight.Bold,
                            color = if (scanState.errorMessages.isEmpty()) SuccessGreen else MaterialTheme.colorScheme.primary
                        )
                        if (scanState.isProcessing && scanState.totalCount > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    scanState.processedCount.toFloat() / scanState.totalCount.coerceAtLeast(1)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (scanState.errorMessages.isNotEmpty()) {
                            scanState.errorMessages.forEach { err ->
                                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // Live CameraX Preview & Capture
            if (useCameraMode && hasCameraPermission && selectedExam != null) {
                LiveCameraOmrBox(
                    isBusy = scanState.isProcessing,
                    onCaptured = { bmp ->
                        viewModel.evaluateSingleCameraBitmap(bmp, selectedExam!!)
                    }
                )
            }

            // Evaluated Annotated Output Preview
            scanState.lastOutput?.let { out ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "রোল: ${out.rollNumber} | সেকশন: ${out.section} | নম্বর: ${out.obtainedMarks}",
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onGoToResults) {
                                Text("সব ফলাফল দেখুন")
                            }
                        }
                        Text(
                            "সঠিক: ${out.correctCount} (সবুজ)  •  ভুল: ${out.wrongCount} (লাল)  •  একাধিক: ${out.multipleCount} (হলুদ)",
                            style = MaterialTheme.typography.bodySmall
                        )
                        out.annotatedBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Evaluated OMR Output",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 460.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveCameraOmrBox(
    isBusy: Boolean,
    onCaptured: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
        ) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture
                            )
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            Button(
                onClick = {
                    val executor = ContextCompat.getMainExecutor(context)
                    imageCapture.takePicture(
                        executor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bmp = imageProxyToBitmap(image)
                                image.close()
                                if (bmp != null) onCaptured(bmp)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                // Handled gracefully
                            }
                        }
                    )
                },
                enabled = !isBusy,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
            ) {
                Icon(Icons.Default.Camera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (isBusy) "প্রসেস হচ্ছে..." else "ছবি তুলে মূল্যায়ন করুন")
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rotation = image.imageInfo.rotationDegrees
    return if (rotation != 0) {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        raw.recycle()
        rotated
    } else {
        raw
    }
}
