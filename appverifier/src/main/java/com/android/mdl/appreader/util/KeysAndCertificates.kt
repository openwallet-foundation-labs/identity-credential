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

object KeysAndCertificates {
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

    fun getGoogleReaderCA(context: Context) =
        getCertificate(context, R.raw.google_reader_ca)

    fun getTrustedIssuerCertificates(context: Context) =
        listOf(
            getCertificate(context, R.raw.apple_iaca),
            getCertificate(context, R.raw.bdr_iaca),
            getCertificate(context, R.raw.cacert_youniqx),
            getCertificate(context, R.raw.cbn_iaca),
            getCertificate(context, R.raw.cbn_iaca_ky),
            getCertificate(context, R.raw.cbn_interop_aus),
            getCertificate(context, R.raw.cbn_interop_aus_2),
            getCertificate(context, R.raw.clear_iaca_root_cert),
            getCertificate(context, R.raw.credenceid_mdl_iaca_root),
            getCertificate(context, R.raw.fast_google_root_certificate),
            getCertificate(context, R.raw.google_mdl_iaca_cert),
            getCertificate(context, R.raw.google_mekb_iaca_cert),
            getCertificate(context, R.raw.google_micov_csca_cert),
            getCertificate(context, R.raw.google_reader_ca),
            getCertificate(context, R.raw.hidtestcscamicov_cert),
            getCertificate(context, R.raw.hidtestiacamdl_cert),
            getCertificate(context, R.raw.hidtestiacamekb_cert),
            getCertificate(context, R.raw.iaca_nec_mdl_iaca_cert),
            getCertificate(context, R.raw.iaca_utms),
            getCertificate(context, R.raw.iaca_utms_cer),
            getCertificate(context, R.raw.iaca_zetes),
            getCertificate(context, R.raw.iaca_zetes_cer),
            getCertificate(context, R.raw.idemia_brisbane_interop_iaca),
            getCertificate(context, R.raw.iso_iaca_tmr_cer),
            getCertificate(context, R.raw.louisiana_department_of_motor_vehicles_cert),
            getCertificate(context, R.raw.mdl_iaca_thales_multicert),
            getCertificate(context, R.raw.pid_iaca_int_gen_01_cer),
            getCertificate(context, R.raw.rdw_mekb_testset),
            getCertificate(context, R.raw.samsung_iaca_test_cert_cer),
            getCertificate(context, R.raw.scytales_root_ca),
            getCertificate(context, R.raw.spruce_iaca_cert),
            getCertificate(context, R.raw.thalesinterop2022iaca),
            getCertificate(context, R.raw.ul_cert_iaca_01),
            getCertificate(context, R.raw.ul_cert_iaca_01_cer),
            getCertificate(context, R.raw.ul_cert_iaca_02),
            getCertificate(context, R.raw.ul_cert_iaca_02_cer),
            getCertificate(context, R.raw.ul_micov_testset),
        )

}