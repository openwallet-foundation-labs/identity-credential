package com.android.identity_credential.wallet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.RawRes
import com.android.identity.cbor.Bstr
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.DataItem
import com.android.identity.cbor.Tagged
import com.android.identity.cbor.toDataItem
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.CoseNumberLabel
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Certificate
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.simple.SimpleIssuingAuthority
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.ceil
import kotlin.random.Random

abstract class SelfSignedMdocIssuingAuthority(
    val application: WalletApplication,
    storageEngine: StorageEngine,
) : SimpleIssuingAuthority(
        storageEngine,
    ) {
    companion object {
        private const val TAG = "SelfSignedMdlIssuingAuthority"
    }

    abstract val docType: String

    override fun createPresentationData(
        presentationFormat: CredentialFormat,
        documentConfiguration: DocumentConfiguration,
        authenticationKey: EcPublicKey,
    ): ByteArray {
        // Right now we only support mdoc
        check(presentationFormat == CredentialFormat.MDOC_MSO)

        val now = Timestamp.now()

        // Create AuthKeys and MSOs, make sure they're valid for a long time
        val timeSigned = now
        val validFrom = now
        val validUntil = Timestamp.ofEpochMilli(validFrom.toEpochMilli() + 365 * 24 * 3600 * 1000L)

        // Generate an MSO and issuer-signed data for this authentication key.
        val msoGenerator =
            MobileSecurityObjectGenerator(
                "SHA-256",
                docType,
                authenticationKey,
            )
        msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)
        val randomProvider = Random.Default
        val issuerNameSpaces =
            MdocUtil.generateIssuerNameSpaces(
                documentConfiguration.staticData,
                randomProvider,
                16,
                null,
            )
        for (nameSpaceName in issuerNameSpaces.keys) {
            val digests =
                MdocUtil.calculateDigestsForNameSpace(
                    nameSpaceName,
                    issuerNameSpaces,
                    Algorithm.SHA256,
                )
            msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests)
        }

        ensureDocumentSigningKey()
        val mso = msoGenerator.generate()
        val taggedEncodedMso = Cbor.encode(Tagged(Tagged.ENCODED_CBOR, Bstr(mso)))
        val protectedHeaders =
            mapOf<CoseLabel, DataItem>(
                Pair(
                    CoseNumberLabel(Cose.COSE_LABEL_ALG),
                    Algorithm.ES256.coseAlgorithmIdentifier.toDataItem,
                ),
            )
        val unprotectedHeaders =
            mapOf<CoseLabel, DataItem>(
                Pair(
                    CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
                    CertificateChain(listOf(Certificate(documentSigningKeyCert.encodedCertificate))).toDataItem,
                ),
            )
        val encodedIssuerAuth =
            Cbor.encode(
                Cose.coseSign1Sign(
                    documentSigningKey,
                    taggedEncodedMso,
                    true,
                    Algorithm.ES256,
                    protectedHeaders,
                    unprotectedHeaders,
                ).toDataItem,
            )

        val issuerProvidedAuthenticationData =
            StaticAuthDataGenerator(
                issuerNameSpaces,
                encodedIssuerAuth,
            ).generate()

        Logger.d(TAG, "Created MSO")
        return issuerProvidedAuthenticationData
    }

    private lateinit var documentSigningKey: EcPrivateKey
    private lateinit var documentSigningKeyCert: Certificate

    private fun getRawResourceAsString(
        @RawRes resourceId: Int,
    ): String {
        val inputStream = application.applicationContext.resources.openRawResource(resourceId)
        val bytes = inputStream.readBytes()
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun ensureDocumentSigningKey() {
        if (this::documentSigningKey.isInitialized) {
            return
        }

        // The IACA and DS certificates and keys can be regenerated using the following steps
        //
        // $ ./gradlew --quiet runIdentityCtl --args generateIaca
        // Generated IACA certificate and private key.
        // - Wrote private key to iaca_private_key.pem
        // - Wrote IACA certificate to iaca_certificate.pem
        //
        // $ ./gradlew --quiet runIdentityCtl --args generateDs
        // Generated DS certificate and private key.
        // - Wrote private key to ds_private_key.pem
        // - Wrote DS certificate to ds_certificate.pem
        //
        // $ mv identityctl/iaca_*.pem wallet/src/main/res/raw/
        // $ cp wallet/src/main/res/raw/iaca_certificate.pem appverifier/src/main/res/raw/owf_wallet_iaca_root.pem
        // $ mv identityctl/ds_*.pem wallet/src/main/res/raw/
        //

        documentSigningKeyCert = Certificate.fromPem(getRawResourceAsString(R.raw.ds_certificate))
        Logger.d(TAG, "Cert: " + documentSigningKeyCert.javaX509Certificate.toString())
        documentSigningKey =
            EcPrivateKey.fromPem(
                getRawResourceAsString(R.raw.ds_private_key),
                documentSigningKeyCert.publicKey,
            )
    }

    protected fun bitmapData(defaultResourceId: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        BitmapFactory.decodeResource(
            application.applicationContext.resources,
            defaultResourceId,
        ).compress(Bitmap.CompressFormat.JPEG, 90, baos)
        return baos.toByteArray()
    }

    protected fun createArtwork(
        color1: Int,
        color2: Int,
        portrait: ByteArray?,
        artworkText: String,
    ): ByteArray {
        val width = 800
        val height = ceil(width.toFloat() * 2.125 / 3.375).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint()
        bgPaint.setShader(
            RadialGradient(
                width / 2f,
                height / 2f,
                height / 0.5f,
                color1,
                color2,
                Shader.TileMode.MIRROR,
            ),
        )
        val round = bitmap.width / 25f
        canvas.drawRoundRect(
            0f,
            0f,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            round,
            round,
            bgPaint,
        )

        if (portrait != null) {
            val portraitBitmap = BitmapFactory.decodeByteArray(portrait, 0, portrait.size)
            val src = Rect(0, 0, portraitBitmap.width, portraitBitmap.height)
            val scale = height * 0.7f / portraitBitmap.height.toFloat()
            val dst = RectF(round, round, portraitBitmap.width * scale, portraitBitmap.height * scale)
            canvas.drawBitmap(portraitBitmap, src, dst, bgPaint)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.setColor(Color.WHITE)
        paint.textSize = bitmap.width / 10.0f
        paint.setShadowLayer(2.0f, 1.0f, 1.0f, Color.BLACK)
        val bounds = Rect()
        paint.getTextBounds(artworkText, 0, artworkText.length, bounds)
        val textPadding = bitmap.width / 25f
        val x: Float = textPadding
        val y: Float = bitmap.height - bounds.height() - textPadding + paint.textSize / 2
        canvas.drawText(artworkText, x, y, paint)

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }

    protected fun resourceString(
        id: Int,
        vararg text: String,
    ): String {
        return application.applicationContext.resources.getString(id, *text)
    }

    protected fun resourceBytes(id: Int): ByteArray {
        val stream = application.applicationContext.resources.openRawResource(id)
        val bytes = stream.readBytes()
        stream.close()
        return bytes
    }
}
