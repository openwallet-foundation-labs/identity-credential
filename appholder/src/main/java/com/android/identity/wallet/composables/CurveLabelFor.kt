package com.android.identity.wallet.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.identity.crypto.EcCurve
import com.android.identity.wallet.R

@Composable
fun curveLabelFor(curveOption: EcCurve): String {
    return when (curveOption) {
        EcCurve.P256 -> stringResource(id = R.string.curve_p_256)
        EcCurve.P384 -> stringResource(id = R.string.curve_p_384)
        EcCurve.P521 -> stringResource(id = R.string.curve_p_521)
        EcCurve.BRAINPOOLP256R1 -> stringResource(id = R.string.curve_brain_pool_p_256R1)
        EcCurve.BRAINPOOLP320R1 -> stringResource(id = R.string.curve_brain_pool_p_320R1)
        EcCurve.BRAINPOOLP384R1 -> stringResource(id = R.string.curve_brain_pool_p_384R1)
        EcCurve.BRAINPOOLP512R1 -> stringResource(id = R.string.curve_brain_pool_p_512R1)
        EcCurve.ED25519 -> stringResource(id = R.string.curve_ed25519)
        EcCurve.X25519 -> stringResource(id = R.string.curve_x25519)
        EcCurve.ED448 -> stringResource(id = R.string.curve_ed448)
        EcCurve.X448 -> stringResource(id = R.string.curve_X448)
        else -> ""
    }
}
