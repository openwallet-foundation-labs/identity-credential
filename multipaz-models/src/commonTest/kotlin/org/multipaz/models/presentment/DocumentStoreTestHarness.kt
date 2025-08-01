package org.multipaz.models.presentment

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.RawCbor
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.toDataItem
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.CoseNumberLabel
import org.multipaz.credential.SecureAreaBoundCredential
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.document.buildDocumentStore
import org.multipaz.documenttype.DocumentType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.mdoc.issuersigned.buildIssuerNamespaces
import org.multipaz.mdoc.mso.MobileSecurityObjectGenerator
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.sdjwt.SdJwt
import org.multipaz.sdjwt.credential.KeyBoundSdJwtVcCredential
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.software.SoftwareCreateKeySettings
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.Storage
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.util.Logger
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.time.Duration.Companion.days

/**
 * Test harness for DocumentStore and related types.
 *
 * This provides a test harness for [DocumentStore], [Document], and [Credential]
 * which can be used to test functionality sitting on top of these.
 *
 * Creating a [DocumentStoreTestHarness] is a no-op, call [initialize] to actually
 * populate it.
 */
class DocumentStoreTestHarness {
    companion object {
        const val TAG = "TestDocumentStore"
    }

    lateinit var documentTypeRepository: DocumentTypeRepository

    lateinit var storage: Storage
    lateinit var softwareSecureArea: SoftwareSecureArea
    lateinit var secureAreaRepository: SecureAreaRepository

    lateinit var documentStore: DocumentStore
    lateinit var docMdl: Document
    lateinit var docEuPid: Document
    lateinit var docPhotoId: Document

    lateinit var dsKey: EcPrivateKey

    lateinit var readerRootKey: EcPrivateKey
    lateinit var readerRootCert: X509Cert

    private val lock = Mutex()
    private var isInitialized = false

    /**
     * Initializes the [DocumentStoreTestHarness].
     *
     * This creates [documentStore] and with three documents [docMdl], [docPhotoId],
     * and [docEuPid] populated with sample data. Each document will have one [MdocCredential]
     * in the domain `mdoc` and the EU PID will also have a [KeyBoundSdJwtVcCredential]
     * in the domain `sdjwt`.
     *
     * The [DocumentStore] itself is backed by [EphemeralStorage] and each credential is using
     * a single [SoftwareSecureArea].
     *
     * This method can be called multiple times.
     */
    suspend fun initialize() {
        lock.withLock {
            initializeWithLock()
        }
    }

    private suspend fun initializeWithLock() {
        if (isInitialized) {
            return
        }
        documentTypeRepository = DocumentTypeRepository()
        documentTypeRepository.addDocumentType(DrivingLicense.getDocumentType())
        documentTypeRepository.addDocumentType(PhotoID.getDocumentType())
        documentTypeRepository.addDocumentType(EUPersonalID.getDocumentType())

        storage = EphemeralStorage()

        softwareSecureArea = SoftwareSecureArea.create(storage)
        secureAreaRepository = SecureAreaRepository.Builder()
            .add(softwareSecureArea)
            .build()

        documentStore = buildDocumentStore(storage = storage, secureAreaRepository = secureAreaRepository) {}

        val now = Clock.System.now()
        val signedAt = now - 1.days
        val validFrom =  now - 1.days
        val validUntil = now + 365.days
        val iacaValidFrom = validFrom
        val iacaValidUntil = validUntil
        val dsValidFrom = validFrom
        val dsValidUntil = validUntil

        val iacaKeyPub = EcPublicKey.fromPem(
            """
                    -----BEGIN PUBLIC KEY-----
                    MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE+QDye70m2O0llPXMjVjxVZz3m5k6agT+
                    wih+L79b7jyqUl99sbeUnpxaLD+cmB3HK3twkA7fmVJSobBc+9CDhkh3mx6n+YoH
                    5RulaSWThWBfMyRjsfVODkosHLCDnbPV
                    -----END PUBLIC KEY-----
                """.trimIndent().trim(),
            EcCurve.P384
        )
        val iacaKey = EcPrivateKey.fromPem(
            """
                    -----BEGIN PRIVATE KEY-----
                    MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDCcRuzXW3pW2h9W8pu5
                    /CSR6JSnfnZVATq+408WPoNC3LzXqJEQSMzPsI9U1q+wZ2yhZANiAAT5APJ7vSbY
                    7SWU9cyNWPFVnPebmTpqBP7CKH4vv1vuPKpSX32xt5SenFosP5yYHccre3CQDt+Z
                    UlKhsFz70IOGSHebHqf5igflG6VpJZOFYF8zJGOx9U4OSiwcsIOds9U=
                    -----END PRIVATE KEY-----
                """.trimIndent().trim(),
            iacaKeyPub
        )

        val iacaCert = MdocUtil.generateIacaCertificate(
            iacaKey = iacaKey,
            subject = X500Name.fromName("C=US,CN=OWF Multipaz TEST IACA"),
            serial = ASN1Integer.fromRandom(numBits = 128),
            validFrom = iacaValidFrom,
            validUntil = iacaValidUntil,
            issuerAltNameUrl = "https://github.com/openwallet-foundation-labs/identity-credential",
            crlUrl = "https://github.com/openwallet-foundation-labs/identity-credential/crl"
        )

        dsKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val dsCert = MdocUtil.generateDsCertificate(
            iacaCert = iacaCert,
            iacaKey = iacaKey,
            dsKey = dsKey.publicKey,
            subject = X500Name.fromName("C=US,CN=OWF Multipaz TEST DS"),
            serial = ASN1Integer.fromRandom(numBits = 128),
            validFrom = dsValidFrom,
            validUntil = dsValidUntil,
        )

        val readerRootValidFrom = validFrom
        val readerRootValidUntil = validUntil
        readerRootKey = Crypto.createEcPrivateKey(EcCurve.P256)
        readerRootCert = MdocUtil.generateReaderRootCertificate(
            readerRootKey = readerRootKey,
            subject = X500Name.fromName("C=US,CN=OWF Multipaz TEST Reader Root"),
            serial = ASN1Integer.fromRandom(128),
            validFrom = readerRootValidFrom,
            validUntil = readerRootValidUntil,
            crlUrl = "https://verifier.multipaz.org/crl"
        )

        provisionTestDocuments(
            documentStore = documentStore,
            dsKey = dsKey,
            dsCert = dsCert,
            signedAt = signedAt,
            validFrom = validFrom,
            validUntil = validUntil,
        )

        isInitialized = true
    }

