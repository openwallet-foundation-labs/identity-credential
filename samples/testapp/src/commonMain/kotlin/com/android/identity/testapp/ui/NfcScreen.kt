package com.android.identity.testapp.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.identity.nfc.Nfc
import com.android.identity.nfc.scanNfcTag
import com.android.identity.prompt.PromptDismissedException
import com.android.identity.prompt.PromptModel
import com.android.identity.util.Logger
import com.android.identity.util.toHex
import kotlinx.coroutines.launch

private const val TAG = "NfcScreen"

@Composable
fun NfcScreen(
    promptModel: PromptModel,
    showToast: (message: String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope { promptModel }

    LazyColumn(
        modifier = Modifier.padding(8.dp)
    ) {
        item {
            TextButton(
                onClick = {
                    coroutineScope.launch {
                        try {
                            val ccFile = scanNfcTag(
                                message = "Hold your phone near a NDEF tag.",
                                tagInteractionFunc = { tag, updateMessage ->
                                    tag.selectApplication(Nfc.NDEF_APPLICATION_ID)
                                    tag.selectFile(Nfc.NDEF_CAPABILITY_CONTAINER_FILE_ID)
                                    val ccFile = tag.readBinary(0, 15)
                                    check(ccFile.size == 15) { "CC file is ${ccFile.size} bytes, expected 15" }
                                    updateMessage("CC file: ${ccFile.toHex()}")
                                    ccFile
                                }
                            )
                            showToast("NDEF CC file: ${ccFile.toHex()}")
                        } catch (e: PromptDismissedException) {
                            showToast("Dialog dismissed by user")
                        } catch (e: Throwable) {
                            Logger.e(TAG, "Fail", e)
                            e.printStackTrace()
                            showToast("Fail: ${e.message}")
                        }
                    }
                },
                content = { Text("Scan for NDEF tag and read CC file") }
            )
        }
    }
}

