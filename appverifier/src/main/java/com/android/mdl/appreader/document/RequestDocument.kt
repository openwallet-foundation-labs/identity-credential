package com.android.mdl.appreader.document

import java.io.Serializable


data class RequestDocument(
    val docType: String,
    var itemsToRequest: Map<String, Map<String, Boolean>>
) : Serializable
{
    companion object {
        const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        const val MDL_NAMESPACE = "org.iso.18013.5.1"
        const val MVR_DOCTYPE = "nl.rdw.mekb.1"
        const val MICOV_DOCTYPE = "org.micov.1"
        const val MICOV_VTR_NAMESPACE = "org.micov.vtr.1"
        const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
        const val EU_PID_DOCTYPE = "eu.europa.ec.eudiw.pid.1"
    }
}