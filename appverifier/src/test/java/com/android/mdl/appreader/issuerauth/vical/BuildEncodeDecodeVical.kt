package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.model.ByteString
import co.nstant.`in`.cbor.model.Map
import co.nstant.`in`.cbor.model.UnicodeString
import org.bouncycastle.util.encoders.Hex
import org.junit.Assert
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.EnumSet
import java.util.LinkedList
import java.util.Set

class BuildEncodeDecodeVical {
    @org.junit.jupiter.api.Test
    @Throws(Exception::class)
    fun minimalBuildEncodeDecode() {
        val certificateInfoBuilder = CertificateInfo.Builder(
            TestCertificates.iacaCerts[0],
            setOf(KnownDocType.MDL.docType),
            EnumSet.noneOf(OptionalCertificateInfoKey::class.java)
        )
        val certificateInfo = certificateInfoBuilder.build()
        val builder: Vical.Builder = Vical.Builder("Test", Instant.now(), 1)
        builder.addCertificateInfo(certificateInfo)
        val vical = builder.build()
        val encoder = Vical.Encoder()
        val encodedVical: Map = encoder.encode(vical)
        // println(encodedVical)
        val decoder = Vical.Decoder()
        val decodedVical = decoder.decode(encodedVical)
        // println(decodedVical)
    }

    @org.junit.jupiter.api.Test
    @Throws(Exception::class)
    fun maximalBuildEncodeDecode() {
        // TODO think of what to do if field is missing from certificate

        // --- build a VICAL using a constructor for the required fields ---
        val builder: Vical.Builder = Vical.Builder("Test", Instant.now(), 1)

        // --- build a CertificateInfo for each trusted (root) certificate, and add it
        val certificateInfoBuilder = CertificateInfo.Builder(
            TestCertificates.iacaCerts[0],
            setOf(KnownDocType.MDL.docType), setOf<OptionalCertificateInfoKey>())

        // note that there is an extended constructor to include optional fields from the certificate
        val certificateInfo = certificateInfoBuilder.build()
        builder.addCertificateInfo(certificateInfo)

        // --- indicate when the next update is expected 
        builder.nextUpdate(Instant.now().plus(10, ChronoUnit.DAYS))

        // --- build the VICAL structure as in-memory objects
        val vical = builder.build()

        // --- create an encoder to create the CBOR tree
        val encoder = Vical.Encoder()
        val encodedVical: Map = encoder.encode(vical)
        // println(encodedVical)
        val decoder = Vical.Decoder()
        val decodedVical = decoder.decode(encodedVical)
        // println(decodedVical)
    }

    @org.junit.jupiter.api.Test
    @Throws(Exception::class)
    fun minimalBuildEncodeDecodeWithExtensionsAndRFU() {
        val certificateInfoBuilder = CertificateInfo.Builder(
            TestCertificates.iacaCerts[0],
            setOf(KnownDocType.MDL.docType),
            EnumSet.noneOf(OptionalCertificateInfoKey::class.java)
        )
        val certificateInfo = certificateInfoBuilder.build()
        val builder: Vical.Builder = Vical.Builder("Test", Instant.now(), 1)
        builder.addCertificateInfo(certificateInfo)
        builder.nextUpdate(Instant.now().plus(10, ChronoUnit.DAYS))
        builder.addExtension(UnicodeString("UL extension key 1"), ByteString(EXTENSION_MAGIC))
        builder.addRFU(UnicodeString("UL RFU key 1"), ByteString(RFU_MAGIC))
        val vical = builder.build()
        val encoder = Vical.Encoder()
        val encodedVical = encoder.encodeToBytes(vical)
        Assert.assertTrue(TestUtil.findMagic(encodedVical!!, EXTENSION_MAGIC))
        Assert.assertTrue(TestUtil.findMagic(encodedVical!!, RFU_MAGIC))
    }

    companion object {
        private val EXTENSION_MAGIC =
            Hex.decode("9279b0d551b8e1dfeaf7a4b3044bf3584389ce08dcd3702ca50152ad38adc695")
        private val RFU_MAGIC =
            Hex.decode("57cc7a902da0c43e94fc3dd7384f97262503a3eaab96de238f58a38e36676577")
    }
}