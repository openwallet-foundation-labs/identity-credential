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
import org.multipaz.crypto.EcPublicKey
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.rpc.backend.BackendEnvironment
import org.multipaz.rpc.backend.Resources
import org.multipaz.mdoc.mso.MobileSecurityObjectGenerator
import org.multipaz.util.toBase64Url
import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlin.time.Instant
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
internal class CredentialFactoryMdl : CredentialFactoryBase() {
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

    override val name: String
        get() = "Example Driver License (mDL)"

    override val logo: String
        get() = "card-mdl.png"

    override suspend fun makeCredential(
        data: DataItem,
        authenticationKey: EcPublicKey?
    ): String {
        val now = Clock.System.now()

        val coreData = data["core"]
        val dateOfBirth = coreData["birth_date"].asDateString
        val address = if (coreData.hasKey("address")) {
            val addressObject = coreData["address"]
            if (addressObject.hasKey("formatted")) addressObject["formatted"] else null
        } else {
            null
        }
        val records = data["records"]
        if (!records.hasKey("mDL")) {
            throw IllegalArgumentException("No driver's license was issued to this person")
        }
        val mdlData = records["mDL"].asMap.values.firstOrNull() ?: buildCborMap { }

        // Create AuthKeys and MSOs, make sure they're valid for 30 days. Also make
        // sure to not use fractional seconds as 18013-5 calls for this (clauses 7.1
        // and 9.1.2.4)
        //
        val timeSigned = Instant.fromEpochSeconds(now.epochSeconds, 0)
        val validFrom = Instant.fromEpochSeconds(now.epochSeconds, 0)
        val validUntil = validFrom + 30.days

        val resources = BackendEnvironment.getInterface(Resources::class)!!

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
                val added = mutableSetOf("birth_date")
                addDataElement("birth_date", dateOfBirth.toDataItemFullDate())

                // Transfer fields from mDL record that have counterparts in the mDL credential
                for ((nameItem, value) in mdlData.asMap) {
                    val name = nameItem.asTstr
                    if (!added.contains(name) && mdocType.dataElements.contains(name)) {
                        addDataElement(name, value)
                        added.add(name)
                    }
                }

                if (address != null) {
                    addDataElement("resident_address", address)
                    added.add("resident_address")
                }

                // Transfer core fields that have counterparts in the mDL credential
                for ((nameItem, value) in coreData.asMap) {
                    val name = nameItem.asTstr
                    if (!added.contains(name) && mdocType.dataElements.contains(name)) {
                        addDataElement(name, value)
                        added.add(name)
                    }
                }

                if (!added.contains("portrait")) {
                    addDataElement(
                        dataElementName = "portrait",
                        value = Bstr(resources.getRawResource("female.jpg")!!.toByteArray())
                    )
                    added.add("portrait")
                }

                // Values derived from the birth_date
                addDataElement("age_in_years",
                    Uint(dateOfBirth.yearsUntil(now.toLocalDateTime(timeZone).date).toULong()))
                addDataElement("age_birth_year", Uint(dateOfBirth.year.toULong()))
                addDataElement("age_over_18", if (ageOver18) Simple.TRUE else Simple.FALSE)
                addDataElement( "age_over_21", if (ageOver21) Simple.TRUE else Simple.FALSE)

                // Add all mandatory elements for completeness if they are missing.
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
                signingCertificateChain.toDataItem()
            )
        )
        val encodedIssuerAuth = Cbor.encode(
            Cose.coseSign1Sign(
                signingKey,
                taggedEncodedMso,
                true,
                signingKey.publicKey.curve.defaultSigningAlgorithm,
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
        const val TAG = "CredentialFactoryMdl"
    }
}