package org.multipaz.selfiecheck

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.multipaz.compose.camera.Camera
import org.multipaz.compose.camera.CameraCaptureResolution
import org.multipaz.facedetection.detectFaces
import org.multipaz.multipaz_vision.generated.resources.Res
import org.multipaz.multipaz_vision.generated.resources.selfie_button_send
import org.multipaz.multipaz_vision.generated.resources.selfie_button_start_check
import org.multipaz.multipaz_vision.generated.resources.selfie_button_try_again
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_center_face
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_circular_gaze_remaining
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_circular_gaze_start
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_clear_smile
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_close_eyes
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_close_firmly
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_completed
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_failed_timeout
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_keep_head_level
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_keep_looking
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_look_around
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_look_straight
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_move_horizontally
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_move_vertically
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_preface
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_rotate_head_down
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_rotate_head_left
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_rotate_head_right
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_rotate_head_up
import org.multipaz.multipaz_vision.generated.resources.selfie_instruction_smile
import org.multipaz.multipaz_vision.generated.resources.selfie_prompt_consent
import org.multipaz.multipaz_vision.generated.resources.selfie_prompt_done
import org.multipaz.selfiecheck.SelfieCheckStep.COMPLETED
import org.multipaz.selfiecheck.SelfieCheckStep.DONE
import org.multipaz.selfiecheck.SelfieCheckStep.FAILED
import org.multipaz.selfiecheck.SelfieCheckStep.IMAGE_TAKEN
import org.multipaz.selfiecheck.SelfieCheckStep.INITIAL
import org.multipaz.selfiecheck.SelfieCheckViewModel.EventId
import org.multipaz.selfiecheck.SelfieCheckViewModel.StepFeedback
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Selfie check screen content presenter.
 * Includes live camera preview as well as dynamic user-guiding info-graphics and textual content.
 *
 * @param modifier Modifier for the screen composition in the parent composable.
 * @param viewModel Selfie check view model orchestrating the user journey.
 * @param identityIssuer The name or identification of the inquiring identity issuer who is receiving the result
 *     of the person image verification (e.g. "Utopia Department of Motor Vehicles").
 */
