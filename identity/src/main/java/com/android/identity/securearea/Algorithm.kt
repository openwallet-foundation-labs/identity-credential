package com.android.identity.securearea

/**
 * Algorithm identifiers.
 *
 * All algorithm identifiers are from the
 * [IANA COSE registry](https://www.iana.org/assignments/cose/cose.xhtml).
 */
enum class Algorithm(val coseAlgorithmIdentifier: Int) {
    /** Used to indicate the algorithm is unset.  */
    UNSET(Int.MAX_VALUE),

    /** The algorithm identifier for signatures using ECDSA with SHA-256  */
    ES256(-7),

    /** The algorithm identifier for signatures using ECDSA with SHA-384  */
    ES384(-35),

    /** The algorithm identifier for signatures using ECDSA with SHA-512  */
    ES512(-36),

    /** The algorithm identifier for signatures using EdDSA  */
    EDDSA(-8),
}