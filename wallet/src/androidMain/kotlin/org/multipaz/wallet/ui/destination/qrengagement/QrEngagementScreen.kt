package org.multipaz_credential.wallet.ui.destination.qrengagement

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.multipaz_credential.wallet.QrEngagementViewModel
import org.multipaz_credential.wallet.R
import org.multipaz_credential.wallet.navigation.WalletDestination
import org.multipaz_credential.wallet.ui.ScreenWithAppBar
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix

@Composable
fun QrEngagementScreen(
    qrEngagementViewModel: QrEngagementViewModel,
    onNavigate: (String) -> Unit,
) {
    BackHandler {
        qrEngagementViewModel.stopQrConnection()
        onNavigate(WalletDestination.PopBackStack.route)
    }

    val engagementState = qrEngagementViewModel.state

    ScreenWithAppBar(title = stringResource(R.string.qr_title), navigationIcon = {
        IconButton(
            onClick = {
                qrEngagementViewModel.stopQrConnection()
                onNavigate(WalletDestination.PopBackStack.route)
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.accessibility_go_back_icon)
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
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(text = stringResource(R.string.qr_instructions))

                    Spacer(modifier = Modifier.height(50.dp))

                    Image(
                        bitmap = encodeQRCodeAsBitmap(deviceEngagementUriEncoded).asImageBitmap(),
                        contentDescription = stringResource(R.string.accessibility_qr_code),
                        modifier = Modifier.fillMaxWidth(0.75f)
                    )
                }
            }

            QrEngagementViewModel.State.CONNECTED -> {
                qrEngagementViewModel.stopQrConnection()
                onNavigate(
                    WalletDestination.PopBackStack
                        .getRouteWithArguments(
                            listOf(
                                Pair(
                                    WalletDestination.PopBackStack.Argument.ROUTE,
                                    WalletDestination.Main.route
                                ),
                                Pair(
                                    WalletDestination.PopBackStack.Argument.INCLUSIVE,
                                    false
                                )
                            )
                        )
                )
            }

            QrEngagementViewModel.State.IDLE -> {}

            QrEngagementViewModel.State.ERROR -> {
                Column {
                    Text(text = stringResource(R.string.qr_error_generating))
                    Button(onClick = { qrEngagementViewModel.startQrEngagement() }) {
                        Text(text = stringResource(R.string.qr_button_retry))
                    }
                }
            }
        }
    }
}

private fun encodeQRCodeAsBitmap(str: String): Bitmap {
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

