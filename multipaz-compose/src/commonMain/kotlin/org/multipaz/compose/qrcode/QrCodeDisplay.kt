package org.multipaz.compose.qrcode

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.io.bytestring.ByteString
import org.multipaz.util.toBase64Url

@Composable
fun QrCodeDisplay(
    deviceEngagement: MutableState<ByteString?>,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        deviceEngagement.value?.let { engagement ->
            val qrCodeBitmap = remember(engagement) {
                val mdocUrl = "mdoc:" + engagement.toByteArray().toBase64Url()
                generateQrCode(mdocUrl)
            }
            Text(text = "Present QR code to mdoc reader")
            Image(
                modifier = Modifier.fillMaxWidth(),
                bitmap = qrCodeBitmap,
                contentDescription = "Device engagement QR code",
                contentScale = ContentScale.FillWidth
            )
            Button(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}