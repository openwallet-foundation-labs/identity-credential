package com.android.identity.wallet.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.credential.Credential
import com.android.identity.credential.CredentialUtil
import com.android.identity.credential.NameSpacedData
import com.android.identity.internal.Util
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.securearea.SoftwareSecureArea
import com.android.identity.util.Timestamp
import com.android.identity.wallet.HolderApp
import com.android.identity.wallet.document.DocumentInformation
import com.android.identity.wallet.document.KeysAndCertificates
import com.android.identity.wallet.selfsigned.ProvisionInfo
import com.android.identity.wallet.support.SecureAreaSupport
import com.android.identity.wallet.support.SoftwareKeystoreSecureAreaSupportState
import com.android.identity.wallet.support.toSecureAreaState
import com.android.identity.wallet.util.DocumentData.MICOV_DOCTYPE
import com.android.identity.wallet.util.DocumentData.MVR_DOCTYPE
import java.io.ByteArrayOutputStream
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Random

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

        val secureArea: SecureArea = provisionInfo.currentSecureArea.secureArea
        val settings = provisionInfo.secureAreaSupportState
            .createKeystoreSettings(provisionInfo.validityInDays)

        val credential = credentialStore.createCredential(provisionInfo.credentialName(), secureArea, settings)
        credential.nameSpacedData = nameSpacedData

        repeat(provisionInfo.numberMso) {
            val pendingKey = credential.createPendingAuthenticationKey(secureArea, settings, null)
            pendingKey.applicationData.setBoolean(AUTH_KEY_DOMAIN, true)
        }
        provisionAuthKeys(credential, provisionInfo.docType, provisionInfo.validityInDays)

        credential.applicationData.setString(USER_VISIBLE_NAME, provisionInfo.docName)
        credential.applicationData.setString(DOCUMENT_TYPE, provisionInfo.docType)
        credential.applicationData.setString(DATE_PROVISIONED, dateTimeFormatter.format(ZonedDateTime.now()))
        credential.applicationData.setNumber(CARD_ART, provisionInfo.docColor.toLong())
        credential.applicationData.setBoolean(IS_SELF_SIGNED, true)
        credential.applicationData.setNumber(MAX_USAGES_PER_KEY, provisionInfo.maxUseMso.toLong())
        credential.applicationData.setNumber(VALIDITY_IN_DAYS, provisionInfo.validityInDays.toLong())
        credential.applicationData.setNumber(MIN_VALIDITY_IN_DAYS, provisionInfo.minValidityInDays.toLong())
        credential.applicationData.setString(MDOC_AUTHENTICATION, provisionInfo.secureAreaSupportState.mDocAuthOption.mDocAuthentication.name)
        credential.applicationData.setNumber(LAST_TIME_USED, -1)
        if (provisionInfo.secureAreaSupportState is SoftwareKeystoreSecureAreaSupportState) {
            credential.applicationData.setString(PASSPHRASE, provisionInfo.secureAreaSupportState.passphrase)
        }
    }

    private fun ProvisionInfo.credentialName(): String {
        val regex = Regex("[^A-Za-z0-9 ]")
        return regex.replace(docName, "").replace(" ", "_").lowercase()
    }

    fun trackUsageTimestamp(credential: Credential) {
        val now = Timestamp.now()
        credential.applicationData.setNumber(LAST_TIME_USED, now.toEpochMilli())
    }

    fun refreshAuthKeys(credential: Credential, documentInformation: DocumentInformation) {
        val pendingKeysCount = manageKeysFor(credential)
        if (pendingKeysCount <= 0) return
        val minValidityInDays = credential.applicationData.getNumber(MIN_VALIDITY_IN_DAYS).toInt()
        provisionAuthKeys(credential, documentInformation.docType, minValidityInDays)
    }

    private fun provisionAuthKeys(
        credential: Credential,
        documentType: String,
        validityInDays: Int
    ) {
        val nowMillis = Timestamp.now().toEpochMilli()
        val timeSigned = Timestamp.now()
        val timeValidityBegin = Timestamp.ofEpochMilli(nowMillis)
        val timeValidityEnd = validityInDays.toTimestampFromNow()

        for (pendingAuthKey in credential.pendingAuthenticationKeys) {
            val msoGenerator = MobileSecurityObjectGenerator(
                "SHA-256",
                documentType,
                pendingAuthKey.attestation.first().publicKey
            )
            msoGenerator.setValidityInfo(timeSigned, timeValidityBegin, timeValidityEnd, null)

            // For mDLs, override the portrait with AuthenticationKeyCounter on top
            //
            var dataElementExceptions: Map<String, List<String>>? = null
            var dataElementOverrides: Map<String, Map<String, ByteArray>>? = null
            if (documentType.equals("org.iso.18013.5.1.mDL")) {
                val portrait = credential.nameSpacedData.getDataElementByteString(
                    "org.iso.18013.5.1", "portrait")
                val portrait_override = overridePortrait(portrait,
                    pendingAuthKey.authenticationKeyCounter)

                dataElementExceptions =
                    mapOf("org.iso.18013.5.1" to listOf("given_name", "portrait"))
                dataElementOverrides =
                    mapOf("org.iso.18013.5.1" to mapOf(
                        "portrait" to Util.cborEncodeBytestring(portrait_override)))
            }

            val deterministicRandomProvider = Random(42)
            val issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
                credential.nameSpacedData,
                deterministicRandomProvider,
                16,
                dataElementOverrides
            )

            for (nameSpaceName in issuerNameSpaces.keys) {
                val digests = MdocUtil.calculateDigestsForNameSpace(
                    nameSpaceName,
                    issuerNameSpaces,
                    "SHA-256"
                )
                msoGenerator.addDigestIdsForNamespace(nameSpaceName, digests)
            }

            val mso = msoGenerator.generate()
            val taggedEncodedMso = Util.cborEncode(Util.cborBuildTaggedByteString(mso))

            val issuerKeyPair = when (documentType) {
                MVR_DOCTYPE -> KeysAndCertificates.getMekbDsKeyPair(context)
                MICOV_DOCTYPE -> KeysAndCertificates.getMicovDsKeyPair(context)
                else -> KeysAndCertificates.getMdlDsKeyPair(context)
            }

            val issuerCert = when (documentType) {
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
                timeValidityBegin,
                timeValidityEnd
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

    private fun manageKeysFor(credential: Credential): Int {
        val mDocAuthOption = credential.applicationData.getString(MDOC_AUTHENTICATION)
        val secureArea = when (credential.credentialSecureArea) {
            is AndroidKeystoreSecureArea -> {
                secureAreaRepository.implementations.first { it is AndroidKeystoreSecureArea }
            }

            is SoftwareSecureArea -> {
                secureAreaRepository.implementations.first { it is SoftwareSecureArea }
            }

            else -> throw IllegalStateException("Unknown keystore secure area implementation")
        }

        val settings = SecureAreaSupport.getInstance(context, credential)
            .getSecureAreaSupportState()
            .createKeystoreSettingForCredential(mDocAuthOption, credential)

        val minValidTimeDays = credential.applicationData.getNumber(MIN_VALIDITY_IN_DAYS)
        val maxUsagesPerKey = credential.applicationData.getNumber(MAX_USAGES_PER_KEY)
        return CredentialUtil.managedAuthenticationKeyHelper(
            credential,
            secureArea,
            settings,
            AUTH_KEY_DOMAIN,
            Timestamp.now(),
            credential.authenticationKeys.size,
            maxUsagesPerKey.toInt(),
            minValidTimeDays * 24 * 60 * 60 * 1000L
        )
    }

    companion object {

        private const val AUTH_KEY_DOMAIN = "some_hardcoded_string"
        private const val USER_VISIBLE_NAME = "userVisibleName"
        private const val DOCUMENT_TYPE = "documentType"
        private const val DATE_PROVISIONED = "dateProvisioned"
        private const val CARD_ART = "cardArt"
        private const val IS_SELF_SIGNED = "isSelfSigned"
        private const val MAX_USAGES_PER_KEY = "maxUsagesPerKey"
        private const val VALIDITY_IN_DAYS = "validityInDays"
        private const val MIN_VALIDITY_IN_DAYS = "minValidityInDays"
        private const val MDOC_AUTHENTICATION = "mDocAuthentication"
        private const val LAST_TIME_USED = "lastTimeUsed"
        const val PASSPHRASE = "passphrase"

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
                val authKeys = authenticationKeys.map { key ->
                    val info = credentialSecureArea.getKeyInfo(key.alias)
                    DocumentInformation.KeyData(
                        alias = key.alias,
                        validFrom = key.validFrom.formatted(),
                        validUntil = key.validUntil.formatted(),
                        issuerDataBytesCount = key.issuerProvidedData.size,
                        usagesCount = key.usageCount,
                        keyPurposes = info.keyPurposes,
                        ecCurve = info.ecCurve,
                        isHardwareBacked = info.isHardwareBacked
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
                    docName = name,
                    docType = it.applicationData.getString(DOCUMENT_TYPE),
                    dateProvisioned = it.applicationData.getString(DATE_PROVISIONED),
                    documentColor = it.applicationData.getNumber(CARD_ART).toInt(),
                    selfSigned = it.applicationData.getBoolean(IS_SELF_SIGNED),
                    maxUsagesPerKey = it.applicationData.getNumber(MAX_USAGES_PER_KEY).toInt(),
                    mDocAuthOption = it.applicationData.getString(MDOC_AUTHENTICATION),
                    currentSecureArea = it.credentialSecureArea.toSecureAreaState(),
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