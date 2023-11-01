/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.CborBuilder
import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.builder.ArrayBuilder
import co.nstant.`in`.cbor.builder.MapBuilder
import co.nstant.`in`.cbor.model.*
import co.nstant.`in`.cbor.model.Array
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.Number
import org.bouncycastle.asn1.*
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.Bytes
import org.bouncycastle.util.encoders.Hex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlin.ArithmeticException
import kotlin.Boolean
import kotlin.ByteArray
import kotlin.IllegalArgumentException
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.Long
import kotlin.RuntimeException
import kotlin.String
import kotlin.Suppress
import kotlin.Throws
import kotlin.collections.ArrayList
import kotlin.collections.Collection
import kotlin.collections.List
import kotlin.collections.contentEquals
import kotlin.collections.indices
import kotlin.collections.sliceArray
import kotlin.require

/**
 * Utility functions.
 */
internal object IdentityUtil {
    private const val COSE_LABEL_ALG: Long = 1
    private const val COSE_LABEL_X5CHAIN: Long = 33 // temporary identifier

    // From RFC 8152: Table 5: ECDSA Algorithm Values
    private const val COSE_ALG_ECDSA_256: Long = -7
    private const val COSE_ALG_ECDSA_384: Long = -35
    private const val COSE_ALG_ECDSA_512: Long = -36
    private const val COSE_ALG_HMAC_256_256: Long = 5
    private const val CBOR_SEMANTIC_TAG_ENCODED_CBOR: Long = 24
    private const val COSE_KEY_KTY: Long = 1
    private const val COSE_KEY_TYPE_EC2: Long = 2
    private const val COSE_KEY_EC2_CRV: Long = -1
    private const val COSE_KEY_EC2_X: Long = -2
    private const val COSE_KEY_EC2_Y: Long = -3
    private const val COSE_KEY_EC2_CRV_P256: Long = 1
    fun cborEncode(dataItem: DataItem?): ByteArray {
        val baos = ByteArrayOutputStream()
        try {
            CborEncoder(baos).encode(dataItem)
        } catch (e: CborException) {
            // This should never happen and we don't want cborEncode() to throw since that
            // would complicate all callers. Log it instead.
            throw IllegalStateException("Unexpected failure encoding data", e)
        }
        return baos.toByteArray()
    }

    fun cborEncodeWithoutCanonicalizing(dataItem: DataItem?): ByteArray {
        val baos = ByteArrayOutputStream()
        try {
            CborEncoder(baos).nonCanonical().encode(dataItem)
        } catch (e: CborException) {
            // This should never happen and we don't want cborEncode() to throw since that
            // would complicate all callers. Log it instead.
            throw IllegalStateException("Unexpected failure encoding data", e)
        }
        return baos.toByteArray()
    }

    fun cborEncodeBoolean(value: Boolean): ByteArray {
        return cborEncode(CborBuilder().add(value).build().get(0))
    }

    fun cborEncodeString(value: String?): ByteArray {
        return cborEncode(CborBuilder().add(value).build().get(0))
    }

    fun cborEncodeNumber(value: Long): ByteArray {
        return cborEncode(CborBuilder().add(value).build().get(0))
    }

    fun cborEncodeBytestring(value: ByteArray?): ByteArray {
        return cborEncode(CborBuilder().add(value).build().get(0))
    }

    // Used Timestamp: Android API version of Instant
    fun cborEncodeDateTime(timestamp: Instant): ByteArray {
        return cborEncode(cborBuildDateTime(timestamp))
    }

    /**
     * Returns #6.0(tstr) where tstr is the ISO 8601 encoding of the given point in time.
     * Only supports UTC times.
     */
    // Used Timestamp: Android API version of Instant
    fun cborBuildDateTime(timestamp: Instant): DataItem {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        df.timeZone = TimeZone.getTimeZone("UTC")
        val `val` = Date(timestamp.toEpochMilli())
        val dateString = df.format(`val`)
        val dataItem: DataItem = UnicodeString(dateString)
        dataItem.setTag(0)
        return dataItem
    }

    fun cborDecode(encodedBytes: ByteArray?): DataItem {
        val bais = ByteArrayInputStream(encodedBytes)
        var dataItems = try {
            CborDecoder(bais).decode()
        } catch (e: CborException) {
            throw IllegalArgumentException("Error decoding CBOR", e)
        }
        require(dataItems.size == 1) {
            ("Unexpected number of items, expected 1 got "
                    + dataItems.size)
        }
        return dataItems.get(0)
    }

