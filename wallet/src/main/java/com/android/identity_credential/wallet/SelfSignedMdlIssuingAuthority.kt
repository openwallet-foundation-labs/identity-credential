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
import com.android.identity.credential.NameSpacedData
import com.android.identity.credentialtype.knowntypes.DrivingLicense
import com.android.identity.credentialtype.knowntypes.Options
import com.android.identity.internal.Util
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialPresentationFormat
import com.android.identity.issuance.IssuingAuthorityConfiguration
import com.android.identity.issuance.evidence.EvidenceRequestIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceRequestMessage
import com.android.identity.issuance.evidence.EvidenceRequestQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceRequestQuestionString
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseIcaoPassiveAuthentication
import com.android.identity.issuance.evidence.EvidenceResponseMessage
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.issuance.evidence.EvidenceResponseQuestionString
import com.android.identity.issuance.simple.SimpleIssuingAuthority
import com.android.identity.issuance.simple.SimpleIssuingAuthorityProofingGraph
import com.android.identity.issuance.simple.SimpleIssuingAuthorityProofingGraph.SimpleNode
import com.android.identity.issuance.simple.SimpleIssuingAuthorityProofingGraph.MultipleChoiceNode
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.EcCurve
import com.android.identity.securearea.SecureArea
import com.android.identity.storage.StorageEngine
import com.android.identity.util.Logger
import com.android.identity.util.Timestamp
import com.android.identity_credential.mrtd.MrtdNfcData
import com.android.identity_credential.mrtd.MrtdNfcDataDecoder
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import kotlin.math.ceil

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
                                        authenticationKey: PublicKey
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
            authenticationKey,
            EcCurve.P256
        )
        msoGenerator.setValidityInfo(timeSigned, validFrom, validUntil, null)
        val randomProvider = SecureRandom()
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
                "SHA-256"
            )
            msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests)
        }

        ensureIssuerSigningKeys()
        val mso = msoGenerator.generate()
        val taggedEncodedMso = Util.cborEncode(Util.cborBuildTaggedByteString(mso))
        val issuerCertChain = listOf(issuerSigningKeyCert!!)
        val encodedIssuerAuth = Util.cborEncode(
            Util.coseSign1Sign(
                issuerSigningKeyPair!!.private,
                "SHA256withECDSA", taggedEncodedMso,
                null,
                issuerCertChain
            )
        )

        val issuerProvidedAuthenticationData = StaticAuthDataGenerator(
            issuerNameSpaces,
            encodedIssuerAuth
        ).generate()

        Logger.d(TAG, "Created MSO")
        return issuerProvidedAuthenticationData
    }

    var issuerSigningKeyPair: KeyPair? = null
    var issuerSigningKeyCert: X509Certificate? = null

    private fun ensureIssuerSigningKeys() {
        if (issuerSigningKeyPair != null) {
            return
        }
        issuerSigningKeyPair = createIssuingAuthorityKeyPair()
        issuerSigningKeyCert = getSelfSignedIssuerAuthorityCertificate(issuerSigningKeyPair!!)
    }

    private fun createIssuingAuthorityKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        kpg.initialize(ecSpec)
        return kpg.generateKeyPair()
    }

    private fun getSelfSignedIssuerAuthorityCertificate(
        issuerAuthorityKeyPair: KeyPair
    ): X509Certificate {
        val issuer: X500Name = X500Name("CN=State Of Utopia")
        val subject: X500Name = X500Name("CN=State Of Utopia Issuing Authority Signing Key")

        // Valid from now to five years from now.
        val now = Date()
        val kMilliSecsInOneYear = 365L * 24 * 60 * 60 * 1000
        val expirationDate = Date(now.time + 5 * kMilliSecsInOneYear)
        val serial = BigInteger("42")
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            now,
            expirationDate,
            subject,
            issuerAuthorityKeyPair.public
        )
        val signer: ContentSigner = JcaContentSignerBuilder("SHA256withECDSA")
            .build(issuerAuthorityKeyPair.private)
        val certHolder: X509CertificateHolder = builder.build(signer)
        return JcaX509CertificateConverter().getCertificate(certHolder)
    }

    override fun getProofingGraphRoot(): SimpleIssuingAuthorityProofingGraph.Node {
        // TODO: use Kotlin DSL for evidence request and graph builders
        val tail = SimpleIssuingAuthorityProofingGraph()
            .add(SimpleNode("art",
                EvidenceRequestQuestionMultipleChoice(
                    "Select the card art for the credential",
                    listOf("Green", "Blue", "Red"),
                    "Continue",
                )))
            .add(SimpleNode("message",
                EvidenceRequestMessage(
                    "Your application is about to be sent the ID issuer for " +
                            "verification. You will get notified when the " +
                            "application is approved.",
                    "Continue",
                    null,
                )))
            .build()
        return SimpleIssuingAuthorityProofingGraph()
            .add(SimpleNode("tos",
            EvidenceRequestMessage(
                "Here's a long string with TOS",
                "Accept",
                "Do Not Accept",
            )))
            .addMultipleChoice(MultipleChoiceNode("path",
                EvidenceRequestQuestionMultipleChoice(
                    "How do you want to authenticate",
                    listOf("Scan passport", "Answer questions"),
                    "Continue",
                ),
                mapOf(
                    "Scan passport" to SimpleIssuingAuthorityProofingGraph()
                        .add(SimpleNode("passport",
                            EvidenceRequestIcaoPassiveAuthentication(listOf(1, 2))))
                        .addTail(tail)
                        .build(),
                    "Answer questions" to SimpleIssuingAuthorityProofingGraph()
                        .add(SimpleNode("firstName",
                            EvidenceRequestQuestionString(
                                "What first name should be used for the mDL?",
                                "Erika",
                                "Continue",
                            )))
                        .addTail(tail)
                        .build()
                )))
                .build()
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

        val icaoData = collectedEvidence["passport"]
        lateinit var firstName: String
        lateinit var lastName: String
        lateinit var portrait: ByteArray
        var sex: Long
        if (icaoData is EvidenceResponseIcaoPassiveAuthentication) {
            val mrtdData = MrtdNfcData(
                icaoData.dataGroups[1]!!, icaoData.dataGroups[2]!!, icaoData.securityObject)
            val decoder = MrtdNfcDataDecoder(application.cacheDir)
            val decoded = decoder.decode(mrtdData)
            firstName = decoded.firstName
            lastName = decoded.lastName
            sex = when (decoded.gender) {
                "MALE" -> 1
                "FEMALE" -> 2
                else -> 0
            }
            val baos = ByteArrayOutputStream()
            decoded.photo!!.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            portrait = baos.toByteArray()
        } else {
            val evidenceWithName = collectedEvidence["firstName"]
            firstName = (evidenceWithName as EvidenceResponseQuestionString).answer
            lastName = "Mustermann"
            sex = 2
            val baos = ByteArrayOutputStream()
            BitmapFactory.decodeResource(
                application.applicationContext.resources,
                R.drawable.img_erika_portrait
            ).compress(Bitmap.CompressFormat.JPEG, 90, baos)
            portrait = baos.toByteArray()
        }


        val cardArtColor = (collectedEvidence["art"] as EvidenceResponseQuestionMultipleChoice).answer
        val gradientColor = when (cardArtColor) {
            "Green" -> {
                Pair(
                    android.graphics.Color.rgb(64, 255, 64),
                    android.graphics.Color.rgb(0, 96, 0),
                )
            }
            "Blue" -> {
                Pair(
                    android.graphics.Color.rgb(64, 64, 255),
                    android.graphics.Color.rgb(0, 0, 96),
                )
            }
            "Red" -> {
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

        val baos = ByteArrayOutputStream()
        BitmapFactory.decodeResource(
            application.applicationContext.resources,
            R.drawable.img_erika_signature
        ).compress(Bitmap.CompressFormat.JPEG, 90, baos)
        val signatureOrUsualMark: ByteArray = baos.toByteArray()

        val now = Timestamp.now()
        val issueDate = now
        val expiryDate = Timestamp.ofEpochMilli(issueDate.toEpochMilli() + 5*365*24*3600*1000L)

        val staticData = NameSpacedData.Builder()
            .putEntryString(MDL_NAMESPACE, "given_name", firstName)
            .putEntryString(MDL_NAMESPACE, "family_name", lastName)
            .putEntryByteString(MDL_NAMESPACE, "portrait", portrait)
            .putEntryByteString(MDL_NAMESPACE, "signature_usual_mark", signatureOrUsualMark)
            .putEntryNumber(MDL_NAMESPACE, "sex", sex)
            .putEntry(MDL_NAMESPACE, "issue_date", Util.cborEncodeDateTime(issueDate))
            .putEntry(MDL_NAMESPACE, "expiry_date", Util.cborEncodeDateTime(expiryDate))
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