@Composable
fun SelfieCheck(
    modifier: Modifier = Modifier,
    viewModel: SelfieCheckViewModel,
    onVerificationComplete: () -> Unit,
    identityIssuer: String
) {
    val isLandscape by viewModel.isLandscape.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val instructions by viewModel.instructions.collectAsState()
    val hapticFeedback = LocalHapticFeedback.current
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()
    val countdownProgress by viewModel.countdownProgress.collectAsState()
    val sectors = remember { mutableStateOf(List(8) { false }) }
    var isShowingCameraPreview by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()


    LaunchedEffect(currentStep) {
        if (currentStep == DONE) {
            onVerificationComplete()
        }
        isShowingCameraPreview = !(currentStep == INITIAL || currentStep == DONE)
    }


    // Step success feedback monitor.
    LaunchedEffect(viewModel.stepSuccessEvent) {
        viewModel.stepSuccessEvent.collect {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val cameraPreviewContent = @Composable {
        Box(
            modifier = Modifier
                .fillMaxWidth(if (isShowingCameraPreview) 0.8f else 0f) // Hide but not remove to track orientation.
                .aspectRatio(1f)
                .clip(CircleShape)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            Camera(
                modifier = Modifier.fillMaxSize(),
                captureResolution = CameraCaptureResolution.MEDIUM,
                showCameraPreview = isShowingCameraPreview,
                onFrameCaptured = { frameData ->
                    viewModel.setLandscape(frameData.isLandscape)
                    if (currentStep != INITIAL &&
                        currentStep != DONE &&
                        currentStep != FAILED
                    ) {
                        val faces = detectFaces(frameData)
                        if (!faces.isNullOrEmpty()) {
                            viewModel.onFaceDataUpdated(
                                faces[0],
                                frameData
                            )
                        }
                    }
                }
            )

            // Face preview overlay for centering the face.
            if (currentStep != INITIAL &&
                currentStep != COMPLETED &&
                currentStep != FAILED &&
                currentStep != IMAGE_TAKEN &&
                currentStep != DONE
            ) {
                // Overlays with graphic step hints.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .aspectRatio(0.75f)
                        .border(
                            BorderStroke(4.dp, MaterialTheme.colorScheme.primary),
                            CircleShape
                        )
                )
                when (instructions.eventId) {
                    EventId.MOVE_HORIZONTALLY -> VisualHintMoveHorizontally(this, Modifier)
                    EventId.MOVE_VERTICALLY -> VisualHintMoveVertically(this, Modifier)
                    EventId.ROTATE_HEAD_DOWN -> VisualHintMoveUpDown(this, Modifier, isUp = false)
                    EventId.ROTATE_HEAD_UP -> VisualHintMoveUpDown(this, Modifier, isUp = true)
                    EventId.ROTATE_HEAD_LEFT -> VisualHintMoveLeftRight(this, Modifier, isLeft = true)
                    EventId.ROTATE_HEAD_RIGHT -> VisualHintMoveLeftRight(this, Modifier, isLeft = false)
                    EventId.SECTOR_HIT -> {
                        // New list to facilitate recomposition of Canvas.
                        val newList = sectors.value.toMutableList().apply {
                            this[instructions.textParam.toInt()] = true
                        }
                        sectors.value = newList.toList()
                        VisualHintGaze(Modifier, sectorStates = sectors.value)

                        if (!sectors.value.contains(false)) {
                            coroutineScope.launch {
                                delay(500L)
                                // Reset if all sectors are filled by prior check session.
                                sectors.value = MutableList(GAZE_CIRCLE_SECTORS) { false }
                            }
                        }
                    }

                    EventId.KEEP_HEAD_LEVEL -> { // Reset to start over.
                        sectors.value = MutableList(GAZE_CIRCLE_SECTORS) { false }
                        VisualHintGaze(Modifier, sectorStates = sectors.value)
                    }

                    EventId.LOOK_STRAIGHT -> {
                        VisualHintMoveHorizontally(this, Modifier)
                        VisualHintMoveVertically(this, Modifier)
                    }

                    else -> {}
                }
            }
        }
    }

    val instructionAndControlsContent = @Composable {
        if (isLandscape && currentStep == INITIAL) {
            // Show the button on the right in Landscape mode (more space for instructions),
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Step prompt.
                Card(
                    modifier = Modifier.fillMaxWidth().padding(4.dp).weight(2f),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    SelfieCheckInstructions(instructions)
                }

                Button(
                    onClick = { viewModel.startSelfieCheck() },
                    modifier = Modifier.wrapContentWidth().weight(1f)
                ) {
                    Text(
                        if (currentStep == FAILED) {
                            stringResource(Res.string.selfie_button_try_again)
                        } else {
                            stringResource(Res.string.selfie_button_start_check)
                        }
                    )
                }
            }
        }
        else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (isLandscape) Arrangement.Center else Arrangement.Top,
                modifier = if (isLandscape)
                    Modifier.fillMaxHeight().padding(start = 16.dp) else Modifier.fillMaxWidth()
            ) {
                // Step prompt.
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    SelfieCheckInstructions(instructions)
                }

                Spacer(modifier = Modifier.height(24.dp))

                when (currentStep) {
                    INITIAL, FAILED -> {
                        Button(
                            onClick = { viewModel.startSelfieCheck() },
                            modifier = Modifier.align(Alignment.End).wrapContentWidth()
                        ) {
                            Text(
                                if (currentStep == FAILED) {
                                    stringResource(Res.string.selfie_button_try_again)
                                } else {
                                    stringResource(Res.string.selfie_button_start_check)
                                }
                            )
                        }
                    }

                    IMAGE_TAKEN -> // Ask for image sharing consent.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val isChecked = remember { mutableStateOf(false) }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = isChecked.value,
                                    onCheckedChange = {
                                        isChecked.value = it

                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                val fullText = stringResource(Res.string.selfie_prompt_consent, identityIssuer)
                                Text(
                                    text = buildAnnotatedString {
                                        val startIndex = fullText.indexOf(identityIssuer)
                                        val endIndex = startIndex + identityIssuer.length
                                        append(fullText.substring(0, startIndex))
                                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                                            append(fullText.substring(startIndex, endIndex))
                                        }
                                        append(fullText.substring(endIndex))
                                    }
                                )
                            }

                            Button(
                                onClick = { viewModel.selfieSendConsentReceived() },
                                modifier = Modifier.wrapContentSize(),
                                enabled = isChecked.value
                            ) {
                                Text(stringResource(Res.string.selfie_button_send))
                            }
                        }

                    DONE -> {
                        Text(
                            stringResource(Res.string.selfie_prompt_done),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                    }

                    else -> {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(64.dp)
                        ) {
                            CircularProgressIndicator(
                                progress = { countdownProgress },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 6.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "$countdownSeconds",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (!isLandscape) { // Needed only here to push content up.
                    Spacer(modifier = Modifier.height(64.dp))
                }
            }
        }
    }

    val selfieImageContent = @Composable {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = viewModel.internalCapturedFaceImagePreview ?: ImageBitmap(1, 1),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (isLandscape) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                //.padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,

            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentStep == IMAGE_TAKEN) {
                        selfieImageContent()
                    } else {
                        cameraPreviewContent()
                    }
                }
                if (isShowingCameraPreview) {
                    Spacer(modifier = Modifier.width(16.dp)) // Space between camera and controls.
                }

                Box(
                    modifier = if (isShowingCameraPreview) Modifier.weight(1f) else Modifier,
                    contentAlignment = Alignment.Center // Center the content vertically within this Box.
                ) {
                    instructionAndControlsContent()
                }
            }
        }
    } else { // Portrait layout.
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            if (currentStep == IMAGE_TAKEN) {
                selfieImageContent()
            } else {
                cameraPreviewContent()
            }

            instructionAndControlsContent()
        }
    }
}

