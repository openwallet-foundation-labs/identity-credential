package com.android.identity.testapp.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraEngine
import org.multipaz.compose.camera.CameraSelection
import org.multipaz.compose.camera.CameraWorkResult
import org.multipaz.compose.camera.ScreenOrientation
import org.multipaz.compose.camera.ScreenOrientationObserver
import org.multipaz.compose.camera.rememberScreenOrientation
import org.multipaz.compose.permissions.rememberCameraPermissionState
import org.multipaz.facedetect.camera.plugins.facecapture.FaceCaptureConfig
import org.multipaz.facedetect.camera.plugins.facecapture.rememberFaceCapturePlugin
import org.multipaz.facedetect.camera.plugins.facedetect.rememberFaceDetectorPlugin
import org.multipaz.facedetect.camera.plugins.facedetect.transformPoint
import org.multipaz.facedetect.camera.plugins.facedetect.transformRect
import org.multipaz.util.Logger

private const val TAG = "FaceMatchingScreen"

private sealed class FaceMatchingScreenState {
    data object NoFaceEnrolled : FaceMatchingScreenState()
    data class FaceEnrolled(val enrolledFace: ImageBitmap?) : FaceMatchingScreenState()
    data class DetectFace(val cameraSelection: CameraSelection) : FaceMatchingScreenState()
}

