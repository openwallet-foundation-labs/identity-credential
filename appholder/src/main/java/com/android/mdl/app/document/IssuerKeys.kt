package com.android.mdl.app.document

import android.content.Context
import com.android.mdl.app.R
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

object IssuerKeys {

    private fun getCertificate(context: Context, resourceId: Int): X509Certificate {
        val certInputStream = context.resources.openRawResource(resourceId)
        val factory: CertificateFactory = CertificateFactory.getInstance("X509")
        return factory.generateCertificate(certInputStream) as X509Certificate
    }

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

    private fun getPublicKey(context: Context, resourceId: Int): PublicKey {
        val keyBytes: ByteArray = getKeyBytes(context.resources.openRawResource(resourceId))
        val spec = X509EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePublic(spec)
    }

    fun getMdlKeyPair(context: Context) =
        KeyPair(
            getPublicKey(context, R.raw.google_mdl_ds_pubkey),
            getPrivateKey(context, R.raw.google_mdl_ds_privkey)
        )

    fun getMekbKeyPair(context: Context) =
        KeyPair(
            getPublicKey(context, R.raw.google_mekb_ds_pubkey),
            getPrivateKey(context, R.raw.google_mekb_ds_privkey)
        )

    fun getMicovKeyPair(context: Context) =
        KeyPair(
            getPublicKey(context, R.raw.google_micov_ds_pubkey),
            getPrivateKey(context, R.raw.google_micov_ds_privkey)
        )

    fun getMdlCertificate(context: Context) = getCertificate(context, R.raw.google_mdl_ds_cert)

    fun getMekbCertificate(context: Context) = getCertificate(context, R.raw.google_mekb_ds_cert)

    fun getMicovCertificate(context: Context) = getCertificate(context, R.raw.google_micov_ds_cert)

    fun getRABdrCertificate(context: Context) =
        getCertificate(context, R.raw.bdr_iaca_iso_iec_fdis_18013_5_test_only)

    fun getRACbnCertificate(context: Context) =
        getCertificate(context, R.raw.cbn_iaca)

    fun getRAPanasonicCertificate(context: Context) =
        getCertificate(context, R.raw.iaca_zetes)

    fun getRAIdemiaCertificate(context: Context) =
        getCertificate(context, R.raw.idemia_lahague_interop_readerauthca)

    fun getRAThalesCertificate(context: Context) =
        getCertificate(context, R.raw.mdl_iaca_thales_multicert)

    fun getRASamsungCertificate(context: Context) =
        getCertificate(context, R.raw.samsung_iaca_test_cert)

    fun getRAScytalesCertificate(context: Context) =
        getCertificate(context, R.raw.scytales_root_ca)

    fun getRAUl1Certificate(context: Context) =
        getCertificate(context, R.raw.ul_cert_ca_01)

    fun getRAUl2Certificate(context: Context) =
        getCertificate(context, R.raw.ul_cert_ca_02)

    fun getRAGoogleCertificate(context: Context) =
        getCertificate(context, R.raw.google_mdl_ra_cert)
}