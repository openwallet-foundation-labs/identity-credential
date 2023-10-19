package com.android.identity.wallet.support

import com.android.identity.credential.Credential
import com.android.identity.securearea.SecureArea
import com.android.identity.securearea.SoftwareSecureArea
import com.android.identity.storage.EphemeralStorageEngine
import com.android.identity.util.Timestamp
import com.android.identity.wallet.support.softwarekeystore.SoftwareAuthKeyCurveState
import com.android.identity.wallet.composables.state.MdocAuthOption
import com.android.identity.wallet.composables.state.MdocAuthStateOption
import com.android.identity.wallet.util.ProvisioningUtil
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.security.PrivateKey
import java.security.cert.X509Certificate

@Parcelize
data class SoftwareKeystoreSecureAreaSupportState(
    override val mDocAuthOption: MdocAuthOption = MdocAuthOption(),
    val softwareAuthKeyCurveState: SoftwareAuthKeyCurveState = SoftwareAuthKeyCurveState(),
    val passphrase: String = "",
    val authKeyCurve: SoftwareAuthKeyCurveState = SoftwareAuthKeyCurveState(),
) : SecureAreaSupportState {

    @IgnoredOnParcel
    private lateinit var softwareAttestationKey: PrivateKey
    @IgnoredOnParcel
    private lateinit var softwareAttestationKeySignatureAlgorithm: String
    @IgnoredOnParcel
    private lateinit var softwareAttestationKeyCertification: List<X509Certificate>

    override fun createKeystoreSettings(validityInDays: Int): SecureArea.CreateKeySettings {
        val passphraseValue = passphrase.ifBlank { null }
        val keyPurposes = mDocAuthOption.mDocAuthentication.toKeyPurpose()
        if (!this::softwareAttestationKey.isInitialized) {
            initSoftwareAttestationKey()
        }
        val builder = SoftwareSecureArea.CreateKeySettings.Builder("challenge".toByteArray())
            .setAttestationKey(
                softwareAttestationKey,
                softwareAttestationKeySignatureAlgorithm,
                softwareAttestationKeyCertification
            )
            .setPassphraseRequired(passphraseValue != null, passphraseValue)
            .setEcCurve(authKeyCurve.authCurve.toEcCurve())
            .setKeyPurposes(keyPurposes)
        return builder.build()
    }

    override fun createKeystoreSettingForCredential(
        mDocAuthOption: String,
        credential: Credential
    ): SecureArea.CreateKeySettings {
        val keyInfo = credential.credentialSecureArea
            .getKeyInfo(credential.credentialKeyAlias) as SoftwareSecureArea.KeyInfo
        if (!this::softwareAttestationKey.isInitialized) {
            initSoftwareAttestationKey()
        }
        val keyPurpose = MdocAuthStateOption.valueOf(mDocAuthOption).toKeyPurpose()
        val passphrase = credential.applicationData.getString(ProvisioningUtil.PASSPHRASE)
        val builder = SoftwareSecureArea.CreateKeySettings.Builder("challenge".toByteArray())
            .setAttestationKey(
                softwareAttestationKey,
                softwareAttestationKeySignatureAlgorithm,
                softwareAttestationKeyCertification
            )
            .setPassphraseRequired(passphrase.isNotBlank(), passphrase)
            .setKeyPurposes(keyPurpose)
            .setEcCurve(keyInfo.ecCurve)
        return builder.build()
    }

    private fun initSoftwareAttestationKey() {
        val secureArea = SoftwareSecureArea(EphemeralStorageEngine())
        val now = Timestamp.now()
        secureArea.createKey(
            "SoftwareAttestationRoot",
            SoftwareSecureArea.CreateKeySettings.Builder("".toByteArray())
                .setEcCurve(SecureArea.EC_CURVE_P256)
                .setKeyPurposes(SecureArea.KEY_PURPOSE_SIGN)
                .setSubject("CN=Software Attestation Root")
                .setValidityPeriod(
                    now,
                    Timestamp.ofEpochMilli(now.toEpochMilli() + 10L * 86400 * 365 * 1000)
                )
                .build()
        )
        softwareAttestationKey = secureArea.getPrivateKey("SoftwareAttestationRoot", null)
        softwareAttestationKeySignatureAlgorithm = "SHA256withECDSA"
        softwareAttestationKeyCertification = secureArea.getKeyInfo("SoftwareAttestationRoot").attestation
    }
}