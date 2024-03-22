package com.android.identity.sdjwt

import com.android.identity.crypto.Algorithm

/**
 * Information about an issuer.
 *
 * @param iss a URL pointing to the issuer. At that URL there would typically be
 *        metadata about the issuer available, along with currently valid public keys. This
 *        parameter will be copied into the payload of the JWT.
 * @param alg the algorithm (e.g., ES256) used by the issuer to sign the SD-JWTs. This
 *        parameter will be copied into the header of the JWT.
 * @param kid a parameter further identifying the key, if necessary (e.g., when the
 *        iss URL points to a file with multiple keys. This parameter will be copied
 *        into the header of the JWT.
 */
data class Issuer(
    val iss: String,
    val alg: Algorithm,
    val kid: String? = null /* optional */
)
