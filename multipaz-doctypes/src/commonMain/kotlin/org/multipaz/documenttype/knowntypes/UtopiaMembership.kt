package org.multipaz.documenttype.knowntypes

import kotlinx.datetime.LocalDate
import org.multipaz.cbor.toDataItem
import org.multipaz.cbor.toDataItemFullDate
import org.multipaz.documenttype.DocumentAttributeType
import org.multipaz.documenttype.DocumentType
import org.multipaz.documenttype.Icon
import org.multipaz.util.fromBase64Url

/**
 * Object containing the metadata of the Utopia Wholesale Membership mdoc Document Type.
 */
object UtopiaMembership {
    const val DOCTYPE = "org.utopia.mdoc.membership"
    const val NAMESPACE = "org.utopia.mdoc.membership"

    /**
     * Build the Membership Document Type (mdoc only).
     */
    fun getDocumentType(): DocumentType {
        return DocumentType.Builder("Utopia Wholesale Membership")
            .addMdocDocumentType(DOCTYPE)

            // Core holder fields (mirrors how demo flows reuse PID data)
            .addMdocAttribute(
                DocumentAttributeType.String,
                "family_name",
                "Family Name",
                "Last name, surname, or primary identifier of the member.",
                true,
                NAMESPACE,
                Icon.PERSON,
                SampleData.FAMILY_NAME.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "given_name",
                "Given Names",
                "First name(s) or secondary identifier of the member.",
                true,
                NAMESPACE,
                Icon.PERSON,
                SampleData.GIVEN_NAME.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Picture,
                "portrait",
                "Photo of Holder",
                "Portrait image of the member.",
                true,
                NAMESPACE,
                Icon.ACCOUNT_BOX,
                SampleData.PORTRAIT_BASE64URL.fromBase64Url().toDataItem()
            )

            // Membership-specific fields
            .addMdocAttribute(
                DocumentAttributeType.String,
                "membership_number",
                "Membership Number",
                "Unique membership identifier assigned by the issuer.",
                true,
                NAMESPACE,
                Icon.NUMBERS,
                SampleData.MEMBERSHIP_NUMBER.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.Date,
                "issue_date",
                "Date of Issue",
                "Date when membership was issued.",
                true,
                NAMESPACE,
                Icon.DATE_RANGE,
                LocalDate.parse(SampleData.ISSUE_DATE).toDataItemFullDate()
            )
            .addMdocAttribute(
                DocumentAttributeType.Date,
                "expiry_date",
                "Date of Expiry",
                "Date when membership expires.",
                true,
                NAMESPACE,
                Icon.CALENDAR_CLOCK,
                LocalDate.parse(SampleData.EXPIRY_DATE).toDataItemFullDate()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "issuing_authority",
                "Issuing Authority",
                "Name of the membership issuing authority.",
                true,
                NAMESPACE,
                Icon.ACCOUNT_BALANCE,
                SampleData.ISSUING_AUTHORITY_MEMBERSHIP.toDataItem()
            )
            .addMdocAttribute(
                DocumentAttributeType.String,
                "membership_tier",
                "Membership Tier",
                "Membership level/tier (e.g., Bronze, Silver, Gold).",
                false,
                NAMESPACE,
                Icon.STARS,
                SampleData.MEMBERSHIP_TIER.toDataItem()
            )
            .build()
    }
}


