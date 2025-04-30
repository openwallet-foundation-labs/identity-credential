package org.multipaz.models.presentment

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.buildJsonObject
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
import org.multipaz.credential.CredentialLoader
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
import org.multipaz.document.DocumentMetadata
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentType
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.mdoc.issuersigned.buildIssuerNamespaces
import org.multipaz.mdoc.mso.MobileSecurityObjectGenerator
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.sdjwt.Issuer
import org.multipaz.sdjwt.SdJwtVcGenerator
import org.multipaz.sdjwt.credential.KeyBoundSdJwtVcCredential
import org.multipaz.sdjwt.util.JsonWebKey
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.software.SoftwareCreateKeySettings
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.Storage
import org.multipaz.storage.StorageTableSpec
import org.multipaz.storage.base.BaseStorageTable
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.util.Logger
import org.multipaz.util.fromHex
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

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
     * The [DocumentStore] itself is backed by [EphemeralStorage] and is using
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
        secureAreaRepository = SecureAreaRepository.build {
            add(softwareSecureArea)
        }

        val credentialLoader = CredentialLoader()
        credentialLoader.addCredentialImplementation(MdocCredential::class) {
            document -> MdocCredential(document)
        }
        documentStore = DocumentStore(
            storage = storage,
            secureAreaRepository = secureAreaRepository,
            credentialLoader = credentialLoader,
            documentMetadataFactory = TestDocumentMetadata::create,
            documentTableSpec = testDocumentTableSpec
        )

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

        // TODO: Generate random serials with sufficient entropy as per 18013-5 Annex B
        val iacaCert = MdocUtil.generateIacaCertificate(
            iacaKey = iacaKey,
            subject = X500Name.fromName("C=US,CN=OWF Multipaz TEST IACA"),
            serial = ASN1Integer("26457B125F0AD75217A98EE6CFDEA7FC486221".fromHex()),
            validFrom = iacaValidFrom,
            validUntil = iacaValidUntil,
            issuerAltNameUrl = "https://github.com/openwallet-foundation-labs/identity-credential",
            crlUrl = "https://github.com/openwallet-foundation-labs/identity-credential/crl"
        )

        val dsKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val dsCert = MdocUtil.generateDsCertificate(
            iacaCert = iacaCert,
            iacaKey = iacaKey,
            dsKey = dsKey.publicKey,
            subject = X500Name.fromName("C=US,CN=OWF Multipaz TEST DS"),
            serial = ASN1Integer("26457B125F0AD75217A98EE6CFDEA7FC486221".fromHex()),
            validFrom = dsValidFrom,
            validUntil = dsValidUntil,
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

    private class TestDocumentMetadata private constructor(
        private val saveFn: suspend (data: ByteString) -> Unit
    ) : DocumentMetadata {
        override val provisioned: Boolean
            get() = true
        override val displayName: String?
            get() = null
        override val typeDisplayName: String?
            get() = null
        override val cardArt: ByteString?
            get() = null
        override val issuerLogo: ByteString?
            get() = null

        override suspend fun documentDeleted() {
        }

        companion object {
            suspend fun create(
                documentId: String,
                serializedData: ByteString?,
                saveFn: suspend (data: ByteString) -> Unit
            ): TestDocumentMetadata {
                return TestDocumentMetadata(saveFn)
            }
        }
    }

    private val testDocumentTableSpec = object: StorageTableSpec(
        name = "TestDocuments",
        supportExpiration = false,
        supportPartitions = false,
        schemaVersion = 1L,           // Bump every time incompatible changes are made
    ) {
        override suspend fun schemaUpgrade(oldTable: BaseStorageTable) {
            oldTable.deleteAll()
        }
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
            dsKey = dsKey,
            dsCert = dsCert,
            documentType = DrivingLicense.getDocumentType(),
            signedAt = signedAt,
            validFrom = validFrom,
            validUntil = validUntil,
        )
        docEuPid = provisionDocument(
            documentStore = documentStore,
            dsKey = dsKey,
            dsCert = dsCert,
            documentType = PhotoID.getDocumentType(),
            signedAt = signedAt,
            validFrom = validFrom,
            validUntil = validUntil,
        )
        docPhotoId = provisionDocument(
            documentStore = documentStore,
            dsKey = dsKey,
            dsCert = dsCert,
            documentType = EUPersonalID.getDocumentType(),
            signedAt = signedAt,
            validFrom = validFrom,
            validUntil = validUntil,
        )
    }

    private suspend fun provisionDocument(
        documentStore: DocumentStore,
        dsKey: EcPrivateKey,
        dsCert: X509Cert,
        documentType: DocumentType,
        signedAt: Instant,
        validFrom: Instant,
        validUntil: Instant,
    ): Document {
        val document = documentStore.createDocument {}

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

        if (documentType.vcDocumentType != null) {
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
        if (documentType.vcDocumentType == null) {
            return
        }

        val identityAttributes = buildJsonObject {
            for ((claimName, attribute) in documentType.vcDocumentType!!.claims) {
                val sampleValue = attribute.sampleValueVc
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
            vct = documentType.vcDocumentType!!.type,
            createKeySettings = SoftwareCreateKeySettings.Builder().build()
        )

        val sdJwtVcGenerator = SdJwtVcGenerator(
            vct = credential.vct,
            payload = identityAttributes,
            issuer = Issuer(
                "https://example-issuer.com",
                dsKey.publicKey.curve.defaultSigningAlgorithmFullySpecified,
                null,
                X509CertChain(listOf(dsCert))
            ),
        )
        sdJwtVcGenerator.publicKey =
            (credential as? SecureAreaBoundCredential)?.let { JsonWebKey(it.getAttestation().publicKey) }
        sdJwtVcGenerator.timeSigned = signedAt
        sdJwtVcGenerator.timeValidityBegin = validFrom
        sdJwtVcGenerator.timeValidityEnd = validUntil
        val sdJwt = sdJwtVcGenerator.generateSdJwt(dsKey)
        credential.certify(
            sdJwt.toString().encodeToByteArray(),
            validFrom,
            validUntil
        )
    }

}