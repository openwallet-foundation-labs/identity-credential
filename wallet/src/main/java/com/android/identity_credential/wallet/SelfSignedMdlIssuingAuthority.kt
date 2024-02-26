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
import com.android.identity.cbor.dataItem
import com.android.identity.cbor.dateTimeString
import com.android.identity.cose.Cose
import com.android.identity.cose.CoseLabel
import com.android.identity.cose.CoseNumberLabel
import com.android.identity.credential.NameSpacedData
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Certificate
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.EcPublicKey
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialPresentationFormat
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnelResult
import com.android.identity.issuance.evidence.EvidenceResponseIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceResponseMessage
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity.issuance.simple.SimpleIcaoNfcTunnelDriver
import com.android.identity.issuance.simple.SimpleIssuingAuthority
import com.android.identity.issuance.simple.SimpleIssuingAuthorityProofingGraph
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity_credential.mrtd.MrtdNfcData
import com.android.identity_credential.mrtd.MrtdNfcDataDecoder
import kotlinx.datetime.Clock
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.time.Duration.Companion.days


class SelfSignedMdlIssuingAuthority(
    val application: WalletApplication,
    storageEngine: StorageEngine
): SimpleIssuingAuthority(
    storageEngine
) {
    companion object {
        private const val TAG = "SelfSignedMdlIssuingAuthority"

        val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        val MDL_NAMESPACE = "org.iso.18013.5.1"
        val AAMVA_NAMESPACE = "org.iso.18013.5.1.aamva"
    }

    override lateinit var configuration: IssuingAuthorityConfiguration

    init {
        val baos = ByteArrayOutputStream()
        BitmapFactory.decodeResource(
            application.applicationContext.resources,
            R.drawable.img_erika_portrait
        )
            .compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val icon: ByteArray = baos.toByteArray()
        configuration = IssuingAuthorityConfiguration(
            "mDL_Utopia",
            "Utopia mDL",
            icon,
            setOf(CredentialPresentationFormat.MDOC_MSO),
            createCredentialConfiguration(null)
        )
    }

    override fun createPresentationData(presentationFormat: CredentialPresentationFormat,
                                        credentialConfiguration: CredentialConfiguration,
                                        authenticationKey: EcPublicKey
    ): ByteArray {
        // Right now we only support mdoc
        check(presentationFormat == CredentialPresentationFormat.MDOC_MSO)

        val now = Timestamp.now()

        // Create AuthKeys and MSOs, make sure they're valid for a long time
        val timeSigned = now
        val validFrom = now
        val validUntil = Timestamp.ofEpochMilli(validFrom.toEpochMilli() + 365*24*3600*1000L)

        // Generate an MSO and issuer-signed data for this authentication key.
        val msoGenerator = MobileSecurityObjectGenerator(
            "SHA-256",
            MDL_DOCTYPE,
            authenticationKey
        )
        msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)
        val randomProvider = Random.Default
        val issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
            credentialConfiguration.staticData,
            randomProvider,
            16,
            null
        )
        for (nameSpaceName in issuerNameSpaces.keys) {
            val digests = MdocUtil.calculateDigestsForNameSpace(
                nameSpaceName,
                issuerNameSpaces,
                Algorithm.SHA256
            )
            msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests)
        }

        ensureDocumentSigningKey()
        val mso = msoGenerator.generate()
        val taggedEncodedMso = Cbor.encode(Tagged(Tagged.ENCODED_CBOR, Bstr(mso)))
        val issuerCertChain = listOf(documentSigningKeyCert)
        val protectedHeaders = mapOf<CoseLabel, DataItem>(Pair(
            CoseNumberLabel(Cose.COSE_LABEL_ALG),
            Algorithm.ES256.coseAlgorithmIdentifier.dataItem
        ))
        val unprotectedHeaders = mapOf<CoseLabel, DataItem>(Pair(
            CoseNumberLabel(Cose.COSE_LABEL_X5CHAIN),
            CertificateChain(listOf(Certificate(documentSigningKeyCert.encodedCertificate))).dataItem
        ))
        val encodedIssuerAuth = Cbor.encode(
            Cose.coseSign1Sign(
                documentSigningKey,
                taggedEncodedMso,
                true,
                Algorithm.ES256,
                protectedHeaders,
                unprotectedHeaders
            ).dataItem
        )

        val issuerProvidedAuthenticationData = StaticAuthDataGenerator(
            issuerNameSpaces,
            encodedIssuerAuth
        ).generate()

        Logger.d(TAG, "Created MSO")
        return issuerProvidedAuthenticationData
    }

    private lateinit var documentSigningKey: EcPrivateKey
    private lateinit var documentSigningKeyCert: Certificate

    private fun getRawResourceAsString(@RawRes resourceId: Int): String {
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
        documentSigningKey = EcPrivateKey.fromPem(
            getRawResourceAsString(R.raw.ds_private_key),
            documentSigningKeyCert.publicKey
        )

    }

    override fun createNfcTunnelHandler(): SimpleIcaoNfcTunnelDriver {
        return NfcTunnelDriver()
    }


    override fun getProofingGraphRoot(): SimpleIssuingAuthorityProofingGraph.Node {
        return SimpleIssuingAuthorityProofingGraph.create {
            message(
                "tos",
                """Here's a long string with TOS. Lorem ipsum dolor sit amet, consectetur
                    | adipiscing elit. Vivamus dictum vel metus non mattis. Ut mattis,
                    | ipsum vel hendrerit consectetur, mauris purus ultricies nulla, sit amet
                    | pulvinar odio lorem sed justo. Aliquam erat volutpat.
                    | Nulla facilisi. """.trimMargin().replace("\n", ""),
                "Accept",
                "Do Not Accept",
            )
            choice("path", "How do you want to authenticate", "Continue") {
                on("icaoPassive", "Scan passport") {
                    icaoPassiveAuthentication("passive", listOf(1, 2, 7))
                }
                on("icaoTunnel", "Scan passport with authentication") {
                    icaoTunnel("tunnel", listOf(1, 2, 7)) {
                        whenChipAuthenticated {
                            message("inform",
                                "Excellent! You passport supports chip authentication",
                                "Continue",
                                null
                            )
                        }
                        whenActiveAuthenticated {
                            message("inform",
                                "Nice! You passport supports active authentication",
                                "Continue",
                                null
                            )
                        }
                        whenNotAuthenticated {
                            message("inform",
                                "Your passport only supports passive authentication. " +
                                        "Who knows, maybe it is a clone? There is no protection.",
                                "Continue",
                                null
                            )
                        }
                    }
                }
                on("questions", "Answer questions") {
                    question(
                        "firstName",
                        "What first name should be used for the mDL?",
                        "Erika",
                        "Continue"
                    )
                }
            }
            choice("art", "Select the card art for the credential", "Continue") {
                on("green", "Green") {}
                on("blue", "Blue") {}
                on("red", "Red") {}
            }
            message("message",
                "Your application is about to be sent the ID issuer for " +
                        "verification. You will get notified when the " +
                        "application is approved.",
                "Continue",
                null
            )
        }
    }

    override fun checkEvidence(collectedEvidence: Map<String, EvidenceResponse>): Boolean {
        return (collectedEvidence["tos"] as EvidenceResponseMessage).acknowledged
    }

    override fun generateCredentialConfiguration(collectedEvidence: Map<String, EvidenceResponse>): CredentialConfiguration {
        return createCredentialConfiguration(collectedEvidence)
    }

    private fun createCredentialConfiguration(collectedEvidence: Map<String, EvidenceResponse>?): CredentialConfiguration {
        if (collectedEvidence == null) {
            return CredentialConfiguration(
                "Utopia mDL (pending)",
                createArtwork(
                    Color.rgb(192, 192, 192),
                    Color.rgb(96, 96, 96),
                    null,
                    "mDL (Pending)",
                ),
                NameSpacedData.Builder().build()
            )
        }

        val icaoPassiveData = collectedEvidence["passive"]
        val icaoTunnelData = collectedEvidence["tunnel"]
        val firstName: String
        val lastName: String
        val portrait: ByteArray
        val signatureOrUsualMark: ByteArray
        val sex: Long
        if (icaoPassiveData is EvidenceResponseIcaoPassiveAuthentication ||
            icaoTunnelData is EvidenceResponseIcaoNfcTunnelResult) {
            val mrtdData = if (icaoTunnelData is EvidenceResponseIcaoNfcTunnelResult)
                    MrtdNfcData(icaoTunnelData.dataGroups, icaoTunnelData.securityObject)
                else if (icaoPassiveData is EvidenceResponseIcaoPassiveAuthentication)
                    MrtdNfcData(icaoPassiveData.dataGroups, icaoPassiveData.securityObject)
                else
                    throw IllegalStateException("Should not happen")
            val decoder = MrtdNfcDataDecoder(application.cacheDir)
            val decoded = decoder.decode(mrtdData)
            firstName = decoded.firstName
            lastName = decoded.lastName
            sex = when (decoded.gender) {
                "MALE" -> 1
                "FEMALE" -> 2
                else -> 0
            }
            portrait = bitmapData(decoded.photo, R.drawable.img_erika_portrait)
            signatureOrUsualMark = bitmapData(decoded.signature, R.drawable.img_erika_signature)
        } else {
            val evidenceWithName = collectedEvidence["firstName"]
            firstName = (evidenceWithName as EvidenceResponseQuestionString).answer
            lastName = "Mustermann"
            sex = 2
            portrait = bitmapData(null, R.drawable.img_erika_portrait)
            signatureOrUsualMark = bitmapData(null, R.drawable.img_erika_signature)
        }


        val cardArtColor = (collectedEvidence["art"] as EvidenceResponseQuestionMultipleChoice).answerId
        val gradientColor = when (cardArtColor) {
            "green" -> {
                Pair(
                    android.graphics.Color.rgb(64, 255, 64),
                    android.graphics.Color.rgb(0, 96, 0),
                )
            }
            "blue" -> {
                Pair(
                    android.graphics.Color.rgb(64, 64, 255),
                    android.graphics.Color.rgb(0, 0, 96),
                )
            }
            "red" -> {
                Pair(
                    android.graphics.Color.rgb(255, 64, 64),
                    android.graphics.Color.rgb(96, 0, 0),
                )
            }
            else -> {
                Pair(
                    android.graphics.Color.rgb(255, 255, 64),
                    android.graphics.Color.rgb(96, 96, 0),
                )
            }
        }


        val now = Clock.System.now()
        val issueDate = now
        val expiryDate = now + 5.days*365

        val staticData = NameSpacedData.Builder()
            .putEntryString(MDL_NAMESPACE, "given_name", firstName)
            .putEntryString(MDL_NAMESPACE, "family_name", lastName)
            .putEntryByteString(MDL_NAMESPACE, "portrait", portrait)
            .putEntryByteString(MDL_NAMESPACE, "signature_usual_mark", signatureOrUsualMark)
            .putEntryNumber(MDL_NAMESPACE, "sex", sex)
            .putEntry(MDL_NAMESPACE, "issue_date", Cbor.encode(issueDate.dateTimeString))
            .putEntry(MDL_NAMESPACE, "expiry_date", Cbor.encode(expiryDate.dateTimeString))
            .putEntryString(MDL_NAMESPACE, "document_number", "1234567890")
            .putEntryString(MDL_NAMESPACE, "issuing_authority", "State of Utopia")
            .putEntryString(AAMVA_NAMESPACE, "DHS_compliance", "F")
            .putEntryNumber(AAMVA_NAMESPACE, "EDL_credential", 1)
            .putEntryBoolean(MDL_NAMESPACE, "age_over_18", true)
            .putEntryBoolean(MDL_NAMESPACE, "age_over_21", true)
            .build()

        return CredentialConfiguration(
            "${firstName}'s mDL",
            createArtwork(
                gradientColor.first,
                gradientColor.second,
                portrait,
                "${firstName}'s mDL",
            ),
            staticData
        )
    }

    private fun bitmapData(bitmap: Bitmap?, defaultResourceId: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        (bitmap
            ?: BitmapFactory.decodeResource(
                application.applicationContext.resources,
                defaultResourceId
            )).compress(Bitmap.CompressFormat.JPEG, 90, baos)
        return baos.toByteArray()
    }

    private fun createArtwork(color1: Int,
                              color2: Int,
                              portrait: ByteArray?,
                              artworkText: String): ByteArray {
        val width = 800
        val height = ceil(width.toFloat() * 2.125 / 3.375).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint()
        bgPaint.setShader(
            RadialGradient(
                width / 2f, height / 2f,
                height / 0.5f, color1, color2, Shader.TileMode.MIRROR
            )
        )
        val round = bitmap.width / 25f
        canvas.drawRoundRect(
            0f,
            0f,
            bitmap.width.toFloat(),
            bitmap.height.toFloat(),
            round,
            round,
            bgPaint
        )

        if (portrait != null) {
            val portraitBitmap = BitmapFactory.decodeByteArray(portrait, 0, portrait.size)
            val src = Rect(0, 0, portraitBitmap.width, portraitBitmap.height)
            val scale = height * 0.7f / portraitBitmap.height.toFloat()
            val dst = RectF(round, round, portraitBitmap.width*scale, portraitBitmap.height*scale);
            canvas.drawBitmap(portraitBitmap, src, dst, bgPaint)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.setColor(android.graphics.Color.WHITE)
        paint.textSize = bitmap.width / 10.0f
        paint.setShadowLayer(2.0f, 1.0f, 1.0f, android.graphics.Color.BLACK)
        val bounds = Rect()
        paint.getTextBounds(artworkText, 0, artworkText.length, bounds)
        val textPadding = bitmap.width/25f
        val x: Float = textPadding
        val y: Float = bitmap.height - bounds.height() - textPadding + paint.textSize/2
        canvas.drawText(artworkText, x, y, paint)

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
        return baos.toByteArray()
    }

}