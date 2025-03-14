package org.multipaz.provisioning.proofing

import org.multipaz.cbor.CborMap
import org.multipaz.flow.server.Resources
import org.multipaz.provisioning.CredentialConfiguration
import org.multipaz.securearea.config.SecureAreaConfigurationAndroidKeystore
import org.multipaz.securearea.config.SecureAreaConfigurationCloud
import org.multipaz.securearea.config.SecureAreaConfigurationSoftware
import org.multipaz.provisioning.WalletApplicationCapabilities
import org.multipaz.provisioning.evidence.EvidenceResponse
import org.multipaz.provisioning.evidence.EvidenceResponseCreatePassphrase
import org.multipaz.provisioning.evidence.EvidenceResponseQuestionMultipleChoice
import org.multipaz.provisioning.evidence.EvidenceResponseSetupCloudSecureArea
import org.multipaz.securearea.PassphraseConstraints
import org.multipaz.securearea.toDataItem
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.Algorithm
import java.net.URLEncoder
import kotlin.random.Random

fun defaultGraph(
    documentId: String,
    resources: Resources,
    walletApplicationCapabilities: WalletApplicationCapabilities,
    developerModeEnabled: Boolean,
    cloudSecureAreaUrl: String,
    tosText: String,
    tosAssets: Map<String, ByteString>
): ProofingGraph {
    val devAssets = mapOf("experiment_icon.svg" to resources.getRawResource("experiment_icon.svg")!!)
    val devNotice = "\n\n![Development Setting](experiment_icon.svg){style=height:1.5em;vertical-align:middle;margin-right:0.5em}" +
            " Development Mode setting"
    return ProofingGraph.create {
        message(
            "tos",
            tosText,
            tosAssets,
            "Accept",
            "Reject",
        )
        choice(
            id = "path",
            message = "Scan ePassport/eID?",
            assets = mapOf(),
            acceptButtonText = "Continue"
        ) {
            on(id = "hardcoded", text = "No, create document with hard-coded data") {
                if (developerModeEnabled) {
                    choice(
                        id = "devmode_image_format",
                        message = "Choose format for images in document $devNotice",
                        assets = devAssets,
                        acceptButtonText = "Continue"
                    ) {
                        on(id = "devmode_image_format_jpeg", text = "JPEG") {}
                        on(id = "devmode_image_format_jpeg2000", text = "JPEG 2000") {}
                    }
                }
            }
            on(id = "passport", text = "Yes, derive the document from ePassport/eID") {
                icaoTunnel("tunnel", listOf(1, 2, 7), true) {
                    whenChipAuthenticated {}
                    whenActiveAuthenticated {}
                    whenNotAuthenticated {}
                }
            }
        }
        if (walletApplicationCapabilities.directAccessSupported) {
            choice(
                id = "directAccess",
                message = "Would you like to be able to present this document when the device is powered-off?",
                assets = mapOf(),
                acceptButtonText = "Continue"
            ) {
                on(id = "yes", text = "Yes") {

                }
                on(id = "no", text = "No") {

                }
            }
        }
        if (developerModeEnabled) {
            choice(
                id = "devmode_sa",
                message = "Choose Secure Area $devNotice",
                assets = devAssets,
                acceptButtonText = "Continue"
            ) {
                on(id = "devmode_sa_android", text = "Android Keystore") {
                    choice(
                        id = "devmode_sa_android_use_strongbox",
                        message = "Use StrongBox $devNotice",
                        assets = devAssets,
                        acceptButtonText = "Continue"
                    ) {
                        on(id = "devmode_sa_android_use_strongbox_no", text = "Don't use StrongBox") {}
                        on(id = "devmode_sa_android_use_strongbox_yes", text = "Use StrongBox") {}
                    }
                    choice(
                        id = "devmode_sa_android_user_auth",
                        message = "Choose user authentication $devNotice",
                        assets = devAssets,
                        acceptButtonText = "Continue"
                    ) {
                        on(id = "devmode_sa_android_user_auth_lskf_biometrics", text = "LSKF or Biometrics") {}
                        on(id = "devmode_sa_android_user_auth_lskf", text = "Only LSKF") {}
                        on(id = "devmode_sa_android_user_auth_biometrics", text = "Only Biometrics") {}
                        on(id = "devmode_sa_android_user_auth_none", text = "None") {}
                    }
                    choice(
                        id = "devmode_sa_android_mdoc_auth",
                        message = "Choose mdoc authentication mode and EC curve $devNotice",
                        assets = devAssets,
                        acceptButtonText = "Continue"
                    ) {
                        on(id = "devmode_sa_android_mdoc_auth_ecdsa_p256", text = "ECDSA w/ P-256") {}
                        on(id = "devmode_sa_android_mdoc_auth_ed25519", text = "EdDSA w/ Ed25519") {}
                        on(id = "devmode_sa_android_mdoc_auth_ed448", text = "EdDSA w/ Ed448") {}
                        on(id = "devmode_sa_android_mdoc_auth_ecdh_p256", text = "ECDH w/ P-256") {}
                        on(id = "devmode_sa_android_mdoc_auth_x25519", text = "XDH w/ X25519") {}
                        on(id = "devmode_sa_android_mdoc_auth_x448", text = "XDH w/ X448") {}
                    }
                }

                on(id = "devmode_sa_software", text = "Software") {
                    choice(
                        id = "devmode_sa_software_passphrase_complexity",
                        message = "Choose what kind of passphrase to use $devNotice",
                        assets = devAssets,
                        acceptButtonText = "Continue"
                    ) {
                        on(id = "devmode_sa_software_passphrase_6_digit_pin", text = "6-digit PIN") {
                            createPassphrase(
                                "devmode_sa_software_passphrase_6",
                                message = "## Choose 6-digit PIN\n\nChoose the PIN to use for the document.\n\nThis is asked every time the document is presented so make sure you memorize it and don't share it with anyone else. $devNotice",
                                verifyMessage = "## Verify PIN\n\nEnter the PIN you chose in the previous screen. $devNotice",
                                assets = devAssets,
                                PassphraseConstraints.PIN_SIX_DIGITS
                            )
                        }
                        on(id = "devmode_sa_software_passphrase_8_char_or_longer_passphrase", text = "Passphrase 8 chars or longer") {
                            createPassphrase(
                                "devmode_sa_software_passphrase_8",
                                message = "## Choose passphrase\n\nChoose the passphrase to use for the document.\n\nThis is asked every time the document is presented so make sure you memorize it and don't share it with anyone else. $devNotice",
                                verifyMessage = "## Verify passphrase\n\nEnter the passphrase you chose in the previous screen. $devNotice",
                                assets = devAssets,
                                PassphraseConstraints(8, Int.MAX_VALUE, false)
                            )
                        }
                        on(id = "devmode_sa_software_passphrase_none", text = "None") {}
                    }
                    choice(
                        id = "devmode_sa_software_mdoc_auth",
                        message = "Choose mdoc authentication mode and EC curve $devNotice",
                        assets = devAssets,
                        acceptButtonText = "Continue"
                    ) {
                        on(id = "devmode_sa_software_mdoc_auth_ecdsa_p256", text = "ECDSA w/ P-256") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdsa_p384", text = "ECDSA w/ P-384") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdsa_p521", text = "ECDSA w/ P-521") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdsa_brainpoolp256r1", text = "ECDSA w/ brainpoolP256r1") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdsa_brainpoolp320r1", text = "ECDSA w/ brainpoolP320r1") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdsa_brainpoolp384r1", text = "ECDSA w/ brainpoolP384r1") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdsa_brainpoolp512r1", text = "ECDSA w/ brainpoolP512r1") {}
                        on(id = "devmode_sa_software_mdoc_auth_ed25519", text = "EdDSA w/ Ed25519") {}
                        on(id = "devmode_sa_software_mdoc_auth_ed448", text = "EdDSA w/ Ed448") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdh_p256", text = "ECDH w/ P-256") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdh_p384", text = "ECDH w/ P-384") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdh_p521", text = "ECDH w/ P-521") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdh_brainpoolp256r1", text = "ECDH w/ brainpoolP256r1") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdh_brainpoolp320r1", text = "ECDH w/ brainpoolP320r1") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdh_brainpoolp384r1", text = "ECDH w/ brainpoolP384r1") {}
                        on(id = "devmode_sa_software_mdoc_auth_ecdh_brainpoolp512r1", text = "ECDH w/ brainpoolP512r1") {}
                        on(id = "devmode_sa_software_mdoc_auth_x25519", text = "XDH w/ X25519") {}
                        on(id = "devmode_sa_software_mdoc_auth_x448", text = "XDH w/ X448") {}
                    }
                }

                on(id = "devmode_sa_cloud", text = "Cloud Secure Area") {
                    setupCloudSecureArea(
                        "devmode_sa_cloud_setup_csa",
                        cloudSecureAreaIdentifier = "CloudSecureArea?id=${documentId}&url=${URLEncoder.encode(cloudSecureAreaUrl, "UTF-8")}",
                        passphraseConstraints = PassphraseConstraints.PIN_SIX_DIGITS,
                        message = "## Choose 6-digit PIN\n\nChoose the PIN to use for the document.\n\nThis is asked every time the document is presented so make sure you memorize it and don't share it with anyone else. $devNotice",
                        verifyMessage = "## Verify PIN\n\nEnter the PIN you chose in the previous screen. $devNotice",
                        assets = devAssets,
                    )
                    choice(
                        id = "devmode_sa_cloud_user_auth",
                        message = "Choose whether to also require user authentication $devNotice",
                        assets = devAssets,
                        acceptButtonText = "Continue"
                    ) {
                        on(id = "devmode_sa_cloud_user_auth_lskf_biometrics", text = "LSKF or Biometrics") {}
                        on(id = "devmode_sa_cloud_user_auth_lskf", text = "Only LSKF") {}
                        on(id = "devmode_sa_cloud_user_auth_biometrics", text = "Only Biometrics") {}
                        on(id = "devmode_sa_cloud_user_auth_none", text = "None") {}
                    }
                }
            }
        }
        if (developerModeEnabled) {
            choice(
                id = "choose_selfie",
                message = "Verify using a selfie? $devNotice",
                assets = devAssets,
                acceptButtonText = "Continue"
            ) {
                on(id = "no_selfie", text = "No, skip selfie verification") {}
                on(id = "use_selfie", text = "Yes, take a selfie for verification") {
                    createSelfieRequest(id = "selfie_request")
                }
            }
        }
        completionMessage(
            id = "detailedMessage",
            messageTitle = "Document Submission Complete",
            message = "Your application is now being verified by the issuer. This process might take a few hours.",
            assets = mapOf(),
            acceptButtonText = "Done",
            rejectButtonText = null
        )
        requestNotificationPermission(
            "notificationPermission",
            permissionNotAvailableMessage = """
                ## Receive notifications?
                
                If there are updates to your document the issuer will send an updated document
                to your device. If you are interested, we can send a notification to make you aware
                of when this happens. This requires granting a permission.
                
                If you previously denied this permission, attempting to grant it again might not do
                anything and you may need to go into Settings and manually enable
                notifications for this application.
            """.trimIndent(),
            grantPermissionButtonText = "Grant Permission",
            continueWithoutPermissionButtonText = "No Thanks",
            assets = mapOf()
        )
    }
}

