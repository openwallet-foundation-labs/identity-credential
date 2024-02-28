package com.android.identity.wallet.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.android.identity.credential.Credential
import com.android.identity.credential.CredentialUtil
import com.android.identity.credential.NameSpacedData
import com.android.identity.crypto.Algorithm
import com.android.identity.internal.Util
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.util.Timestamp
import com.android.identity.wallet.HolderApp
import com.android.identity.wallet.document.DocumentInformation
import com.android.identity.wallet.document.KeysAndCertificates
import com.android.identity.wallet.selfsigned.ProvisionInfo
import com.android.identity.wallet.support.SecureAreaSupport
import com.android.identity.wallet.util.DocumentData.MICOV_DOCTYPE
import com.android.identity.wallet.util.DocumentData.MVR_DOCTYPE
import java.io.ByteArrayOutputStream
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

class ProvisioningUtil private constructor(
    private val context: Context
) {

    val secureAreaRepository = SecureAreaRepository()
    val credentialStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        HolderApp.createCredentialStore(context, secureAreaRepository)
    }

    fun provisionSelfSigned(
        nameSpacedData: NameSpacedData,
        provisionInfo: ProvisionInfo,
    ) {
        val credential = credentialStore.createCredential(
            provisionInfo.credentialName())
        credential.applicationData.setNameSpacedData("credentialData", nameSpacedData)

        val authKeySecureArea: SecureArea = provisionInfo.currentSecureArea.secureArea

        // Store all settings for the credential that are not SecureArea specific
        credential.applicationData.setString(USER_VISIBLE_NAME, provisionInfo.docName)
        credential.applicationData.setString(DOCUMENT_TYPE, provisionInfo.docType)
        credential.applicationData.setString(DATE_PROVISIONED, dateTimeFormatter.format(ZonedDateTime.now()))
        credential.applicationData.setNumber(CARD_ART, provisionInfo.docColor.toLong())
        credential.applicationData.setBoolean(IS_SELF_SIGNED, true)
        credential.applicationData.setNumber(MAX_USAGES_PER_KEY, provisionInfo.maxUseMso.toLong())
        credential.applicationData.setNumber(VALIDITY_IN_DAYS, provisionInfo.validityInDays.toLong())
        credential.applicationData.setNumber(MIN_VALIDITY_IN_DAYS, provisionInfo.minValidityInDays.toLong())
        credential.applicationData.setNumber(LAST_TIME_USED, -1)
        credential.applicationData.setString(AUTH_KEY_SECURE_AREA_IDENTIFIER, authKeySecureArea.identifier)
        credential.applicationData.setNumber(NUM_AUTH_KEYS, provisionInfo.numberMso.toLong())

        // Store settings for auth-key creation, these are all SecureArea-specific and we store
        // them in a single blob at AUTH_KEY_SETTINGS
        val support = SecureAreaSupport.getInstance(context, authKeySecureArea)
        credential.applicationData.setData(
            AUTH_KEY_SETTINGS,
            support.createAuthKeySettingsConfiguration(provisionInfo.secureAreaSupportState))

        // Create initial batch of auth keys
        refreshAuthKeys(credential, provisionInfo.docType)
    }

    private fun ProvisionInfo.credentialName(): String {
        val regex = Regex("[^A-Za-z0-9 ]")
        return regex.replace(docName, "").replace(" ", "_").lowercase()
    }

    fun trackUsageTimestamp(credential: Credential) {
        val now = Timestamp.now()
        credential.applicationData.setNumber(LAST_TIME_USED, now.toEpochMilli())
    }

    fun refreshAuthKeys(credential: Credential, docType: String) {
        val secureAreaIdentifier = credential.applicationData.getString(AUTH_KEY_SECURE_AREA_IDENTIFIER)
        val minValidTimeDays = credential.applicationData.getNumber(MIN_VALIDITY_IN_DAYS)
        val maxUsagesPerKey = credential.applicationData.getNumber(MAX_USAGES_PER_KEY)
        val numAuthKeys = credential.applicationData.getNumber(NUM_AUTH_KEYS)
        val validityInDays = credential.applicationData.getNumber(VALIDITY_IN_DAYS).toInt()

        val now = Timestamp.now()
        val validFrom = now
        val validUntil = Timestamp.ofEpochMilli(validFrom.toEpochMilli() + validityInDays*86400*1000L)

        val secureArea = secureAreaRepository.getImplementation(secureAreaIdentifier)
            ?: throw IllegalStateException("No Secure Area with id ${secureAreaIdentifier} for credential ${credential.name}")

        val support = SecureAreaSupport.getInstance(context, secureArea)
        val settings = support.createAuthKeySettingsFromConfiguration(
            credential.applicationData.getData(AUTH_KEY_SETTINGS),
            "challenge".toByteArray(),
            validFrom,
            validUntil
        )

        val pendingKeysCount = CredentialUtil.managedAuthenticationKeyHelper(
            credential,
            secureArea,
            settings,
            AUTH_KEY_DOMAIN,
            now,
            numAuthKeys.toInt(),
            maxUsagesPerKey.toInt(),
            minValidTimeDays*24*60*60*1000L,
            false
        )
        if (pendingKeysCount <= 0) {
            return
        }

        for (pendingAuthKey in credential.pendingAuthenticationKeys) {
            val msoGenerator = MobileSecurityObjectGenerator(
                "SHA-256",
                docType,
                pendingAuthKey.attestation.certificates.first().publicKey
            )
            msoGenerator.setValidityInfo(now, validFrom, validUntil, null)

            // For mDLs, override the portrait with AuthenticationKeyCounter on top
            //
            var dataElementExceptions: Map<String, List<String>>? = null
            var dataElementOverrides: Map<String, Map<String, ByteArray>>? = null
            if (docType.equals("org.iso.18013.5.1.mDL")) {
                val portrait = credential.applicationData.getNameSpacedData("credentialData")
                    .getDataElementByteString("org.iso.18013.5.1", "portrait")
                val portrait_override = overridePortrait(portrait,
                    pendingAuthKey.authenticationKeyCounter)

                dataElementExceptions =
                    mapOf("org.iso.18013.5.1" to listOf("given_name", "portrait"))
                dataElementOverrides =
                    mapOf("org.iso.18013.5.1" to mapOf(
                        "portrait" to Util.cborEncodeBytestring(portrait_override)))
            }

            val issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
                credential.applicationData.getNameSpacedData("credentialData"),
                Random.Default,
                16,
                dataElementOverrides
            )

            for (nameSpaceName in issuerNameSpaces.keys) {
                val digests = MdocUtil.calculateDigestsForNameSpace(
                    nameSpaceName,
                    issuerNameSpaces,
                    Algorithm.SHA256
                )
                msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests)
            }

            val mso = msoGenerator.generate()
            val taggedEncodedMso = Util.cborEncode(Util.cborBuildTaggedByteString(mso))

            val issuerKeyPair = when (docType) {
                MVR_DOCTYPE -> KeysAndCertificates.getMekbDsKeyPair(context)
                MICOV_DOCTYPE -> KeysAndCertificates.getMicovDsKeyPair(context)
                else -> KeysAndCertificates.getMdlDsKeyPair(context)
            }

            val issuerCert = when (docType) {
                MVR_DOCTYPE -> KeysAndCertificates.getMekbDsCertificate(context)
                MICOV_DOCTYPE -> KeysAndCertificates.getMicovDsCertificate(context)
                else -> KeysAndCertificates.getMdlDsCertificate(context)
            }

            val issuerCertChain = ArrayList<X509Certificate>()
            issuerCertChain.add(issuerCert)
            val encodedIssuerAuth = Util.cborEncode(
                Util.coseSign1Sign(
                    issuerKeyPair.private,
                    "SHA256withECDSA",
                    taggedEncodedMso,
                    null,
                    issuerCertChain
                )
            )

            val issuerProvidedAuthenticationData = StaticAuthDataGenerator(
                MdocUtil.stripIssuerNameSpaces(issuerNameSpaces, dataElementExceptions),
                encodedIssuerAuth
            ).generate()

            pendingAuthKey.certify(
                issuerProvidedAuthenticationData,
                validFrom,
                validUntil
            )
        }
    }

    // Puts the string "MSO ${counter}" on top of the portrait image.
    private fun overridePortrait(encodedPortrait: ByteArray, counter: Number): ByteArray {
        val options = BitmapFactory.Options()
        options.inMutable = true
        val bitmap = BitmapFactory.decodeByteArray(
            encodedPortrait,
            0,
            encodedPortrait.size,
            options)

        val text = "MSO ${counter}"
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.setColor(Color.WHITE)
        paint.textSize = bitmap.width / 5.0f
        paint.setShadowLayer(2.0f, 1.0f, 1.0f, Color.BLACK)
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val x: Float = (bitmap.width - bounds.width()) / 2.0f
        val y: Float = (bitmap.height - bounds.height()) / 4.0f
        canvas.drawText(text, x, y, paint)

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos)
        val encodedModifiedPortrait: ByteArray = baos.toByteArray()

        return encodedModifiedPortrait
    }

    companion object {

        const val AUTH_KEY_DOMAIN = "mdoc/MSO"
        private const val USER_VISIBLE_NAME = "userVisibleName"
        private const val DOCUMENT_TYPE = "documentType"
        private const val DATE_PROVISIONED = "dateProvisioned"
        private const val CARD_ART = "cardArt"
        private const val IS_SELF_SIGNED = "isSelfSigned"
        private const val MAX_USAGES_PER_KEY = "maxUsagesPerKey"
        private const val VALIDITY_IN_DAYS = "validityInDays"
        private const val MIN_VALIDITY_IN_DAYS = "minValidityInDays"
        private const val LAST_TIME_USED = "lastTimeUsed"
        private const val NUM_AUTH_KEYS = "numAuthKeys"
        private const val AUTH_KEY_SETTINGS = "authKeySettings"
        private const val AUTH_KEY_SECURE_AREA_IDENTIFIER = "authKeySecureAreaIdentifier"

        private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: ProvisioningUtil? = null

        fun getInstance(context: Context) = instance ?: synchronized(this) {
            instance ?: ProvisioningUtil(context).also { instance = it }
        }

        val defaultSecureArea: SecureArea
            get() = requireNotNull(instance?.secureAreaRepository?.implementations?.first())

        fun Credential?.toDocumentInformation(): DocumentInformation? {
            return this?.let {

                val authKeySecureAreaIdentifier = it.applicationData.getString(AUTH_KEY_SECURE_AREA_IDENTIFIER)
                val authKeySecureArea = instance!!.secureAreaRepository.getImplementation(authKeySecureAreaIdentifier)
                    ?: throw IllegalStateException("No Secure Area with id ${authKeySecureAreaIdentifier} for credential ${it.name}")

                val authKeys = authenticationKeys.map { key ->
                    val info = authKeySecureArea.getKeyInfo(key.alias)
                    DocumentInformation.KeyData(
                        counter = key.authenticationKeyCounter.toInt(),
                        validFrom = key.validFrom.formatted(),
                        validUntil = key.validUntil.formatted(),
                        domain = key.domain,
                        issuerDataBytesCount = key.issuerProvidedData.size,
                        usagesCount = key.usageCount,
                        keyPurposes = info.keyPurposes.first(),
                        ecCurve = info.publicKey.curve,
                        isHardwareBacked = false,  // TODO: remove
                        secureAreaDisplayName = authKeySecureArea.displayName
                    )
                }
                val lastTimeUsedMillis = it.applicationData.getNumber(LAST_TIME_USED)
                val lastTimeUsed = if (lastTimeUsedMillis == -1L) {
                    ""
                } else {
                    Timestamp.ofEpochMilli(lastTimeUsedMillis).formatted()
                }
                DocumentInformation(
                    userVisibleName = it.applicationData.getString(USER_VISIBLE_NAME),
                    docName = it.name,
                    docType = it.applicationData.getString(DOCUMENT_TYPE),
                    dateProvisioned = it.applicationData.getString(DATE_PROVISIONED),
                    documentColor = it.applicationData.getNumber(CARD_ART).toInt(),
                    selfSigned = it.applicationData.getBoolean(IS_SELF_SIGNED),
                    maxUsagesPerKey = it.applicationData.getNumber(MAX_USAGES_PER_KEY).toInt(),
                    lastTimeUsed = lastTimeUsed,
                    authKeys = authKeys
                )
            }
        }

        private fun Timestamp.formatted(): String {
            val instant = Instant.ofEpochMilli(this.toEpochMilli())
            val dateTime = ZonedDateTime.ofInstant(instant, ZoneId.systemDefault())
            return dateTimeFormatter.format(dateTime)
        }
    }
}