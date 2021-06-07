package com.ul.ims.gmdl.appholder.util

object DocumentData {
    const val DUMMY_CREDENTIAL_NAME = "dummyCredential"
    const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
    const val MDL_NAMESPACE = "org.iso.18013.5.1"
    const val AAMVA_NAMESPACE = "org.aamva.18013.5.1"

    enum class ErikaStaticData(val identifier: String, val value: String) {
        VISIBLE_NAME("visible_name", "E. Mustermann"),
        FAMILY_NAME("family_name", "Mustermann"),
        GIVEN_NAMES("given_name", "Erika"),
        ISSUING_COUNTRY("issuing_country", "US"),
        ISSUING_AUTHORITY("issuing_authority", "Google"),
        LICENSE_NUMBER("document_number", "5094962111")
    }
}