    fun cborDecodeBoolean(data: ByteArray?): Boolean {
        val simple: SimpleValue = cborDecode(data) as SimpleValue
        return simple.getSimpleValueType() == SimpleValueType.TRUE
    }

    /**
     * Accepts a `DataItem`, attempts to cast it to a `Number`, then returns the value
     * Throws `IllegalArgumentException` if the `DataItem` is not a `Number`. This
     * method also checks bounds, and if the given data item is too large to fit in a long, it
     * throws `ArithmeticException`.
     */
    fun checkedLongValue(item: DataItem): Long {
        val bigNum = castTo(
            Number::class.java, item
        ).value
        val result = bigNum.toLong()
        if (bigNum != BigInteger.valueOf(result)) {
            throw ArithmeticException("Expected long value, got '$bigNum'")
        }
        return result
    }

    fun cborDecodeString(data: ByteArray?): String {
        return checkedStringValue(cborDecode(data))
    }

    /**
     * Accepts a `DataItem`, attempts to cast it to a `UnicodeString`, then returns the
     * value. Throws `IllegalArgumentException` if the `DataItem` is not a
     * `UnicodeString`.
     */
    fun checkedStringValue(item: DataItem): String {
        return castTo<UnicodeString, DataItem>(UnicodeString::class.java, item).getString()
    }

    fun cborDecodeLong(data: ByteArray?): Long {
        return checkedLongValue(cborDecode(data))
    }

    fun cborDecodeByteString(data: ByteArray?): ByteArray {
        val dataItem: DataItem = cborDecode(data)
        return castTo(ByteString::class.java, dataItem).bytes
    }

    fun cborDecodeDateTime(data: ByteArray?): Instant {
        return cborDecodeDateTime(cborDecode(data))
    }

    fun cborDecodeDateTime(di: DataItem): Instant {
        require(di is UnicodeString) { "Passed in data is not a Unicode-string" }
        require(
            !(!di.hasTag() || di.getTag().getValue() != 0L)
        ) { "Passed in data is not tagged with tag 0" }
        val dateString = checkedStringValue(di)

        // Manually parse the timezone
        var parsedTz = TimeZone.getTimeZone("UTC")
        if (!dateString.endsWith("Z")) {
            val timeZoneSubstr = dateString.substring(dateString.length - 6)
            parsedTz = TimeZone.getTimeZone("GMT$timeZoneSubstr")
        }
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        df.timeZone = parsedTz
        val date: Date = try {
            df.parse(dateString)!!
        } catch (e: ParseException) {
            throw RuntimeException("Error parsing string", e)
        }
        return Instant.ofEpochMilli(date.getTime())
    }

