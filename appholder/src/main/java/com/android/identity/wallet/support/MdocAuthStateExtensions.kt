package com.android.identity.wallet.support

import com.android.identity.securearea.SecureArea
import com.android.identity.wallet.composables.state.MdocAuthStateOption

@SecureArea.KeyPurpose
fun MdocAuthStateOption.toKeyPurpose(): Int {
    return if (this == MdocAuthStateOption.ECDSA) {
        SecureArea.KEY_PURPOSE_SIGN
    } else {
        SecureArea.KEY_PURPOSE_AGREE_KEY
    }
}