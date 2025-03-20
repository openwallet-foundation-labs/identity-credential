package org.multipaz.wallet.provisioning.simple

import org.multipaz.cbor.DataItem
import org.multipaz.provisioning.RegistrationFlow
import org.multipaz.provisioning.RegistrationConfiguration
import org.multipaz.provisioning.RegistrationResponse

class SimpleIssuingAuthorityRegistrationFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    private val documentId: String
) : RegistrationFlow {

    override suspend fun getDocumentRegistrationConfiguration(): RegistrationConfiguration {
        return RegistrationConfiguration(documentId)
    }

    override suspend fun sendDocumentRegistrationResponse(response: RegistrationResponse) {
        issuingAuthority.addDocumentId(documentId, response)
    }

    override suspend fun complete() {
        // noop
    }

    // Unused in client implementations
    override val flowPath: String
        get() {
            throw UnsupportedOperationException("Unexpected call")
        }

    // Unused in client implementations
    override val flowState: DataItem
        get() {
            throw UnsupportedOperationException("Unexpected call")
        }
}