    private suspend fun provisionTestDocuments(
        documentStore: DocumentStore,
        dsKey: EcPrivateKey,
        dsCert: X509Cert,
        signedAt: Instant,
        validFrom: Instant,
        validUntil: Instant,
    ) {
        docMdl = provisionDocument(
            documentStore = documentStore,
            displayName = "mDL",
            dsKey = dsKey,
            dsCert = dsCert,
            documentType = DrivingLicense.getDocumentType(),
            signedAt = signedAt,
            validFrom = validFrom,
            validUntil = validUntil,
        )
        docEuPid = provisionDocument(
            documentStore = documentStore,
            displayName = "EU PID",
            dsKey = dsKey,
            dsCert = dsCert,
            documentType = EUPersonalID.getDocumentType(),
            signedAt = signedAt,
            validFrom = validFrom,
            validUntil = validUntil,
        )
        docPhotoId = provisionDocument(
            documentStore = documentStore,
            displayName = "Photo ID",
            dsKey = dsKey,
            dsCert = dsCert,
            documentType = PhotoID.getDocumentType(),
            signedAt = signedAt,
            validFrom = validFrom,
            validUntil = validUntil,
        )
    }

    private suspend fun provisionDocument(
        documentStore: DocumentStore,
        displayName: String,
        dsKey: EcPrivateKey,
        dsCert: X509Cert,
        documentType: DocumentType,
        signedAt: Instant,
        validFrom: Instant,
        validUntil: Instant,
    ): Document {
        val document = documentStore.createDocument(
            displayName = displayName
        )

        if (documentType.mdocDocumentType != null) {
            addMdocCredential(
                document = document,
                documentType = documentType,
                signedAt = signedAt,
                validFrom = validFrom,
                validUntil = validUntil,
                dsKey = dsKey,
                dsCert = dsCert,
            )
        }

        if (documentType.jsonDocumentType != null) {
            addSdJwtVcCredential(
                document = document,
                documentType = documentType,
                signedAt = signedAt,
                validFrom = validFrom,
                validUntil = validUntil,
                dsKey = dsKey,
                dsCert = dsCert,
            )
        }

        return document
    }

