package com.android.identity.crypto

import com.android.identity.asn1.ASN1
import com.android.identity.asn1.ASN1BitString
import com.android.identity.asn1.ASN1Boolean
import com.android.identity.asn1.ASN1Integer
import com.android.identity.asn1.ASN1Object
import com.android.identity.asn1.ASN1ObjectIdentifier
import com.android.identity.asn1.ASN1OctetString
import com.android.identity.asn1.ASN1Sequence
import com.android.identity.asn1.ASN1Set
import com.android.identity.asn1.ASN1String
import com.android.identity.asn1.ASN1TagClass
import com.android.identity.asn1.ASN1TaggedObject
import com.android.identity.asn1.ASN1Time
import com.android.identity.cbor.Bstr
import com.android.identity.cbor.DataItem
import kotlinx.datetime.Instant
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A data type for a X509 certificate.
 *
 * @param encodedCertificate the bytes of the X.509 certificate.
 */
class X509Cert(
    val encodedCertificate: ByteArray
) {
    override fun equals(other: Any?): Boolean = other is X509Cert &&
            encodedCertificate contentEquals other.encodedCertificate

    override fun hashCode(): Int = encodedCertificate.contentHashCode()

    /**
     * Gets an [DataItem] with the encoded X.509 certificate.
     */
    fun toDataItem(): DataItem = Bstr(encodedCertificate)

    /**
     * Encode this certificate in PEM format
     *
     * @return a PEM encoded string.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun toPem(): String {
        val sb = StringBuilder()
        sb.append("-----BEGIN CERTIFICATE-----\n")
        sb.append(Base64.Mime.encode(encodedCertificate))
        sb.append("\n-----END CERTIFICATE-----\n")
        return sb.toString()
    }

    /**
     * Checks if the certificate was signed with a given key.
     *
     * @param publicKey the key to check the signature with.
     * @return `true` if the certificate was signed with the given key, `false` otherwise.
     */
    fun verify(publicKey: EcPublicKey): Boolean {
        val ecSignature = when (signatureAlgorithm) {
            Algorithm.ES256,
            Algorithm.ES384,
            Algorithm.ES512 -> {
                EcSignature.fromDerEncoded(publicKey.curve.bitSize, signature)
            }
            Algorithm.EDDSA -> {
                val len = signature.size
                val r = signature.sliceArray(IntRange(0, len/2 - 1))
                val s = signature.sliceArray(IntRange(len/2, len - 1))
                EcSignature(r, s)
            }
            else -> throw IllegalArgumentException("Unsupported algorithm $signatureAlgorithm")
        }
        return Crypto.checkSignature(
            publicKey,
            tbsCertificate,
            signatureAlgorithm,
            ecSignature
        )
    }

    private val parsedCert: ASN1Sequence by lazy {
        ASN1.decode(encodedCertificate)!! as ASN1Sequence
    }

    private val tbsCert: ASN1Sequence by lazy {
        parsedCert.elements[0] as ASN1Sequence
    }

    /**
     * TODO: docs
     */
    val version: Int
        get() {
            val versionCode =
                ((tbsCert.elements[0] as ASN1TaggedObject).child as ASN1Integer).toLong().toInt()
            return versionCode
        }

    /**
     * TODO: docs
     */
    val serialNumber: ByteArray
        get() = (tbsCert.elements[1] as ASN1Integer).value

    /**
     * TODO: docs
     */
    val subject: X501Name
        get() = parseName(tbsCert.elements[5] as ASN1Sequence)

    /**
     * TODO: docs
     */
    val issuer: X501Name
        get() = parseName(tbsCert.elements[3] as ASN1Sequence)

    /**
     * TODO: docs
     */
    val validityNotBefore: Instant
        get() = ((tbsCert.elements[4] as ASN1Sequence).elements[0] as ASN1Time).value

    /**
     * TODO: docs
     */
    val validityNotAfter: Instant
        get() = ((tbsCert.elements[4] as ASN1Sequence).elements[1] as ASN1Time).value

    /**
     * TODO: docs
     */
    val tbsCertificate: ByteArray
        get() = ASN1.encode(tbsCert)

    /**
     * TODO: docs
     */
    val signature: ByteArray
        get() = (parsedCert.elements[2] as ASN1BitString).value

    /**
     * TODO: docs
     */
    val signatureAlgorithm: Algorithm
        get() {
            val algorithmIdentifier = parsedCert.elements[1] as ASN1Sequence
            val algorithmOid = (algorithmIdentifier.elements[0] as ASN1ObjectIdentifier).oid
            return when (algorithmOid) {
                "1.2.840.10045.4.3.2" -> Algorithm.ES256
                "1.2.840.10045.4.3.3" -> Algorithm.ES384
                "1.2.840.10045.4.3.4" -> Algorithm.ES512
                "1.3.101.112", "1.3.101.113" -> Algorithm.EDDSA  // ED25519, ED448
                else -> throw IllegalArgumentException("Unexpected algorithm OID $algorithmOid")
            }
        }

    /**
     * The public key in the certificate, as an Elliptic Curve key.
     *
     * Note that this is only supported for curves in [Crypto.supportedCurves].
     *
     * @throws IllegalStateException if the public key for the certificate isn't an EC key or
     * supported by the platform.
     */
    val ecPublicKey: EcPublicKey
        get() {
            val subjectPublicKeyInfo = tbsCert.elements[6] as ASN1Sequence
            val algorithmIdentifier = subjectPublicKeyInfo.elements[0] as ASN1Sequence
            val algorithmOid = (algorithmIdentifier.elements[0] as ASN1ObjectIdentifier).oid
            val curve = when (algorithmOid) {
                // https://datatracker.ietf.org/doc/html/rfc5480#section-2.1.1
                "1.2.840.10045.2.1" -> {
                    val ecCurveString = (algorithmIdentifier.elements[1] as ASN1ObjectIdentifier).oid
                    when (ecCurveString) {
                        "1.2.840.10045.3.1.7" -> EcCurve.P256
                        "1.3.132.0.34" -> EcCurve.P384
                        "1.3.132.0.35" -> EcCurve.P521
                        "1.3.36.3.3.2.8.1.1.7" -> EcCurve.BRAINPOOLP256R1
                        "1.3.36.3.3.2.8.1.1.9" -> EcCurve.BRAINPOOLP320R1
                        "1.3.36.3.3.2.8.1.1.11" -> EcCurve.BRAINPOOLP384R1
                        "1.3.36.3.3.2.8.1.1.13" -> EcCurve.BRAINPOOLP512R1
                        else -> throw IllegalStateException("Unexpected curve OID $ecCurveString")
                    }
                }
                "1.3.101.110" -> EcCurve.X25519
                "1.3.101.111" -> EcCurve.X448
                "1.3.101.112" -> EcCurve.ED25519
                "1.3.101.113" -> EcCurve.ED448
                else -> throw IllegalStateException("Unexpected OID $algorithmOid")
            }
            val keyMaterial = (subjectPublicKeyInfo.elements[1] as ASN1BitString).value
            return when (curve) {
                EcCurve.P256,
                EcCurve.P384,
                EcCurve.P521,
                EcCurve.BRAINPOOLP256R1,
                EcCurve.BRAINPOOLP320R1,
                EcCurve.BRAINPOOLP384R1,
                EcCurve.BRAINPOOLP512R1 -> {
                    EcPublicKeyDoubleCoordinate.fromUncompressedPointEncoding(curve, keyMaterial)
                }
                EcCurve.ED25519,
                EcCurve.X25519,
                EcCurve.ED448,
                EcCurve.X448 -> {
                    EcPublicKeyOkp(curve, keyMaterial)
                }
            }
        }

    /**
     * TODO: docs
     */
    val criticalExtensionOIDs: Set<String>
        get() = getExtensionOIDs(true)

    /**
     * TODO: docs
     */
    val nonCriticalExtensionOIDs: Set<String>
        get() = getExtensionOIDs(false)

    private fun getExtensionsSeq(): ASN1Sequence? {
        for (elem in tbsCert.elements) {
            if (elem is ASN1TaggedObject &&
                elem.tagClass == ASN1TagClass.CONTEXT_SPECIFIC &&
                elem.tagNumber == 0x03 &&
                elem.child is ASN1Sequence) {
                return elem.child
            }
        }
        return null
    }

    private fun getExtensionOIDs(getCritical: Boolean): Set<String> {
        val extSeq = getExtensionsSeq() ?: return emptySet()
        val ret = mutableSetOf<String>()
        for (ext in extSeq.elements) {
            ext as ASN1Sequence
            val isCritical = if (ext.elements.size == 3) {
                (ext.elements[1] as ASN1Boolean).value
            } else {
                false
            }
            if ((isCritical && getCritical) || (!isCritical && !getCritical)) {
                ret.add((ext.elements[0] as ASN1ObjectIdentifier).oid)
            }
        }
        return ret
    }

    /**
     * TODO: docs
     */
    fun getExtensionValue(oid: String): ByteArray? {
        val extSeq = getExtensionsSeq() ?: return null
        for (ext in extSeq.elements) {
            ext as ASN1Sequence
            if ((ext.elements[0] as ASN1ObjectIdentifier).oid == oid) {
                if (ext.elements.size == 3) {
                    return (ext.elements[2] as ASN1OctetString).value
                } else {
                    return (ext.elements[1] as ASN1OctetString).value
                }
            }
        }
        return null
    }

    /**
     * TODO: docs
     */
    val subjectKeyIdentifier: ByteArray? = getExtensionValue("2.5.29.14")

    /**
     * TODO: docs
     */
    val authorityKeyIdentifier: ByteArray? = getExtensionValue("2.5.29.35")

    companion object {
        // TODO: enum with well-known extension values?

        /**
         * Creates a [X509Cert] from a PEM encoded string.
         *
         * @param pemEncoding the PEM encoded string.
         * @return a new [X509Cert].
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun fromPem(pemEncoding: String): X509Cert {
            val encoded = Base64.Mime.decode(pemEncoding
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .trim())
            return X509Cert(encoded)
        }

        /**
         * Gets a [X509Cert] from a [DataItem].
         *
         * @param dataItem the data item, must have been encoded with [toDataItem].
         * @return the certificate.
         */
        fun fromDataItem(dataItem: DataItem): X509Cert {
            return X509Cert(dataItem.asBstr)
        }

        // TODO: create()
    }

    class Builder(
        private val publicKey: EcPublicKey,
        private val signingKey: EcPrivateKey,
        private val signingKeyCertificate: X509Cert?,
        private val signatureAlgorithm: Algorithm,
        private val serialNumber: ByteArray,
        private val subject: X501Name,
        private val issuer: X501Name,
        private val validFrom: Instant,
        private val validUntil: Instant,
    ) {
        private data class Extension(
            val critical: Boolean,
            val value: ByteArray
        )
        private val extensions = mutableMapOf<String, Extension>()

        fun addExtension(oid: String, critical: Boolean, value: ByteArray) {
            extensions.put(oid, Extension(critical, value))
        }

        fun build(): X509Cert {
            val signatureAlgorithmSeq = signatureAlgorithm.getSignatureAlgorithmSeq(signingKey.curve)

            val subjectPublicKey = when (publicKey) {
                is EcPublicKeyDoubleCoordinate -> {
                    publicKey.asUncompressedPointEncoding
                }
                is EcPublicKeyOkp -> {
                    publicKey.x
                }
            }
            val subjectPublicKeyInfoSeq = ASN1Sequence(listOf(
                publicKey.curve.getCurveAlgorithmSeq(),
                ASN1BitString(0, subjectPublicKey)
            ))

            val tbsCertObjs = mutableListOf(
                ASN1TaggedObject(ASN1TagClass.CONTEXT_SPECIFIC, 0, ASN1Integer(2L)),
                ASN1Integer(serialNumber),
                signatureAlgorithmSeq,
                generateName(issuer),
                ASN1Sequence(listOf(
                    ASN1Time(validFrom),
                    ASN1Time(validUntil)
                )),
                generateName(subject),
                subjectPublicKeyInfoSeq,
            )

            if (extensions.size > 0) {
                val extensionObjs = mutableListOf<ASN1Object>()
                for ((oid, ext) in extensions) {
                    extensionObjs.add(
                        if (ext.critical) {
                            ASN1Sequence(
                                listOf(
                                    ASN1ObjectIdentifier(oid),
                                    ASN1Boolean(true),
                                    ASN1OctetString(ext.value)
                                )
                            )
                        } else {
                            ASN1Sequence(
                                listOf(
                                    ASN1ObjectIdentifier(oid),
                                    ASN1OctetString(ext.value)
                                )
                            )
                        }
                    )
                }
                tbsCertObjs.add(ASN1TaggedObject(
                    ASN1TagClass.CONTEXT_SPECIFIC,
                    3,
                    ASN1Sequence(extensionObjs)
                ))
            }

            val tbsCert = ASN1Sequence(tbsCertObjs)

            val encodedTbsCert = ASN1.encode(tbsCert)
            val signature = Crypto.sign(
                signingKey,
                signatureAlgorithm,
                encodedTbsCert
            )
            val encodedSignature = when (signatureAlgorithm) {
                Algorithm.ES256,
                Algorithm.ES384,
                Algorithm.ES512 -> signature.toDerEncoded()
                Algorithm.EDDSA -> signature.r + signature.s
                else -> throw IllegalArgumentException("Unsupported signature algorithm $signatureAlgorithm")
            }
            val cert = ASN1Sequence(listOf(
                tbsCert,
                signatureAlgorithmSeq,
                ASN1BitString(0, encodedSignature),
            ))
            return X509Cert(ASN1.encode(cert))
        }
    }
}

