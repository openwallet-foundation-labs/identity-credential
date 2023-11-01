package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import org.bouncycastle.util.encoders.Hex
import org.junit.Before
import org.junit.BeforeClass
import org.junit.jupiter.api.BeforeAll
// import org.junit.Test
// import org.junit.jupiter.api.Be
import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.EnumSet
import java.util.LinkedList
import java.util.Set

class BuildEncodeDecodeCertificateInfo {




    companion object {
        private val EXTENSION_MAGIC =
            Hex.decode("9279b0d551b8e1dfeaf7a4b3044bf3584389ce08dcd3702ca50152ad38adc695")
        private val RFU_MAGIC =
            Hex.decode("57cc7a902da0c43e94fc3dd7384f97262503a3eaab96de238f58a38e36676577")


//        @BeforeAll
//        @JvmStatic
//        @Throws(Exception::class)
//        fun loadCertificates() {
//            val certFact = CertificateFactory.getInstance("X509")
//            // TODO() // add resources first, check if this resource loading should be performed differently
//            // val certStream = resources.openRawResource("raw/google_mdl_iaca_cert.pem")
//                // this.javaClass.classLoader.getResourceAsStream("raw/google_mdl_iaca_cert.pem")
//            val cert = certFact.generateCertificate(ROOT_CERT.byteInputStream(Charsets.US_ASCII)) as X509Certificate
//            iacaCerts.add(cert)
//        }

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
        // println(certificateInfo)
        encodeDecode(certificateInfo)
    }

    @org.junit.jupiter.api.Test
    @Throws(Exception::class)
    fun maximalBuildEncodeDecode() {

        // TODO think of what to do if field is missing from certificate
        val certificateFields: MutableSet<OptionalCertificateInfoKey> =
            mutableSetOf<OptionalCertificateInfoKey>()
        certificateFields.addAll(OptionalCertificateInfoKey.certificateBasedFields)
        // TODO add field to certificate
        certificateFields.remove(OptionalCertificateInfoKey.STATE_OR_PROVINCE_NAME)
        val builder = CertificateInfo.Builder(
            TestCertificates.iacaCerts[0],
            setOf(KnownDocType.MDL.docType),
            certificateFields
        )
        // TODO add method to add issuingAuthority, extensions and  to CertificateInfo
        builder.indicateIssuingAuthority("Maarten")
        val certificateInfo = builder.build()
        // println(certificateInfo)
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
        val decodedCertificateInfo = decoder.decode(encodedCertificateInfo2)
        // println(decodedCertificateInfo)
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
        // println(certificateInfo)
        encodeDecode(certificateInfo)
    }
}
