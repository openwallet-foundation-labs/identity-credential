package org.multipaz.mdoc.zkp.longfellow

/** Enum for the MDocVerifierErrorCode.  */
internal enum class VerifierCodeEnum(val value: Int) {
    MDOC_VERIFIER_SUCCESS(0),
    MDOC_VERIFIER_CIRCUIT_PARSING_FAILURE(1),
    MDOC_VERIFIER_PROOF_TOO_SMALL(2),
    MDOC_VERIFIER_HASH_PARSING_FAILURE(3),
    MDOC_VERIFIER_SIGNATURE_PARSING_FAILURE(4),
    MDOC_VERIFIER_GENERAL_FAILURE(5);

    companion object {
        private val map = values().associateBy(VerifierCodeEnum::value)
        fun fromInt(value: Int): VerifierCodeEnum? = map[value]
    }
}
