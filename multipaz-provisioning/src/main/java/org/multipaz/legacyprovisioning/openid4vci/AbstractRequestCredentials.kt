package org.multipaz.legacyprovisioning.openid4vci

import org.multipaz.legacyprovisioning.CredentialConfiguration
import org.multipaz.legacyprovisioning.CredentialFormat

interface AbstractRequestCredentials {
    val documentId: String
    val credentialConfiguration: CredentialConfiguration
    var format: CredentialFormat?
}
