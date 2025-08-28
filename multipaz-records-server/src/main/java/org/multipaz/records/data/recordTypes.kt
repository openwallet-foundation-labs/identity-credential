package org.multipaz.records.data

import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.Icon
import org.multipaz.documenttype.knowntypes.Options

/**
 * Describes schema of the data stored in [Identity]. Record type with the identifier "core"
 * describes core data, other record types describe possible records.
 */
val recordTypes = RecordType.buildMap {
    addComplex("core") {
        displayName = "Core personal information"
        addString(
            identifier = "family_name",
            displayName = "Family Name",
            description = "Last name, surname, or primary identifier, of the person.",
            icon = Icon.PERSON,
        )
        addString(
            identifier = "given_name",
            displayName = "Given Names",
            description = "First name(s), other name(s), or secondary identifier, of the person",
            icon = Icon.PERSON,
        )
        addDate(
            identifier = "birth_date",
            displayName = "Date of Birth",
            description = "Day, month and year on which the person was born. If unknown, approximate date of birth",
            icon = Icon.TODAY,
        )
        addPicture(
            identifier = "portrait",
            displayName = "Photo of the person",
            description = "A reproduction of the person’s portrait.",
            icon = Icon.FACE,
        )
        addString(
            identifier = "utopia_id_number",
            displayName = "Utopia id number",
            description = "Unique and immutable number assigned to everyone by Utopia Registry",
            icon = Icon.ACCOUNT_BOX,
        )
    }
    addComplex("mDL") {
        displayName = "Driver's License"
        addDate(
            identifier = "issue_date",
            displayName = "Date of Issue",
            description = "Date when mDL was issued",
            icon = Icon.TODAY,
        )
        addDate(
            identifier = "expiry_date",
            displayName = "Date of Expiry",
            description = "Date when mDL expires",
            icon = Icon.TODAY,
        )
        addPrimitive(
            type = DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
            identifier = "issuing_country",
            displayName = "Issuing Country",
            description = "Alpha-2 country code, as defined in ISO 3166-1, of the issuing authority’s country or territory",
            icon = Icon.ACCOUNT_BALANCE,
        )
        addString(
            identifier = "issuing_authority",
            displayName = "Issuing Authority",
            description = "Issuing authority name.",
            icon = Icon.ACCOUNT_BALANCE,
        )
        addString(
            identifier = "document_number",
            displayName = "License Number",
            description = "The number assigned or calculated by the issuing authority.",
            icon = Icon.NUMBERS,
        )
    }
}