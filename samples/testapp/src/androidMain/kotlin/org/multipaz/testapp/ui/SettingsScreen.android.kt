package org.multipaz.testapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.multipaz.testapp.TestAppSettingsModel
import org.multipaz.testapp.TestAppSettingsModel.RoutingOption

@Composable
actual fun NfcRoutingChoice(settingsModel: TestAppSettingsModel, modifier: Modifier) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "NFC Routing:",
            style = MaterialTheme.typography.titleMedium
        )
        val seAccessRouting = settingsModel.nfcRoutingOption.collectAsState().value
        CompactTwoRadioChoice(
            option1Text = "Host",
            option1Value = RoutingOption.HOST,
            option2Text = "SE",
            option2Value = RoutingOption.SE,
            currentSelection = seAccessRouting,
            onOptionSelected = { selected ->
                settingsModel.selectNfcRoutingDestination(selected)
            }
        )
    }
}

/** Common compact composable row item displaying two named Radio Buttons in a single row after the description.
 *
 * @param modifier The modifier to be applied to the composable.
 * @param option1Text The text of the first option.
 * @param option1Value The value of the first option.
 * @param option2Text The text of the second option.
 * @param option2Value The value of the second option.
 * @param currentSelection The currently selected option.
 * @param onOptionSelected A callback to be invoked when a new option is selected.
 * @param enabled Whether the composable is enabled for interaction.
 */
@Composable
private fun <T> CompactTwoRadioChoice(
    modifier: Modifier = Modifier,
    option1Text: String,
    option1Value: T,
    option2Text: String,
    option2Value: T,
    currentSelection: T?,
    onOptionSelected: (selection: T) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Option 1
        Row(
            modifier = Modifier
                .selectable(
                    selected = (currentSelection == option1Value),
                    onClick = { if (enabled) onOptionSelected(option1Value) },
                    role = Role.RadioButton,
                    enabled = enabled
                )
                .padding(end = 16.dp), // Space between the two options.
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = (currentSelection == option1Value),
                onClick = null, // Handled by Row's selectable.
                enabled = enabled
            )
            Text(
                text = option1Text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        // Option 2
        Row(
            modifier = Modifier
                .selectable(
                    selected = (currentSelection == option2Value),
                    onClick = { if (enabled) onOptionSelected(option2Value) },
                    role = Role.RadioButton,
                    enabled = enabled
                )
                .padding(start = 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = (currentSelection == option2Value),
                onClick = null, // Handled by Row's selectable.
                enabled = enabled
            )
            Text(
                text = option2Text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}