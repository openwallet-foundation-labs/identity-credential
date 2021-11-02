package com.android.mdl.appreader.util

import android.content.Context
import com.android.mdl.appreader.R
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

object IssuerKeys {
    private fun getKeyBytes(keyInputStream: InputStream): ByteArray {
        val keyBytes = keyInputStream.readBytes()
        val publicKeyPEM = String(keyBytes, StandardCharsets.US_ASCII)
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")

        return Base64.getDecoder().decode(publicKeyPEM)
    }

    private fun getPrivateKey(context: Context, resourceId: Int): PrivateKey {
        val keyBytes: ByteArray = getKeyBytes(context.resources.openRawResource(resourceId))
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePrivate(spec)
    }

    private fun getCertificate(context: Context, resourceId: Int): X509Certificate {
        val certInputStream = context.resources.openRawResource(resourceId)
        val factory: CertificateFactory = CertificateFactory.getInstance("X509")
        return factory.generateCertificate(certInputStream) as X509Certificate
    }

    fun getRAGooglePrivateKey(context: Context) =
        getPrivateKey(context, R.raw.google_mdl_ra_privkey)

    fun getRAGoogleCertificate(context: Context) =
        getCertificate(context, R.raw.google_mdl_ra_cert)

    fun getTrustedIssuerCertificates(context: Context) =
        listOf(
            getCertificate(context, R.raw.google_mdl_iaca_cert),
            getCertificate(context, R.raw.google_mekb_iaca_cert),
            getCertificate(context, R.raw.google_micov_csca_cert),
        )

}