@Composable
fun FaceMatchingScreen(
    showToast: (message: String) -> Unit,
) {
    val cameraPermissionState = rememberCameraPermissionState()
    var faceScreenState: FaceMatchingScreenState by remember { mutableStateOf(FaceMatchingScreenState.NoFaceEnrolled) }
    var displayedFaceBitmap: ImageBitmap? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    if (!cameraPermissionState.isGranted) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = CenterHorizontally
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        cameraPermissionState.launchPermissionRequest()
                    }
                }
            ) {
                Text("Request Camera permission")
            }
        }
    } else {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            when (faceScreenState) {
                is FaceMatchingScreenState.NoFaceEnrolled ->
                    FaceNotEnrolledScreen(
                        onFaceDetection = { cameraSelection ->
                            faceScreenState = FaceMatchingScreenState.DetectFace(cameraSelection)
                        }
                    )

                is FaceMatchingScreenState.FaceEnrolled -> {
                    FaceEnrolledScreen(
                        displayedFaceBitmap,
                        onDeleteFace = {
                            displayedFaceBitmap = null
                            faceScreenState = FaceMatchingScreenState.NoFaceEnrolled
                        }
                    )
                }

                is FaceMatchingScreenState.DetectFace -> {
                    FaceDetectionScreen(
                        cameraSelection = (faceScreenState as FaceMatchingScreenState.DetectFace).cameraSelection,
                        onNavigateBack = {
                            faceScreenState =
                                displayedFaceBitmap?.let { FaceMatchingScreenState.FaceEnrolled(it) }
                                    ?: FaceMatchingScreenState.NoFaceEnrolled
                        },
                        onFaceEnrolled = { enrolledFaceBitmap ->
                            displayedFaceBitmap = enrolledFaceBitmap
                            faceScreenState = FaceMatchingScreenState.FaceEnrolled(displayedFaceBitmap)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FaceDetectionScreen(
    cameraSelection: CameraSelection,
    onFaceEnrolled: (enrolledFaceImage: ImageBitmap) -> Unit,
    onNavigateBack: () -> Unit
) {
    var cameraInitialized by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var cameraEngine by remember { mutableStateOf<CameraEngine?>(null) }
    val faceDetector = rememberFaceDetectorPlugin(coroutineScope)
    val faceCapture = rememberFaceCapturePlugin(
        FaceCaptureConfig(
            isFrontCamera = cameraSelection == CameraSelection.DEFAULT_FRONT_CAMERA
        )
    )
    var cameraRatio by remember { mutableStateOf(1f) }
    var borderRect by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    val orientationObserver = remember { ScreenOrientationObserver() }
    val orientation = rememberScreenOrientation(orientationObserver)
    // Using a mutableStateOf to keep the flow across recompositions
    var latestDetectedFace by remember { mutableStateOf<CameraWorkResult.FaceDetectionSuccess?>(null) }

    // Collect the flow in a LaunchedEffect with stable keys
    LaunchedEffect(faceDetector) {
        faceDetector.faceDetectionFlow.consumeAsFlow()
            .collect { result ->
                when (result) {
                    is CameraWorkResult.FaceDetectionSuccess -> {
                        // Update the state only if the result is different
                        if (result != latestDetectedFace) {
                            latestDetectedFace = result
                            Logger.d(TAG, "detected")
                        }
                    }

                    else -> {
                        Logger.e(
                            TAG, "Face detection error.",
                            (result as CameraWorkResult.Error).exception
                        )
                    }
                }
            }
    }

    // Collect the orientation in a LaunchedEffect
    LaunchedEffect(orientation) {
        Logger.d(TAG, "Orientation changed: $orientation")
    }

    Dialog(onDismissRequest = { onNavigateBack() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column {
                Text(
                    text = "Capture detected face",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                Box(
                    modifier = if (cameraInitialized) {
                        if (orientation == ScreenOrientation.LANDSCAPE) {
                            Modifier
                                .fillMaxHeight()
                                .weight(1f)
                                .aspectRatio(1f / cameraRatio, false)
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(cameraRatio, false)
                        }
                            .onGloballyPositioned {
                                if (latestDetectedFace == null) {
                                    borderRect = Rect(0f, 0f, 0f, 0f)
                                }
                                borderRect = it.boundsInRoot()
                            }
                    } else {
                        Modifier.fillMaxSize()
                    }
                ) {
                    Camera(
                        modifier = if (cameraInitialized) {
                            if (orientation == ScreenOrientation.LANDSCAPE) {
                                Modifier.fillMaxHeight()
                            } else {
                                Modifier.fillMaxWidth()
                            }
                        } else {
                            Modifier.fillMaxSize()
                        },
                        cameraConfiguration = {
                            setCameraLens(cameraSelection)
                            showPreview(cameraPreview = true)
                            addPlugin(faceDetector)
                            addPlugin(faceCapture)
                        },
                        onCameraReady = {
                            cameraEngine = it
                            cameraEngine!!.initializePlugins()
                            faceDetector.startDetection()
                            cameraRatio = cameraEngine?.getAspectRatio()?.toFloat() ?: 1f
                            cameraInitialized = true
                            Logger.d(TAG, "CameraEngine ready")
                        }
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        drawRect(
                            color = Color.Green,
                            topLeft = Offset(0f, 0f),
                            size = Size(
                                borderRect.width,
                                borderRect.height
                            ),
                            style = Stroke(width = 5.dp.toPx())
                        )
                        drawFaceOverlay(latestDetectedFace, cameraSelection)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        Logger.d(TAG, "Trigger capture")
                        latestDetectedFace?.let {
                            coroutineScope.launch {
                                faceCapture.captureFace(it).collectLatest { result ->
                                    onFaceEnrolled(result)
                                }
                            }
                        }
                    }) {
                        Text(text = "Capture")
                    }
                    TextButton(onClick = {
                        cameraInitialized = false
                        onNavigateBack()
                    }) {
                        Text(text = "Close")
                    }
                }
            }
        }
    }
}

@Composable
fun FaceEnrolledScreen(displayedFaceBitmap: ImageBitmap?, onDeleteFace: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()), // Make the Column scrollable
    ) {
        Text("Enrolled Face")
        Image(
            bitmap = displayedFaceBitmap ?: createRectangularImageBitmap(128, 128, Color.Gray),
            contentDescription = "Enrolled face image",
            modifier = Modifier
                .size(200.dp, 200.dp)
                .align(CenterHorizontally)
        )
        TextButton(onClick = { onDeleteFace() }) {
            Text("Delete Enrolled Face")
        }
    }
}

@Composable
fun FaceNotEnrolledScreen(onFaceDetection: (CameraSelection) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
        Text("No Face Enrolled")

        TextButton(onClick = { onFaceDetection(CameraSelection.DEFAULT_FRONT_CAMERA) }) {
            Text("Enroll Face using Front Camera (Selfie)")
        }

        TextButton(onClick = { onFaceDetection(CameraSelection.DEFAULT_BACK_CAMERA) }) {
            Text("Enroll Face using Back Camera (Portrait)")
        }
    }
}

private fun DrawScope.drawFaceOverlay(
    detectedFace: CameraWorkResult.FaceDetectionSuccess?,
    cameraSelection: CameraSelection
) {
    if (detectedFace == null) return

    with(detectedFace) {
        faceData.let { data ->
            imageSize.let { imageSize ->
                cameraAngle.let { imageRotation ->
                    val transformedRect = transformRect(
                        rect = data.faceRect,
                        previewSize = size,
                        imageSize = imageSize ?: size, //For iOS, assuming the face is in image coordinates.
                        imageRotation = imageRotation ?: 0,
                        isFrontCamera = cameraSelection == CameraSelection.DEFAULT_FRONT_CAMERA
                    )

                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(transformedRect.left, transformedRect.top),
                        size = Size(
                            transformedRect.width,
                            transformedRect.height
                        ),
                        style = Stroke(width = 5.dp.toPx())
                    )

                    val leftEye = transformPoint(
                        data.leftEyePosition,
                        outputSize = size,
                        detectorSize = imageSize ?: size,
                        imageRotation = imageRotation ?: 0,
                        isFrontCamera = cameraSelection == CameraSelection.DEFAULT_FRONT_CAMERA
                    )

                    val rightEye = transformPoint(
                        data.rightEyePosition,
                        outputSize = size,
                        detectorSize = imageSize ?: size ,
                        imageRotation = imageRotation ?: 0,
                        isFrontCamera = cameraSelection == CameraSelection.DEFAULT_FRONT_CAMERA
                    )

                    val mouthBottom = transformPoint(
                        data.mouthPosition,
                        outputSize = size,
                        detectorSize = imageSize ?: size,
                        imageRotation = imageRotation ?: 0,
                        isFrontCamera = cameraSelection == CameraSelection.DEFAULT_FRONT_CAMERA
                    )

                    drawLine(
                        color = Color.Red,
                        leftEye, rightEye,
                        strokeWidth = 5.dp.toPx()
                    )

                    drawLine(
                        color = Color.Red,
                        leftEye, mouthBottom,
                        strokeWidth = 5.dp.toPx()
                    )

                    drawLine(
                        color = Color.Red,
                        rightEye, mouthBottom,
                        strokeWidth = 5.dp.toPx()
                    )
                }
            }
        }
    }
}

/** Placeholder. */
fun createRectangularImageBitmap(width: Int, height: Int, color: Color): ImageBitmap {
    val bitmap = ImageBitmap(width, height)
    val paint = Paint().apply {
        this.color = color
        style = PaintingStyle.Fill
    }

    val path = Path().apply {
        addRect(Rect(0f, 0f, width.toFloat(), height.toFloat()))
    }

    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)

    canvas.drawPath(path, paint)
    return bitmap
}