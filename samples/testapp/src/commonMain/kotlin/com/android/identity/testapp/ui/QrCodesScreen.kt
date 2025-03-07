package org.multipaz.testapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.multipaz.compose.qrcode.ShowQrCodeDialog
import org.multipaz.compose.qrcode.ScanQrCodeDialog

@Composable
fun QrCodesScreen(
    showToast: (message: String) -> Unit
) {
    val showMdocQrCodeDialog = remember { mutableStateOf(false) }
    val showUrlQrCodeDialog = remember { mutableStateOf(false) }
    val showQrScanDialog = remember { mutableStateOf(false) }

    if (showMdocQrCodeDialog.value) {
        ShowQrCodeDialog(
            title = { Text(text = "Scan code on reader") },
            text = { Text(text = "Your personal information won't be shared yet. You don't need to hand your " +
                    "phone to anyone to share your ID.") },
            dismissButton = "Close",
            // This is the DeviceEngagement test vector from ISO/IEC 18013-5:2021 Annex D encoded
            // as specified in clause 8.2.2.3.
            data = "mdoc:owBjMS4wAYIB2BhYS6QBAiABIVggWojRgrzl9C76WZQ/MzWdAuipaP8onZPl" +
                    "+kRLYkNDFn/iJYILFujPhY3cdpBAe6YdTDOCNwqM/PPeaqZy/GClV6oy/GcCgYMCAaMA9AH1C1BF7+90KyxIN6kKOw4dBaaRBw==",
            onDismiss = { showMdocQrCodeDialog.value = false }
        )
    }

    if (showUrlQrCodeDialog.value) {
        ShowQrCodeDialog(
            title = { Text(text = "Scan code with phone") },
            text = { Text(text = "This is a QR code for https://github.com/openwallet-foundation-labs/identity-credential") },
            dismissButton = "Close",
            data = "https://github.com/openwallet-foundation-labs/identity-credential",
            onDismiss = { showUrlQrCodeDialog.value = false }
        )
    }

    if (showQrScanDialog.value) {
        ScanQrCodeDialog(
            title = { Text ("Scan code") },
            text = { Text ("Ask the person you wish to request identity attributes from to present" +
                    " a QR code. This is usually in their identity wallet.") },
            dismissButton = "Close",
            onCodeScanned = { data ->
                if (data.startsWith("mdoc:")) {
                    showToast("Scanned mdoc URI $data")
                    showQrScanDialog.value = false
                    true
                } else {
                    false
                }
            },
            onDismiss = { showQrScanDialog.value = false }
        )
    }

    LazyColumn(
        modifier = Modifier.padding(8.dp)
    ) {
        item {
            TextButton(
                onClick = { showMdocQrCodeDialog.value = true },
                content = { Text("Show mdoc QR code") }
            )
        }

        item {
            TextButton(
                onClick = { showUrlQrCodeDialog.value = true },
                content = { Text("Show URL QR code") }
            )
        }

        item {
            TextButton(
                onClick = { showQrScanDialog.value = true },
                content = { Text("Scan mdoc QR code") }
            )
        }
    }

}

