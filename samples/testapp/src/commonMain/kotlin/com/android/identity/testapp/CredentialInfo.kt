package org.multipaz.testapp

import org.multipaz.credential.Credential
import org.multipaz.securearea.KeyInfo

data class CredentialInfo(
    val credential: Credential,
    val keyInfo: KeyInfo?,
    val keyInvalidated: Boolean
)