package com.android.identity.nfc

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.identity.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

internal class NfcTagReaderModalBottomSheet(
    private val dialogMessage: MutableState<String>,
    private val dialogIcon: MutableState<Int>,
    private val onDismissed: () -> Unit
): BottomSheetDialogFragment() {

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismissed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {

                val sheetState = rememberModalBottomSheetState()

                ModalBottomSheet(
                    onDismissRequest = { dismiss() },
                    sheetState = sheetState,
                    dragHandle = {},
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.nfc_tag_reader_modal_bottom_sheet_ready_to_scan),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineLarge,
                            )
                        Text(
                            text = dialogMessage.value,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Image(
                            painter = painterResource(dialogIcon.value),
                            modifier = Modifier.size(128.dp),
                            contentDescription = null
                        )
                        FilledTonalButton(
                            onClick = {
                                dismiss()
                            }
                        ) {
                            Text(text = "Cancel")
                        }
                    }
                }
            }
        }
    }
}
