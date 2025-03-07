package org.multipaz.documenttype

import org.multipaz.cbor.Bstr
import org.multipaz.cbor.CborArray
import org.multipaz.cbor.CborMap
import org.multipaz.cbor.Simple
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.Tstr
import org.multipaz.cbor.Uint
import org.multipaz.cbor.toDataItemDateTimeString
import org.multipaz.cbor.toDataItemFullDate
import org.multipaz.documenttype.knowntypes.DrivingLicense
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class TestDocumentTypeRepository {

    @Test
    fun testDocumentTypeRepositoryDrivingLicense() {
        val documentTypeRepository = DocumentTypeRepository()
        documentTypeRepository.addDocumentType(DrivingLicense.getDocumentType())
        val documentTypes = documentTypeRepository.documentTypes
        assertEquals(1, documentTypes.count())
        assertEquals("Driving License", documentTypes[0].displayName)
        assertEquals("org.iso.18013.5.1.mDL", documentTypes[0].mdocDocumentType?.docType)
        assertEquals(
            "org.iso.18013.5.1",
            documentTypes[0].mdocDocumentType?.namespaces?.iterator()?.next()?.key
        )
        assertEquals(
            "org.iso.18013.5.1",
            documentTypes[0].mdocDocumentType?.namespaces?.iterator()?.next()?.value?.namespace
        )
        assertEquals(
            DocumentAttributeType.String,
            documentTypes[0].mdocDocumentType?.namespaces?.get("org.iso.18013.5.1")?.dataElements?.get("family_name")?.attribute?.type
        )
        assertEquals(
            "org.iso.18013.5.1.aamva",
            documentTypes[0].mdocDocumentType?.namespaces?.values?.toList()?.last()?.namespace
        )
        assertEquals(
            DocumentAttributeType.ComplexType,
            documentTypes[0].mdocDocumentType?.namespaces?.get("org.iso.18013.5.1.aamva")?.dataElements?.get("domestic_driving_privileges")?.attribute?.type
        )
    }

    @Test
    fun testRenderValueAsString() {
        val ct = DrivingLicense.getDocumentType()
        val mdlNs = ct.mdocDocumentType!!.namespaces[DrivingLicense.MDL_NAMESPACE]!!
        val aamvaNs = ct.mdocDocumentType!!.namespaces[DrivingLicense.AAMVA_NAMESPACE]!!

        // CredentialAttributeType.Boolean
        assertEquals(
            "true",
            mdlNs.dataElements["age_over_18"]?.renderValue(Simple.TRUE)
        )
        assertEquals(
            "false",
            mdlNs.dataElements["age_over_18"]?.renderValue(Simple.FALSE)
        )
        assertEquals(
            "yes",
            mdlNs.dataElements["age_over_18"]?.renderValue(
                Simple.TRUE, trueFalseStrings = Pair("no", "yes"))
        )
        assertEquals(
            "no",
            mdlNs.dataElements["age_over_18"]?.renderValue(
                Simple.FALSE, trueFalseStrings = Pair("no", "yes"))
        )

        // CredentialAttributeType.String
        assertEquals(
            "Erika",
            mdlNs.dataElements["given_name"]?.renderValue(Tstr("Erika"))
        )

        // CredentialAttributeType.Number
        assertEquals(
            "180",
            mdlNs.dataElements["height"]?.renderValue(Uint(180UL))
        )

        // CredentialAttributeType.IntegerOptions
        assertEquals(
            "Donor",
            aamvaNs.dataElements["organ_donor"]?.renderValue(Uint(1UL))
        )
        // If we don't know the enumerated value, check we render the raw value.
        assertEquals(
            "2",
            aamvaNs.dataElements["organ_donor"]?.renderValue(Uint(2UL))
        )

        // CredentialAttributeType.StringOptions
        assertEquals(
            "Asian or Pacific Islander",
            aamvaNs.dataElements["race_ethnicity"]?.renderValue(Tstr("AP"))
        )
        // If we don't know the enumerated value, check we render the raw value.
        assertEquals(
            "AddedLater",
            aamvaNs.dataElements["race_ethnicity"]?.renderValue(Tstr("AddedLater"))
        )

        // CredentialAttributeType.Picture
        assertEquals(
            "0 bytes",
            mdlNs.dataElements["portrait"]?.renderValue(Bstr(byteArrayOf()))
        )
        assertEquals(
            "1 byte",
            mdlNs.dataElements["portrait"]?.renderValue(Bstr(byteArrayOf(1)))
        )
        assertEquals(
            "3 bytes",
            mdlNs.dataElements["portrait"]?.renderValue(Bstr(byteArrayOf(1, 2, 3)))
        )

        // CredentialAttributeType.DateTime - supports both tdate, full-date, and ISO/IEC 23220-2 maps
        assertEquals(
            "1976-02-03T06:30:00",
            MdocDataElement(
                DocumentAttribute(DocumentAttributeType.DateTime, "", "", "", null, null, null),
                false
            ).renderValue(
                Instant.parse("1976-02-03T05:30:00Z").toDataItemDateTimeString(),
                timeZone = TimeZone.of("Europe/Copenhagen")
            )
        )
        assertEquals(
            "1976-02-03T06:30:00",
            MdocDataElement(
                DocumentAttribute(DocumentAttributeType.DateTime, "", "", "", null, null, null),
                false
            ).renderValue(
                CborMap.builder()
                    .put("birth_date", Instant.parse("1976-02-03T05:30:00Z").toDataItemDateTimeString())
                    .end().build(),
                timeZone = TimeZone.of("Europe/Copenhagen")
            )
        )
        // ... if using a full-date we render the point in time as midnight. The timezone
        // isn't taken into account, check a couple of different timezones
        for (zoneId in listOf("Europe/Copenhagen", "Australia/Brisbane", "Pacific/Honolulu")) {
            assertEquals(
                "1976-02-03T00:00:00",
                MdocDataElement(
                    DocumentAttribute(DocumentAttributeType.DateTime, "", "", "", null, null, null),
                    false
                ).renderValue(
                    LocalDate.parse("1976-02-03").toDataItemFullDate(),
                    timeZone = TimeZone.of(zoneId)
                )
            )
            assertEquals(
                "1976-02-03T00:00:00",
                MdocDataElement(
                    DocumentAttribute(DocumentAttributeType.DateTime, "", "", "", null, null, null),
                    false
                ).renderValue(
                    CborMap.builder()
                        .put("birth_date", LocalDate.parse("1976-02-03").toDataItemFullDate())
                        .end().build(),
                    timeZone = TimeZone.of(zoneId)
                )
            )
        }

        // CredentialAttributeType.Date - supports both tdate, full-date, and ISO/IEC 23220-2 maps
        // for tdate the timezone is taken into account, for full-date it isn't.
        assertEquals(
            "1976-02-03",
            mdlNs.dataElements["birth_date"]?.renderValue(
                Instant.parse("1976-02-03T05:30:00Z").toDataItemDateTimeString(),
                timeZone = TimeZone.of("Europe/Copenhagen")
            )
        )
        assertEquals(
            "1976-02-02",
            mdlNs.dataElements["birth_date"]?.renderValue(
                Instant.parse("1976-02-03T05:30:00Z").toDataItemDateTimeString(),
                timeZone = TimeZone.of("America/Los_Angeles")
            )
        )
        assertEquals(
            "1976-02-03",
            mdlNs.dataElements["birth_date"]?.renderValue(
                LocalDate.parse("1976-02-03").toDataItemFullDate(),
                timeZone = TimeZone.of("Europe/Copenhagen")
            )
        )
        assertEquals(
            "1976-02-03",
            mdlNs.dataElements["birth_date"]?.renderValue(
                LocalDate.parse("1976-02-03").toDataItemFullDate(),
                timeZone = TimeZone.of("America/Los_Angeles")
            )
        )
        assertEquals(
            "1976-02-03",
            mdlNs.dataElements["birth_date"]?.renderValue(
                CborMap.builder()
                    .put("birth_date", Instant.parse("1976-02-03T05:30:00Z").toDataItemDateTimeString())
                    .end().build(),
                timeZone = TimeZone.of("America/New_York")
            )
        )
        assertEquals(
            "1976-02-03",
            mdlNs.dataElements["birth_date"]?.renderValue(
                CborMap.builder()
                    .put("birth_date", LocalDate.parse("1976-02-03").toDataItemFullDate())
                    .end().build(),
                timeZone = TimeZone.of("America/New_York")
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
        assertEquals(
            "[{\"vehicle_category_code\": \"A\", \"issue_date\": 1004(\"2018-08-09\"), " +
                    "\"expiry_date\": 1004(\"2024-10-20\")}, {\"vehicle_category_code\": \"B\", " +
                    "\"issue_date\": 1004(\"2017-02-23\"), \"expiry_date\": 1004(\"2024-10-20\")}]",
            mdlNs.dataElements["driving_privileges"]?.renderValue(drivingPrivileges)
        )


        // Now check that it does the right thing if a value is passed which
        // isn't expected by the document type
        assertEquals(
            "3 bytes",
            mdlNs.dataElements["portrait"]?.renderValue(Bstr(byteArrayOf(1, 2, 3)))
        )

    }

}