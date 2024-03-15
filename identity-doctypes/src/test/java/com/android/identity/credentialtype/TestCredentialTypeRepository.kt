package com.android.identity.credentialtype

import com.android.identity.cbor.Bstr
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.Simple
import com.android.identity.cbor.Tagged
import com.android.identity.cbor.Tstr
import com.android.identity.cbor.Uint
import com.android.identity.cbor.toDataItemDateTimeString
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert
import org.junit.Test

class TestCredentialTypeRepository {

    @Test
    fun testCredentialTypeRepositoryDrivingLicense() {
        val credentialTypeRepository = CredentialTypeRepository()
        credentialTypeRepository.addCredentialType(DrivingLicense.getCredentialType())
        val credentialTypes = credentialTypeRepository.credentialTypes
        assert(credentialTypes.count() == 1)
        assert(credentialTypes[0].displayName == "Driving License")
        assert(credentialTypes[0].mdocCredentialType?.docType == "org.iso.18013.5.1.mDL")
        assert(credentialTypes[0].vcCredentialType?.type == "Iso18013DriversLicenseCredential")
        assert(
            credentialTypes[0].mdocCredentialType?.namespaces?.iterator()
                ?.next()?.key == "org.iso.18013.5.1"
        )
        assert(
            credentialTypes[0].mdocCredentialType?.namespaces?.iterator()
                ?.next()?.value?.namespace == "org.iso.18013.5.1"
        )
        assert(
            credentialTypes[0].mdocCredentialType?.namespaces?.get("org.iso.18013.5.1")?.dataElements?.get(
                "family_name"
            )?.attribute?.type == CredentialAttributeType.String
        )
        assert(
            credentialTypes[0].mdocCredentialType?.namespaces?.values?.toList()
                ?.last()?.namespace == "org.iso.18013.5.1.aamva"
        )
        assert(
            credentialTypes[0].mdocCredentialType?.namespaces?.get("org.iso.18013.5.1.aamva")?.dataElements?.get(
                "domestic_driving_privileges"
            )?.attribute?.type == CredentialAttributeType.ComplexType
        )
    }

