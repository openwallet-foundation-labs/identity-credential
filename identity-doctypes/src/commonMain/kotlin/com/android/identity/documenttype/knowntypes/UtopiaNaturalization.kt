package com.android.identity.documenttype.knowntypes

import com.android.identity.cbor.toDataItem
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.Icon

/**
 * Naturalization Certificate of the fictional State of Utopia.
 */
object UtopiaNaturalization {
    const val VCT = "http://utopia.example.com/vct/naturalization"

    /**
     * Build the Utopia Naturalization Certificate Document Type.
     */
    fun getDocumentType(): DocumentType {
        return DocumentType.Builder("Utopia Naturalization Certificate")
            .addVcDocumentType(VCT)
            .addVcAttribute(
                DocumentAttributeType.String,
                "family_name",
                "Family Name",
                "Current last name(s), surname(s), or primary identifier of the naturalized person",
                Icon.PERSON,
                SampleData.FAMILY_NAME.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "given_name",
                "Given Names",
                "Current first name(s), other name(s), or secondary identifier of the naturalized person",
                Icon.PERSON,
                SampleData.GIVEN_NAME.toDataItem()
            )
            .addVcAttribute(
                DocumentAttributeType.Date,
                "birth_date",
                "Date of Birth",
                "Day, month, and year on which the naturalized person was born. If unknown, approximate date of birth.",
                Icon.TODAY,
                SampleData.birthDate.toDataItemFullDate()
            )
            .addVcAttribute(
                DocumentAttributeType.Date,
                "naturalization_date",
                "Date of Naturalization",
                "Date (and possibly time) when the person was naturalized.",
                Icon.DATE_RANGE,
                SampleData.issueDate.toDataItemFullDate()
            )
            .addSampleRequest(
                id = "full",
                displayName = "All Data Elements",
                vcClaims = listOf()
            )
            .build()
    }
}