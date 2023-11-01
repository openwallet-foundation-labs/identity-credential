package com.android.mdl.appreader.issuerauth.vical

import java.util.Optional

/**
 * KnownDocType is a helper class that provides an easy-to-use enum for DocType and maps OID's to the DocTypes.
 *
 * This helper class is not used in the API as it only captures some of the possible mappings from docType to keyUsage.
 *
 * @author UL TS BV
 */
// TODO this mapping should probably be dynamic and configurable for a VICAL
enum class KnownDocType(
    /**
     * Retrieves the full DocType string (e.g. "org.iso.18013.5.1.mDL")
     *
     * @return the full DocType
     */
    val docType: String,
    /**
     * Retrieves the extended key usage for the specific DocType.
     * @return the extended key usage
     */
    val extendedKeyUsage: String
) {
    MDL("org.iso.18013.5.1.mDL", "1.0.18013.5.1.7"), MEKB(
        "nl.rdw.mekb.1",
        "2.16.528.1.1010.2.2.1"
    ),
    MICOV("org.micov.1", "not.defined");

    companion object {
        /**
         * Returns the DocType associated with the specific key usage OID, or empty if no document type has been configured.
         * @param keyUsage
         * @return
         */
        fun forKeyUsage(keyUsage: String?): Optional<KnownDocType> {
            for (docType in values()) {
                if (docType.extendedKeyUsage.equals(keyUsage, ignoreCase = true)) {
                    return Optional.of(docType)
                }
            }
            return Optional.empty()
        }
    }
}