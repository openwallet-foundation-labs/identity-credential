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
import com.android.identity.wallet.support.softwarekeystore.SoftwareAuthKeyCurveOption
import com.android.identity.wallet.support.softwarekeystore.SoftwareAuthKeyCurveState
import com.android.identity.wallet.composables.state.MdocAuthOption
import com.android.identity.wallet.composables.state.MdocAuthStateOption

@Composable
fun AuthenticationKeyCurveSoftware(
    modifier: Modifier = Modifier,
    state: SoftwareAuthKeyCurveState,
    mDocAuthState: MdocAuthOption,
    onSoftwareAuthKeyCurveChanged: (newValue: SoftwareAuthKeyCurveOption) -> Unit
) {
    LabeledUserInput(
        modifier = modifier,
        label = stringResource(id = R.string.authentication_key_curve_label)
    ) {
        var keyCurveDropDownExpanded by remember { mutableStateOf(false) }
        val clickModifier = if (state.isEnabled) {
            Modifier.clickable { keyCurveDropDownExpanded = true }
        } else {
            Modifier
        }
        val alpha = if (state.isEnabled) 1f else .5f
        OutlinedContainerHorizontal(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(alpha)
                .then(clickModifier)
        ) {
            ValueLabel(
                modifier = Modifier.weight(1f),
                label = curveLabelFor(state.authCurve.toEcCurve())
            )
            DropDownIndicator()
        }
        val entries =
            SoftwareAuthKeyCurveOption.values().toMutableList()
        if (mDocAuthState.mDocAuthentication == MdocAuthStateOption.ECDSA) {
            entries.remove(SoftwareAuthKeyCurveOption.X448)
            entries.remove(SoftwareAuthKeyCurveOption.X25519)
        } else {
            entries.remove(SoftwareAuthKeyCurveOption.Ed448)
            entries.remove(SoftwareAuthKeyCurveOption.Ed25519)
        }
        DropdownMenu(
            expanded = keyCurveDropDownExpanded,
            onDismissRequest = { keyCurveDropDownExpanded = false }
        ) {
            for (entry in entries) {
                TextDropDownRow(
                    label = curveLabelFor(curveOption = entry.toEcCurve()),
                    onSelected = {
                        onSoftwareAuthKeyCurveChanged(entry)
                        keyCurveDropDownExpanded = false
                    }
                )
            }
        }
    }
}