    @Test
    fun testRenderValueAsString() {
        val ct = DrivingLicense.getCredentialType()
        val mdlNs = ct.mdocCredentialType!!.namespaces[DrivingLicense.MDL_NAMESPACE]!!
        val aamvaNs = ct.mdocCredentialType!!.namespaces[DrivingLicense.AAMVA_NAMESPACE]!!

        // CredentialAttributeType.Boolean
        Assert.assertEquals(
            "true",
            mdlNs.dataElements["age_over_18"]?.renderValue(Simple.TRUE)
        )
        Assert.assertEquals(
            "false",
            mdlNs.dataElements["age_over_18"]?.renderValue(Simple.FALSE)
        )
        Assert.assertEquals(
            "yes",
            mdlNs.dataElements["age_over_18"]?.renderValue(
                Simple.TRUE, trueFalseStrings = Pair("no", "yes"))
        )
        Assert.assertEquals(
            "no",
            mdlNs.dataElements["age_over_18"]?.renderValue(
                Simple.FALSE, trueFalseStrings = Pair("no", "yes"))
        )

        // CredentialAttributeType.String
        Assert.assertEquals(
            "Erika",
            mdlNs.dataElements["given_name"]?.renderValue(Tstr("Erika"))
        )

        // CredentialAttributeType.Number
        Assert.assertEquals(
            "180",
            mdlNs.dataElements["height"]?.renderValue(Uint(180UL))
        )

        // CredentialAttributeType.IntegerOptions
        Assert.assertEquals(
            "Donor",
            aamvaNs.dataElements["organ_donor"]?.renderValue(Uint(1UL))
        )
        // If we don't know the enumerated value, check we render the raw value.
        Assert.assertEquals(
            "2",
            aamvaNs.dataElements["organ_donor"]?.renderValue(Uint(2UL))
        )

        // CredentialAttributeType.StringOptions
        Assert.assertEquals(
            "Asian or Pacific Islander",
            aamvaNs.dataElements["race_ethnicity"]?.renderValue(Tstr("AP"))
        )
        // If we don't know the enumerated value, check we render the raw value.
        Assert.assertEquals(
            "AddedLater",
            aamvaNs.dataElements["race_ethnicity"]?.renderValue(Tstr("AddedLater"))
        )

        // CredentialAttributeType.Picture
        Assert.assertEquals(
            "0 bytes",
            mdlNs.dataElements["portrait"]?.renderValue(Bstr(byteArrayOf()))
        )
        Assert.assertEquals(
            "1 byte",
            mdlNs.dataElements["portrait"]?.renderValue(Bstr(byteArrayOf(1)))
        )
        Assert.assertEquals(
            "3 bytes",
            mdlNs.dataElements["portrait"]?.renderValue(Bstr(byteArrayOf(1, 2, 3)))
        )

        // CredentialAttributeType.DateTime - supports both tdate and full-date
        Assert.assertEquals(
            "1976-02-03 06:30:00",
            MdocDataElement(
                CredentialAttribute(
                    CredentialAttributeType.DateTime,
                    "",
                    "",
                    "",
                    null
                ),
                false
            ).renderValue(
                Instant.parse("1976-02-03T05:30:00Z").toDataItemDateTimeString,
                timeZone = TimeZone.of("Europe/Copenhagen")
            )
        )
        // ... if using a full-date we render the point in time as midnight. The timezone
        // isn't taken into account, check a couple of different timezones
        for (zoneId in listOf("Europe/Copenhagen", "Australia/Brisbane", "Pacific/Honolulu")) {
            Assert.assertEquals(
                "1976-02-03 00:00:00",
                MdocDataElement(
                    CredentialAttribute(
                        CredentialAttributeType.DateTime,
                        "",
                        "",
                        "",
                        null
                    ),
                    false
                ).renderValue(
                    LocalDate.parse("1976-02-03").toDataItemFullDate,
                    timeZone = TimeZone.of(zoneId)
                )
            )
        }

        // CredentialAttributeType.Date - supports both tdate and full-date... for tdate
        // the timezone is taken into account, for full-date it isn't.
        Assert.assertEquals(
            "1976-02-03",
            mdlNs.dataElements["birth_date"]?.renderValue(
                Instant.parse("1976-02-03T05:30:00Z").toDataItemDateTimeString,
                timeZone = TimeZone.of("Europe/Copenhagen")
            )
        )
        Assert.assertEquals(
            "1976-02-02",
            mdlNs.dataElements["birth_date"]?.renderValue(
                Instant.parse("1976-02-03T05:30:00Z").toDataItemDateTimeString,
                timeZone = TimeZone.of("America/Los_Angeles")
            )
        )
        Assert.assertEquals(
            "1976-02-03",
            mdlNs.dataElements["birth_date"]?.renderValue(
                LocalDate.parse("1976-02-03").toDataItemFullDate,
                timeZone = TimeZone.of("Europe/Copenhagen")
            )
        )
        Assert.assertEquals(
            "1976-02-03",
            mdlNs.dataElements["birth_date"]?.renderValue(
                LocalDate.parse("1976-02-03").toDataItemFullDate,
                timeZone = TimeZone.of("America/Los_Angeles")
            )
        )

        // CredentialAttributeType.ComplexType
        val drivingPrivileges = CborArray.builder()
            .addMap()
            .put("vehicle_category_code", "A")
            .put("issue_date", Tagged(1004, Tstr("2018-08-09")))
            .put("expiry_date", Tagged(1004, Tstr("2024-10-20")))
            .end()
            .addMap()
            .put("vehicle_category_code", "B")
            .put("issue_date", Tagged(1004, Tstr("2017-02-23")))
            .put("expiry_date", Tagged(1004, Tstr("2024-10-20")))
            .end()
            .end().build()
        // Note, this isn't very nice but it's not clear we can do any better at this point,
        // fitting complex stuff like this into a string is going to be a mess no matter how
        // you slice or dice it.
        //
        // We could add the ability to add dedicated renderers at credential type registration
        // time and those could return rich text, e.g. Markup. It's not clear that this would
        // be an advantage of the app doing this itself. Maybe...
        //
        Assert.assertEquals(
            "[{\"vehicle_category_code\": \"A\", \"issue_date\": 1004(\"2018-08-09\"), " +
                    "\"expiry_date\": 1004(\"2024-10-20\")}, {\"vehicle_category_code\": \"B\", " +
                    "\"issue_date\": 1004(\"2017-02-23\"), \"expiry_date\": 1004(\"2024-10-20\")}]",
            mdlNs.dataElements["driving_privileges"]?.renderValue(drivingPrivileges)
        )


        // Now check that it does the right thing if a value is passed which
        // isn't expected by the document type
        Assert.assertEquals(
            "3 bytes",
            mdlNs.dataElements["portrait"]?.renderValue(Bstr(byteArrayOf(1, 2, 3)))
        )

    }

}