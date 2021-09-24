package com.android.mdl.app.util

object DocumentData {
    const val DUMMY_CREDENTIAL_NAME = "dummyCredential"
    const val DUMMY_MVR_CREDENTIAL_NAME = "dummyCredentialMVR"
    const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
    const val MDL_NAMESPACE = "org.iso.18013.5.1"
    const val MVR_DOCTYPE = "nl.rdw.mekb.1"
    const val MVR_NAMESPACE = "nl.rdw.mekb.1"
    const val AAMVA_NAMESPACE = "org.aamva.18013.5.1"

    enum class ErikaStaticData(val identifier: String, val value: String) {
        VISIBLE_NAME("visible_name", "E. Mustermann"),
    }

    enum class MekbStaticData(val identifier: String, val value: String) {
        VISIBLE_NAME("visible_name", "MEKB 1"),
    }
}