fun defaultCredentialConfiguration(
    documentId: String,
    walletApplicationCapabilities: WalletApplicationCapabilities,
    collectedEvidence: Map<String, EvidenceResponse>
): CredentialConfiguration {
    val challenge = ByteString(Random.nextBytes(16))
    if (!collectedEvidence.containsKey("devmode_sa")) {
        return CredentialConfiguration(
            challenge = challenge,
            keyAssertionRequired = false,
            secureAreaConfiguration = SecureAreaConfigurationAndroidKeystore(
                algorithm = Algorithm.ESP256.name,
                useStrongBox = true,
                userAuthenticationRequired = true,
                userAuthenticationTimeoutMillis = 0,
                userAuthenticationTypes = 3  // LSKF + Biometrics
            )
        )
    }

    val chosenSa =
        (collectedEvidence["devmode_sa"] as EvidenceResponseQuestionMultipleChoice).answerId
    when (chosenSa) {
        "devmode_sa_android" -> {
            val useStrongBox = when ((collectedEvidence["devmode_sa_android_use_strongbox"]
                    as EvidenceResponseQuestionMultipleChoice).answerId) {
                "devmode_sa_android_use_strongbox_yes" -> true
                "devmode_sa_android_use_strongbox_no" -> false
                else -> throw IllegalStateException()
            }
            val userAuthType = when ((collectedEvidence["devmode_sa_android_user_auth"]
                    as EvidenceResponseQuestionMultipleChoice).answerId) {
                "devmode_sa_android_user_auth_lskf_biometrics" -> 3
                "devmode_sa_android_user_auth_lskf" -> 1
                "devmode_sa_android_user_auth_biometrics" -> 2
                "devmode_sa_android_user_auth_none" -> 0
                else -> throw IllegalStateException()
            }
            val algorithm = when ((collectedEvidence["devmode_sa_android_mdoc_auth"]
                    as EvidenceResponseQuestionMultipleChoice).answerId) {
                "devmode_sa_android_mdoc_auth_ecdsa_p256" -> Algorithm.ESP256
                "devmode_sa_android_mdoc_auth_ed25519" -> Algorithm.ED25519
                "devmode_sa_android_mdoc_auth_ed448" -> Algorithm.ED448
                "devmode_sa_android_mdoc_auth_ecdh_p256" -> Algorithm.ECDH_P256
                "devmode_sa_android_mdoc_auth_x25519" -> Algorithm.ECDH_X25519
                "devmode_sa_android_mdoc_auth_x448" -> Algorithm.ECDH_X448
                else -> throw IllegalStateException()
            }
            return CredentialConfiguration(
                challenge = challenge,
                keyAssertionRequired = false,
                secureAreaConfiguration = SecureAreaConfigurationAndroidKeystore(
                    algorithm = algorithm.name,
                    useStrongBox = useStrongBox,
                    userAuthenticationRequired = userAuthType != 0,
                    userAuthenticationTimeoutMillis = 0,
                    userAuthenticationTypes = userAuthType.toLong(),
                )
            )
        }

        "devmode_sa_software" -> {
            val algorithm = when ((collectedEvidence["devmode_sa_software_mdoc_auth"]
                    as EvidenceResponseQuestionMultipleChoice).answerId) {
                "devmode_sa_software_mdoc_auth_ecdsa_p256" -> Algorithm.ESP256
                "devmode_sa_software_mdoc_auth_ecdsa_p384" -> Algorithm.ESP384
                "devmode_sa_software_mdoc_auth_ecdsa_p521" -> Algorithm.ESP512
                "devmode_sa_software_mdoc_auth_ecdsa_brainpoolp256r1" -> Algorithm.ESB256
                "devmode_sa_software_mdoc_auth_ecdsa_brainpoolp320r1" -> Algorithm.ESB320
                "devmode_sa_software_mdoc_auth_ecdsa_brainpoolp384r1" -> Algorithm.ESB384
                "devmode_sa_software_mdoc_auth_ecdsa_brainpoolp512r1" -> Algorithm.ESB512
                "devmode_sa_software_mdoc_auth_ed25519" -> Algorithm.ED25519
                "devmode_sa_software_mdoc_auth_ed448" -> Algorithm.ED448
                "devmode_sa_software_mdoc_auth_ecdh_p256" -> Algorithm.ECDH_P256
                "devmode_sa_software_mdoc_auth_ecdh_p384" -> Algorithm.ECDH_P384
                "devmode_sa_software_mdoc_auth_ecdh_p521" -> Algorithm.ECDH_P521
                "devmode_sa_software_mdoc_auth_ecdh_brainpoolp256r1" -> Algorithm.ECDH_BRAINPOOLP256R1
                "devmode_sa_software_mdoc_auth_ecdh_brainpoolp320r1" -> Algorithm.ECDH_BRAINPOOLP320R1
                "devmode_sa_software_mdoc_auth_ecdh_brainpoolp384r1" -> Algorithm.ECDH_BRAINPOOLP384R1
                "devmode_sa_software_mdoc_auth_ecdh_brainpoolp512r1" -> Algorithm.ECDH_BRAINPOOLP512R1
                "devmode_sa_software_mdoc_auth_x25519" -> Algorithm.ECDH_X25519
                "devmode_sa_software_mdoc_auth_x448" -> Algorithm.ECDH_X448
                else -> throw IllegalStateException()
            }
            var passphrase: String? = null
            val passphraseConstraints =
                when ((collectedEvidence["devmode_sa_software_passphrase_complexity"]
                        as EvidenceResponseQuestionMultipleChoice).answerId) {
                    "devmode_sa_software_passphrase_none" -> null
                    "devmode_sa_software_passphrase_6_digit_pin" -> {
                        passphrase = (collectedEvidence["devmode_sa_software_passphrase_6"]
                                as EvidenceResponseCreatePassphrase).passphrase
                        PassphraseConstraints.PIN_SIX_DIGITS
                    }

                    "devmode_sa_software_passphrase_8_char_or_longer_passphrase" -> {
                        passphrase = (collectedEvidence["devmode_sa_software_passphrase_8"]
                                as EvidenceResponseCreatePassphrase).passphrase
                        PassphraseConstraints(8, Int.MAX_VALUE, false)
                    }

                    else -> throw IllegalStateException()
                }
            return CredentialConfiguration(
                challenge = challenge,
                keyAssertionRequired = false,
                secureAreaConfiguration = SecureAreaConfigurationSoftware(
                    algorithm = algorithm.name,
                    passphrase = passphrase,
                    passphraseConstraints = passphraseConstraints!!
                )
            )
        }

        "devmode_sa_cloud" -> {
            val userAuthType = when ((collectedEvidence["devmode_sa_cloud_user_auth"]
                    as EvidenceResponseQuestionMultipleChoice).answerId) {
                "devmode_sa_cloud_user_auth_lskf_biometrics" -> 3
                "devmode_sa_cloud_user_auth_lskf" -> 1
                "devmode_sa_cloud_user_auth_biometrics" -> 2
                "devmode_sa_cloud_user_auth_none" -> 0
                else -> throw IllegalStateException()
            }
            // Use cloud secure area setup when the evidence was collected! NB: settings may change
            val cloudSecureAreaId = (collectedEvidence["devmode_sa_cloud_setup_csa"] as EvidenceResponseSetupCloudSecureArea)
                .cloudSecureAreaIdentifier
            return CredentialConfiguration(
                challenge = challenge,
                keyAssertionRequired = false,
                secureAreaConfiguration = SecureAreaConfigurationCloud(
                    algorithm = Algorithm.ESP256.name,
                    cloudSecureAreaId = cloudSecureAreaId,
                    userAuthenticationTimeoutMillis = 0,
                    useStrongBox = true,
                    passphraseRequired = true,
                    userAuthenticationRequired = userAuthType != 0,
                    userAuthenticationTypes = userAuthType.toLong()
                )
            )
        }

        else -> {
            throw IllegalStateException("Unexpected value $chosenSa")
        }
    }
}