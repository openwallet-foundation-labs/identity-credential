package org.multipaz.testapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun SettingMultipleChoice(
    modifier: Modifier = Modifier,
    title: String,
    choices: List<String>,
    initialChoice: String,
    onChoiceSelected: (choice: String) -> Unit,
    enabled: Boolean = true
) {
    val choice = remember { mutableStateOf(initialChoice) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            modifier = Modifier.weight(0.9f),
            text = "$title: ${choice.value}",
            fontWeight = FontWeight.Normal,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        val expanded = remember { mutableStateOf(false) }
        Box {
            Button(
                onClick = { expanded.value = !expanded.value },
                enabled = enabled
            ) {
                Text("Change")
            }
            DropdownMenu(
                expanded = expanded.value,
                onDismissRequest = { expanded.value = false }
            ) {
                for (c in choices) {
                    DropdownMenuItem(
                        text = { Text(c) },
                        onClick = {
                            choice.value = c
                            expanded.value = false
                            onChoiceSelected(c)
                        })
                }
            }
        }
    }
}