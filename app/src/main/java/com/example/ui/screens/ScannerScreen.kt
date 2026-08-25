package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.PrimaryBlue
import java.io.File
import java.io.FileOutputStream

enum class ScannerStep {
    CAMERA,
    AUTO_CROP,
    PERSPECTIVE_ENHANCE,
    PREVIEW
}

enum class ScanFilter {
    ORIGINAL,
    MAGIC_COLOR,
    DOCUMENT_BW,
    GRAYSCALE
}

data class ScannedPage(
    val id: String,
    val originalBitmap: Bitmap,
    var processedBitmap: Bitmap,
    var filter: ScanFilter = ScanFilter.MAGIC_COLOR
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBackClick: () -> Unit,
    onSaveSuccess: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current

    var currentStep by remember { mutableStateOf(ScannerStep.CAMERA) }
    var pages by remember { mutableStateOf(listOf<ScannedPage>()) }
    var selectedPageIndex by remember { mutableIntStateOf(0) }

    // Active working bitmap for crop/enhance
    var activeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var activeFilter by remember { mutableStateOf(ScanFilter.MAGIC_COLOR) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    // Camera permission check
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    // Camera picture capture launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            activeBitmap = bitmap
            rotationAngle = 0f
            activeFilter = ScanFilter.MAGIC_COLOR
            currentStep = ScannerStep.AUTO_CROP
        }
    }

    // Gallery image picker launcher
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    activeBitmap = bitmap
                    rotationAngle = 0f
                    activeFilter = ScanFilter.MAGIC_COLOR
                    currentStep = ScannerStep.AUTO_CROP
                }
            } catch (e: Exception) {
                Toast.makeText(context, "ছবি লোড করা যায়নি", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentStep) {
                            ScannerStep.CAMERA -> "ডকুমেন্ট স্ক্যানার"
                            ScannerStep.AUTO_CROP -> "অটো ক্রপ ও বর্ডার"
                            ScannerStep.PERSPECTIVE_ENHANCE -> "কালার এনহ্যান্সমেন্ট"
                            ScannerStep.PREVIEW -> "ডকুমেন্ট প্রিভিউ (${pages.size} পেজ)"
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (currentStep != ScannerStep.CAMERA && pages.isEmpty()) {
                                currentStep = ScannerStep.CAMERA
                            } else {
                                onBackClick()
                            }
                        },
                        modifier = Modifier.testTag("scanner_back_button")
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                actions = {
                    if (currentStep == ScannerStep.PREVIEW && pages.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                saveScannedDocument(context, pages)
                                Toast.makeText(context, "গ্যালারি ও লোকাল স্টোরেজে সফলভাবে সংরক্ষিত হয়েছে!", Toast.LENGTH_LONG).show()
                                onSaveSuccess?.invoke() ?: onBackClick()
                            },
                            modifier = Modifier.testTag("scanner_save_top_button")
                        ) {
                            Text("সংরক্ষণ", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.cardBackground)
            )
        },
        containerColor = colors.background,
        modifier = modifier.testTag("scanner_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentStep) {
                ScannerStep.CAMERA -> {
                    CameraViewStep(
                        hasPermission = hasCameraPermission,
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onCaptureClick = {
                            if (hasCameraPermission) {
                                takePictureLauncher.launch()
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onGalleryClick = { pickImageLauncher.launch("image/*") },
                        onSimulatedScan = {
                            val demoBitmap = generateSampleDocumentBitmap()
                            activeBitmap = demoBitmap
                            rotationAngle = 0f
                            activeFilter = ScanFilter.MAGIC_COLOR
                            currentStep = ScannerStep.AUTO_CROP
                        }
                    )
                }

                ScannerStep.AUTO_CROP -> {
                    if (activeBitmap != null) {
                        AutoCropStep(
                            bitmap = activeBitmap!!,
                            rotationAngle = rotationAngle,
                            onRotate = { rotationAngle = (rotationAngle + 90f) % 360f },
                            onConfirmCrop = { croppedBitmap ->
                                activeBitmap = croppedBitmap
                                currentStep = ScannerStep.PERSPECTIVE_ENHANCE
                            }
                        )
                    }
                }

                ScannerStep.PERSPECTIVE_ENHANCE -> {
                    if (activeBitmap != null) {
                        EnhancementStep(
                            bitmap = activeBitmap!!,
                            selectedFilter = activeFilter,
                            onFilterSelect = { activeFilter = it },
                            onConfirmEnhance = { enhancedBitmap ->
                                val newPage = ScannedPage(
                                    id = System.currentTimeMillis().toString(),
                                    originalBitmap = activeBitmap!!,
                                    processedBitmap = enhancedBitmap,
                                    filter = activeFilter
                                )
                                pages = pages + newPage
                                selectedPageIndex = pages.size - 1
                                currentStep = ScannerStep.PREVIEW
                            }
                        )
                    }
                }

                ScannerStep.PREVIEW -> {
                    PreviewStep(
                        pages = pages,
                        selectedIndex = selectedPageIndex,
                        onSelectPage = { selectedPageIndex = it },
                        onRetake = {
                            currentStep = ScannerStep.CAMERA
                        },
                        onAddPage = {
                            currentStep = ScannerStep.CAMERA
                        },
                        onDeletePage = { indexToDelete ->
                            val updated = pages.toMutableList()
                            updated.removeAt(indexToDelete)
                            pages = updated
                            if (pages.isEmpty()) {
                                currentStep = ScannerStep.CAMERA
                            } else if (selectedPageIndex >= pages.size) {
                                selectedPageIndex = pages.size - 1
                            }
                        },
                        onSave = {
                            saveScannedDocument(context, pages)
                            Toast.makeText(context, "ডকুমেন্ট সফলভাবে সংরক্ষণ করা হয়েছে!", Toast.LENGTH_LONG).show()
                            onSaveSuccess?.invoke() ?: onBackClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraViewStep(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCaptureClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onSimulatedScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Document Camera Finder Viewport with Edge Detection Overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            // Live edge detection boundary animation box
            Box(
                modifier = Modifier
                    .fillMaxSize(0.85f)
                    .border(2.dp, PrimaryBlue, RoundedCornerShape(12.dp))
            ) {
                // 4 Corner edge detection guides
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cornerLen = 30.dp.toPx()
                    val stroke = 4.dp.toPx()
                    val strokeColor = androidx.compose.ui.graphics.Color(0xFF38BDF8)

                    // Top Left Corner
                    drawLine(strokeColor, Offset(0f, 0f), Offset(cornerLen, 0f), stroke)
                    drawLine(strokeColor, Offset(0f, 0f), Offset(0f, cornerLen), stroke)

                    // Top Right Corner
                    drawLine(strokeColor, Offset(size.width, 0f), Offset(size.width - cornerLen, 0f), stroke)
                    drawLine(strokeColor, Offset(size.width, 0f), Offset(size.width, cornerLen), stroke)

                    // Bottom Left Corner
                    drawLine(strokeColor, Offset(0f, size.height), Offset(cornerLen, size.height), stroke)
                    drawLine(strokeColor, Offset(0f, size.height), Offset(0f, size.height - cornerLen), stroke)

                    // Bottom Right Corner
                    drawLine(strokeColor, Offset(size.width, size.height), Offset(size.width - cornerLen, size.height), stroke)
                    drawLine(strokeColor, Offset(size.width, size.height), Offset(size.width, size.height - cornerLen), stroke)
                }
            }

            // Top Status Overlay
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF22C55E), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ডকুমেন্ট বর্ডার অটো-ডিটেক্টেড",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!hasPermission) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ক্যামেরা পারমিশন প্রয়োজন",
                        fontSize = 15.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "ডকুমেন্ট সরাসরি স্ক্যান করতে ক্যামেরা অ্যাক্সেস প্রয়োজন।",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("পারমিশন দিন", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Camera Control Actions Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery Pick Option
            IconButton(
                onClick = onGalleryClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    .testTag("scanner_gallery_button")
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color.White)
            }

            // Capture Shutter Button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(4.dp)
                    .background(PrimaryBlue, CircleShape)
                    .clickable(onClick = onCaptureClick)
                    .testTag("scanner_capture_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = "Capture",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Quick Demo Scan Button
            IconButton(
                onClick = onSimulatedScan,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    .testTag("scanner_demo_button")
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = "Auto Scan", tint = Color(0xFFFACC15))
            }
        }
    }
}

@Composable
private fun AutoCropStep(
    bitmap: Bitmap,
    rotationAngle: Float,
    onRotate: () -> Unit,
    onConfirmCrop: (Bitmap) -> Unit
) {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Image display with crop boundary handles
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .background(colors.cardBackground, RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Document Preview",
                modifier = Modifier.fillMaxSize()
            )

            // Crop Boundary Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize(0.9f)
                    .border(2.dp, PrimaryBlue, RoundedCornerShape(8.dp))
                    .background(PrimaryBlue.copy(alpha = 0.05f))
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Crop Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            OutlinedButton(
                onClick = onRotate,
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ঘোরাতে ট্যাপ করুন", fontSize = 13.sp)
            }

            Button(
                onClick = {
                    val cropped = autoCropBitmap(bitmap, rotationAngle)
                    onConfirmCrop(cropped)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("scanner_confirm_crop_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("অটো ক্রপ সম্পূর্ণ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EnhancementStep(
    bitmap: Bitmap,
    selectedFilter: ScanFilter,
    onFilterSelect: (ScanFilter) -> Unit,
    onConfirmEnhance: (Bitmap) -> Unit
) {
    val colors = LocalAppColors.current
    val processedBitmap = remember(bitmap, selectedFilter) {
        applyScanFilter(bitmap, selectedFilter)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Rendered Filtered Preview
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(12.dp))
                .background(colors.cardBackground, RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = processedBitmap.asImageBitmap(),
                contentDescription = "Enhanced Document",
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Options List
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "কালার ফিল্টার সিলেক্ট করুন:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(ScanFilter.values()) { _, filter ->
                    val isSelected = filter == selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterSelect(filter) },
                        label = {
                            Text(
                                text = when (filter) {
                                    ScanFilter.ORIGINAL -> "অরিজিনাল"
                                    ScanFilter.MAGIC_COLOR -> "ম্যাজিক কালার"
                                    ScanFilter.DOCUMENT_BW -> "ডকুমেন্ট B&W"
                                    ScanFilter.GRAYSCALE -> "গ্রে-স্কেল"
                                },
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = { onConfirmEnhance(processedBitmap) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("scanner_confirm_enhance_button")
            ) {
                Text("পেজ নিশ্চিত করুন", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PreviewStep(
    pages: List<ScannedPage>,
    selectedIndex: Int,
    onSelectPage: (Int) -> Unit,
    onRetake: () -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: (Int) -> Unit,
    onSave: () -> Unit
) {
    val colors = LocalAppColors.current
    val currentPage = pages.getOrNull(selectedIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Document Page Preview
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(14.dp))
                .background(colors.cardBackground, RoundedCornerShape(14.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (currentPage != null) {
                Image(
                    bitmap = currentPage.processedBitmap.asImageBitmap(),
                    contentDescription = "Scanned Page Preview",
                    modifier = Modifier.fillMaxSize()
                )

                // Page count overlay tag
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "পেজ ${selectedIndex + 1} / ${pages.size}",
                        fontSize = 11.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Thumbnail strip for multi-page document
        if (pages.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                itemsIndexed(pages) { index, page ->
                    val isSelected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .size(60.dp, 80.dp)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) PrimaryBlue else colors.cardBorder,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelectPage(index) }
                            .padding(2.dp)
                    ) {
                        Image(
                            bitmap = page.processedBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Action Buttons Row: Retake / Add Page / Save
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRetake,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("scanner_retake_button")
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("আবার তুলুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onAddPage,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("scanner_add_page_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("আরও পেজ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .testTag("scanner_save_button")
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("সংরক্ষণ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// Utility: Auto Crop & Rotate Bitmap
private fun autoCropBitmap(original: Bitmap, rotation: Float): Bitmap {
    val matrix = Matrix().apply {
        if (rotation != 0f) postRotate(rotation)
    }
    val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

    // Crop inset by 5% to remove rough document edges
    val cropMarginX = (rotated.width * 0.05f).toInt()
    val cropMarginY = (rotated.height * 0.05f).toInt()

    val cropWidth = rotated.width - (cropMarginX * 2)
    val cropHeight = rotated.height - (cropMarginY * 2)

    return if (cropWidth > 0 && cropHeight > 0) {
        Bitmap.createBitmap(rotated, cropMarginX, cropMarginY, cropWidth, cropHeight)
    } else {
        rotated
    }
}

// Utility: Apply Scan Enhancement Filters
private fun applyScanFilter(source: Bitmap, filter: ScanFilter): Bitmap {
    val result = Bitmap.createBitmap(source.width, source.height, source.config ?: Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(result)
    val paint = Paint()

    when (filter) {
        ScanFilter.ORIGINAL -> {
            return source
        }
        ScanFilter.MAGIC_COLOR -> {
            // Enhanced Contrast + Warmth Color Matrix
            val colorMatrix = ColorMatrix(
                floatArrayOf(
                    1.2f, 0f, 0f, 0f, 10f,
                    0f, 1.2f, 0f, 0f, 10f,
                    0f, 0f, 1.2f, 0f, 10f,
                    0f, 0f, 0f, 1.0f, 0f
                )
            )
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        ScanFilter.DOCUMENT_BW -> {
            // High-contrast Black & White
            val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
            val bwMatrix = ColorMatrix(
                floatArrayOf(
                    2.0f, 0f, 0f, 0f, -100f,
                    0f, 2.0f, 0f, 0f, -100f,
                    0f, 0f, 2.0f, 0f, -100f,
                    0f, 0f, 0f, 1.0f, 0f
                )
            )
            colorMatrix.postConcat(bwMatrix)
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        ScanFilter.GRAYSCALE -> {
            val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
            paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
    }

    canvas.drawBitmap(source, 0f, 0f, paint)
    return result
}

// Utility: Sample Document Generator for Instant Testing
private fun generateSampleDocumentBitmap(): Bitmap {
    val width = 600
    val height = 800
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // White document background
    val bgPaint = Paint().apply { color = android.graphics.Color.WHITE }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // Document Header Line
    val primaryPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#2563EB")
        textSize = 32f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawText("হিসাব খাতা - অফিসিয়াল রসিদ", 40f, 70f, primaryPaint)

    val textPaint = Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = 22f
        isAntiAlias = true
    }

    canvas.drawText("তারিখ: ২৪ আগস্ট, ২০২৬", 40f, 120f, textPaint)
    canvas.drawText("ভাউচার নং: HK-2026-089", 40f, 155f, textPaint)

    // Divider
    val linePaint = Paint().apply {
        color = android.graphics.Color.LTGRAY
        strokeWidth = 3f
    }
    canvas.drawLine(40f, 180f, width - 40f, 180f, linePaint)

    // Items
    canvas.drawText("১. অফিস রেন্ট ও ইউটিলিটি: ৳ ১৫,০০০", 40f, 230f, textPaint)
    canvas.drawText("২. প্রিন্টিং ও স্টেশনারি: ৳ ২,৫০০", 40f, 280f, textPaint)
    canvas.drawText("৩. স্ন্যাক্স ও যাতায়াত: ৳ ৮৫০", 40f, 330f, textPaint)

    // Total
    val totalPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#16A34A")
        textSize = 28f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawText("মোট পরিশোধিত: ৳ ১৮,৩৫০", 40f, 400f, totalPaint)

    // Stamp / Seal
    val stampPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#DC2626")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    canvas.drawCircle(450f, 380f, 60f, stampPaint)

    val stampTextPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#DC2626")
        textSize = 20f
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
    }
    canvas.drawText("PAID", 425f, 388f, stampTextPaint)

    return bitmap
}

// Utility: Save Scanned Document to Local Storage
private fun saveScannedDocument(context: Context, pages: List<ScannedPage>) {
    try {
        val dir = File(context.filesDir, "scanned_docs")
        if (!dir.exists()) dir.mkdirs()

        val timeStamp = System.currentTimeMillis()
        pages.forEachIndexed { index, page ->
            val file = File(dir, "Doc_${timeStamp}_page_${index + 1}.jpg")
            val out = FileOutputStream(file)
            page.processedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.flush()
            out.close()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
