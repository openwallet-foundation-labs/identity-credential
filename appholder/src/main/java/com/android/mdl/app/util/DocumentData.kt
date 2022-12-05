package com.android.mdl.app.util

object DocumentData {
    const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
    const val MDL_NAMESPACE = "org.iso.18013.5.1"
    const val MVR_DOCTYPE = "nl.rdw.mekb.1"
    const val MVR_NAMESPACE = "nl.rdw.mekb.1"
    const val MICOV_DOCTYPE = "org.micov.1"
    const val MICOV_VTR_NAMESPACE = "org.micov.vtr.1"
    const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
    const val AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva"
    const val EU_PID_DOCTYPE = "eu.europa.ec.eudiw.pid.1"
    const val EU_PID_NAMESPACE = "eu.europa.ec.eudiw.pid.1"

    enum class ErikaStaticData(val identifier: String, val value: String) {
        VISIBLE_NAME("visible_name", "Driving License"),
    }

    enum class MekbStaticData(val identifier: String, val value: String) {
        VISIBLE_NAME("visible_name", "Vehicle Registration"),
    }

    enum class MicovStaticData(val identifier: String, val value: String) {
        VISIBLE_NAME("visible_name", "Vaccination Document"),
    }
}