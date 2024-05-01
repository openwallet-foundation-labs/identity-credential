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
import com.android.identity.wallet.support.androidkeystore.AndroidAuthKeyCurveOption
import com.android.identity.wallet.support.androidkeystore.AndroidAuthKeyCurveState

@Composable
fun AuthenticationKeyCurveAndroid(
    modifier: Modifier = Modifier,
    state: AndroidAuthKeyCurveState,
    mDocAuthState: MdocAuthOption,
    onAndroidAuthKeyCurveChanged: (newValue: AndroidAuthKeyCurveOption) -> Unit,
) {
    LabeledUserInput(
        modifier = modifier,
        label = stringResource(id = R.string.authentication_key_curve_label),
    ) {
        var keyCurveDropDownExpanded by remember { mutableStateOf(false) }
        val clickModifier =
            if (state.isEnabled) {
                Modifier.clickable { keyCurveDropDownExpanded = true }
            } else {
                Modifier
            }
        val alpha = if (state.isEnabled) 1f else .5f
        OutlinedContainerHorizontal(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(alpha)
                    .then(clickModifier),
        ) {
            ValueLabel(
                modifier = Modifier.weight(1f),
                label = curveLabelFor(state.authCurve.toEcCurve()),
            )
            DropDownIndicator()
        }
        DropdownMenu(
            expanded = keyCurveDropDownExpanded,
            onDismissRequest = { keyCurveDropDownExpanded = false },
        ) {
            val ecCurveOption =
                if (mDocAuthState.mDocAuthentication == MdocAuthStateOption.ECDSA) {
                    AndroidAuthKeyCurveOption.Ed25519
                } else {
                    AndroidAuthKeyCurveOption.X25519
                }
            TextDropDownRow(
                label = curveLabelFor(curveOption = AndroidAuthKeyCurveOption.P_256.toEcCurve()),
                onSelected = {
                    onAndroidAuthKeyCurveChanged(AndroidAuthKeyCurveOption.P_256)
                    keyCurveDropDownExpanded = false
                },
            )
            TextDropDownRow(
                label = curveLabelFor(curveOption = ecCurveOption.toEcCurve()),
                onSelected = {
                    onAndroidAuthKeyCurveChanged(ecCurveOption)
                    keyCurveDropDownExpanded = false
                },
            )
        }
    }
}
