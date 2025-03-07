package org.multipaz.testapp

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.RawCbor
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.toDataItem
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.CoseNumberLabel
import org.multipaz.credential.SecureAreaBoundCredential
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.document.Document
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentCannedRequest
import org.multipaz.documenttype.DocumentType
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.documenttype.knowntypes.UtopiaMovieTicket
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.mdoc.issuersigned.buildIssuerNamespaces
import org.multipaz.mdoc.mso.MobileSecurityObjectGenerator
import org.multipaz.mdoc.request.DeviceRequestGenerator
import org.multipaz.sdjwt.Issuer
import org.multipaz.sdjwt.SdJwtVcGenerator
import org.multipaz.sdjwt.credential.KeyBoundSdJwtVcCredential
import org.multipaz.sdjwt.credential.KeylessSdJwtVcCredential
import org.multipaz.sdjwt.util.JsonWebKey
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.KeyPurpose
import org.multipaz.securearea.SecureArea
import org.multipaz.util.Logger
import identitycredential.samples.testapp.generated.resources.Res
import identitycredential.samples.testapp.generated.resources.driving_license_card_art
import identitycredential.samples.testapp.generated.resources.movie_ticket_cart_art
import identitycredential.samples.testapp.generated.resources.photo_id_card_art
import identitycredential.samples.testapp.generated.resources.pid_card_art
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getDrawableResourceBytes
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.iterator
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

object TestAppUtils {
    private const val TAG = "TestAppUtils"

    const val MDOC_CREDENTIAL_DOMAIN_AUTH = "mdoc_credential_domain_auth"
    const val MDOC_CREDENTIAL_DOMAIN_NO_AUTH = "mdoc_credential_domain_no_auth"
    const val SDJWT_CREDENTIAL_DOMAIN_AUTH = "sdjwt_credential_domain_auth"
    const val SDJWT_CREDENTIAL_DOMAIN_NO_AUTH = "sdjwt_credential_domain_no_auth"
    const val SDJWT_CREDENTIAL_DOMAIN_KEYLESS = "sdjwt_credential_domain_keyless"

    fun generateEncodedDeviceRequest(
        request: DocumentCannedRequest,
        encodedSessionTranscript: ByteArray,
        readerKey: EcPrivateKey,
        readerCert: X509Cert,
        readerRootCert: X509Cert,
    ): ByteArray {
        val mdocRequest = request.mdocRequest!!
        val itemsToRequest = mutableMapOf<String, MutableMap<String, Boolean>>()
        for (ns in mdocRequest.namespacesToRequest) {
            for ((de, intentToRetain) in ns.dataElementsToRequest) {
                itemsToRequest.getOrPut(ns.namespace) { mutableMapOf() }
                    .put(de.attribute.identifier, intentToRetain)
            }
        }

        val deviceRequestGenerator = DeviceRequestGenerator(encodedSessionTranscript)
        deviceRequestGenerator.addDocumentRequest(
            docType = mdocRequest.docType,
            itemsToRequest = itemsToRequest,
            requestInfo = null,
            readerKey = readerKey,
            signatureAlgorithm = readerKey.curve.defaultSigningAlgorithm,
            readerKeyCertificateChain = X509CertChain(listOf(readerCert, readerRootCert)),
        )
        return deviceRequestGenerator.generate()
    }

    fun generateEncodedSessionTranscript(
        encodedDeviceEngagement: ByteArray,
        handover: DataItem,
        eReaderKey: EcPublicKey
    ): ByteArray {
        val encodedEReaderKey = Cbor.encode(eReaderKey.toCoseKey().toDataItem())
        return Cbor.encode(
            CborArray.builder()
                .add(Tagged(24, Bstr(encodedDeviceEngagement)))
                .add(Tagged(24, Bstr(encodedEReaderKey)))
                .add(handover)
                .end()
                .build()
        )
    }


    val provisionedDocumentTypes = listOf(
        DrivingLicense.getDocumentType(),
        PhotoID.getDocumentType(),
        EUPersonalID.getDocumentType(),
        UtopiaMovieTicket.getDocumentType()
    )

