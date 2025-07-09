package org.multipaz.records.data

import org.multipaz.documenttype.DocumentAttribute
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.Icon
import org.multipaz.documenttype.knowntypes.Options

/**
 * Describes schema of the data stored in [Identity]. Record type with the identifier "core"
 * describes core data, other record types describe possible records.
 *
 * TODO: create DSL for more compact definition and define more records and core data fields.
 */
val recordTypes = listOf(
    RecordType(
        attribute = DocumentAttribute(
            type = DocumentAttributeType.ComplexType,
            identifier = "core",
            displayName = "Core personal information",
            description = "",
            icon = null,
            sampleValueJson = null,
            sampleValueMdoc = null
        ),
        subAttributes = listOf(
            RecordType(
                DocumentAttribute(
                    type = DocumentAttributeType.String,
                    identifier = "family_name",
                    displayName = "Family Name",
                    description = "Last name, surname, or primary identifier, of the person.",
                    icon = Icon.PERSON,
                    sampleValueMdoc = null,
                    sampleValueJson = null
                )
            ),
            RecordType(
                DocumentAttribute(
                    type = DocumentAttributeType.String,
                    identifier = "given_name",
                    displayName = "Given Names",
                    description = "First name(s), other name(s), or secondary identifier, of the person",
                    icon = Icon.PERSON,
                    sampleValueMdoc = null,
                    sampleValueJson = null
                )
            ),
            RecordType(
                DocumentAttribute(
                    type = DocumentAttributeType.Date,
                    identifier = "birth_date",
                    displayName = "Date of Birth",
                    description = "Day, month and year on which the person was born. If unknown, approximate date of birth",
                    icon = Icon.TODAY,
                    sampleValueMdoc = null,
                    sampleValueJson = null
                )
            ),
            RecordType(
                DocumentAttribute(
                    type = DocumentAttributeType.Picture,
                    identifier = "portrait",
                    displayName = "Photo of the person",
                    description = "A reproduction of the person’s portrait.",
                    icon = Icon.ACCOUNT_BOX,
                    sampleValueMdoc = null,
                    sampleValueJson = null
                )
            )
        ).associateBy { it.attribute.identifier }
    ),
    RecordType(
        attribute = DocumentAttribute(
            type = DocumentAttributeType.ComplexType,
            identifier = "mDL",
            displayName = "Driver's License",
            description = "",
            icon = null,
            sampleValueJson = null,
            sampleValueMdoc = null
        ),
        subAttributes = listOf(
            RecordType(
                DocumentAttribute(
                    DocumentAttributeType.Date,
                    identifier = "issue_date",
                    displayName = "Date of Issue",
                    description = "Date when mDL was issued",
                    icon = Icon.TODAY,
                    sampleValueMdoc = null,
                    sampleValueJson = null
                )
            ),
            RecordType(
                DocumentAttribute(
                    type = DocumentAttributeType.Date,
                    identifier = "expiry_date",
                    displayName = "Date of Expiry",
                    description = "Date when mDL expires",
                    icon = Icon.TODAY,
                    sampleValueMdoc = null,
                    sampleValueJson = null
                )
            ),
            RecordType(
                DocumentAttribute(
                    type = DocumentAttributeType.StringOptions(Options.COUNTRY_ISO_3166_1_ALPHA_2),
                    identifier = "issuing_country",
                    displayName = "Issuing Country",
                    description = "Alpha-2 country code, as defined in ISO 3166-1, of the issuing authority’s country or territory",
                    icon = Icon.ACCOUNT_BALANCE,
                    sampleValueMdoc = null,
                    sampleValueJson = null
                )
            ),
            RecordType(
                DocumentAttribute(
                    type = DocumentAttributeType.String,
                    identifier = "issuing_authority",
                    displayName = "Issuing Authority",
                    description = "Issuing authority name.",
                    icon = Icon.ACCOUNT_BALANCE,
                    sampleValueMdoc = null,
                    sampleValueJson = null
                )
            ),
            RecordType(
                DocumentAttribute(
                    type = DocumentAttributeType.String,
                    identifier = "document_number",
                    displayName = "License Number",
                    description = "The number assigned or calculated by the issuing authority.",
                    icon = Icon.NUMBERS,
                    sampleValueMdoc = null,
                    sampleValueJson = null
                )
            )
        ).associateBy { it.attribute.identifier }
    )
).associateBy { it.attribute.identifier }