package com.android.identity.android.mdoc.document

/**
 * Enumeration with the official names of the namespaces as specified by the mDoc standard
 */
enum class Namespace(val value: String) {

    /**
     * eu.europa.ec.eudiw.pid.1
     */
    EUPID("eu.europa.ec.eudiw.pid.1"),

    /**
     * org.iso.18013.5.1
     */
    MDL("org.iso.18013.5.1"),

    /**
     * org.iso.18013.5.1.aamva
     */
    MDL_AAMVA("org.iso.18013.5.1.aamva"),

    /**
     * org.micov.attestation.1
     */
    MICOV_ATT("org.micov.attestation.1"),

    /**
     * org.micov.vtr.1
     */
    MICOV_VTR("org.micov.vtr.1"),

    /**
     * nl.rdw.mekb.1
     */
    MVR("nl.rdw.mekb.1")
}