    suspend fun provisionTestDocuments(
        documentStore: DocumentStore,
        secureArea: SecureArea,
        secureAreaCreateKeySettingsFunc: (
            challenge: ByteString,
            curve: EcCurve,
            keyPurposes: Set<KeyPurpose>,
            userAuthenticationRequired: Boolean,
            validFrom: Instant,
            validUntil: Instant
        ) -> CreateKeySettings,
        dsKey: EcPrivateKey,
        dsCert: X509Cert,
        deviceKeyPurposes: Set<KeyPurpose>,
        deviceKeyCurve: EcCurve,
    ) {
        if (documentStore.listDocuments().size >= 5) {
            // Assume documents are provisioned
            // TODO: do we want a more granular check
            return
        }
        provisionDocument(
            documentStore,
            secureArea,
            secureAreaCreateKeySettingsFunc,
            dsKey,
            dsCert,
            deviceKeyPurposes,
            deviceKeyCurve,
            DrivingLicense.getDocumentType(),
            "Erika",
            "Erika's Driving License",
            Res.drawable.driving_license_card_art
        )
        provisionDocument(
            documentStore,
            secureArea,
            secureAreaCreateKeySettingsFunc,
            dsKey,
            dsCert,
            deviceKeyPurposes,
            deviceKeyCurve,
            PhotoID.getDocumentType(),
            "Erika",
            "Erika's Photo ID",
            Res.drawable.photo_id_card_art
        )
        provisionDocument(
            documentStore,
            secureArea,
            secureAreaCreateKeySettingsFunc,
            dsKey,
            dsCert,
            deviceKeyPurposes,
            deviceKeyCurve,
            PhotoID.getDocumentType(),
            "Erika #2",
            "Erika's Photo ID #2",
            Res.drawable.photo_id_card_art
        )
        provisionDocument(
            documentStore,
            secureArea,
            secureAreaCreateKeySettingsFunc,
            dsKey,
            dsCert,
            deviceKeyPurposes,
            deviceKeyCurve,
            EUPersonalID.getDocumentType(),
            "Erika",
            "Erika's EU PID",
            Res.drawable.pid_card_art
        )
        provisionDocument(
            documentStore,
            secureArea,
            secureAreaCreateKeySettingsFunc,
            dsKey,
            dsCert,
            deviceKeyPurposes,
            deviceKeyCurve,
            UtopiaMovieTicket.getDocumentType(),
            "Erika",
            "Erika's Movie Ticket",
            Res.drawable.movie_ticket_cart_art
        )
    }

    // TODO: also provision SD-JWT credentials, if applicable
    @OptIn(ExperimentalResourceApi::class)
    private suspend fun provisionDocument(
        documentStore: DocumentStore,
        secureArea: SecureArea,
        secureAreaCreateKeySettingsFunc: (
            challenge: ByteString,
            curve: EcCurve,
            keyPurposes: Set<KeyPurpose>,
            userAuthenticationRequired: Boolean,
            validFrom: Instant,
            validUntil: Instant
        ) -> CreateKeySettings,
        dsKey: EcPrivateKey,
        dsCert: X509Cert,
        deviceKeyPurposes: Set<KeyPurpose>,
        deviceKeyCurve: EcCurve,
        documentType: DocumentType,
        givenNameOverride: String,
        displayName: String,
        cardArtResource: DrawableResource,
    ) {
        val cardArt = getDrawableResourceBytes(
            getSystemResourceEnvironment(),
            cardArtResource,
        )

        val document = documentStore.createDocument {
            val metadata = it as TestAppDocumentMetadata
            metadata.initialize(
                displayName = displayName,
                typeDisplayName = documentType.displayName,
                cardArt = ByteString(cardArt),
            )
        }

        val now = Clock.System.now()
        val signedAt = now - 1.hours
        val validFrom =  now - 1.hours
        val validUntil = now + 365.days

        if (documentType.mdocDocumentType != null) {
            addMdocCredentials(
                document = document,
                documentType = documentType,
                secureArea = secureArea,
                secureAreaCreateKeySettingsFunc = secureAreaCreateKeySettingsFunc,
                deviceKeyCurve = deviceKeyCurve,
                deviceKeyPurposes = deviceKeyPurposes,
                signedAt = signedAt,
                validFrom = validFrom,
                validUntil = validUntil,
                dsKey = dsKey,
                dsCert = dsCert,
                givenNameOverride = givenNameOverride
            )
        }

        if (documentType.vcDocumentType != null) {
            addSdJwtVcCredentials(
                document = document,
                documentType = documentType,
                secureArea = secureArea,
                secureAreaCreateKeySettingsFunc = secureAreaCreateKeySettingsFunc,
                deviceKeyCurve = deviceKeyCurve,
                deviceKeyPurposes = deviceKeyPurposes,
                signedAt = signedAt,
                validFrom = validFrom,
                validUntil = validUntil,
                dsKey = dsKey,
                dsCert = dsCert,
                givenNameOverride = givenNameOverride
            )
        }
    }

