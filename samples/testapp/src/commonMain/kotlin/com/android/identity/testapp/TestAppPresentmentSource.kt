package org.multipaz.testapp

import org.multipaz.models.ui.presentment.PresentmentSource
import org.multipaz.credential.Credential
import org.multipaz.document.Document
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.request.MdocRequest
import org.multipaz.request.Request
import org.multipaz.request.VcRequest
import org.multipaz.sdjwt.credential.KeylessSdJwtVcCredential
import org.multipaz.sdjwt.credential.SdJwtVcCredential
import org.multipaz.trustmanagement.TrustPoint
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.multipaz.appsupport.ui.presentment.CredentialForPresentment

class TestAppPresentmentSource(
    val app: App
): PresentmentSource {

    override val documentTypeRepository: DocumentTypeRepository
        get() = app.documentTypeRepository

    override fun findTrustPoint(request: Request): TrustPoint? {
        return request.requester.certChain?.let {
            val trustResult = app.readerTrustManager.verify(it.certificates)
            if (trustResult.isTrusted) {
                trustResult.trustPoints[0]
            } else {
                null
            }
        }
    }

    override suspend fun getDocumentsMatchingRequest(
        request: Request,
    ): List<Document> {
        return when (request) {
            is MdocRequest -> mdocFindDocumentsForRequest(app, request)
            is VcRequest -> sdjwtFindDocumentsForRequest(app, request)
        }
    }

    override suspend fun getCredentialForPresentment(
        request: Request,
        document: Document
    ): CredentialForPresentment {
        return when (request) {
            is MdocRequest -> mdocGetCredentialsForPresentment(app, request, document)
            is VcRequest -> sdjwtGetCredentialsForPresentment(app, request, document)
        }
    }

    override fun shouldShowConsentPrompt(
        credential: Credential,
        request: Request,
    ): Boolean {
        return app.settingsModel.presentmentShowConsentPrompt.value
    }

    override fun shouldPreferSignatureToKeyAgreement(
        document: Document,
        request: Request
    ): Boolean {
        return app.settingsModel.presentmentPreferSignatureToKeyAgreement.value
    }
}

private suspend fun mdocFindDocumentsForRequest(
    app: App,
    request: MdocRequest,
): List<Document> {
    val now = Clock.System.now()
    val result = mutableListOf<Document>()

    for (documentName in app.documentStore.listDocuments()) {
        val document = app.documentStore.lookupDocument(documentName) ?: continue
        if (mdocDocumentMatchesRequest(request, document)) {
            result.add(document)
        }
    }
    return result
}

private suspend fun mdocDocumentMatchesRequest(
    request: MdocRequest,
    document: Document,
): Boolean {
    for (credential in document.getCertifiedCredentials()) {
        if (credential is MdocCredential && credential.docType == request.docType) {
            return true
        }
    }
    return false
}

private suspend fun mdocGetCredentialsForPresentment(
    app: App,
    request: MdocRequest,
    document: Document,
): CredentialForPresentment {
    val now = Clock.System.now()

    val (signingDomain, keyAgreementDomain) = if (app.settingsModel.presentmentRequireAuthentication.value) {
        Pair(TestAppUtils.CREDENTIAL_DOMAIN_MDOC_USER_AUTH, TestAppUtils.CREDENTIAL_DOMAIN_MDOC_MAC_USER_AUTH)
    } else {
        Pair(TestAppUtils.CREDENTIAL_DOMAIN_MDOC_NO_USER_AUTH, TestAppUtils.CREDENTIAL_DOMAIN_MDOC_MAC_NO_USER_AUTH)
    }
    return CredentialForPresentment(
        credential = document.findCredential(
            domain = signingDomain,
            now = now
        ),
        credentialKeyAgreement = document.findCredential(
            domain = keyAgreementDomain,
            now = now
        )
    )
}

private suspend fun sdjwtFindDocumentsForRequest(
    app: App,
    request: VcRequest,
): List<Document> {
    val now = Clock.System.now()
    val result = mutableListOf<Document>()

    for (documentName in app.documentStore.listDocuments()) {
        val document = app.documentStore.lookupDocument(documentName) ?: continue
        if (sdjwtDocumentMatchesRequest(request, document)) {
            result.add(document)
        }
    }
    return result
}

private suspend fun sdjwtDocumentMatchesRequest(
    request: VcRequest,
    document: Document,
): Boolean {
    for (credential in document.getCertifiedCredentials()) {
        if (credential is SdJwtVcCredential && credential.vct == request.vct) {
            return true
        }
    }
    return false
}

private suspend fun sdjwtFindCredentialInDocument(
    app: App,
    request: VcRequest,
    now: Instant,
    document: Document,
): Credential? {
    val domain = if (document.getCertifiedCredentials().firstOrNull() is KeylessSdJwtVcCredential) {
        TestAppUtils.CREDENTIAL_DOMAIN_SDJWT_KEYLESS
    } else {
        if (app.settingsModel.presentmentRequireAuthentication.value) {
            TestAppUtils.CREDENTIAL_DOMAIN_SDJWT_USER_AUTH
        } else {
            TestAppUtils.CREDENTIAL_DOMAIN_SDJWT_NO_USER_AUTH
        }
    }
    for (credential in document.getCertifiedCredentials()) {
        if (credential is SdJwtVcCredential && credential.vct == request.vct) {
            val credentialCandidate = document.findCredential(
                domain = domain,
                now = now
            )
            if (credentialCandidate != null) {
                return credentialCandidate
            }
        }
    }
    return null
}

private suspend fun sdjwtGetCredentialsForPresentment(
    app: App,
    request: VcRequest,
    document: Document,
): CredentialForPresentment {
    val now = Clock.System.now()

    if (document.getCertifiedCredentials().firstOrNull() is KeylessSdJwtVcCredential) {
        return CredentialForPresentment(
            credential = document.findCredential(
                domain = TestAppUtils.CREDENTIAL_DOMAIN_SDJWT_KEYLESS,
                now = now
            ),
            credentialKeyAgreement = null
        )
    }
    val domain = if (app.settingsModel.presentmentRequireAuthentication.value) {
        TestAppUtils.CREDENTIAL_DOMAIN_SDJWT_USER_AUTH
    } else {
        TestAppUtils.CREDENTIAL_DOMAIN_SDJWT_NO_USER_AUTH
    }
    return CredentialForPresentment(
        credential = document.findCredential(
            domain = domain,
            now = now
        ),
        credentialKeyAgreement = null
    )
}
