package com.android.identity.server.openid4vci

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Tagged
import com.android.identity.cbor.Tstr
import com.android.identity.cbor.toDataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.CoseNumberLabel
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.document.NameSpacedData
import com.android.identity.documenttype.knowntypes.DrivingLicense
import com.android.identity.documenttype.knowntypes.EUPersonalID
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.flow.server.Resources
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.util.toBase64Url
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

internal class CredentialFactoryMdl : CredentialFactory {
    override val offerId: String
        get() = "mDL"

    override val scope: String
        get() = "mDL"

    override val format: Openid4VciFormat
        get() = openId4VciFormatMdl

    override val proofSigningAlgorithms: List<String>
        get() = CredentialFactory.DEFAULT_PROOF_SIGNING_ALGORITHMS

    override val cryptographicBindingMethods: List<String>
        get() = listOf("cose_key")

    override val credentialSigningAlgorithms: List<String>
        get() = CredentialFactory.DEFAULT_CREDENTIAL_SIGNING_ALGORITHMS

    override val name: String
        get() = "Example Driver License (mDL)"

    override val logo: String
        get() = "card-mdl.png"

    override suspend fun makeCredential(
        environment: FlowEnvironment,
        state: IssuanceState,
        authenticationKey: EcPublicKey
    ): String {
        val now = Clock.System.now()

        // Create AuthKeys and MSOs, make sure they're valid for 30 days. Also make
        // sure to not use fractional seconds as 18013-5 calls for this (clauses 7.1
        // and 9.1.2.4)
        //
        val timeSigned = Instant.fromEpochSeconds(now.epochSeconds, 0)
        val validFrom = Instant.fromEpochSeconds(now.epochSeconds, 0)
        val validUntil = validFrom + 30.days

        // Generate an MSO and issuer-signed data for this authentication key.
        val docType = DrivingLicense.MDL_DOCTYPE
        val msoGenerator = MobileSecurityObjectGenerator(
            "SHA-256",
            docType,
            authenticationKey
        )
        msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)

        val credentialData = NameSpacedData.Builder()

        // As we do not have driver license database, just make up some data to fill mDL
        // for demo purposes. Take what we can from the PID that was presented as evidence.
        val source = state.credentialData!!
        val mdocType = DrivingLicense.getDocumentType()
            .mdocDocumentType!!.namespaces[DrivingLicense.MDL_NAMESPACE]!!
        for (elementName in source.getDataElementNames(EUPersonalID.EUPID_NAMESPACE)) {
            val value = source.getDataElement(EUPersonalID.EUPID_NAMESPACE, elementName)
            if (mdocType.dataElements.containsKey(elementName)) {
                credentialData.putEntry(DrivingLicense.MDL_NAMESPACE, elementName, value)
            }
        }
        val useMalePhoto = source.hasDataElement(EUPersonalID.EUPID_NAMESPACE, "gender") &&
            source.getDataElementNumber(EUPersonalID.EUPID_NAMESPACE, "gender") == 1L
        val photoResource = if (useMalePhoto) "openid4vci/male.jpg" else "openid4vci/female.jpg"
        val photoBytes = environment.getInterface(Resources::class)!!.getRawResource(photoResource)
        credentialData.putEntryByteString(
            DrivingLicense.MDL_NAMESPACE, "portrait", photoBytes!!.toByteArray())

        credentialData.putEntry(
            DrivingLicense.MDL_NAMESPACE,
            "driving_privileges",
            Cbor.encode(CborArray.builder()
                .addMap()
                .put("vehicle_category_code", "A")
                .put("issue_date", Tagged(1004, Tstr("2018-08-09")))
                .put("expiry_date", Tagged(1004, Tstr("2028-09-01")))
                .end()
                .addMap()
                .put("vehicle_category_code", "B")
                .put("issue_date", Tagged(1004, Tstr("2017-02-23")))
                .put("expiry_date", Tagged(1004, Tstr("2028-09-01")))
                .end()
                .end()
                .build())
        )

        val randomProvider = Random.Default
        val issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
            credentialData.build(),
            randomProvider,
            16,
            null
        )
        for (nameSpaceName in issuerNameSpaces.keys) {
            val digests = MdocUtil.calculateDigestsForNameSpace(
                nameSpaceName,
                issuerNameSpaces,
                Algorithm.SHA256
            )
            msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests)
        }

        val resources = environment.getInterface(Resources::class)!!
        val documentSigningKeyCert = X509Cert.fromPem(
            resources.getStringResource("ds_certificate.pem")!!)
        val documentSigningKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            documentSigningKeyCert.ecPublicKey
        )

        val mso = msoGenerator.generate()
        val taggedEncodedMso = Cbor.encode(Tagged(Tagged.ENCODED_CBOR, Bstr(mso)))
        val protectedHeaders = mapOf<CoseLabel, DataItem>(Pair(
            CoseNumberLabel(Cose.COSE_LABEL_ALG),
            Algorithm.ES256.coseAlgorithmIdentifier.toDataItem()
        ))
        val unprotectedHeaders = mapOf<CoseLabel, DataItem>(Pair(
            CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
            X509CertChain(listOf(
                X509Cert(documentSigningKeyCert.encodedCertificate)
            )
            ).toDataItem()
        ))
        val encodedIssuerAuth = Cbor.encode(
            Cose.coseSign1Sign(
                documentSigningKey,
                taggedEncodedMso,
                true,
                Algorithm.ES256,
                protectedHeaders,
                unprotectedHeaders
            ).toDataItem()
        )

        val issuerProvidedAuthenticationData = StaticAuthDataGenerator(
            issuerNameSpaces,
            encodedIssuerAuth
        ).generate()

        return issuerProvidedAuthenticationData.toBase64Url()
    }
}