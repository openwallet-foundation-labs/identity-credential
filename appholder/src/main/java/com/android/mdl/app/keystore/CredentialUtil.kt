package com.android.mdl.app.keystore

import android.annotation.SuppressLint
import android.content.Context
import com.android.identity.android.securearea.AndroidKeystoreSecureArea
import com.android.identity.android.storage.AndroidStorageEngine
import com.android.identity.credential.Credential
import com.android.identity.credential.CredentialStore
import com.android.identity.credential.CredentialUtil
import com.android.identity.credential.NameSpacedData
import com.android.identity.internal.Util
import com.android.identity.mdoc.mso.MobileSecurityObjectGenerator
import com.android.identity.mdoc.mso.StaticAuthDataGenerator
import com.android.identity.mdoc.util.MdocUtil
import com.android.identity.securearea.BouncyCastleSecureArea
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SecureAreaRepository
import com.android.identity.util.Timestamp
import com.android.mdl.app.document.DocumentInformation
import com.android.mdl.app.document.KeysAndCertificates
import com.android.mdl.app.document.SecureAreaImplementationState
import com.android.mdl.app.selfsigned.ProvisionInfo
import com.android.mdl.app.util.DocumentData.MICOV_DOCTYPE
import com.android.mdl.app.util.DocumentData.MVR_DOCTYPE
import com.android.mdl.app.util.PreferencesHelper
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import java.util.Date
import java.util.Random