private fun Algorithm.getSignatureAlgorithmSeq(signingKeyCurve: EcCurve): ASN1Sequence {
    val signatureAlgorithmOid = when (this) {
        Algorithm.ES256 -> "1.2.840.10045.4.3.2"
        Algorithm.ES384 -> "1.2.840.10045.4.3.3"
        Algorithm.ES512 -> "1.2.840.10045.4.3.4"
        Algorithm.EDDSA -> {
            when (signingKeyCurve) {
                EcCurve.ED25519 -> "1.3.101.112"
                EcCurve.ED448 -> "1.3.101.113"
                else -> throw IllegalArgumentException(
                    "Unsupported curve ${signingKeyCurve} for $this")
            }
        }
        else -> {
            throw IllegalArgumentException("Unsupported signature algorithm $this")
        }
    }
    return ASN1Sequence(listOf(ASN1ObjectIdentifier(signatureAlgorithmOid)))
}

private fun EcCurve.getCurveAlgorithmSeq(): ASN1Sequence {
    val (algOid, paramOid) = when (this) {
        EcCurve.P256 -> Pair("1.2.840.10045.2.1", "1.2.840.10045.3.1.7")
        EcCurve.P384 -> Pair("1.2.840.10045.2.1", "1.3.132.0.34")
        EcCurve.P521 -> Pair("1.2.840.10045.2.1", "1.3.132.0.35")
        EcCurve.BRAINPOOLP256R1 -> Pair("1.2.840.10045.2.1", "1.3.36.3.3.2.8.1.1.7")
        EcCurve.BRAINPOOLP320R1 -> Pair("1.2.840.10045.2.1", "1.3.36.3.3.2.8.1.1.9")
        EcCurve.BRAINPOOLP384R1 -> Pair("1.2.840.10045.2.1", "1.3.36.3.3.2.8.1.1.11")
        EcCurve.BRAINPOOLP512R1 -> Pair("1.2.840.10045.2.1", "1.3.36.3.3.2.8.1.1.13")
        EcCurve.X25519 -> Pair("1.3.101.110", null)
        EcCurve.X448 -> Pair("1.3.101.111", null)
        EcCurve.ED25519 -> Pair("1.3.101.112", null)
        EcCurve.ED448 -> Pair("1.3.101.113", null)
    }
    if (paramOid != null) {
        return ASN1Sequence(listOf(
            ASN1ObjectIdentifier(algOid),
            ASN1ObjectIdentifier(paramOid)
        ))
    }
    return ASN1Sequence(listOf(
        ASN1ObjectIdentifier(algOid),
    ))
}

private fun parseName(obj: ASN1Sequence): X501Name {
    val components = mutableMapOf<String, ASN1String>()
    for (elem in obj.elements) {
        val dnSet = elem as ASN1Set
        val typeAndValue = dnSet.elements[0] as ASN1Sequence
        val oidObject = typeAndValue.elements[0] as ASN1ObjectIdentifier
        val nameObject = typeAndValue.elements[1] as ASN1String
        components.put(oidObject.oid, nameObject)
    }
    return X501Name(components)
}

private fun generateName(name: X501Name): ASN1Sequence {
    val objs = mutableListOf<ASN1Object>()
    for ((oid, value) in name.components) {
        objs.add(
            ASN1Set(listOf(
                ASN1Sequence(listOf(
                    ASN1ObjectIdentifier(oid),
                    value
                ))
            ))
        )
    }
    return ASN1Sequence(objs)
}
