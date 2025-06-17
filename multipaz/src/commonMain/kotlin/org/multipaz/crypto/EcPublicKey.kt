package org.multipaz.crypto

import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.annotation.CborSerializationImplemented
import org.multipaz.cbor.toDataItem
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseKey
import org.multipaz.cose.CoseLabel
import org.multipaz.cose.toCoseLabel
import org.multipaz.util.fromBase64Url
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * An EC Public Key.
 *
 * @param curve the curve of the key.
 */
@CborSerializationImplemented(schemaId = "elQPzwBQGz5CU2YDTAgAa5l5sTHdJrxubfMWHJcHjHU")
sealed class EcPublicKey(
    open val curve: EcCurve
) {

    /**
     * Creates a [CoseKey] object for the key.
     *
     * The resulting object contains [Cose.COSE_KEY_KTY], [Cose.COSE_KEY_PARAM_CRV],
     * [Cose.COSE_KEY_PARAM_X] and also [Cose.COSE_KEY_PARAM_Y] in case of a double-
     * coordinate curve.
     *
     * @param additionalLabels additional labels to include.
     */
    abstract fun toCoseKey(additionalLabels: Map<CoseLabel, DataItem> = emptyMap()): CoseKey

    /**
     * Encode this key in PEM format
     *
     * @return a PEM encoded string.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun toPem(): String = Crypto.ecPublicKeyToPem(this)

    /**
     * Encodes the public key as a JSON Web Key according to
     * [RFC 7517](https://datatracker.ietf.org/doc/html/rfc7517).
     *
     * By default this only includes the `kty`, `crv`, `x`, `y` (if double-coordinate) claims,
     * use [additionalClaims] to include other claims.
     *
     * @param additionalClaims additional claims to include or `null`.
     * @return a JSON Web Key.
     */
    abstract fun toJwk(
        additionalClaims: JsonObject? = null,
    ): JsonObject

    /**
     * Gets the Json Web Key Thumbprint.
     *
     * This is defined in [RFC 7638](https://datatracker.ietf.org/doc/html/rfc7638)
     *
     * @param digestAlgorithm the digest algorithm to use for creating the thumbprint.
     */
    abstract fun toJwkThumbprint(digestAlgorithm: Algorithm): ByteString

    fun toDataItem(): DataItem = toCoseKey().toDataItem()

    companion object {
        /**
         * Creates an [EcPublicKey] from a PEM encoded string.
         *
         * @param pemEncoding the PEM encoded string.
         * @param curve the curve of the key..
         * @return a new [EcPublicKey].
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun fromPem(pemEncoding: String, curve: EcCurve): EcPublicKey =
            Crypto.ecPublicKeyFromPem(pemEncoding, curve)

        /**
         * Gets a [EcPublicKey] from a COSE Key.
         *
         * @param coseKey the COSE Key.
         * @return the public key.
         */
        fun fromCoseKey(coseKey: CoseKey): EcPublicKey =
            when (coseKey.keyType) {
                Cose.COSE_KEY_TYPE_EC2.toDataItem() -> {
                    val curve = EcCurve.fromInt(
                        coseKey.labels[Cose.COSE_KEY_PARAM_CRV.toCoseLabel]!!.asNumber.toInt()
                    )
                    val keySizeOctets = (curve.bitSize + 7) / 8
                    val x = coseKey.labels[Cose.COSE_KEY_PARAM_X.toCoseLabel]!!.asBstr
                    val y = coseKey.labels[Cose.COSE_KEY_PARAM_Y.toCoseLabel]!!.asBstr
                    check(x.size == keySizeOctets)
                    check(y.size == keySizeOctets)
                    // TODO: maybe check that (x, y) is a point on the curve?
                    EcPublicKeyDoubleCoordinate(curve, x, y)
                }

                Cose.COSE_KEY_TYPE_OKP.toDataItem() -> {
                    val curve = EcCurve.fromInt(
                        coseKey.labels[Cose.COSE_KEY_PARAM_CRV.toCoseLabel]!!.asNumber.toInt()
                    )
                    val x = coseKey.labels[Cose.COSE_KEY_PARAM_X.toCoseLabel]!!.asBstr
                    EcPublicKeyOkp(curve, x)
                }

                else -> {
                    throw IllegalArgumentException("Unknown key type $coseKey.keyType")
                }
            }

        /**
         * Creates a [EcPublicKey] from a JSON Web Key according to
         * [RFC 7517](https://datatracker.ietf.org/doc/html/rfc7517).
         *
         * @param jwk the JSON Web Key.
         * @return the public key.
         */
        fun fromJwk(jwk: JsonObject): EcPublicKey {
            return when (val kty = jwk["kty"]!!.jsonPrimitive.content) {
                "OKP" -> {
                    EcPublicKeyOkp(
                        EcCurve.fromJwkName(jwk["crv"]!!.jsonPrimitive.content),
                        jwk["x"]!!.jsonPrimitive.content.fromBase64Url()
                    )
                }
                "EC" -> {
                    EcPublicKeyDoubleCoordinate(
                        EcCurve.fromJwkName(jwk["crv"]!!.jsonPrimitive.content),
                        jwk["x"]!!.jsonPrimitive.content.fromBase64Url(),
                        jwk["y"]!!.jsonPrimitive.content.fromBase64Url()
                    )
                }
                else -> throw IllegalArgumentException("Unsupported key type $kty")
            }
        }

        fun fromDataItem(dataItem: DataItem): EcPublicKey {
            return CoseKey.fromDataItem(dataItem).ecPublicKey
        }
    }
}
