package com.android.identity.wallet.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.identity.securearea.SecureArea
import com.android.identity.wallet.R

@Composable
fun curveLabelFor(
    @SecureArea.EcCurve curveOption: Int
): String {
    return when (curveOption) {
        SecureArea.EC_CURVE_P256 -> stringResource(id = R.string.curve_p_256)
        SecureArea.EC_CURVE_P384 -> stringResource(id = R.string.curve_p_384)
        SecureArea.EC_CURVE_P521 -> stringResource(id = R.string.curve_p_521)
        SecureArea.EC_CURVE_BRAINPOOLP256R1 -> stringResource(id = R.string.curve_brain_pool_p_256R1)
        SecureArea.EC_CURVE_BRAINPOOLP320R1 -> stringResource(id = R.string.curve_brain_pool_p_320R1)
        SecureArea.EC_CURVE_BRAINPOOLP384R1 -> stringResource(id = R.string.curve_brain_pool_p_384R1)
        SecureArea.EC_CURVE_BRAINPOOLP512R1 -> stringResource(id = R.string.curve_brain_pool_p_512R1)
        SecureArea.EC_CURVE_ED25519 -> stringResource(id = R.string.curve_ed25519)
        SecureArea.EC_CURVE_X25519 -> stringResource(id = R.string.curve_x25519)
        SecureArea.EC_CURVE_ED448 -> stringResource(id = R.string.curve_ed448)
        SecureArea.EC_CURVE_X448 -> stringResource(id = R.string.curve_X448)
        else -> ""
    }
}