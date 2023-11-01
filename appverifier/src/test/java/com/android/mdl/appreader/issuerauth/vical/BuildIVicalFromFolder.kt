package com.android.mdl.appreader.issuerauth.vical

import co.nstant.`in`.cbor.CborDecoder
import co.nstant.`in`.cbor.CborEncoder
import co.nstant.`in`.cbor.CborException
import co.nstant.`in`.cbor.model.Map
import org.bouncycastle.util.encoders.Hex
import org.bouncycastle.util.io.pem.PemReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileReader
import java.io.IOException
import java.nio.file.Files
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Optional

object BuildIVicalFromFolder {
    @Throws(Exception::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val folder = File(args[0])
        if (!folder.isDirectory) {
            throw RuntimeException(args[0] + " is not a folder")
        }
        val cert = readCertificate(File("UL_VICAL_CA.cer"))
        val key = readPEMECPrivateKey("UL_VICAL_CA_privkey.pem")
        val serial = 2
        val sig = createAndSign(folder, serial, cert, key)
        val vicalVerifier: VicalVerifier = IdentityCredentialVicalVerifier()
        val verificationResult = vicalVerifier.verifyCose1Signature(sig)
        println(verificationResult)
        Files.write(File("vical_$serial.bin").toPath(), sig)
        val sigB64 = Base64.getEncoder().encode(sig)
        Files.write(File("vical_$serial.b64").toPath(), sigB64)
    }

    private fun hasPossibleCertificateExtension(filename: String): Boolean {
        return filename.matches(".*[.](?:cer|der|crt|pem)$".toRegex())
    }

    fun createAndSign(
        folder: File,
        serial: Int,
        signingCertificate: X509Certificate, signingKey: PrivateKey?
    ): ByteArray {
        // TODO nextupdate?
        val vicalBuilder: Vical.Builder = Vical.Builder(
            signingCertificate.issuerX500Principal.name,
            Instant.now(),
           1
        )
        val files = folder.listFiles { f: File ->
            hasPossibleCertificateExtension(f.name) &&
                    f.canRead()
        }
        for (possibleCertFile in files) {
            var cert: X509Certificate
            cert = try {
                readCertificate(possibleCertFile)
            } catch (e: CertificateException) {
                // TODO FNF and class cast cannot really happen here, should move to runtime exception in method
                continue
            } catch (e: FileNotFoundException) {
                continue
            } catch (e: ClassCastException) {
                continue
            }
            val certificateFields: Set<OptionalCertificateInfoKey> =
                OptionalCertificateInfoKey.certificateBasedFields
            var certInfoBuilder: CertificateInfo.Builder
            certInfoBuilder = try {
                CertificateInfo.Builder(cert, emptySet(), certificateFields)
            } catch (e: Exception) {
                // TODO provide better runtime exception
                throw RuntimeException("Uncaught exception, blame developer", e)
            }
            val certInfo = certInfoBuilder.build()
            vicalBuilder.addCertificateInfo(certInfo)
        }
        val vical = vicalBuilder.build()
        try {
            File("vical_$serial.txt").writeText(vical.toString())
        } catch (e: IOException) {
            throw RuntimeException("Could not create textual vical file", e)
        }
        val encoder = Vical.Encoder()
        val vicalDataStructure: Map = encoder.encode(vical)
        var unsignedVical: ByteArray
        try {
            ByteArrayOutputStream().use { baos ->
                val cborEncoder = CborEncoder(baos)
                cborEncoder.encode(vicalDataStructure)
                unsignedVical = baos.toByteArray()
            }
        } catch (e: CborException) {
            // TODO provide better runtime exception
            throw RuntimeException("Uncaught exception, blame developer", e)
        } catch (e: IOException) {
            throw RuntimeException("Uncaught exception, blame developer", e)
        }
        val signer: VicalSigner =
            IdentityCredentialVicalSigner(signingCertificate, signingKey!!, "SHA384WithECDSA")
        val signature: ByteArray
        signature = try {
            signer.createCose1Signature(unsignedVical)
        } catch (e: CertificateEncodingException) {
            // TODO provide better runtime exception
            throw RuntimeException("Uncaught exception, blame developer", e)
        }
        println(unsignedVical.size)
        println(Hex.toHexString(unsignedVical))
        println(signature.size)
        println(Hex.toHexString(signature))
        val decoder = CborDecoder(ByteArrayInputStream(signature))
        try {
            val tree = decoder.decode()
            System.out.println(tree)
        } catch (e: CborException) {
            // TODO provide better runtime exception
            throw RuntimeException("Uncaught exception, blame developer", e)
        }
        return signature
    }

    @Throws(CertificateException::class, FileNotFoundException::class)
    private fun readCertificate(possibleCertFile: File): X509Certificate {
        val certFactory: CertificateFactory
        certFactory = try {
            CertificateFactory.getInstance("X509")
        } catch (e: CertificateException) {
            // TODO provide better runtime exception
            throw RuntimeException("Uncaught exception, blame developer", e)
        }
        return certFactory.generateCertificate(FileInputStream(possibleCertFile)) as X509Certificate
    }

    private fun readPEMECPrivateKey(filename: String): PrivateKey {
        try {
            PemReader(FileReader(filename)).use { pemReader ->
                val pemObject = pemReader.readPemObject()
                val ecdsa = KeyFactory.getInstance("EC")
                return ecdsa.generatePrivate(PKCS8EncodedKeySpec(pemObject.content))
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e)
        } catch (e: InvalidKeySpecException) {
            throw RuntimeException(e)
        }
    }
}