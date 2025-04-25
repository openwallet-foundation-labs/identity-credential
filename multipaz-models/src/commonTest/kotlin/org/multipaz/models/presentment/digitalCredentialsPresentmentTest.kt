package org.multipaz.models.presentment

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.multipaz.credential.Credential
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.request.MdocRequest
import org.multipaz.request.Request
import org.multipaz.request.VcRequest
import org.multipaz.sdjwt.credential.SdJwtVcCredential
import org.multipaz.trustmanagement.TrustPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DigitalCredentialsPresentmentTest {

    val documentStoreTestHarness = DocumentStoreTestHarness()

    class TestPresentmentMechanism(
        protocol: String,
        request: String,
        document: Document,
        var response: String? = null,
        var closed: Boolean = false
    ): DigitalCredentialsPresentmentMechanism(
        appId = "org.multipaz.testApp",
        webOrigin = "https://www.multipaz.org",
        protocol = protocol,
        request = request,
        document = document,
    ) {
        override fun sendResponse(response: String) {
            this.response = response
        }

        override fun close() {
            closed = true
        }
    }

    class TestPresentmentSource(
        val documentStore: DocumentStore,
        override val documentTypeRepository: DocumentTypeRepository
    ): PresentmentSource {
        override fun findTrustPoint(request: Request): TrustPoint? {
            return null
        }

        override suspend fun getDocumentsMatchingRequest(request: Request): List<Document> {
            return when (request) {
                is MdocRequest -> mdocFindDocumentsForRequest(request)
                is VcRequest -> sdjwtFindDocumentsForRequest(request)
            }
        }

        private suspend fun mdocFindDocumentsForRequest(
            request: MdocRequest,
        ): List<Document> {
            val result = mutableListOf<Document>()
            for (documentName in documentStore.listDocuments()) {
                val document = documentStore.lookupDocument(documentName) ?: continue
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

        private suspend fun sdjwtFindDocumentsForRequest(
            request: VcRequest,
        ): List<Document> {
            val result = mutableListOf<Document>()

            for (documentName in documentStore.listDocuments()) {
                val document = documentStore.lookupDocument(documentName) ?: continue
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

        override suspend fun getCredentialForPresentment(
            request: Request,
            document: Document
        ): CredentialForPresentment {
            return when (request) {
                is MdocRequest -> mdocGetCredentialsForPresentment(request, document)
                is VcRequest -> sdjwtGetCredentialsForPresentment(request, document)
            }
        }

        private suspend fun mdocGetCredentialsForPresentment(
            request: MdocRequest,
            document: Document,
        ): CredentialForPresentment {
            val now = Clock.System.now()
            return CredentialForPresentment(
                credential = document.findCredential(domain = "mdoc", now = now),
                credentialKeyAgreement = null
            )
        }

        private suspend fun sdjwtGetCredentialsForPresentment(
            request: VcRequest,
            document: Document,
        ): CredentialForPresentment {
            val now = Clock.System.now()
            return CredentialForPresentment(
                credential = document.findCredential(domain = "sdjwt", now = now),
                credentialKeyAgreement = null
            )
        }

        override fun shouldShowConsentPrompt(
            credential: Credential,
            request: Request
        ): Boolean = true

        override fun shouldPreferSignatureToKeyAgreement(
            document: Document,
            request: Request
        ): Boolean = true
    }


    @Test
    fun openid4vpHappyPath() = runTest {
        documentStoreTestHarness.initialize()

        /* Will be used when we finish the test
        val presentmentModel = PresentmentModel()

        val presentmentSource = TestPresentmentSource(
            documentStore = documentStoreTestHarness.documentStore,
            documentTypeRepository = documentStoreTestHarness.documentTypeRepository
        )
        val presentmentMechanism = TestPresentmentMechanism(
            protocol = "openid4vp",
            request = """
request: {
 "response_type": "vp_token",
 "expected_origins": [
  "https://ws.davidz25.net"
 ],
 "dcql_query": {
  "credentials": [
   {
    "id": "cred1",
    "format": "mso_mdoc",
    "meta": {
     "doctype_value": "eu.europa.ec.eudi.pid.1"
    },
    "claims": [
     {
      "path": [
       "eu.europa.ec.eudi.pid.1",
       "age_over_18"
      ],
      "intent_to_retain": false
     }
    ]
   }
  ]
 },
 "nonce": "rffsTRJkINaNxU-snxMdhg",
 "client_metadata": {
  "vp_formats": {
   "mso_mdoc": {
    "alg": [
     "ES256"
    ]
   },
   "dc+sd-jwt": {
    "sd-jwt_alg_values": [
     "ES256"
    ],
    "kb-jwt_alg_values": [
     "ES256"
    ]
   }
  },
  "authorization_encrypted_response_alg": "ECDH-ES",
  "authorization_encrypted_response_enc": "A128GCM",
  "jwks": {
   "keys": [
    {
     "kty": "EC",
     "use": "enc",
     "crv": "P-256",
     "alg": "ECDH-ES",
     "x": "V2Px7RVK8Njo7n54BfjOkJc2oewNKES__8SmNi0ETBo",
     "y": "hlXXyDYetDCSaYfnQhRimDfeYT4rHgwH3vh1KsxU3IA"
    }
   ]
  }
 },
 "client_id": "x509_san_dns:ws.davidz25.net",
 "response_mode": "dc_api.jwt"
}
            """.trimIndent(),
            document = documentStoreTestHarness.docMdl,
        )

        val dismissable = MutableStateFlow<Boolean>(true)
        digitalCredentialsPresentment(
            documentTypeRepository = documentStoreTestHarness.documentTypeRepository,
            source = presentmentSource,
            model = presentmentModel,
            mechanism = presentmentMechanism,
            dismissable = dismissable,
            showConsentPrompt = { document, request, trustPoint ->
                true
            }
        )
        // TODO: Check that the response is as expected - this involves using introducing
        //   a utility function to generate the OpenID4VP request as well which means
        //   factoring out code from VerifierServlet.kt
        //assertEquals(presentmentMechanism.response, "")
        assertTrue(presentmentMechanism.closed)

         */
    }

}