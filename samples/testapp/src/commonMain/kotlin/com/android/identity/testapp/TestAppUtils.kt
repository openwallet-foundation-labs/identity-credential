package com.android.identity.testapp

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborMap
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.RawCbor
import com.android.identity.cbor.Tagged
import com.android.identity.cbor.Tstr
import com.android.identity.cbor.toDataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.CoseNumberLabel
import com.android.identity.credential.SecureAreaBoundCredential
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.document.Document
import com.android.identity.document.DocumentStore
import com.android.identity.documenttype.DocumentCannedRequest
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.knowntypes.DrivingLicense
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.documenttype.knowntypes.PhotoID
import com.android.identity.documenttype.knowntypes.UtopiaMovieTicket
import com.android.identity.mdoc.credential.MdocCredential
import com.android.identity.mdoc.issuersigned.buildIssuerNamespaces
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.request.DeviceRequestGenerator
import com.android.identity.sdjwt.Issuer
import com.android.identity.sdjwt.SdJwtVcGenerator
import com.android.identity.sdjwt.credential.KeyBoundSdJwtVcCredential
import com.android.identity.sdjwt.credential.KeylessSdJwtVcCredential
import com.android.identity.sdjwt.util.JsonWebKey
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.SecureArea
import com.android.identity.util.Logger
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