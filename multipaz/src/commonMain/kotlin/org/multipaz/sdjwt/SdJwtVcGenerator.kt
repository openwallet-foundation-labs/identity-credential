package org.multipaz.sdjwt

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcSignature
import org.multipaz.sdjwt.util.JsonWebKey
import org.multipaz.sdjwt.vc.JwtBody
import org.multipaz.sdjwt.vc.JwtHeader
import org.multipaz.util.toBase64Url
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

/**
 * Creates a new SD-JWT Verifiable Credential generator.
 *
 * @param digestAlg for hashing the disclosures (optional, defaults to sha-256)
 * @param random for creating random salts for the disclosures (optional)
 * @param vct document type, to be included in the clear in the VC (optional)
 * @param payload all the attributes to be included undisclosed (hashed) in the VC
 * @param issuer information about the issuer, to be included in the VC, in particular
 *               the URL for key metadata and the signing algorithm used.
 *
 * After creation, the following properties can optionally be set:
 * @property timeSigned when the signing happened (typically Instant.now())
 * @property timeValidityBegin when the credential is supposed to start being valid
 * @property timeValidityEnd when the credential stops being valid
 * @property publicKey the auth key that this VC will be bound to
 *
 * Then, pass a private key or a lambda that can sign bits to generateSdJwt to
 * generate the signed SD-JWT.
 */
class SdJwtVcGenerator(
    private val digestAlg: Algorithm = Algorithm.SHA256,
    private val random: Random = Random.Default,
    private val vct: String = "IdentityCredential",
    private val payload: JsonObject,
    private val issuer: Issuer) {

    private val disclosures: List<Disclosure> = payload
        .map { Disclosure(it.key, it.value, digestAlg, random) }
    private val disclosureHashes: List<String> = disclosures
        .map { it.hash }

    var timeSigned: Instant? = null
    var timeValidityBegin: Instant? = null
    var timeValidityEnd: Instant? = null
    var publicKey: JsonWebKey? = null


    /**
     * Generate the SD-JWT VC. The SD-JWT VC consists of a JWT, followed by disclosures.
     * The JWT in turn consists of a header, a body (holding the data passed into
     * the constructor, plus the hashes of the provided payload data), and a signature.
     *
     * To caller passes in a lambda that does the actual signing, after the to-be-signed
     * data has been properly prepared.
     *
     * @param sign a lambda taking a ByteArray (the data to be signed) and an Issuer
     *        object (which has information about the keypair that is to be used to
     *        sign the SD-JWT, in case that's ambiguous at the time of calling). The
     *        lambda must return, as a [EcSignature], the signature over the to-be-signed
     *        data.
     *
     * @return the SD-JWT VC.
     *
     */
    fun generateSdJwt(
        sign: (toBeSigned:ByteArray, issuer: Issuer) -> EcSignature
    ): SdJwtVerifiableCredential {
        val header = JwtHeader(issuer.alg, issuer.kid)
        val headerStr = header.toString()

        val body = JwtBody(
            disclosureHashes,
            digestAlg,
            issuer.iss,
            vct,
            timeSigned,
            timeValidityBegin,
            timeValidityEnd,
            publicKey
        )
        val bodyStr = body.toString()

        val toBeSigned = "$headerStr.$bodyStr".encodeToByteArray()
        val signature = sign(toBeSigned, issuer)
        val signatureStr = (signature.r + signature.s).toBase64Url()

        return SdJwtVerifiableCredential(
            headerStr,
            bodyStr,
            signatureStr,
            disclosures
        )
    }

    /**
     * Generate an SD-JWT (see [generateSdJwt]).
     *
     * @param key a private key that will be used to sign the SD-JWT VC. This method is
     *        provided for convenience - the caller need only pass the private key, and not
     *        worry about the details of signature creation. If you need more control over the
     *        signing process, use the other provided version of [generateSdJwt].
     *
     * @return the SD-JWT VC
     */
    fun generateSdJwt(key: EcPrivateKey): SdJwtVerifiableCredential {
        return generateSdJwt { toBeSigned, issuer ->
            Crypto.sign(key, issuer.alg, toBeSigned)
        }
    }
}