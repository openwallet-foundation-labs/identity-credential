package com.android.identity.documenttype.knowntypes

import com.android.identity.cbor.toDataItem
import com.android.identity.cbor.toDataItemFullDate
import com.android.identity.documenttype.DocumentAttributeType
import com.android.identity.documenttype.DocumentType
import com.android.identity.documenttype.Icon
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonPrimitive

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
                JsonPrimitive(SampleData.FAMILY_NAME)
            )
            .addVcAttribute(
                DocumentAttributeType.String,
                "given_name",
                "Given Names",
                "Current first name(s), other name(s), or secondary identifier of the naturalized person",
                Icon.PERSON,
                JsonPrimitive(SampleData.GIVEN_NAME)
            )
            .addVcAttribute(
                DocumentAttributeType.Date,
                "birth_date",
                "Date of Birth",
                "Day, month, and year on which the naturalized person was born. If unknown, approximate date of birth.",
                Icon.TODAY,
                JsonPrimitive(SampleData.BIRTH_DATE)
            )
            .addVcAttribute(
                DocumentAttributeType.Date,
                "naturalization_date",
                "Date of Naturalization",
                "Date (and possibly time) when the person was naturalized.",
                Icon.DATE_RANGE,
                JsonPrimitive(SampleData.ISSUE_DATE)
            )
            .addSampleRequest(
                id = "full",
                displayName = "All Data Elements",
                vcClaims = listOf()
            )
            .build()
    }
}