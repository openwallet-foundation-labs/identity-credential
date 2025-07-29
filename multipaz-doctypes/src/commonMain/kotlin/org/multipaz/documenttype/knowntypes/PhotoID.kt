package org.multipaz.documenttype.knowntypes

import org.multipaz.cbor.CborMap
import org.multipaz.cbor.toDataItem
import org.multipaz.cbor.toDataItemFullDate
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentType
import org.multipaz.documenttype.Icon
import org.multipaz.documenttype.knowntypes.EUPersonalID.EUPID_VCT
import org.multipaz.util.fromBase64Url
import kotlinx.datetime.LocalDate
import org.multipaz.cbor.buildCborMap

/**
 * PhotoID according to ISO/IEC TS 23220-4 (E) operational phase - Annex C Photo ID v2
 * 2024-08-14" (WG4/N4583)
 */
object PhotoID {
    const val PHOTO_ID_DOCTYPE = "org.iso.23220.photoID.1"
    const val ISO_23220_2_NAMESPACE = "org.iso.23220.1"
    const val PHOTO_ID_NAMESPACE = "org.iso.23220.photoID.1"
    const val DTC_NAMESPACE = "org.iso.23220.dtc.1"

    /**
     * Build the Driving License Document Type.
     */
    fun getDocumentType(): DocumentType {
        return DocumentType.Builder("Photo ID")
            .addMdocDocumentType(PHOTO_ID_DOCTYPE)
            // First the data elements from ISO/IEC 23220-2.
            //
            .addMdocAttribute(
                DocumentAttributeType.String,
                "family_name_unicode",
                "Family Name",
                "Last name, surname, or primary identifier, of the document holder",
                true,
                ISO_23220_2_NAMESPACE,
                Icon.PERSON,
                SampleData.FAMILY_NAME.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "given_name_unicode",
                "Given Names",
                "First name(s), other name(s), or secondary identifier, of the document holder",
                true,
                ISO_23220_2_NAMESPACE,
                Icon.PERSON,
                SampleData.GIVEN_NAME.toDataItem()
            )
            // Note, this is more complicated than mDL and EU PID, according to ISO/IEC 23220-2
            // clause "6.3.1.1.3 Date of birth as either uncertain or approximate, or both"
            //
            // If date of birth includes an unknown part, the following birth_date structure may be used.
            // birth date = {
            //   "birth_date" : full-date,
            //   ? "approximate_mask": tstr
            // }
            // Approximate_mask is an 8 digit flag to denote the location of the mask in YYYYMMDD
            // format. 1 denotes mask.
            //
            // NOTE “approximate mask” is not intended to be used for calculation.
            //
            .addMdocAttribute(
                DocumentAttributeType.Date,   // TODO: this is a more complex type
                "birth_date",
                "Date of Birth",
                "Day, month and year on which the document holder was born. If unknown, approximate date of birth",
                true,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                buildCborMap {
                    put("birth_date", LocalDate.parse(SampleData.BIRTH_DATE).toDataItemFullDate())
                }
            )
            .addMdocAttribute(
                DocumentAttributeType.Picture,
                "portrait",
                "Photo of Holder",
                "A reproduction of the document holder’s portrait.",
                true,
                ISO_23220_2_NAMESPACE,
                Icon.ACCOUNT_BOX,
                SampleData.PORTRAIT_BASE64URL.fromBase64Url().toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Date,
                "issue_date",
                "Date of Issue",
                "Date when document was issued",
                true,
                ISO_23220_2_NAMESPACE,
                Icon.DATE_RANGE,
                LocalDate.parse(SampleData.ISSUE_DATE).toDataItemFullDate()
            )
            .addMdocAttribute(
                DocumentAttributeType.Date,
                "expiry_date",
                "Date of Expiry",
                "Date when document expires",
                true,
                ISO_23220_2_NAMESPACE,
                Icon.CALENDAR_CLOCK,
                LocalDate.parse(SampleData.EXPIRY_DATE).toDataItemFullDate()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "issuing_authority_unicode",
                "Issuing Authority",
                "Issuing authority name.",
                true,
                ISO_23220_2_NAMESPACE,
                Icon.ACCOUNT_BALANCE,
                SampleData.ISSUING_AUTHORITY_PHOTO_ID.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "issuing_country",
                "Issuing Country",
                "Alpha-2 country code, as defined in ISO 3166-1, of the issuing authority’s country or territory",
                true,
                ISO_23220_2_NAMESPACE,
                Icon.ACCOUNT_BALANCE,
                SampleData.ISSUING_COUNTRY.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Number,
                "age_in_years",
                "Age in Years",
                "The age of the document holder",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_IN_YEARS.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "age_over_13",
                "Older Than 13 Years",
                "Indication whether the document holder is as old or older than 13",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_13.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "age_over_16",
                "Older Than 16 Years",
                "Indication whether the document holder is as old or older than 16",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_16.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "age_over_18",
                "Older Than 18 Years",
                "Indication whether the document holder is as old or older than 18",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_18.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "age_over_21",
                "Older Than 21 Years",
                "Indication whether the document holder is as old or older than 21",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_21.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "age_over_25",
                "Older Than 25 Years",
                "Indication whether the document holder is as old or older than 25",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_25.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "age_over_60",
                "Older Than 60 Years",
                "Indication whether the document holder is as old or older than 60",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_60.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "age_over_62",
                "Older Than 62 Years",
                "Indication whether the document holder is as old or older than 62",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_62.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "age_over_65",
                "Older Than 65 Years",
                "Indication whether the document holder is as old or older than 65",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_65.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Boolean,
                "age_over_68",
                "Older Than 68 Years",
                "Indication whether the document holder is as old or older than 68",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_OVER_68.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Number,
                "age_birth_year",
                "Year of Birth",
                "The year when the document holder was born",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                SampleData.AGE_BIRTH_YEAR.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Date,
                "portrait_capture_date",
                "Portrait Image Timestamp",
                "Date when portrait was taken",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.TODAY,
                LocalDate.parse(SampleData.PORTRAIT_CAPTURE_DATE).toDataItemFullDate()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "birthplace",
                "Place of Birth",
                "Country and municipality or state/province where the document holder was born",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.PLACE,
                SampleData.BIRTH_PLACE.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "name_at_birth",
                "Name at Birth",
                "The name(s) which holder was born.",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.PERSON,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "resident_address_unicode",
                "Resident Address",
                "The place where the document holder resides and/or may be contacted (street/house number, municipality etc.)",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.PLACE,
                SampleData.RESIDENT_ADDRESS.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "resident_city_unicode",
                "Resident City",
                "The city where the document holder lives",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.PLACE,
                SampleData.RESIDENT_CITY.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "resident_postal_code",
                "Resident Postal Code",
                "The postal code of the document holder",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.PLACE,
                SampleData.RESIDENT_POSTAL_CODE.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "resident_country",
                "Resident Country",
                "The country where the document holder lives",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.PLACE,
                SampleData.RESIDENT_COUNTRY.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "resident_city_latin1",
                "Resident City",
                "The city where the document holder lives, in Latin 1 characters",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.PLACE,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.IntegerOptions(Options.SEX_ISO_IEC_5218),
                "sex",
                "Sex",
                "document holder’s sex",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.EMERGENCY,
                SampleData.SEX_ISO218.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "nationality",
                "Nationality",
                "Nationality of the document holder",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.LANGUAGE,
                SampleData.NATIONALITY.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "document_number",
                "License Number",
                "The number assigned or calculated by the issuing authority.",
                true,
                ISO_23220_2_NAMESPACE,
                Icon.NUMBERS,
                SampleData.DOCUMENT_NUMBER.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "issuing_subdivision",
                "Issuing Subdivision",
                "Subdivision code as defined in ISO 3166-2, which issued " +
                        "the mobile eID document or within which the issuing " +
                        "authority is located.",
                false,
                ISO_23220_2_NAMESPACE,
                Icon.ACCOUNT_BALANCE,
                SampleData.ISSUING_JURISDICTION.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "family_name_latin1",
                "Family Name (Latin 1)",
                "Last name, surname, or primary identifier, of the document holder. In Latin 1",
                true,
                ISO_23220_2_NAMESPACE,
                Icon.PERSON,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "given_name_latin1",
                "Given Names (Latin 1)",
                "First name(s), other name(s), or secondary identifier, of the document holder. In Latin 1",
                true,
                ISO_23220_2_NAMESPACE,
                Icon.PERSON,
                null
            )

            // Then the PhotoID specific data elements.
            //
            .addMdocAttribute(
                DocumentAttributeType.String,
                "person_id",
                "Person ID",
                "Person identifier of the Photo ID holder.",
                false,
                PHOTO_ID_NAMESPACE,
                Icon.NUMBERS,
                SampleData.PERSON_ID.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                "birth_country",
                "Birth Country",
                "The country where the Photo ID holder was born, as an " +
                        "Alpha-2 country code as specified in ISO 3166-1.",
                false,
                PHOTO_ID_NAMESPACE,
                Icon.PLACE,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "birth_state",
                "Birth State",
                "The state, province, district, or local area where the " +
                        "Photo ID holder was born.",
                false,
                PHOTO_ID_NAMESPACE,
                Icon.PLACE,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "birth_city",
                "Birth City",
                "The municipality, city, town, or village where the Photo " +
                        "ID holder was born.",
                false,
                PHOTO_ID_NAMESPACE,
                Icon.PLACE,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "administrative_number",
                "Administrative Number",
                "A number assigned by the Photo ID issuer for audit " +
                        "control or other purposes.",
                false,
                PHOTO_ID_NAMESPACE,
                Icon.NUMBERS,
                SampleData.ADMINISTRATIVE_NUMBER.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "resident_street",
                "Resident Street",
                "The name of the street where the Photo ID holder " +
                        "currently resides.",
                false,
                PHOTO_ID_NAMESPACE,
                Icon.PLACE,
                SampleData.RESIDENT_STREET.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "resident_house_number",
                "Resident House Number",
                "The house number where the Photo ID holder currently " +
                        "resides, including any affix or suffix.",
                false,
                PHOTO_ID_NAMESPACE,
                Icon.PLACE,
                SampleData.RESIDENT_HOUSE_NUMBER.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "travel_document_number",
                "Travel Document Number",
                "The number of the travel document to which the Photo " +
                        "ID is associated (if associated to or derived from a travel " +
                        "document).",
                false,
                PHOTO_ID_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "resident_state",
                "Resident State",
                "The state/province/district where the Photo ID holder lives.",
                false,
                PHOTO_ID_NAMESPACE,
                Icon.PLACE,
                SampleData.RESIDENT_STATE.toDataItem()
            )

            // DTC namespace
            //
            .addMdocAttribute(
                DocumentAttributeType.String,
                "dtc_version",
                "DTC-VC version",
                "Version of the DTC-VC definition",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_sod",
                "eMRTD SOD",
                "Binary data of the eMRTD Document Security Object",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg1",
                "eMRTD DG1",
                "Binary data of the eMRTD Data Group 1",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg2",
                "eMRTD DG2",
                "Binary data of the eMRTD Data Group 2",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg3",
                "eMRTD DG3",
                "Binary data of the eMRTD Data Group 3",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg4",
                "eMRTD DG4",
                "Binary data of the eMRTD Data Group 4",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg5",
                "eMRTD DG5",
                "Binary data of the eMRTD Data Group 5",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg6",
                "eMRTD DG6",
                "Binary data of the eMRTD Data Group 6",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg7",
                "eMRTD DG7",
                "Binary data of the eMRTD Data Group 7",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg8",
                "eMRTD DG8",
                "Binary data of the eMRTD Data Group 8",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg9",
                "eMRTD DG9",
                "Binary data of the eMRTD Data Group 9",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg10",
                "eMRTD DG10",
                "Binary data of the eMRTD Data Group 10",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg11",
                "eMRTD DG11",
                "Binary data of the eMRTD Data Group 11",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg12",
                "eMRTD DG12",
                "Binary data of the eMRTD Data Group 12",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg13",
                "eMRTD DG13",
                "Binary data of the eMRTD Data Group 13",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg14",
                "eMRTD DG14",
                "Binary data of the eMRTD Data Group 14",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg15",
                "eMRTD DG15",
                "Binary data of the eMRTD Data Group 15",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dtc_dg16",
                "eMRTD DG16",
                "Binary data of the eMRTD Data Group 16",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )
            .addMdocAttribute(
                DocumentAttributeType.Blob,
                "dg_content_info",
                "eMRTD Content Info",
                "Binary data of the DTCContentInfo",
                false,
                DTC_NAMESPACE,
                Icon.NUMBERS,
                null
            )

            // Finally for the sample requests.
            //
            .addSampleRequest(
                id = "age_over_18",
                displayName ="Age Over 18",
                mdocDataElements = mapOf(
                    ISO_23220_2_NAMESPACE to mapOf(
                        "age_over_18" to false,
                    )
                ),
            )
            .addSampleRequest(
                id = "age_over_18_zkp",
                displayName ="Age Over 18 (ZKP)",
                mdocDataElements = mapOf(
                    ISO_23220_2_NAMESPACE to mapOf(
                        "age_over_18" to false,
                    )
                ),
                mdocUseZkp = true
            )
            .addSampleRequest(
                id = "age_over_18_and_portrait",
                displayName ="Age Over 18 + Portrait",
                mdocDataElements = mapOf(
                    ISO_23220_2_NAMESPACE to mapOf(
                        "age_over_18" to false,
                        "portrait" to false
                    )
                ),
            )
            .addSampleRequest(
                id = "mandatory",
                displayName = "Mandatory Data Elements",
                mdocDataElements = mapOf(
                    ISO_23220_2_NAMESPACE to mapOf(
                            "family_name_unicode" to false,
                            "given_name_unicode" to false,
                            "birth_date" to false,
                            "portrait" to false,
                            "issue_date" to false,
                            "expiry_date" to false,
                            "issuing_authority_unicode" to false,
                            "issuing_country" to false,
                            "age_over_18" to false,
                    )
                )
            )
            .addSampleRequest(
                id = "full",
                displayName ="All Data Elements",
                mdocDataElements = mapOf(
                    ISO_23220_2_NAMESPACE to mapOf(),
                    PHOTO_ID_NAMESPACE to mapOf(),
                    DTC_NAMESPACE to mapOf()
                )
            )
            .build()
    }
}
