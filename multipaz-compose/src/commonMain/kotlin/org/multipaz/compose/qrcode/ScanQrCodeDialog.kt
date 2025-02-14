package org.multipaz.compose.qrcode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions

/**
 * Shows a dialog for scanning QR codes.
 *
 * If the application doesn't have the necessary permission, the user is prompted to grant it.
 *
 * @param title The title of the dialog.
 * @param text The description to include in the dialog, displayed above the QR scanner window.
 * @param additionalContent Content which is displayed below the QR scanner window.
 * @param dismissButton The text for the dismiss button.
 * @param onCodeScanned called when a QR code is scanned, the parameter is the parsed data. Should
 *   return `true` to stop scanning, `false` to continue scanning.
 * @param onDismiss called when the dismiss button is pressed.
 * @param modifier A [Modifier] or `null`.
 */
@Composable
fun ScanQrCodeDialog(
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    additionalContent: (@Composable () -> Unit)? = null,
    dismissButton: String,
    onCodeScanned: (data: String) -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier? = null
) {
    AlertDialog(
        modifier = modifier ?: Modifier,
        title = title,
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                text?.invoke()

                ScannerWithPermissions(
                    modifier = Modifier.height(300.dp),
                    onScanned = { data ->
                        onCodeScanned(data)
                    },
                    types = listOf(CodeType.QR)
                )

                additionalContent?.invoke()
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(dismissButton)
            }
        }
    )
}
