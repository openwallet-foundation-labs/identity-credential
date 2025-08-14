package org.multipaz.models.presentment

import kotlin.time.Clock
import org.multipaz.credential.Credential
import org.multipaz.credential.SecureAreaBoundCredential
import org.multipaz.crypto.EcCurve
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.request.JsonRequest
import org.multipaz.request.JsonRequestedClaim
import org.multipaz.request.MdocRequest
import org.multipaz.request.MdocRequestedClaim
import org.multipaz.request.Request
import org.multipaz.request.RequestedClaim
import org.multipaz.sdjwt.credential.KeylessSdJwtVcCredential
import org.multipaz.trustmanagement.TrustManager


private data class CredentialForPresentment(
    val credential: Credential?,
    val credentialKeyAgreement: Credential?
)

/**
 * An implementation of [PresentmentSource] for when using ISO mdoc and IETF SD-JWT VC credentials.
 *
 * @property documentStore the [DocumentStore] which holds credentials that can be presented.
 * @property documentTypeRepository a [DocumentTypeRepository] which holds metadata for document types.
 * @property readerTrustManager the [TrustManager] used to determine if a reader is trusted.
 * @property zkSystemRepository the [ZkSystemRepository] to use or `null`.
 * @property skipConsentPrompt set to `true` to not show a consent dialog.
 * @property preferSignatureToKeyAgreement whether to use Key Agreement when possible (ISO mdoc only).
 * @property domainMdocSignature the domain to use for [MdocCredential] instances using mdoc ECDSA authentication or `null`.
 * @property domainMdocKeyAgreement the domain to use for [MdocCredential] instances using mdoc MAC authentication or `null`.
 * @property domainKeylessSdJwt the domain to use for [KeylessSdJwtVcCredential] instances or `null`.
 * @property domainKeyBoundSdJwt the domain to use for [domainKeyBoundSdJwt] instances or `null`.
 */
class SimplePresentmentSource(
    override val documentStore: DocumentStore,
    override val documentTypeRepository: DocumentTypeRepository,
    override val readerTrustManager: TrustManager,
    override val zkSystemRepository: ZkSystemRepository? = null,
    override val skipConsentPrompt: Boolean = false,
    val preferSignatureToKeyAgreement: Boolean = true,
    val domainMdocSignature: String? = null,
    val domainMdocKeyAgreement: String? = null,
    val domainKeylessSdJwt: String? = null,
    val domainKeyBoundSdJwt: String? = null,
): PresentmentSource(
    documentStore = documentStore,
    documentTypeRepository = documentTypeRepository,
    readerTrustManager = readerTrustManager,
    zkSystemRepository = zkSystemRepository,
    skipConsentPrompt = skipConsentPrompt
) {
    override suspend fun selectCredential(
        document: Document?,
        request: Request,
        keyAgreementPossible: List<EcCurve>,
    ): Credential? {
        val credsForPresentment = when (request) {
            is MdocRequest -> mdocGetCredentialsForPresentment(request, document)
            is JsonRequest -> sdjwtGetCredentialsForPresentment(request, document)
        }
        if (!preferSignatureToKeyAgreement && credsForPresentment.credentialKeyAgreement != null) {
            credsForPresentment.credentialKeyAgreement as SecureAreaBoundCredential
            val keyInfo = credsForPresentment.credentialKeyAgreement.secureArea.getKeyInfo(
                credsForPresentment.credentialKeyAgreement.alias
            )
            if (keyAgreementPossible.contains(keyInfo.algorithm.curve!!)) {
                return credsForPresentment.credentialKeyAgreement
            }
        }
        return credsForPresentment.credential
    }

    override suspend fun selectCredential(
        document: Document,
        requestedClaims: List<RequestedClaim>,
        keyAgreementPossible: List<EcCurve>,
    ): Credential? {
        check(requestedClaims.size > 0)
        val now = Clock.System.now()
        val credsForPresentment = when (requestedClaims[0]) {
            is MdocRequestedClaim -> {
                CredentialForPresentment(
                    credential = domainMdocSignature?.let {
                        document.findCredential(domain = it, now = now)
                    },
                    credentialKeyAgreement = domainMdocKeyAgreement?.let {
                        document.findCredential(domain = it, now = now)
                    }
                )
            }
            is JsonRequestedClaim -> {
                if (document.getCertifiedCredentials().firstOrNull() is KeylessSdJwtVcCredential) {
                    CredentialForPresentment(
                        credential = domainKeylessSdJwt?.let {
                            document.findCredential(domain = it, now = now)
                        },
                        credentialKeyAgreement = null
                    )
                } else {
                    CredentialForPresentment(
                        credential = domainKeyBoundSdJwt?.let {
                            document.findCredential(domain = it, now = now)
                        },
                        credentialKeyAgreement = null
                    )
                }
            }
        }
        if (!preferSignatureToKeyAgreement && credsForPresentment.credentialKeyAgreement != null) {
            credsForPresentment.credentialKeyAgreement as SecureAreaBoundCredential
            val keyInfo = credsForPresentment.credentialKeyAgreement.secureArea.getKeyInfo(
                credsForPresentment.credentialKeyAgreement.alias
            )
            if (keyAgreementPossible.contains(keyInfo.algorithm.curve!!)) {
                return credsForPresentment.credentialKeyAgreement
            }
        }
        return credsForPresentment.credential
    }

    private suspend fun PresentmentSource.mdocGetCredentialsForPresentment(
        request: MdocRequest,
        document: Document?,
    ): CredentialForPresentment {
        val now = Clock.System.now()
        val documentToQuery = document ?: getDocumentsMatchingRequest(request).first()
        return CredentialForPresentment(
            credential = domainMdocSignature?.let {
                documentToQuery.findCredential(domain = it, now = now)
            },
            credentialKeyAgreement = domainMdocKeyAgreement?.let {
                documentToQuery.findCredential(domain = it, now = now)
            }
        )
    }

    private suspend fun PresentmentSource.sdjwtGetCredentialsForPresentment(
        request: JsonRequest,
        document: Document?,
    ): CredentialForPresentment {
        val now = Clock.System.now()
        val documentToQuery = document ?: getDocumentsMatchingRequest(request).first()
        if (documentToQuery.getCertifiedCredentials().firstOrNull() is KeylessSdJwtVcCredential) {
            return CredentialForPresentment(
                credential = domainKeylessSdJwt?.let {
                    documentToQuery.findCredential(domain = it, now = now)
                },
                credentialKeyAgreement = null
            )
        }
        return CredentialForPresentment(
            credential = domainKeyBoundSdJwt?.let {
                documentToQuery.findCredential(domain = it, now = now)
            },
            credentialKeyAgreement = null
        )
    }
}