    /**
     * Similar to a typecast of `value` to the given type `clazz`, except:
     *
     *  * Throws `IllegalArgumentException` instead of `ClassCastException` if
     * `!clazz.isAssignableFrom(value.getClass())`.
     *  * Also throws `IllegalArgumentException` if `value == null`.
     *
     */
    fun <T : V?, V> castTo(clazz: Class<T>, value: V?): T {
        if (value == null) {
            throw IllegalArgumentException("Expected type $clazz, got null")
        }

        val givenClazz = value!!::class.java
        if(!clazz.isAssignableFrom(givenClazz)) {
            throw IllegalArgumentException("Expected type $clazz, got type $givenClazz")
        }

        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    /**
     * Helper function to check if a given certificate chain is valid.
     *
     * NOTE NOTE NOTE: We only check that the certificates in the chain sign each other. We
     * *specifically* don't check that each certificate is also a CA certificate.
     *
     * @param certificateChain the chain to validate.
     * @return `true` if valid, `false` otherwise.
     */
    fun validateCertificateChain(
        certificateChain: Collection<X509Certificate>
    ): Boolean {
        // First check that each certificate signs the previous one...
        var prevCertificate: X509Certificate? = null
        for (certificate in certificateChain) {
            if (prevCertificate != null) {
                // We're not the leaf certificate...
                //
                // Check the previous certificate was signed by this one.
                try {
                    prevCertificate.verify(certificate.publicKey)
                } catch (e: CertificateException) {
                    return false
                } catch (e: InvalidKeyException) {
                    return false
                } catch (e: NoSuchAlgorithmException) {
                    return false
                } catch (e: NoSuchProviderException) {
                    return false
                } catch (e: SignatureException) {
                    return false
                }
            } else {
                // we're the leaf certificate so we're not signing anything nor
                // do we need to be e.g. a CA certificate.
            }
            prevCertificate = certificate
        }
        return true
    }

    private fun coseBuildToBeSigned(
        encodedProtectedHeaders: ByteArray,
        payload: ByteArray?,
        detachedContent: ByteArray?
    ): ByteArray {
        val sigStructure = CborBuilder()
        val array: ArrayBuilder<CborBuilder> = sigStructure.addArray()
        array.add("Signature1")
        array.add(encodedProtectedHeaders)

        // We currently don't support Externally Supplied Data (RFC 8152 section 4.3)
        // so external_aad is the empty bstr
        val emptyExternalAad = ByteArray(0)
        array.add(emptyExternalAad)

        // Next field is the payload, independently of how it's transported (RFC
        // 8152 section 4.4). Since our API specifies only one of |data| and
        // |detachedContent| can be non-empty, it's simply just the non-empty one.
        if (payload != null && payload.size > 0) {
            array.add(payload)
        } else {
            array.add(detachedContent)
        }
        array.end()
        return cborEncode(sigStructure.build().get(0))
    }
    /*
     * From RFC 8152 section 8.1 ECDSA:
     *
     * The signature algorithm results in a pair of integers (R, S).  These
     * integers will be the same length as the length of the key used for
     * the signature process.  The signature is encoded by converting the
     * integers into byte strings of the same length as the key size.  The
     * length is rounded up to the nearest byte and is left padded with zero
     * bits to get to the correct length.  The two integers are then
     * concatenated together to form a byte string that is the resulting
     * signature.
     * 
     * 
     */
    /**
     *
     * Converts a DER / X9.62 encoded signature to a "flat" signature consisting of just the r and s components as
     * unsigned, statically sized, big endian integers. The size will be twice the key size in bytes.
     *
     * @param signature the signature
     * @param keySize the key size that needs to be used
     * @return the signature as a concatenation of the r- and s-values
     */
    private fun signatureDerToCose(signature: ByteArray, keySize: Int): ByteArray {
        val asn1: ASN1Primitive
        asn1 = try {
            ASN1InputStream(ByteArrayInputStream(signature)).readObject()
        } catch (e: IOException) {
            throw IllegalArgumentException("Error decoding DER signature", e)
        }
        val asn1Encodables = castTo(
            ASN1Sequence::class.java, asn1
        ).toArray()
        require(asn1Encodables.size == 2) { "Expected two items in sequence" }
        val r = castTo(
            ASN1Integer::class.java, asn1Encodables[0].toASN1Primitive()
        ).value
        val s = castTo(
            ASN1Integer::class.java, asn1Encodables[1].toASN1Primitive()
        ).value
        val rBytes = stripLeadingZeroes(r.toByteArray())
        val sBytes = stripLeadingZeroes(s.toByteArray())
        val baos = ByteArrayOutputStream()
        try {
            for (n in 0 until keySize - rBytes.size) {
                baos.write(0x00)
            }
            baos.write(rBytes)
            for (n in 0 until keySize - sBytes.size) {
                baos.write(0x00)
            }
            baos.write(sBytes)
        } catch (e: IOException) {
            throw IllegalArgumentException(e)
        }
        return baos.toByteArray()
    }

    private fun signatureCoseToDer(signature: ByteArray): ByteArray {
        // r and s are always positive and may use all bits so use the constructor which
        // parses them as unsigned.
        val keySizeBytes = signature.size / 2
        val r = BigInteger(
            1, Arrays.copyOfRange(
                signature, 0, keySizeBytes
            )
        )
        val s = BigInteger(
            1, Arrays.copyOfRange(
                signature, keySizeBytes, signature.size
            )
        )
        try {
            ByteArrayOutputStream().use { baos ->
                val seq = DERSequenceGenerator(baos)
                seq.addObject(ASN1Integer(r.toByteArray()))
                seq.addObject(ASN1Integer(s.toByteArray()))
                seq.close()
                return baos.toByteArray()
            }
        } catch (e: IOException) {
            throw IllegalStateException("Error generating DER signature", e)
        }
    }

    /**
     * Note: this uses the default JCA provider which may not support a lot of curves, for
     * example it doesn't support Brainpool curves. If you need to use such curves, use
     * [.coseSign1Sign] instead with a
     * Signature created using a provider that does have support.
     *
     * Currently only ECDSA signatures are supported.
     *
     * TODO: add support and tests for Ed25519 and Ed448.
     */
    fun coseSign1Sign(
        key: PrivateKey?,
        algorithm: String?, data: ByteArray?,
        additionalData: ByteArray?,
        certificateChain: Collection<X509Certificate>?
    ): DataItem {
        val s: Signature
        try {
            s = Signature.getInstance(algorithm)
            s.initSign(key)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("Caught exception", e)
        } catch (e: InvalidKeyException) {
            throw IllegalStateException("Caught exception", e)
        }
        require(key is ECPrivateKey) { "Only EC private keys supported at this time" }
        val keySize = determineKeySize(key)
        val dataLen = data?.size ?: 0
        val detachedContentLen = additionalData?.size ?: 0
        require(!(dataLen > 0 && detachedContentLen > 0)) { "data and detachedContent cannot both be non-empty" }
        val alg: Long
        alg = if (s.algorithm.equals("SHA256withECDSA", ignoreCase = true)) {
            COSE_ALG_ECDSA_256
        } else if (s.algorithm.equals("SHA384withECDSA", ignoreCase = true)) {
            COSE_ALG_ECDSA_384
        } else if (s.algorithm.equals("SHA512withECDSA", ignoreCase = true)) {
            COSE_ALG_ECDSA_512
        } else {
            throw IllegalArgumentException("Unsupported algorithm " + s.algorithm)
        }
        val protectedHeaders = CborBuilder()
        val protectedHeadersMap: MapBuilder<CborBuilder> = protectedHeaders.addMap()
        protectedHeadersMap.put(COSE_LABEL_ALG, alg)
        val protectedHeadersBytes = cborEncode(protectedHeaders.build().get(0))
        val toBeSigned = coseBuildToBeSigned(protectedHeadersBytes, data, additionalData)
        var coseSignature = try {
            s.update(toBeSigned)
            val derSignature = s.sign()
            signatureDerToCose(derSignature, keySize)
        } catch (e: SignatureException) {
            throw IllegalStateException("Error signing data", e)
        }
        val builder = CborBuilder()
        val array: ArrayBuilder<CborBuilder> = builder.addArray()
        array.add(protectedHeadersBytes)
        val unprotectedHeaders: MapBuilder<ArrayBuilder<CborBuilder>> = array.addMap()
        try {
            if (certificateChain != null && certificateChain.size > 0) {
                if (certificateChain.size == 1) {
                    val cert = certificateChain.iterator().next()
                    unprotectedHeaders.put(COSE_LABEL_X5CHAIN, cert.encoded)
                } else {
                    val x5chainsArray: ArrayBuilder<MapBuilder<ArrayBuilder<CborBuilder>>> =
                        unprotectedHeaders
                            .putArray(COSE_LABEL_X5CHAIN)
                    for (cert in certificateChain) {
                        x5chainsArray.add(cert.encoded)
                    }
                }
            }
        } catch (e: CertificateEncodingException) {
            throw IllegalStateException("Error encoding certificate", e)
        }
        if (data == null || data.size == 0) {
            array.add(SimpleValue(SimpleValueType.NULL))
        } else {
            array.add(data)
        }
        array.add(coseSignature)
        return builder.build().get(0)
    }

    private fun determineKeySize(ecKey: ECPrivateKey): Int {
        val field = ecKey.params.curve.field
        return (field.fieldSize + Bytes.SIZE - 1) / Bytes.SIZE
    }

    /**
     * Currently only ECDSA signatures are supported.
     *
     * TODO: add support and tests for Ed25519 and Ed448.
     */
    fun coseSign1CheckSignature(
        coseSign1: DataItem,
        detachedContent: ByteArray?, publicKey: PublicKey?
    ): Boolean {
        require(coseSign1.getMajorType() == MajorType.ARRAY) { "Data item is not an array" }
        val items: List<DataItem> = (coseSign1 as Array).getDataItems()
        require(items.size >= 4) { "Expected at least four items in COSE_Sign1 array" }
        require(items[0].getMajorType() == MajorType.BYTE_STRING) { "Item 0 (protected headers) is not a byte-string" }
        val encodedProtectedHeaders = (items[0] as ByteString).bytes
        var payload = ByteArray(0)
        if (items[2].getMajorType() == MajorType.SPECIAL) {
            require((items[2] as Special).specialType == SpecialType.SIMPLE_VALUE) { "Item 2 (payload) is a special but not a simple value" }
            val simple: SimpleValue = items[2] as SimpleValue
            require(simple.getSimpleValueType() == SimpleValueType.NULL) { "Item 2 (payload) is a simple but not the value null" }
        } else if (items[2].getMajorType() == MajorType.BYTE_STRING) {
            payload = (items[2] as ByteString).bytes
        } else {
            throw IllegalArgumentException("Item 2 (payload) is not nil or byte-string")
        }
        println(Hex.toHexString(payload))
        require(items[3].getMajorType() == MajorType.BYTE_STRING) { "Item 3 (signature) is not a byte-string" }
        val coseSignature = (items[3] as ByteString).bytes
        val derSignature = signatureCoseToDer(coseSignature)
        println(Hex.toHexString(derSignature))
        val dataLen = payload.size
        val detachedContentLen = detachedContent?.size ?: 0
        require(!(dataLen > 0 && detachedContentLen > 0)) { "data and detachedContent cannot both be non-empty" }
        val protectedHeaders: DataItem = cborDecode(encodedProtectedHeaders)
        val alg = cborMapExtractNumber(protectedHeaders as Map, COSE_LABEL_ALG)
        val signature: String
        signature = if (alg == COSE_ALG_ECDSA_256) {
            "SHA256withECDSA"
        } else if (alg == COSE_ALG_ECDSA_384) {
            "SHA384withECDSA"
        } else if (alg == COSE_ALG_ECDSA_512) {
            "SHA512withECDSA"
        } else {
            throw IllegalArgumentException("Unsupported COSE alg $alg")
        }
        println(signature)
        val toBeSigned = coseBuildToBeSigned(
            encodedProtectedHeaders, payload,
            detachedContent
        )
        println(Hex.toHexString(toBeSigned))
        return try {
            // Use BouncyCastle provider for verification since it supports a lot more curves than
            // the default provider, including the brainpool curves
            //
            val verifier = Signature.getInstance(
                signature,
                BouncyCastleProvider()
            )
            verifier.initVerify(publicKey)
            verifier.update(toBeSigned)
            verifier.verify(derSignature)
        } catch (e: SignatureException) {
            throw IllegalStateException("Error verifying signature", e)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("Error verifying signature", e)
        } catch (e: InvalidKeyException) {
            throw IllegalStateException("Error verifying signature", e)
        }
    }

    private fun coseBuildToBeMACed(
        encodedProtectedHeaders: ByteArray,
        payload: ByteArray?,
        detachedContent: ByteArray?
    ): ByteArray {
        val macStructure = CborBuilder()
        val array: ArrayBuilder<CborBuilder> = macStructure.addArray()
        array.add("MAC0")
        array.add(encodedProtectedHeaders)

        // We currently don't support Externally Supplied Data (RFC 8152 section 4.3)
        // so external_aad is the empty bstr
        val emptyExternalAad = ByteArray(0)
        array.add(emptyExternalAad)

        // Next field is the payload, independently of how it's transported (RFC
        // 8152 section 4.4). Since our API specifies only one of |data| and
        // |detachedContent| can be non-empty, it's simply just the non-empty one.
        if (payload != null && payload.size > 0) {
            array.add(payload)
        } else {
            array.add(detachedContent)
        }
        return cborEncode(macStructure.build().get(0))
    }

    fun coseMac0(
        key: SecretKey?,
        data: ByteArray?,
        detachedContent: ByteArray?
    ): DataItem {
        val dataLen = data?.size ?: 0
        val detachedContentLen = detachedContent?.size ?: 0
        require(!(dataLen > 0 && detachedContentLen > 0)) { "data and detachedContent cannot both be non-empty" }
        val protectedHeaders = CborBuilder()
        val protectedHeadersMap: MapBuilder<CborBuilder> = protectedHeaders.addMap()
        protectedHeadersMap.put(COSE_LABEL_ALG, COSE_ALG_HMAC_256_256)
        val protectedHeadersBytes = cborEncode(protectedHeaders.build().get(0))
        val toBeMACed = coseBuildToBeMACed(protectedHeadersBytes, data, detachedContent)
        val mac: ByteArray
        mac = try {
            val m = Mac.getInstance("HmacSHA256")
            m.init(key)
            m.update(toBeMACed)
            m.doFinal()
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalStateException("Unexpected error", e)
        } catch (e: InvalidKeyException) {
            throw IllegalStateException("Unexpected error", e)
        }
        val builder = CborBuilder()
        val array: ArrayBuilder<CborBuilder> = builder.addArray()
        array.add(protectedHeadersBytes)
        /* MapBuilder<ArrayBuilder<CborBuilder>> unprotectedHeaders = */array.addMap()
        if (data == null || data.size == 0) {
            array.add(SimpleValue(SimpleValueType.NULL))
        } else {
            array.add(data)
        }
        array.add(mac)
        return builder.build().get(0)
    }

    fun coseMac0GetTag(coseMac0: DataItem): ByteArray {
        val items: List<DataItem> = castTo(
            Array::class.java, coseMac0
        ).dataItems
        require(items.size >= 4) { "coseMac0 have less than 4 elements" }
        val tagItem: DataItem = items[3]
        return castTo(ByteString::class.java, tagItem).bytes
    }

    fun hasSubByteArray(haystack: ByteArray, needle: ByteArray): Boolean {
        if (haystack.size < needle.size) {
            return false
        }

        // brute-force but good enough since users will only pass relatively small amounts of data.
        for (off in 0 until haystack.size - needle.size) {
            if (haystack.sliceArray(IntRange(off, off + needle.size)).contentEquals(needle)) {
                return true
            }

//            if (Arrays.compare(haystack, off, off + needle.size, needle, 0, needle.size) == 0) {
//                return true
//            }
        }
        return false
    }

    fun stripLeadingZeroes(value: ByteArray): ByteArray {
        for (off in value.indices) {
            if (value[off] != 0x00.toByte()) {
                return Arrays.copyOfRange(value, off, value.size - off)
            }
        }
        return ByteArray(0)
    }

    /**
     * Returns #6.24(bstr) of the given already encoded CBOR
     */
    fun cborBuildTaggedByteString(encodedCbor: ByteArray?): DataItem {
        val item: DataItem = ByteString(encodedCbor)
        item.setTag(CBOR_SEMANTIC_TAG_ENCODED_CBOR)
        return item
    }

    /**
     * For a #6.24(bstr), extracts the bytes.
     */
    fun cborExtractTaggedCbor(encodedTaggedBytestring: ByteArray?): ByteArray {
        val item: DataItem = cborDecode(encodedTaggedBytestring)
        val itemByteString = castTo(
            ByteString::class.java, item
        )
        require(
            !(!item.hasTag() || item.getTag().getValue() != CBOR_SEMANTIC_TAG_ENCODED_CBOR)
        ) { "ByteString is not tagged with tag 24" }
        return itemByteString.bytes
    }

    /**
     * For a #6.24(bstr), extracts the bytes and decodes it and returns
     * the decoded CBOR as a DataItem.
     */
    fun cborExtractTaggedAndEncodedCbor(item: DataItem): DataItem {
        val itemByteString =
            castTo(
                ByteString::class.java, item
            )
        require(
            !(!item.hasTag() || item.getTag()
                .getValue() != CBOR_SEMANTIC_TAG_ENCODED_CBOR)
        ) { "ByteString is not tagged with tag 24" }
        val encodedCbor = itemByteString.bytes
        return cborDecode(encodedCbor)
    }

    /**
     * Returns the empty byte-array if no data is included in the structure.
     */
    fun coseSign1GetData(coseSign1: DataItem): ByteArray {
        require(coseSign1.getMajorType() == MajorType.ARRAY) { "Data item is not an array" }
        val items: List<DataItem> = castTo(
            Array::class.java, coseSign1
        ).dataItems
        require(items.size >= 4) { "Expected at least four items in COSE_Sign1 array" }
        var payload = ByteArray(0)
        if (items[2].getMajorType() == MajorType.SPECIAL) {
            require((items[2] as Special).specialType == SpecialType.SIMPLE_VALUE) { "Item 2 (payload) is a special but not a simple value" }
            val simple: SimpleValue =
                castTo<SimpleValue, DataItem>(SimpleValue::class.java, items[2])
            require(simple.getSimpleValueType() == SimpleValueType.NULL) { "Item 2 (payload) is a simple but not the value null" }
        } else if (items[2].getMajorType() == MajorType.BYTE_STRING) {
            payload = castTo(ByteString::class.java, items[2]).bytes
        } else {
            throw IllegalArgumentException("Item 2 (payload) is not nil or byte-string")
        }
        return payload
    }

    /**
     * Retrieves the X509 certificate chain (x5chain) from the COSE_Sign1 signature.
     *
     * @return the empty collection if no x5chain is included in the structure
     * @throws RuntimeException if the given bytes aren't valid COSE_Sign1
     */
    @Throws(IllegalArgumentException::class)
    fun coseSign1GetX5Chain(
        coseSign1: DataItem
    ): List<X509Certificate> {
        val ret = ArrayList<X509Certificate>()
        require(coseSign1.getMajorType() == MajorType.ARRAY) { "Data item is not an array" }
        val items: List<DataItem> = castTo(
            Array::class.java, coseSign1
        ).dataItems
        require(items.size >= 4) { "Expected at least four items in COSE_Sign1 array" }
        require(items[1].getMajorType() == MajorType.MAP) { "Item 1 (unprotected headers) is not a map" }
        val map = items[1] as Map
        val x5chainItem: DataItem? = map[UnsignedInteger(COSE_LABEL_X5CHAIN)]
        if (x5chainItem != null) {
            try {
                val factory = CertificateFactory.getInstance("X.509")
                if (x5chainItem is ByteString) {
                    val certBais = ByteArrayInputStream(
                        x5chainItem.bytes
                    )
                    ret.add(factory.generateCertificate(certBais) as X509Certificate)
                } else if (x5chainItem is Array) {
                    val x5chainItemArray: Array = x5chainItem
                    // NOTE was: for (DataItem certItem : castTo(Array.class, x5chainItem).getDataItems()) {
                    for (certItem in x5chainItemArray.getDataItems()) {
                        val certBais = ByteArrayInputStream(
                            castTo(ByteString::class.java, certItem).bytes
                        )
                        ret.add(factory.generateCertificate(certBais) as X509Certificate)
                    }
                } else {
                    throw IllegalArgumentException("Unexpected type for x5chain value")
                }
            } catch (e: CertificateException) {
                throw IllegalArgumentException("Unexpected error", e)
            }
        }
        return ret
    }

    fun cborBuildCoseKey(key: PublicKey): DataItem {
        val ecKey =
            key as ECPublicKey
        val w = ecKey.w
        // X and Y are always positive so for interop we remove any leading zeroes
        // inserted by the BigInteger encoder.
        val x = stripLeadingZeroes(w.affineX.toByteArray())
        val y = stripLeadingZeroes(w.affineY.toByteArray())
        return CborBuilder()
            .addMap()
            .put(COSE_KEY_KTY, COSE_KEY_TYPE_EC2)
            .put(COSE_KEY_EC2_CRV, COSE_KEY_EC2_CRV_P256)
            .put(COSE_KEY_EC2_X, x)
            .put(COSE_KEY_EC2_Y, y)
            .end()
            .build().get(0)
    }

    fun cborMapHasKey(map: DataItem, key: String?): Boolean {
        val item: DataItem? = castTo(
            Map::class.java,
            map
        )[UnicodeString(key)]
        return item != null
    }

    fun cborMapHasKey(map: DataItem, key: Long): Boolean {
        val keyDataItem: DataItem = if (key >= 0) UnsignedInteger(key) else NegativeInteger(key)
        val item: DataItem? = castTo(
            Map::class.java,
            map
        )[keyDataItem]
        return item != null
    }

    fun cborMapExtractNumber(map: DataItem?, key: Long): Long {
        val keyDataItem: DataItem = if (key >= 0) UnsignedInteger(key) else NegativeInteger(key)
        val item: DataItem = castTo(
            Map::class.java,
            map
        )[keyDataItem]
        return checkedLongValue(item)
    }
}