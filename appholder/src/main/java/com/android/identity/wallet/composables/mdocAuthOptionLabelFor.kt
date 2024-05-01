package com.android.identity.wallet.composables

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.android.identity.wallet.R
import com.android.identity.wallet.composables.state.MdocAuthStateOption

@Composable
fun mdocAuthOptionLabelFor(state: MdocAuthStateOption): String {
    return when (state) {
        MdocAuthStateOption.ECDSA ->
            stringResource(id = R.string.mdoc_auth_ecdsa)

        MdocAuthStateOption.MAC ->
            stringResource(id = R.string.mdoc_auth_mac)
    }
}