private object Painter {
    const val NUM_RADIANS_PER_DEGREE = PI / 180.0
    const val STROKE_WIDTH = 4f
    const val ARCH_WIDTH = 20f
    val filledColor = Color.Red
    val strokeColor = Color.White
}

@Composable
fun VisualHintGaze(modifier: Modifier, sectorStates: List<Boolean>) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val center = Offset(canvasWidth / 2, canvasHeight / 2)

        val outerRadius = (kotlin.math.min(canvasWidth, canvasHeight) / 2f) - Painter.STROKE_WIDTH / 2
        val innerRadius = outerRadius - Painter.ARCH_WIDTH

        if (sectorStates.isEmpty()) return@Canvas

        val totalAngle = 180f // Drawing on the bottom half (0 for top and change sign in the sweepAnglePerSector).
        val numSectors = 8
        val sweepAnglePerSector = totalAngle / numSectors
        var currentStartAngle = 0f

        sectorStates.forEachIndexed { _, isFilled ->
            if (isFilled) {
                // Draw filled sector.
                val sectorPath = Path().apply {
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            center.x - innerRadius,
                            center.y - innerRadius,
                            center.x + innerRadius,
                            center.y + innerRadius
                        ),
                        startAngleDegrees = currentStartAngle,
                        sweepAngleDegrees = sweepAnglePerSector,
                        forceMoveTo = true // Important: starts a new sub-path.
                    )

                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            center.x - outerRadius,
                            center.y - outerRadius,
                            center.x + outerRadius,
                            center.y + outerRadius
                        ),
                        startAngleDegrees = currentStartAngle + sweepAnglePerSector,
                        sweepAngleDegrees = -sweepAnglePerSector, // Counterclockwise.
                        forceMoveTo = false
                    )

                    close()
                }
                drawPath(
                    path = sectorPath,
                    color = Painter.filledColor,
                    style = Fill
                )
            }

            // Outer arc of the sector.
            drawArc(
                color = Painter.strokeColor,
                startAngle = currentStartAngle,
                sweepAngle = sweepAnglePerSector,
                useCenter = false,
                topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                size = Size(outerRadius * 2, outerRadius * 2),
                style = Stroke(width = Painter.STROKE_WIDTH)
            )
            // Inner arc of the sector.
            drawArc(
                color = Painter.strokeColor,
                startAngle = currentStartAngle,
                sweepAngle = sweepAnglePerSector,
                useCenter = false,
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius),
                size = Size(innerRadius * 2, innerRadius * 2),
                style = Stroke(width = Painter.STROKE_WIDTH)
            )

            drawDivider(currentStartAngle, center, outerRadius, innerRadius)

            currentStartAngle += sweepAnglePerSector
        }

        // End divider.
        drawDivider(currentStartAngle, center, outerRadius, innerRadius)
    }
}

/** Arch sector divider. */
private fun DrawScope.drawDivider(
    currentStartAngle: Float,
    center: Offset,
    outerRadius: Float,
    innerRadius: Float
) {
    val finalAngleRad = currentStartAngle * Painter.NUM_RADIANS_PER_DEGREE
    val finalOuter = Offset(
        center.x + outerRadius * cos(finalAngleRad).toFloat(),
        center.y + outerRadius * sin(finalAngleRad).toFloat()
    )
    val finalInner = Offset(
        center.x + innerRadius * cos(finalAngleRad).toFloat(),
        center.y + innerRadius * sin(finalAngleRad).toFloat()
    )
    drawLine(Painter.strokeColor, finalInner, finalOuter, Painter.STROKE_WIDTH)
}

@Composable
fun VisualHintMoveLeftRight(boxScope: BoxScope, modifier: Modifier.Companion, isLeft: Boolean) {
    val leftIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val rightIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward
    with(boxScope) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLeft) {
                Icon(
                    imageVector = leftIcon,
                    contentDescription = "Look Left",
                    tint = Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(leftIcon.defaultWidth))
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isLeft) {
                Icon(
                    imageVector = rightIcon,
                    contentDescription = "Look Right",
                    tint = Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(rightIcon.defaultWidth))
            }
        }
    }
}

