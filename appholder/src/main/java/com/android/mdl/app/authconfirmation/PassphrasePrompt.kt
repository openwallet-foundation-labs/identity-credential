package com.android.mdl.app.authconfirmation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.app.R
import com.android.mdl.app.theme.HolderAppTheme

class PassphrasePrompt : DialogFragment() {

    private val viewModel by activityViewModels<PassphrasePromptViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                HolderAppTheme {
                    PassphrasePromptUI(
                        onDone = { passphrase ->
                            viewModel.authorize(userPassphrase = passphrase)
                            findNavController().navigateUp()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PassphrasePromptUI(
    onDone: (passphrase: String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(id = R.string.passphrase_prompt_title),
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = stringResource(id = R.string.passphrase_prompt_message),
                style = MaterialTheme.typography.titleSmall
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = value,
                onValueChange = { value = it },
                textStyle = MaterialTheme.typography.bodyMedium
            )
            TextButton(
                modifier = Modifier
                    .align(Alignment.End),
                onClick = { onDone(value) }) {
                Text(text = stringResource(id = R.string.bt_ok))
            }
        }
    }
}

@Composable
@Preview
private fun PreviewPassphrasePrompt() {
    HolderAppTheme {
        PassphrasePromptUI(onDone = {})
    }
}