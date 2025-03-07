package org.multipaz.testapp

import org.multipaz.models.ui.presentment.PresentmentSource
import org.multipaz.credential.Credential
import org.multipaz.document.Document
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.UtopiaMovieTicket
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.request.MdocRequest
import org.multipaz.request.Request
import org.multipaz.request.VcRequest
import org.multipaz.sdjwt.credential.KeylessSdJwtVcCredential
import org.multipaz.sdjwt.credential.SdJwtVcCredential
import org.multipaz.trustmanagement.TrustPoint
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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

    override suspend fun selectCredentialForPresentment(
        request: Request,
        preSelectedDocument: Document?
    ): List<Credential> {
        when (request) {
            is MdocRequest -> return mdocFindCredentialsForRequest(app, request, preSelectedDocument)
            is VcRequest -> return sdjwtFindCredentialsForRequest(app, request, preSelectedDocument)
        }

    }

    override fun shouldShowConsentPrompt(
        credential: Credential,
        request: Request,
    ): Boolean {
        return app.settingsModel.presentmentShowConsentPrompt.value
    }

    override fun shouldPreferSignatureToKeyAgreement(
        credential: Credential,
        request: Request
    ): Boolean {
        return app.settingsModel.presentmentPreferSignatureToKeyAgreement.value
    }
}

private suspend fun mdocFindCredentialsForRequest(
    app: App,
    request: MdocRequest,
    preSelectedDocument: Document?
): List<Credential> {
    val now = Clock.System.now()
    val result = mutableListOf<Credential>()

    if (preSelectedDocument != null) {
        val credential = mdocFindCredentialInDocument(app, request, now, preSelectedDocument)
        if (credential != null) {
            result.add(credential)
            return result
        }
    }

    for (documentName in app.documentStore.listDocuments()) {
        val document = app.documentStore.lookupDocument(documentName) ?: continue
        val credential = mdocFindCredentialInDocument(app, request, now, document)
        if (credential != null) {
            result.add(credential)
        }
    }
    return result
}

private suspend fun mdocFindCredentialInDocument(
    app: App,
    request: MdocRequest,
    now: Instant,
    document: Document,
): Credential? {
    val domain = if (app.settingsModel.presentmentRequireAuthentication.value) {
        TestAppUtils.MDOC_CREDENTIAL_DOMAIN_AUTH
    } else {
        TestAppUtils.MDOC_CREDENTIAL_DOMAIN_NO_AUTH
    }
    for (credential in document.getCertifiedCredentials()) {
        if (credential is MdocCredential && credential.docType == request.docType) {
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

private suspend fun sdjwtFindCredentialsForRequest(
    app: App,
    request: VcRequest,
    preSelectedDocument: Document?
): List<Credential> {
    val now = Clock.System.now()
    val result = mutableListOf<Credential>()

    if (preSelectedDocument != null) {
        val credential = sdjwtFindCredentialInDocument(app, request, now, preSelectedDocument)
        if (credential != null) {
            result.add(credential)
            return result
        }
    }

    for (documentName in app.documentStore.listDocuments()) {
        val document = app.documentStore.lookupDocument(documentName) ?: continue
        val credential = sdjwtFindCredentialInDocument(app, request, now, document)
        if (credential != null) {
            result.add(credential)
        }
    }
    return result
}

private suspend fun sdjwtFindCredentialInDocument(
    app: App,
    request: VcRequest,
    now: Instant,
    document: Document,
): Credential? {
    val domain = if (document.getCertifiedCredentials().firstOrNull() is KeylessSdJwtVcCredential) {
        TestAppUtils.SDJWT_CREDENTIAL_DOMAIN_KEYLESS
    } else {
        if (app.settingsModel.presentmentRequireAuthentication.value) {
            TestAppUtils.SDJWT_CREDENTIAL_DOMAIN_AUTH
        } else {
            TestAppUtils.SDJWT_CREDENTIAL_DOMAIN_NO_AUTH
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