class CredentialUtil private constructor(
    private val context: Context
) {

    private val storageDir: File
        get() = PreferencesHelper.getKeystoreBackedStorageLocation(context)

    private val storageEngine: AndroidStorageEngine
        get() = AndroidStorageEngine.Builder(context, storageDir).build()

    private val androidKeystoreSecureArea: SecureArea
        get() = AndroidKeystoreSecureArea(context, storageEngine)

    private val bouncyCastleSecureArea: SecureArea
        get() = BouncyCastleSecureArea(storageEngine)

    val credentialStore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val keystoreEngineRepository = SecureAreaRepository()
        keystoreEngineRepository.addImplementation(androidKeystoreSecureArea)
        keystoreEngineRepository.addImplementation(bouncyCastleSecureArea)
        CredentialStore(storageEngine, keystoreEngineRepository)
    }

    fun provisionSelfSigned(
        nameSpacedData: NameSpacedData,
        provisionInfo: ProvisionInfo,
    ) {
        val settings = when (provisionInfo.secureAreaImplementationStateType) {
            SecureAreaImplementationState.Android -> createAndroidKeystoreSettings(
                provisioningChallenge = CHALLENGE,
                userAuthenticationRequired = provisionInfo.userAuthentication,
                authTimeoutSeconds = provisionInfo.userAuthenticationTimeoutSeconds.toLong()
            )

            SecureAreaImplementationState.BouncyCastle -> createBouncyCastleKeystoreSettings(
                passphrase = provisionInfo.passphrase
            )
        }
        if (provisionInfo.userAuthentication) {
            createAndroidKey(provisionInfo.userAuthenticationTimeoutSeconds.toLong())
        } else if (provisionInfo.passphrase != null) {
            createBouncyCastleKey(provisionInfo.passphrase)
        }

        val credential = credentialStore.createCredential(provisionInfo.docName, settings)
        credential.nameSpacedData = nameSpacedData

        provisionAuthKeys(credential, provisionInfo.docType, settings, provisionInfo.numberMso)

        credential.applicationData.setString(USER_VISIBLE_NAME, provisionInfo.docName)
        credential.applicationData.setString(DOCUMENT_TYPE, provisionInfo.docType)
        credential.applicationData.setString(DATE_PROVISIONED, dateTimeFormatter.format(ZonedDateTime.now()))
        credential.applicationData.setNumber(CARD_ART, provisionInfo.docColor.toLong())
        credential.applicationData.setBoolean(IS_SELF_SIGNED, true)
        credential.applicationData.setNumber(MAX_USAGES_PER_KEY, provisionInfo.maxUseMso.toLong())
    }

    fun refreshAuthKeys(
        credential: Credential,
        documentInformation: DocumentInformation
    ) {
        val keySettings = when (credential.credentialSecureArea) {
            is AndroidKeystoreSecureArea -> {
                createAndroidKeystoreSettings(CHALLENGE, false)
            }

            is BouncyCastleSecureArea -> {
                createBouncyCastleKeystoreSettings()
            }

            else -> throw IllegalStateException("Unknown keystore secure area implementation")
        }
        val pendingKeysCount = manageKeysFor(documentInformation, credential)
        if (pendingKeysCount <= 0) return
        provisionAuthKeys(credential, documentInformation.docType, keySettings, pendingKeysCount)
    }

    private fun provisionAuthKeys(
        credential: Credential,
        documentType: String,
        keySettings: SecureArea.CreateKeySettings,
        msoCount: Int
    ) {
        val nowMillis = Timestamp.now().toEpochMilli()
        val timeSigned = Timestamp.now()
        val timeValidityBegin = Timestamp.ofEpochMilli(nowMillis)
        val timeValidityEnd = Timestamp.ofEpochMilli(nowMillis + 10 * 86400 * 1000)
        repeat(msoCount) {
            val pendingAuthKey = credential.createPendingAuthenticationKey(keySettings, null)
            val msoGenerator = MobileSecurityObjectGenerator(
                "SHA-256",
                documentType,
                pendingAuthKey.attestation.first().publicKey
            )
            msoGenerator.setValidityInfo(timeSigned, timeValidityBegin, timeValidityEnd, null)

            val deterministicRandomProvider = Random(42)
            val issuerNameSpaces = MdocUtil.generateIssuerNameSpaces(
                credential.nameSpacedData,
                deterministicRandomProvider,
                16
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
                MdocUtil.stripIssuerNameSpaces(issuerNameSpaces),
                encodedIssuerAuth
            ).generate()

            pendingAuthKey.certify(
                issuerProvidedAuthenticationData,
                timeValidityBegin,
                timeValidityEnd
            )
        }
    }

    private fun generateIssuingAuthorityKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        val ecSpec = ECGenParameterSpec("secp256r1")
        keyPairGenerator.initialize(ecSpec)
        return keyPairGenerator.generateKeyPair()
    }

    private fun getSelfSignedIssuerAuthorityCertificate(
        issuerAuthorityKeyPair: KeyPair
    ): X509Certificate {
        val issuer = X500Name("CN=State Of Utopia")
        val subject = X500Name("CN=State Of Utopia Issuing Authority Signing Key")

        val now = Date()
        val milliSecsInOneYear = 365L * 24 * 60 * 60 * 1000
        val expirationDate = Date(now.time + 5 * milliSecsInOneYear)
        val serial = BigInteger("42")
        val builder = JcaX509v3CertificateBuilder(
            issuer,
            serial,
            now,
            expirationDate,
            subject,
            issuerAuthorityKeyPair.public
        )

        val signer = JcaContentSignerBuilder("SHA256withECDSA")
            .build(issuerAuthorityKeyPair.private)

        val certHolder = builder.build(signer)
        return JcaX509CertificateConverter().getCertificate(certHolder)
    }

    fun manageKeysFor(
        documentInformation: DocumentInformation?,
        credential: Credential
    ): Int {
        val settings = when (credential.credentialSecureArea) {
            is AndroidKeystoreSecureArea -> {
                createAndroidKeystoreSettings(CHALLENGE, false)
            }

            is BouncyCastleSecureArea -> {
                createBouncyCastleKeystoreSettings()
            }

            else -> throw IllegalStateException("Unknown keystore secure area implementation")
        }
        return CredentialUtil.managedAuthenticationKeyHelper(
            credential,
            settings,
            "some_hardcoded_string",
            Timestamp.now(),
            credential.authenticationKeys.size,
            documentInformation?.maxUsagesPerKey ?: 1,
            1000
        )
    }

    private fun createAndroidKeystoreSettings(
        provisioningChallenge: ByteArray,
        userAuthenticationRequired: Boolean,
        authTimeoutSeconds: Long = 10
    ): AndroidKeystoreSecureArea.CreateKeySettings {

        return AndroidKeystoreSecureArea.CreateKeySettings.Builder(provisioningChallenge)
            .setKeyPurposes(SecureArea.KEY_PURPOSE_SIGN or SecureArea.KEY_PURPOSE_AGREE_KEY)
            .setUserAuthenticationRequired(
                userAuthenticationRequired,
                authTimeoutSeconds * 1000,
                AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_BIOMETRIC
            ).build()
    }

    private fun createBouncyCastleKeystoreSettings(
        passphrase: String? = null
    ): BouncyCastleSecureArea.CreateKeySettings {
        return BouncyCastleSecureArea.CreateKeySettings.Builder()
            .setPassphraseRequired(passphrase != null, passphrase)
            .setKeyPurposes(SecureArea.KEY_PURPOSE_SIGN or SecureArea.KEY_PURPOSE_AGREE_KEY)
            .build()
    }

    private fun createAndroidKey(timeoutSeconds: Long) {
        androidKeystoreSecureArea.createKey(
            ANDROID_KEY_BIOMETRIC,
            AndroidKeystoreSecureArea.CreateKeySettings.Builder(CHALLENGE)
                .setUserAuthenticationRequired(
                    true,
                    timeoutSeconds * 1000,
                    AndroidKeystoreSecureArea.USER_AUTHENTICATION_TYPE_BIOMETRIC
                ).build()
        )
    }

    private fun createBouncyCastleKey(passphrase: String) {
        bouncyCastleSecureArea.createKey(
            BOUNCY_CASTLE_KEY_PASSPHRASE,
            BouncyCastleSecureArea.CreateKeySettings.Builder()
                .setPassphraseRequired(true, passphrase)
                .build()
        )
    }

    companion object {

        private const val USER_VISIBLE_NAME = "userVisibleName"
        private const val DOCUMENT_TYPE = "documentType"
        private const val DATE_PROVISIONED = "dateProvisioned"
        private const val CARD_ART = "cardArt"
        private const val IS_SELF_SIGNED = "isSelfSigned"
        private const val MAX_USAGES_PER_KEY = "maxUsagesPerKey"

        private const val ANDROID_KEY_BIOMETRIC = "user_auth_key_with_timeout"
        private const val BOUNCY_CASTLE_KEY_PASSPHRASE = "bouncy_castle_auth_key"
        private val CHALLENGE = "challenge".toByteArray()

        private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: com.android.mdl.app.keystore.CredentialUtil? = null

        fun getInstance(context: Context) = instance ?: synchronized(this) {
            instance ?: CredentialUtil(context).also { instance = it }
        }

        fun Credential?.toDocumentInformation(): DocumentInformation? {
            return this?.let {
                val authKeys = authenticationKeys.map { key ->
                    DocumentInformation.KeyData(
                        alias = key.alias,
                        validFrom = key.validFrom.formatted(),
                        validUntil = key.validUntil.formatted(),
                        issuerDataBytesCount = key.issuerProvidedData.size,
                        usagesCount = key.usageCount
                    )
                }
                DocumentInformation(
                    userVisibleName = it.applicationData.getString(USER_VISIBLE_NAME),
                    docType = it.applicationData.getString(DOCUMENT_TYPE),
                    dateProvisioned = it.applicationData.getString(DATE_PROVISIONED),
                    documentColor = it.applicationData.getNumber(CARD_ART).toInt(),
                    selfSigned = it.applicationData.getBoolean(IS_SELF_SIGNED),
                    maxUsagesPerKey = it.applicationData.getNumber(MAX_USAGES_PER_KEY).toInt(),
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