package org.multipaz.issuance.simple

import org.multipaz.cbor.DataItem
import org.multipaz.device.DeviceAssertion
import org.multipaz.issuance.CredentialConfiguration
import org.multipaz.issuance.CredentialFormat
import org.multipaz.issuance.CredentialRequest
import org.multipaz.issuance.KeyPossessionChallenge
import org.multipaz.issuance.KeyPossessionProof
import org.multipaz.issuance.RequestCredentialsFlow

class SimpleIssuingAuthorityRequestCredentialsFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    private val documentId: String,
    private val credentialConfiguration: CredentialConfiguration
) : RequestCredentialsFlow {
    lateinit var format: CredentialFormat

    override suspend fun getCredentialConfiguration(format: CredentialFormat): CredentialConfiguration {
        this.format = format
        return credentialConfiguration
    }

    override suspend fun sendCredentials(
        credentialRequests: List<CredentialRequest>,
        keysAssertion: DeviceAssertion?
    ): List<KeyPossessionChallenge> {
        // TODO: should check attestations
        issuingAuthority.addCpoRequests(documentId, format, credentialRequests)
        return emptyList()
    }

    override suspend fun sendPossessionProofs(keyPossessionProofs: List<KeyPossessionProof>) {
        throw UnsupportedOperationException()
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