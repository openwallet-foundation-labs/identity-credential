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
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
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
 * Factory for Driver's License credentials.
 */
internal class CredentialFactoryUtopiaMdl : CredentialFactory {
    override val offerId: String
        get() = "utopia_wholesale"

    override val scope: String
        get() = "utopia_wholesale_mDL"

    override val format: Openid4VciFormat
        get() = openId4VciFormatMdl

    override val proofSigningAlgorithms: List<String>
        get() = CredentialFactory.DEFAULT_PROOF_SIGNING_ALGORITHMS

    override val cryptographicBindingMethods: List<String>
        get() = listOf("cose_key")

    override val credentialSigningAlgorithms: List<String>
        get() = CredentialFactory.DEFAULT_CREDENTIAL_SIGNING_ALGORITHMS

    override val name: String
        get() = "Utopia Wholesale Membership"

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

        val records = data["records"]
        if (!records.hasKey("mDL")) {
            throw IllegalArgumentException("No membership was issued to this person")
        }
        val mdlData = records["mDL"].asMap.values.firstOrNull() ?: buildCborMap { }

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
            Algorithm.SHA256,
            docType,
            authenticationKey!!
        )
        msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)

        // As we do not have driver license database, just make up some data to fill mDL
        // for demo purposes. Take what we can from the PID that was presented as evidence.
        val mdocType = DrivingLicense.getDocumentType()
            .mdocDocumentType!!.namespaces[DrivingLicense.MDL_NAMESPACE]!!

        val timeZone = TimeZone.currentSystemDefault()
        val dateOfBirthInstant = dateOfBirth.atStartOfDayIn(timeZone)
        // over 18/21 is calculated purely based on calendar date (not based on the birth time zone)
        val ageOver18 = now > dateOfBirthInstant.plus(18, DateTimeUnit.YEAR, timeZone)
        val ageOver21 = now > dateOfBirthInstant.plus(21, DateTimeUnit.YEAR, timeZone)

        val issuerNamespaces = buildIssuerNamespaces {
            addNamespace(DrivingLicense.MDL_NAMESPACE) {
                // Transfer core fields
                addDataElement("family_name", coreData["family_name"])
                addDataElement("given_name", coreData["given_name"])
                addDataElement("portrait", Bstr(portrait))
                addDataElement("birth_date", dateOfBirth.toDataItemFullDate())
                val added = mutableSetOf("family_name", "given_name", "portrait", "birth_date")

                // Transfer fields from mDL record that have counterparts in the mDL credential
                for ((nameItem, value) in mdlData.asMap) {
                    val name = nameItem.asTstr
                    if (mdocType.dataElements.contains(name)) {
                        addDataElement(name, value)
                        added.add(name)
                    }
                }

                // Values derived from the birth_date
                addDataElement("age_in_years",
                    Uint(dateOfBirth.yearsUntil(now.toLocalDateTime(timeZone).date).toULong()))
                addDataElement("age_birth_year", Uint(dateOfBirth.year.toULong()))
                addDataElement("age_over_18", if (ageOver18) Simple.TRUE else Simple.FALSE)
                addDataElement( "age_over_21", if (ageOver21) Simple.TRUE else Simple.FALSE)

//                addDataElement(
//                    "driving_privileges",
//                    buildCborArray {
//                        addCborMap {
//                            put("vehicle_category_code", "A")
//                            put("issue_date", Tagged(1004, Tstr("2018-08-09")))
//                            put("expiry_date", Tagged(1004, Tstr("2028-09-01")))
//                        }
//                        addCborMap {
//                            put("vehicle_category_code", "B")
//                            put("issue_date", Tagged(1004, Tstr("2017-02-23")))
//                            put("expiry_date", Tagged(1004, Tstr("2028-09-01")))
//                        }
//                    }
//                )
//                added.add("driving_privileges")

                // Add all mandatory elements for completeness. This will come from the System of
                // Record in the future
                for ((elementName, data) in mdocType.dataElements) {
                    if (!data.mandatory || added.contains(elementName)) {
                        continue
                    }
                    val value = data.attribute.sampleValueMdoc
                    if (value != null) {
                        addDataElement(elementName, value)
                    } else {
                        Logger.e(TAG, "Could not fill '$elementName': no sample data")
                    }
                }
            }
        }

        msoGenerator.addValueDigests(issuerNamespaces)

        val documentSigningKeyCert = X509Cert.fromPem(
            resources.getStringResource("ds_certificate.pem")!!)
        val documentSigningKey = EcPrivateKey.fromPem(
            resources.getStringResource("ds_private_key.pem")!!,
            documentSigningKeyCert.ecPublicKey
        )

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
        const val TAG = "CredentialFactoryUtopiaMdl"
    }
}
