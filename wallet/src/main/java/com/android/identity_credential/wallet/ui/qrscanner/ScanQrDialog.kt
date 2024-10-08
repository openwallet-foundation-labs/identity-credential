package com.android.identity_credential.wallet.ui.qrscanner

import android.Manifest
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.R
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScannerView
import com.budiyev.android.codescanner.DecodeCallback
import com.budiyev.android.codescanner.ErrorCallback
import com.budiyev.android.codescanner.ScanMode
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

const val TAG = "ScanQrDialog"

/**
 * Composable Dialog that shows a [title], [description], Qr scanner/camera surface, and close button.
 * Issues callbacks to [onClose] for dismiss requests (from dialog or close button). When a Qr code
 * is successfully scanned, the callback [onScannedQrCode] is invoked with the decoded text.
 *
 * A [modifier] can be passed for setting the Dialog's dimensions
 */
@Composable
fun ScanQrDialog(
    title: String,
    description: String,
    onClose: () -> Unit,
    onScannedQrCode: (String) -> Unit,
    modifier: Modifier? = null
) {
    // if provided, use the passed-in modifier and set dialog properties that allow for adjustment of dimensions
    val (dialogModier: Modifier, dialogProperties: DialogProperties) =
        if (modifier != null) {
            Pair(
                modifier, DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            )
        } else {
            Pair(Modifier, DialogProperties())
        }

    AlertDialog(
        modifier = dialogModier,
        properties = dialogProperties,
        icon = {
            Icon(
                Icons.Filled.QrCode,
                contentDescription = stringResource(R.string.reader_screen_qr_icon_content_description)
            )
        },
        title = { Text(text = title) },
        text = { QrScanner(description, onScannedQrCode) },
        onDismissRequest = { onClose.invoke() },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = {
                    onClose.invoke()
                }
            ) {
                Text(stringResource(R.string.reader_screen_scan_qr_dialog_dismiss_button))
            }
        }
    )
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun QrScanner(
    description: String,
    onScannedQrCode: (String) -> Unit,
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    if (!cameraPermissionState.status.isGranted) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier.padding(20.dp),
                    text = stringResource(R.string.reader_screen_scan_qr_dialog_missing_permission_text)
                )
                Button(
                    onClick = {
                        cameraPermissionState.launchPermissionRequest()
                    }
                ) {
                    Text(stringResource(R.string.reader_screen_scan_qr_dialog_request_permission_button))
                }
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = description
                )
                Row(
                    modifier = Modifier
                        .width(300.dp)
                        .height(300.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        factory = { context ->
                            CodeScannerView(context).apply {
                                val codeScanner = CodeScanner(context, this).apply {
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    isAutoFocusEnabled = true
                                    isAutoFocusButtonVisible = false
                                    scanMode = ScanMode.SINGLE
                                    decodeCallback = DecodeCallback { result ->
                                        releaseResources()
                                        onScannedQrCode.invoke(result.text)
                                    }
                                    errorCallback = ErrorCallback { error ->
                                        Logger.w(TAG, "Error scanning QR", error)
                                        releaseResources()
                                    }
                                    camera = CodeScanner.CAMERA_BACK
                                    isFlashEnabled = false
                                }
                                codeScanner.startPreview()
                            }
                        },
                    )
                }
            }
        }
    }
}