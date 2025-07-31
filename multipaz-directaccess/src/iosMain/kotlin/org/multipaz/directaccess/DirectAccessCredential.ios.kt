package org.multipaz.directaccess

import org.multipaz.claim.Claim
import org.multipaz.credential.Credential
import org.multipaz.document.Document
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.securearea.KeyAttestation

actual class DirectAccessCredential : Credential {

    actual constructor(document: Document) : super(document) {
        TODO("NOOP")
    }

    actual override val credentialType: String
        get() = TODO("NOOP")

    actual override fun getClaims(documentTypeRepository: DocumentTypeRepository?): List<Claim> {
        TODO("NOOP")
    }

    actual companion object {
        actual const val CREDENTIAL_TYPE: String = ""
        actual suspend fun create(
            document: Document,
            asReplacementForIdentifier: String?,
            domain: String,
            documentType: String
        ): DirectAccessCredential {
            TODO("NOOP")
        }

        actual suspend fun delete(documentType: String) {
            TODO("NOOP")
        }
    }

    actual val attestation: KeyAttestation
        get() = TODO("NOOP")
}