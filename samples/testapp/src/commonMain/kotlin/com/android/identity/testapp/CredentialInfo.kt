package com.android.identity.testapp

import com.android.identity.credential.Credential
import com.android.identity.securearea.KeyInfo

data class CredentialInfo(
    val credential: Credential,
    val keyInfo: KeyInfo?,
    val keyInvalidated: Boolean
)