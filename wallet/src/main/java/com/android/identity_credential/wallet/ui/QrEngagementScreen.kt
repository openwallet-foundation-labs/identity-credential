package com.android.identity_credential.wallet.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.android.identity_credential.wallet.QrEngagementViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix

@Composable
fun QrEngagementScreen(qrEngagementViewModel: QrEngagementViewModel,
                       navigation: NavHostController) {
    BackHandler {
        qrEngagementViewModel.stopQrConnection()
        navigation.popBackStack()
    }

    val engagementState = qrEngagementViewModel.state

    ScreenWithAppBar(title = "QR Code", navigationIcon = {
        IconButton(
            onClick = {
                qrEngagementViewModel.stopQrConnection()
                navigation.popBackStack()
            }
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back Arrow"
            )
        }
    }
    ) {
        when (engagementState) {
            QrEngagementViewModel.State.STARTING -> {
                CircularProgressIndicator(
                    modifier = Modifier.width(64.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            QrEngagementViewModel.State.LISTENING -> {
                val deviceEngagementUriEncoded = qrEngagementViewModel.qrCode
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        bitmap = encodeQRCodeAsBitmap(deviceEngagementUriEncoded).asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            QrEngagementViewModel.State.CONNECTED -> {
                qrEngagementViewModel.stopQrConnection()
                navigation.popBackStack("MainScreen", inclusive = false)
            }

            // startQrEngagement is called before navigating to this compose, so let user know we're
            // waiting (until the QR code is generated/errors out)
            QrEngagementViewModel.State.IDLE -> {
                CircularProgressIndicator(
                    modifier = Modifier.width(64.dp),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

            QrEngagementViewModel.State.ERROR -> {
                Column {
                    Text(text = "Error generating QR Code")
                    Button(onClick = {qrEngagementViewModel.startQrEngagement()}) {
                        Text(text = "Retry")
                    }
                }
            }
        }
    }
}

fun encodeQRCodeAsBitmap(str: String): Bitmap {
    val width = 800
    val result: BitMatrix = try {
        MultiFormatWriter().encode(
            str,
            BarcodeFormat.QR_CODE, width, width, null
        )
    } catch (e: WriterException) {
        throw java.lang.IllegalArgumentException(e)
    }
    val w = result.width
    val h = result.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val offset = y * w
        for (x in 0 until w) {
            pixels[offset + x] = if (result[x, y]) Color.BLACK else Color.WHITE
        }
    }
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, w, h)
    return bitmap
}