    private suspend fun addMdocCredential(
        document: Document,
        documentType: DocumentType,
        signedAt: Instant,
        validFrom: Instant,
        validUntil: Instant,
        dsKey: EcPrivateKey,
        dsCert: X509Cert,
    ) {
        val issuerNamespaces = buildIssuerNamespaces {
            for ((nsName, ns) in documentType.mdocDocumentType?.namespaces!!) {
                addNamespace(nsName) {
                    for ((deName, de) in ns.dataElements) {
                        val sampleValue = de.attribute.sampleValueMdoc
                        if (sampleValue != null) {
                            addDataElement(deName, sampleValue)
                        } else {
                            Logger.w(TAG, "No sample value for data element $deName")
                        }
                    }
                }
            }
        }

        // Create authentication keys...
        val mdocCredential = MdocCredential.create(
            document = document,
            asReplacementForIdentifier = null,
            domain = "mdoc",
            secureArea = softwareSecureArea,
            docType = documentType.mdocDocumentType!!.docType,
            createKeySettings = SoftwareCreateKeySettings.Builder().build()
        )

        // Generate an MSO and issuer-signed data for this authentication key.
        val msoGenerator = MobileSecurityObjectGenerator(
            Algorithm.SHA256,
            documentType.mdocDocumentType!!.docType,
            mdocCredential.getAttestation().publicKey
        )
        msoGenerator.setValidityInfo(signedAt, validFrom, validUntil, null)
        msoGenerator.addValueDigests(issuerNamespaces)

        val mso = msoGenerator.generate()
        val taggedEncodedMso = Cbor.encode(Tagged(24, Bstr(mso)))

        // IssuerAuth is a COSE_Sign1 where payload is MobileSecurityObjectBytes
        //
        // MobileSecurityObjectBytes = #6.24(bstr .cbor MobileSecurityObject)
        //
        val protectedHeaders = mapOf<CoseLabel, DataItem>(
            Pair(
                CoseNumberLabel(Cose.COSE_LABEL_ALG),
                Algorithm.ES256.coseAlgorithmIdentifier!!.toDataItem()
            )
        )
        val unprotectedHeaders = mapOf<CoseLabel, DataItem>(
            Pair(
                CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
                X509CertChain(listOf(dsCert)).toDataItem()
            )
        )
        val encodedIssuerAuth = Cbor.encode(
            Cose.coseSign1Sign(
                dsKey,
                taggedEncodedMso,
                true,
                dsKey.publicKey.curve.defaultSigningAlgorithm,
                protectedHeaders,
                unprotectedHeaders
            ).toDataItem()
        )
        val issuerProvidedAuthenticationData = Cbor.encode(
            buildCborMap {
                put("nameSpaces", issuerNamespaces.toDataItem())
                put("issuerAuth", RawCbor(encodedIssuerAuth))
            }
        )

        // Now that we have issuer-provided authentication data we certify the authentication key.
        mdocCredential.certify(
            issuerProvidedAuthenticationData,
            validFrom,
            validUntil
        )
    }

    // Technically - according to RFC 7800 at least - SD-JWT could do MACing too but it would
    // need to be specced out in e.g. SD-JWT VC profile where to get the public key from the
    // recipient. So for now, we only support signing.
    //
    private suspend fun addSdJwtVcCredential(
        document: Document,
        documentType: DocumentType,
        signedAt: Instant,
        validFrom: Instant,
        validUntil: Instant,
        dsKey: EcPrivateKey,
        dsCert: X509Cert,
    ) {
        if (documentType.jsonDocumentType == null) {
            return
        }

        val identityAttributes = buildJsonObject {
            for ((claimName, attribute) in documentType.jsonDocumentType!!.claims) {
                // Skip sub-claims.
                if (claimName.contains('.')) {
                    continue
                }
                val sampleValue = attribute.sampleValueJson
                if (sampleValue != null) {
                    put(claimName, sampleValue)
                } else {
                    Logger.w(TAG, "No sample value for claim $claimName")
                }
            }
        }

        val credential = KeyBoundSdJwtVcCredential.create(
            document = document,
            asReplacementForIdentifier = null,
            domain = "sdjwt",
            secureArea = softwareSecureArea,
            vct = documentType.jsonDocumentType!!.vct,
            createKeySettings = SoftwareCreateKeySettings.Builder().build()
        )

        val sdJwt = SdJwt.create(
            issuerKey = dsKey,
            issuerAlgorithm = dsKey.curve.defaultSigningAlgorithmFullySpecified,
            issuerCertChain = X509CertChain(listOf(dsCert)),
            kbKey = (credential as? SecureAreaBoundCredential)?.let { it.secureArea.getKeyInfo(it.alias).publicKey },
            claims = identityAttributes,
            nonSdClaims = buildJsonObject {
                put("iss", "https://example-issuer.com")
                put("vct", credential.vct)
                put("iat", signedAt.epochSeconds)
                put("nbf", validFrom.epochSeconds)
                put("exp", validUntil.epochSeconds)
            },
        )
        credential.certify(
            sdJwt.compactSerialization.encodeToByteArray(),
            validFrom,
            validUntil
        )
    }

}