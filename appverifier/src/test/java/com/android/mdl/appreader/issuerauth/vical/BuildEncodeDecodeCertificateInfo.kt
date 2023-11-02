package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import org.bouncycastle.util.encoders.Hex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.EnumSet

class BuildEncodeDecodeCertificateInfo {

    companion object {
        private val EXTENSION_MAGIC =
            Hex.decode("9279b0d551b8e1dfeaf7a4b3044bf3584389ce08dcd3702ca50152ad38adc695")
        private val RFU_MAGIC =
            Hex.decode("57cc7a902da0c43e94fc3dd7384f97262503a3eaab96de238f58a38e36676577")
    }

    @org.junit.jupiter.api.Test
    @Throws(Exception::class)
    fun minimalBuildEncodeDecode() {
        val builder = CertificateInfo.Builder(
            TestCertificates.iacaCerts[0],
            setOf(KnownDocType.MDL.docType),
            EnumSet.noneOf(OptionalCertificateInfoKey::class.java)
        )
        val certificateInfo = builder.build()
        encodeDecode(certificateInfo)
    }

    @org.junit.jupiter.api.Test
    @Throws(Exception::class)
    fun maximalBuildEncodeDecode() {

        // TODO think of what to do if field is missing from certificate
        val certificateFields: MutableSet<OptionalCertificateInfoKey> =
            mutableSetOf<OptionalCertificateInfoKey>()
        certificateFields.addAll(OptionalCertificateInfoKey.certificateBasedFields)
        // TODO also add field to certificate
        certificateFields.remove(OptionalCertificateInfoKey.STATE_OR_PROVINCE_NAME)
        val builder = CertificateInfo.Builder(
            TestCertificates.iacaCerts[0],
            setOf(KnownDocType.MDL.docType),
            certificateFields
        )
        // TODO add method to add issuingAuthority, extensions and  to CertificateInfo
        builder.indicateIssuingAuthority("Google inc.")
        val certificateInfo = builder.build()
        encodeDecode(certificateInfo)
    }

    @Throws(CborException::class, IOException::class, DataItemDecoderException::class)
    private fun encodeDecode(certificateInfo: CertificateInfo) {
        val encoder = CertificateInfo.Encoder()
        val encodedCertificateInfo: Map = encoder.encode(certificateInfo)
        // println(encodedCertificateInfo)
        var binaryEncodedCertificateInfo: ByteArray
        ByteArrayOutputStream().use { baos ->
            val cborEncoder = CborEncoder(baos)
            cborEncoder.encode(encodedCertificateInfo)
            binaryEncodedCertificateInfo = baos.toByteArray()
        }
        var encodedCertificateInfo2: Map
        ByteArrayInputStream(binaryEncodedCertificateInfo).use { bais ->
            val cborDecoder = CborDecoder(bais)
            encodedCertificateInfo2 = cborDecoder.decodeNext() as Map
        }
        val decoder = CertificateInfo.Decoder()
        // NOTE if no exception then it decoded correctly
        decoder.decode(encodedCertificateInfo2)
    }

    @org.junit.jupiter.api.Test
    @Throws(Exception::class)
    fun minimalBuildEncodeDecodeWithExtensionsAndRFU() {
        val builder = CertificateInfo.Builder(
            TestCertificates.iacaCerts[0],
            setOf(KnownDocType.MDL.docType),
            EnumSet.noneOf(OptionalCertificateInfoKey::class.java)
        )
        builder.addExtension(UnicodeString("UL extension key 1"), ByteString(EXTENSION_MAGIC))
        builder.addRFU(UnicodeString("UL RFU key 1"), ByteString(EXTENSION_MAGIC))
        val certificateInfo = builder.build()
        encodeDecode(certificateInfo)
    }
}
