package com.android.identity.wallet.support

import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureArea
import com.android.identity.wallet.composables.state.MdocAuthStateOption

fun MdocAuthStateOption.toKeyPurpose(): KeyPurpose {
    return if (this == MdocAuthStateOption.ECDSA) {
        KeyPurpose.SIGN
    } else {
        KeyPurpose.AGREE_KEY
    }
}