    private suspend fun addMdocCredentials(
        document: Document,
        documentType: DocumentType,
        secureArea: SecureArea,
        secureAreaCreateKeySettingsFunc: (
            challenge: ByteString,
            curve: EcCurve,
            keyPurposes: Set<KeyPurpose>,
            userAuthenticationRequired: Boolean,
            validFrom: Instant,
            validUntil: Instant
        ) -> CreateKeySettings,
        deviceKeyPurposes: Set<KeyPurpose>,
        deviceKeyCurve: EcCurve,
        signedAt: Instant,
        validFrom: Instant,
        validUntil: Instant,
        dsKey: EcPrivateKey,
        dsCert: X509Cert,
        givenNameOverride: String
    ) {
        val issuerNamespaces = buildIssuerNamespaces {
            for ((nsName, ns) in documentType.mdocDocumentType?.namespaces!!) {
                addNamespace(nsName) {
                    for ((deName, de) in ns.dataElements) {
                        val sampleValue = de.attribute.sampleValueMdoc
                        if (sampleValue != null) {
                            val value = if (deName.startsWith("given_name")) {
                                Tstr(givenNameOverride)
                            } else {
                                sampleValue
                            }
                            addDataElement(deName, value)
                        } else {
                            Logger.w(TAG, "No sample value for data element $deName")
                        }
                    }
                }
            }
        }

        // Create authentication keys...
        for (domain in listOf(MDOC_CREDENTIAL_DOMAIN_AUTH, MDOC_CREDENTIAL_DOMAIN_NO_AUTH)) {
            val userAuthenticationRequired = (domain == MDOC_CREDENTIAL_DOMAIN_AUTH)

            val mdocCredential = MdocCredential.create(
                document = document,
                asReplacementForIdentifier = null,
                domain = domain,
                secureArea = secureArea,
                docType = documentType.mdocDocumentType!!.docType,
                createKeySettings = secureAreaCreateKeySettingsFunc(
                    "Challenge".encodeToByteString(),
                    deviceKeyCurve,
                    deviceKeyPurposes,
                    userAuthenticationRequired,
                    validFrom,
                    validUntil
                )
            )

            // Generate an MSO and issuer-signed data for this authentication key.
            val msoGenerator = MobileSecurityObjectGenerator(
                "SHA-256",
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
                    Algorithm.ES256.coseAlgorithmIdentifier.toDataItem()
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
                    Algorithm.ES256,
                    protectedHeaders,
                    unprotectedHeaders
                ).toDataItem()
            )
            val issuerProvidedAuthenticationData = Cbor.encode(
                CborMap.builder()
                    .put("nameSpaces", issuerNamespaces.toDataItem())
                    .put("issuerAuth", RawCbor(encodedIssuerAuth))
                    .end()
                    .build()
            )

            // Now that we have issuer-provided authentication data we certify the authentication key.
            mdocCredential.certify(
                issuerProvidedAuthenticationData,
                validFrom,
                validUntil
            )
        }

    }

    private suspend fun addSdJwtVcCredentials(
        document: Document,
        documentType: DocumentType,
        secureArea: SecureArea,
        secureAreaCreateKeySettingsFunc: (
            challenge: ByteString,
            curve: EcCurve,
            keyPurposes: Set<KeyPurpose>,
            userAuthenticationRequired: Boolean,
            validFrom: Instant,
            validUntil: Instant
        ) -> CreateKeySettings,
        deviceKeyPurposes: Set<KeyPurpose>,
        deviceKeyCurve: EcCurve,
        signedAt: Instant,
        validFrom: Instant,
        validUntil: Instant,
        dsKey: EcPrivateKey,
        dsCert: X509Cert,
        givenNameOverride: String
    ) {
        if (documentType.vcDocumentType == null) {
            return
        }

        val identityAttributes = buildJsonObject {
            for ((claimName, attribute) in documentType.vcDocumentType!!.claims) {
                val sampleValue = attribute.sampleValueVc
                if (sampleValue != null) {
                    val value = if (claimName.startsWith("given_name")) {
                        JsonPrimitive(givenNameOverride)
                    } else {
                        sampleValue
                    }
                    put(claimName, value)
                } else {
                    Logger.w(TAG, "No sample value for claim $claimName")
                }
            }
        }

        val domains = if (documentType.vcDocumentType!!.keyBound) {
            listOf(SDJWT_CREDENTIAL_DOMAIN_AUTH, SDJWT_CREDENTIAL_DOMAIN_NO_AUTH)
        } else {
            listOf(SDJWT_CREDENTIAL_DOMAIN_KEYLESS)
        }
        for (domain in domains) {
            val credential = if (documentType.vcDocumentType!!.keyBound) {
                val userAuthenticationRequired = (domain == SDJWT_CREDENTIAL_DOMAIN_AUTH)
                KeyBoundSdJwtVcCredential.create(
                    document = document,
                    asReplacementForIdentifier = null,
                    domain = domain,
                    secureArea = secureArea,
                    vct = documentType.vcDocumentType!!.type,
                    createKeySettings = secureAreaCreateKeySettingsFunc(
                        "Challenge".encodeToByteString(),
                        deviceKeyCurve,
                        deviceKeyPurposes,
                        userAuthenticationRequired,
                        validFrom,
                        validUntil
                    )
                )
            } else {
                KeylessSdJwtVcCredential.create(
                    document = document,
                    asReplacementForIdentifier = null,
                    domain = domain,
                    vct = documentType.vcDocumentType!!.type,
                )
            }

            val sdJwtVcGenerator = SdJwtVcGenerator(
                vct = credential.vct,
                payload = identityAttributes,
                issuer = Issuer("https://example-issuer.com", Algorithm.ES256, "key-1"), // TODO
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

}