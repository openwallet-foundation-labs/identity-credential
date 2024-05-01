package com.android.identity.crypto

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

    /** SHA-2 256-bit Hash */
    SHA256(-16),

    /** SHA-2 384-bit Hash */
    SHA384(-43),

    /** SHA-2 512-bit Hash */
    SHA512(-44),

    /** HMAC w/ SHA-256 */
    HMAC_SHA256(5),

    /** HMAC w/ SHA-384 */
    HMAC_SHA384(6),

    /** HMAC w/ SHA-512 */
    HMAC_SHA512(7),

    /** AES-GCM mode w/ 128-bit key, 128-bit tag */
    A128GCM(1),

    /** AES-GCM mode w/ 192-bit key, 128-bit tag */
    A192GCM(2),

    /** AES-GCM mode w/ 256-bit key, 128-bit tag */
    A256GCM(3),

    /**
     * Cipher suite for COSE-HPKE in Base Mode that uses the DHKEM(P-256, HKDF-SHA256) KEM,
     * the HKDF-SHA256 KDF and the AES-128-GCM AEAD.
     *
     * Note that this value is still TBD and the proposed value is from
     * [Use of Hybrid Public-Key Encryption (HPKE) with CBOR Object Signing and Encryption (COSE)](https://www.ietf.org/archive/id/draft-ietf-cose-hpke-07.html#IANA)
     *
     */
    HPKE_BASE_P256_SHA256_AES128GCM(35),

    ;

    companion object {
        /**
         * Creates a [Algorithm] from an identifier.
         *
         * @throws IllegalArgumentException if the identifier isn't in the [Algorithm] enumeration.
         */
        fun fromInt(coseAlgorithmIdentifier: Int): Algorithm =
            Algorithm.values().find { it.coseAlgorithmIdentifier == coseAlgorithmIdentifier }
                ?: throw IllegalArgumentException("No algorithm with COSE identifier $coseAlgorithmIdentifier")
    }
}
