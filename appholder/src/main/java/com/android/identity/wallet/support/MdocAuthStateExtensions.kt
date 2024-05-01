package com.android.identity.wallet.support

import com.android.identity.securearea.KeyPurpose
import com.android.identity.wallet.composables.state.MdocAuthStateOption

fun MdocAuthStateOption.toKeyPurpose(): KeyPurpose =
    if (this == MdocAuthStateOption.ECDSA) {
        KeyPurpose.SIGN
    } else {
        KeyPurpose.AGREE_KEY
    }
