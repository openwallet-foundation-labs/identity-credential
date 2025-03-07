package org.multipaz.cose

import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DiagnosticOption
import org.multipaz.cbor.toDataItem
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPublicKeyDoubleCoordinate
import org.multipaz.crypto.toEcPublicKey
import org.multipaz.util.fromHex

import org.bouncycastle.util.BigIntegers
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class CoseTestsJvm {

    // TODO: Port to CommonTest

    @Test
    fun coseKey() {
        // This checks the encoding of X and Y are encoded as specified in
        // Section 2.3.5 Field-Element-to-Octet-String Conversion of
        // SEC 1: Elliptic Curve Cryptography (https://www.secg.org/sec1-v2.pdf).
        assertEquals(
            "{\n" +
                    "  1: 2,\n" +
                    "  -1: 1,\n" +
                    "  -2: h'0000000000000000000000000000000000000000000000000000000000000001',\n" +
                    "  -3: h'0000000000000000000000000000000000000000000000000000000000000001'\n" +
                    "}",
            Cbor.toDiagnostics(
                EcPublicKeyDoubleCoordinate(
                    EcCurve.P256,
                    BigInteger.valueOf(1).sec1EncodeFieldElementAsOctetString(32),
                    BigInteger.valueOf(1).sec1EncodeFieldElementAsOctetString(32)
                ).toCoseKey().toDataItem(),
                setOf(DiagnosticOption.PRETTY_PRINT)
            )
        )
    }

    @Test
    fun coseKeyWithAdditionalLabels() {
        // Check we can add additional labels to a CoseKey
        val key = EcPublicKeyDoubleCoordinate(
            EcCurve.P256,
            BigInteger.valueOf(1).sec1EncodeFieldElementAsOctetString(32),
            BigInteger.valueOf(1).sec1EncodeFieldElementAsOctetString(32)
        )
        val coseKey = key.toCoseKey(
            mapOf(Pair(Cose.COSE_KEY_KID.toCoseLabel, "name@example.com".toByteArray().toDataItem()))
        )
        assertEquals(
            "{\n" +
                    "  1: 2,\n" +
                    "  -1: 1,\n" +
                    "  -2: h'0000000000000000000000000000000000000000000000000000000000000001',\n" +
                    "  -3: h'0000000000000000000000000000000000000000000000000000000000000001',\n" +
                    "  2: h'6e616d65406578616d706c652e636f6d'\n" +
                    "}",
            Cbor.toDiagnostics(coseKey.toDataItem(), setOf(DiagnosticOption.PRETTY_PRINT))
        )
    }

    @Test
    fun coseSign1X5Chain() {
        // This is a test vector from ISO/IEC 18013-5:2021 Annex D.5.2 Issuer data authentication
        val issuerAuth =
            "8443a10126a118215901f3308201ef30820195a00302010202143c4416eed784f3b413e48" +
            "f56f075abfa6d87eb84300a06082a8648ce3d04030230233114301206035504030c0b7574" +
            "6f7069612069616361310b3009060355040613025553301e170d323031303031303030303" +
            "0305a170d3231313030313030303030305a30213112301006035504030c0975746f706961" +
            "206473310b30090603550406130255533059301306072a8648ce3d020106082a8648ce3d0" +
            "3010703420004ace7ab7340e5d9648c5a72a9a6f56745c7aad436a03a43efea77b5fa7b88" +
            "f0197d57d8983e1b37d3a539f4d588365e38cbbf5b94d68c547b5bc8731dcd2f146ba381a" +
            "83081a5301e0603551d120417301581136578616d706c65406578616d706c652e636f6d30" +
            "1c0603551d1f041530133011a00fa00d820b6578616d706c652e636f6d301d0603551d0e0" +
            "416041414e29017a6c35621ffc7a686b7b72db06cd12351301f0603551d23041830168014" +
            "54fa2383a04c28e0d930792261c80c4881d2c00b300e0603551d0f0101ff0404030207803" +
            "0150603551d250101ff040b3009060728818c5d050102300a06082a8648ce3d0403020348" +
            "00304502210097717ab9016740c8d7bcdaa494a62c053bbdecce1383c1aca72ad08dbc04c" +
            "bb202203bad859c13a63c6d1ad67d814d43e2425caf90d422422c04a8ee0304c0d3a68d59" +
            "03a2d81859039da66776657273696f6e63312e306f646967657374416c676f726974686d6" +
            "75348412d3235366c76616c756544696765737473a2716f72672e69736f2e31383031332e" +
            "352e31ad00582075167333b47b6c2bfb86eccc1f438cf57af055371ac55e1e359e20f254a" +
            "dcebf01582067e539d6139ebd131aef441b445645dd831b2b375b390ca5ef6279b205ed45" +
            "710258203394372ddb78053f36d5d869780e61eda313d44a392092ad8e0527a2fbfe55ae0" +
            "358202e35ad3c4e514bb67b1a9db51ce74e4cb9b7146e41ac52dac9ce86b8613db5550458" +
            "20ea5c3304bb7c4a8dcb51c4c13b65264f845541341342093cca786e058fac2d59055820f" +
            "ae487f68b7a0e87a749774e56e9e1dc3a8ec7b77e490d21f0e1d3475661aa1d0658207d83" +
            "e507ae77db815de4d803b88555d0511d894c897439f5774056416a1c7533075820f0549a1" +
            "45f1cf75cbeeffa881d4857dd438d627cf32174b1731c4c38e12ca936085820b68c8afcb2" +
            "aaf7c581411d2877def155be2eb121a42bc9ba5b7312377e068f660958200b3587d1dd0c2" +
            "a07a35bfb120d99a0abfb5df56865bb7fa15cc8b56a66df6e0c0a5820c98a170cf36e11ab" +
            "b724e98a75a5343dfa2b6ed3df2ecfbb8ef2ee55dd41c8810b5820b57dd036782f7b14c6a" +
            "30faaaae6ccd5054ce88bdfa51a016ba75eda1edea9480c5820651f8736b18480fe252a03" +
            "224ea087b5d10ca5485146c67c74ac4ec3112d4c3a746f72672e69736f2e31383031332e3" +
            "52e312e5553a4005820d80b83d25173c484c5640610ff1a31c949c1d934bf4cf7f18d5223" +
            "b15dd4f21c0158204d80e1e2e4fb246d97895427ce7000bb59bb24c8cd003ecf94bf35bbd" +
            "2917e340258208b331f3b685bca372e85351a25c9484ab7afcdf0d2233105511f778d98c2" +
            "f544035820c343af1bd1690715439161aba73702c474abf992b20c9fb55c36a336ebe01a8" +
            "76d6465766963654b6579496e666fa1696465766963654b6579a40102200121582096313d" +
            "6c63e24e3372742bfdb1a33ba2c897dcd68ab8c753e4fbd48dca6b7f9a2258201fb3269ed" +
            "d418857de1b39a4e4a44b92fa484caa722c228288f01d0c03a2c3d667646f635479706575" +
            "6f72672e69736f2e31383031332e352e312e6d444c6c76616c6964697479496e666fa3667" +
            "369676e6564c074323032302d31302d30315431333a33303a30325a6976616c696446726f" +
            "6dc074323032302d31302d30315431333a33303a30325a6a76616c6964556e74696cc0743" +
            "23032312d31302d30315431333a33303a30325a584059e64205df1e2f708dd6db0847aed7" +
            "9fc7c0201d80fa55badcaf2e1bcf5902e1e5a62e4832044b890ad85aa53f129134775d733" +
            "754d7cb7a413766aeff13cb2e"
        val coseSign1 = Cbor.decode(issuerAuth.fromHex()).asCoseSign1

        val signatureAlgorithm = coseSign1.protectedHeaders[Cose.COSE_LABEL_ALG.toCoseLabel]!!.asNumber
        assertEquals(signatureAlgorithm, Algorithm.ES256.coseAlgorithmIdentifier.toLong())

        // TODO:
        /*
        val x5chain = coseSign1.unprotectedHeaders[Cose.COSE_LABEL_X5CHAIN.toCoseLabel]!!.asCertificateChain
        assertEquals(1, x5chain.certificates.size)
        val cert = x5chain.certificates[0].javaX509Certificate

        assertEquals("C=US, CN=utopia iaca", cert.issuerX500Principal.toString())
        assertEquals("C=US, CN=utopia ds", cert.subjectX500Principal.toString())

        val x = "ace7ab7340e5d9648c5a72a9a6f56745c7aad436a03a43efea77b5fa7b88f019".fromHex()
        val y = "7d57d8983e1b37d3a539f4d588365e38cbbf5b94d68c547b5bc8731dcd2f146b".fromHex()
        val publicKey = EcPublicKeyDoubleCoordinate(EcCurve.P256, x, y)
        assertEquals(publicKey, cert.publicKey.toEcPublicKey(EcCurve.P256))

         */
    }
}

/* Encodes an integer according to Section 2.3.5 Field-Element-to-Octet-String Conversion
 * of SEC 1: Elliptic Curve Cryptography (https://www.secg.org/sec1-v2.pdf).
 */
private fun BigInteger.sec1EncodeFieldElementAsOctetString(octetStringSize: Int): ByteArray {
    return BigIntegers.asUnsignedByteArray(octetStringSize, this)
}
