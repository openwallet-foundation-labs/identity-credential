package org.multipaz.provisioning.openid4vci

import org.multipaz.provisioning.CredentialConfiguration
import org.multipaz.provisioning.CredentialFormat

interface AbstractRequestCredentials {
    val documentId: String
    val credentialConfiguration: CredentialConfiguration
    var format: CredentialFormat?
}