@Composable
fun VisualHintMoveUpDown(boxScope: BoxScope, modifier: Modifier.Companion, isUp: Boolean) {
    val upIcon: ImageVector = Icons.Filled.ArrowUpward
    val downIcon: ImageVector = Icons.Filled.ArrowDownward
    with(boxScope) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isUp) {
                Icon(
                    imageVector = upIcon,
                    contentDescription = "Look up",
                    tint = Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(upIcon.defaultHeight))
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isUp) {
                Icon(
                    imageVector = downIcon,
                    contentDescription = "Look down",
                    tint = Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Spacer(modifier = Modifier.size(downIcon.defaultHeight))
            }
        }
    }
}

@Composable
fun VisualHintMoveVertically(boxScope: BoxScope, modifier: Modifier.Companion) {
    with(boxScope) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowDownward,
                contentDescription = "Vertical motion indicator",
                tint = Color.Red,
                modifier = Modifier.padding(16.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = "Vertical motion indicator",
                tint = Color.Red,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
fun VisualHintMoveHorizontally(
    boxScope: BoxScope,
    modifier: Modifier = Modifier,
) {
    with(boxScope) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Horizontal motion indicator",
                tint = Color.Red,
                modifier = Modifier.padding(16.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Horizontal motion indicator",
                tint = Color.Red,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun SelfieCheckInstructions(stepFeedback: StepFeedback) {
    when (stepFeedback.eventId) {
        EventId.INITIAL -> stringResource(Res.string.selfie_instruction_preface, stepFeedback.textParam)
        EventId.CENTER_FACE -> stringResource(Res.string.selfie_instruction_center_face)
        EventId.ROTATE_HEAD_LEFT -> stringResource(Res.string.selfie_instruction_rotate_head_left)
        EventId.ROTATE_HEAD_RIGHT -> stringResource(Res.string.selfie_instruction_rotate_head_right)
        EventId.ROTATE_HEAD_UP -> stringResource(Res.string.selfie_instruction_rotate_head_up)
        EventId.ROTATE_HEAD_DOWN -> stringResource(Res.string.selfie_instruction_rotate_head_down)
        EventId.CIRCULAR_GAZE_START -> stringResource(Res.string.selfie_instruction_circular_gaze_start)
        EventId.CIRCULAR_GAZE_LEFT ->
            stringResource(Res.string.selfie_instruction_circular_gaze_remaining, stepFeedback.textParam)
        EventId.CLOSE_EYES -> stringResource(Res.string.selfie_instruction_close_eyes)
        EventId.SMILE -> stringResource(Res.string.selfie_instruction_smile)
        EventId.COMPLETED -> stringResource(Res.string.selfie_instruction_completed)
        EventId.MOVE_HORIZONTALLY -> stringResource(Res.string.selfie_instruction_move_horizontally)
        EventId.MOVE_VERTICALLY -> stringResource(Res.string.selfie_instruction_move_vertically)
        EventId.KEEP_HEAD_LEVEL -> stringResource(Res.string.selfie_instruction_keep_head_level)
        EventId.KEEP_LOOKING -> stringResource(Res.string.selfie_instruction_keep_looking, stepFeedback.textParam)
        EventId.LOOK_AROUND -> stringResource(Res.string.selfie_instruction_look_around)
        EventId.CLOSE_FIRMLY -> stringResource(Res.string.selfie_instruction_close_firmly)
        EventId.CLEAR_SMILE -> stringResource(Res.string.selfie_instruction_clear_smile)
        EventId.FAILED_TIMEOUT -> stringResource(Res.string.selfie_instruction_failed_timeout, stepFeedback.textParam)
        EventId.LOOK_STRAIGHT -> stringResource(Res.string.selfie_instruction_look_straight)
        EventId.SECTOR_HIT -> stringResource(Res.string.selfie_instruction_keep_looking)
        EventId.IMAGE_TAKEN -> stringResource(Res.string.selfie_instruction_completed)
        EventId.DONE -> null // No card, or stringResource(Res.string.selfie_instruction_selfie_sent) to show some.
    }?.let {
        if (stepFeedback.eventId == EventId.INITIAL) {
            val fullText = stringResource(Res.string.selfie_instruction_preface, stepFeedback.textParam)
            Text(
                text = buildAnnotatedString {
                    val startIndex = fullText.indexOf(stepFeedback.textParam)
                    val endIndex = startIndex + stepFeedback.textParam.length
                    append(fullText.substring(0, startIndex))
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(fullText.substring(startIndex, endIndex))
                    }
                    append(fullText.substring(endIndex))
                },
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
            )
        } else {
            Text(
                text = it,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                minLines = 2
            )
        }
    }
}
