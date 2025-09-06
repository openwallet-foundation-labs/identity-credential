package org.multipaz.openid4vci.credential

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.toDataItem
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.CoseNumberLabel
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509CertChain
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Resources
import org.multipaz.mdoc.mso.MobileSecurityObjectGenerator
import org.multipaz.util.toBase64Url
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil
import org.multipaz.cbor.RawCbor
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Uint
import org.multipaz.cbor.addCborMap
import org.multipaz.cbor.buildCborArray
import org.multipaz.cbor.buildCborMap
import org.multipaz.cbor.toDataItemFullDate
import org.multipaz.mdoc.issuersigned.buildIssuerNamespaces
import org.multipaz.util.Logger
import kotlin.time.Duration.Companion.days

/**
 * Factory for PhotoID credentials according to ISO/IEC TS 23220-4 (E) operational phase - Annex C Photo ID v2
 */
internal class CredentialFactoryPhotoID : CredentialFactoryBase() {
    override val offerId: String
        get() = "utopia_wholesale"

    override val scope: String
        get() = "utopia_wholesale_photoid"

    override val format: Openid4VciFormat
        get() = openId4VciFormatPhotoId

    override val requireClientAttestation: Boolean get() = false

    override val requireKeyAttestation: Boolean get() = false

    override val proofSigningAlgorithms: List<String>
        get() = CredentialFactory.DEFAULT_PROOF_SIGNING_ALGORITHMS

    override val cryptographicBindingMethods: List<String>
        get() = listOf("cose_key")

    override val name: String
        get() = "Utopia Wholesale Photo ID"

    override val logo: String
        get() = "card-utopia-wholesale.png"

    override suspend fun makeCredential(
        data: DataItem,
        authenticationKey: EcPublicKey?
    ): String {
        val now = Clock.System.now()

        val resources = BackendEnvironment.getInterface(Resources::class)!!

        val coreData = data["core"]
        val dateOfBirth = coreData["birth_date"].asDateString
        val portrait = if (coreData.hasKey("portrait")) {
            coreData["portrait"].asBstr
        } else {
            resources.getRawResource("john_lee.png")!!.toByteArray()
        }

        // Create AuthKeys and MSOs, make sure they're valid for 30 days
        val timeSigned = Instant.fromEpochSeconds(now.epochSeconds, 0)
        val validFrom = Instant.fromEpochSeconds(now.epochSeconds, 0)
        val validUntil = validFrom + 30.days

        // Generate an MSO and issuer-signed data for this authentication key.
        val docType = PhotoID.PHOTO_ID_DOCTYPE
        val msoGenerator = MobileSecurityObjectGenerator(
            Algorithm.SHA256,
            docType,
            authenticationKey!!
        )
        msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)

        // Get PhotoID document type definition
        val mdocType = PhotoID.getDocumentType()
            .mdocDocumentType!!.namespaces[PhotoID.PHOTO_ID_NAMESPACE]!!

        val timeZone = TimeZone.currentSystemDefault()
        val dateOfBirthInstant = dateOfBirth.atStartOfDayIn(timeZone)
        // Calculate age-based flags
        val ageOver18 = now > dateOfBirthInstant.plus(18, DateTimeUnit.YEAR, timeZone)
        val ageOver21 = now > dateOfBirthInstant.plus(21, DateTimeUnit.YEAR, timeZone)

        val issuerNamespaces = buildIssuerNamespaces {
            // ISO 23220-2 namespace (common data elements)
            addNamespace(PhotoID.ISO_23220_2_NAMESPACE) {
                addDataElement("family_name_unicode", coreData["family_name"])
                addDataElement("given_name_unicode", coreData["given_name"])
                addDataElement("portrait", Bstr(portrait))
                addDataElement("birth_date", dateOfBirth.toDataItemFullDate())
                addDataElement("issue_date", LocalDate.parse("2024-04-01").toDataItemFullDate())
                addDataElement("expiry_date", LocalDate.parse("2034-04-01").toDataItemFullDate())
                addDataElement("issuing_authority_unicode", Tstr("Utopia WholeSale Store"))
                addDataElement("issuing_country", Tstr("US"))
                addDataElement("document_number", Tstr("899878797979"))
                addDataElement("family_name_latin1", coreData["family_name"])
                addDataElement("given_name_latin1", coreData["given_name"])

                // Age-based flags
                addDataElement("age_over_18", if (ageOver18) Simple.TRUE else Simple.FALSE)
                addDataElement("age_over_21", if (ageOver21) Simple.TRUE else Simple.FALSE)
                addDataElement("age_in_years", Uint(dateOfBirth.yearsUntil(now.toLocalDateTime(timeZone).date).toULong()))
                addDataElement("age_birth_year", Uint(dateOfBirth.year.toULong()))

                // Additional optional fields
                addDataElement("nationality", Tstr("US"))
            }

            // PhotoID specific namespace
            addNamespace(PhotoID.PHOTO_ID_NAMESPACE) {
                addDataElement("person_id", Tstr("899878797979"))
            }
        }

        msoGenerator.addValueDigests(issuerNamespaces)

        val documentSigningKeyCert = X509Cert.fromPem(
            resources.getStringResource("ds_certificate.pem")!!
        )
        val documentSigningKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            documentSigningKeyCert.ecPublicKey
        )

        val mso = msoGenerator.generate()
        val taggedEncodedMso = Cbor.encode(Tagged(24, Bstr(mso)))

        // IssuerAuth is a COSE_Sign1 where payload is MobileSecurityObjectBytes
        val protectedHeaders = mapOf<CoseLabel, DataItem>(
            Pair(
                CoseNumberLabel(Cose.COSE_LABEL_ALG),
                Algorithm.ES256.coseAlgorithmIdentifier!!.toDataItem()
            )
        )
        val unprotectedHeaders = mapOf<CoseLabel, DataItem>(
            Pair(
                CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
                X509CertChain(listOf(documentSigningKeyCert)).toDataItem()
            )
        )
        val encodedIssuerAuth = Cbor.encode(
            Cose.coseSign1Sign(
                documentSigningKey,
                taggedEncodedMso,
                true,
                documentSigningKey.publicKey.curve.defaultSigningAlgorithm,
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

        return issuerProvidedAuthenticationData.toBase64Url()
    }

    companion object {
        const val TAG = "CredentialFactoryPhotoID"
        private val openId4VciFormatPhotoId = Openid4VciFormatMdoc(PhotoID.PHOTO_ID_DOCTYPE)
    }
}