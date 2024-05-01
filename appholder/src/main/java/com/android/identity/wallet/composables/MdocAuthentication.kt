package com.android.identity.wallet.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import com.android.identity.wallet.R
import com.android.identity.wallet.composables.state.MdocAuthOption
import com.android.identity.wallet.composables.state.MdocAuthStateOption

@Composable
fun MdocAuthentication(
    modifier: Modifier = Modifier,
    state: MdocAuthOption,
    onMdocAuthOptionChange: (newValue: MdocAuthStateOption) -> Unit,
) {
    LabeledUserInput(
        modifier = modifier,
        label = stringResource(id = R.string.mdoc_authentication_label),
    ) {
        var expanded by remember { mutableStateOf(false) }
        val alpha = if (state.isEnabled) 1f else .5f
        val clickModifier =
            if (state.isEnabled) {
                Modifier.clickable { expanded = true }
            } else {
                Modifier
            }
        OutlinedContainerHorizontal(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(alpha)
                    .then(clickModifier),
        ) {
            ValueLabel(
                modifier = Modifier.weight(1f),
                label = mdocAuthOptionLabelFor(state.mDocAuthentication),
            )
            DropDownIndicator()
        }
        DropdownMenu(
            modifier = Modifier.fillMaxWidth(0.8f),
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TextDropDownRow(
                label = stringResource(id = R.string.mdoc_auth_ecdsa),
                onSelected = {
                    onMdocAuthOptionChange(MdocAuthStateOption.ECDSA)
                    expanded = false
                },
            )
            TextDropDownRow(
                label = stringResource(id = R.string.mdoc_auth_mac),
                onSelected = {
                    onMdocAuthOptionChange(MdocAuthStateOption.MAC)
                    expanded = false
                },
            )
        }
    }
}
