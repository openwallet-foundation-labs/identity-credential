package org.multipaz.issuance

/**
 * An enumeration of possible conditions a document can be in, from the issuer's perspective.
 */
enum class DocumentCondition(val value: Int) {
    /**
     * The requested document doesn't exist.
     *
     * This can happen if a document is remote deleted.
     */
    NO_SUCH_DOCUMENT(0),

    /**
     * Proofing is required.
     *
     * For credentials requiring proofing, this is the initial state.
     */
    PROOFING_REQUIRED(1),

    /**
     * Proofing data was received and is currently processing.
     */
    PROOFING_PROCESSING(2),

    /**
     * Proofing failed.
     */
    PROOFING_FAILED(3),

    /**
     * Proofing was accepted and IA is ready to release the credential
     * configuration.
     *
     * This is the default stated after proofing is completed. The IA
     * may also go back to this state if the PII in the credential
     * changes.
     */
    CONFIGURATION_AVAILABLE(4),

    /**
     * The credential configuration has been read and the IA is ready
     * to receive requests for issuance of Document Presentation Objects
     */
    READY(5),

    /**
     * The IA is requesting that the application deletes this credential.
     *
     * The application should initiate the deletion flow when this state
     * has been reached
     */
    DELETION_REQUESTED(6);

    companion object {
        fun fromInt(value: Int): DocumentCondition =
            DocumentCondition.values().first() {it.value == value}
    }
}
