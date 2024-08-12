package com.android.identity.issuance.proofing

import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborMap
import com.android.identity.crypto.EcCurve
import com.android.identity.flow.server.Resources
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseCreatePassphrase
import com.android.identity.issuance.evidence.EvidenceResponseQuestionMultipleChoice
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.securearea.toDataItem
import kotlinx.io.bytestring.ByteString

fun defaultGraph(
    documentId: String,
    resources: Resources,
    developerModeEnabled: Boolean,
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
            on(id = "germanEid", text = "Yes, derive the document from PIN-protected German eID") {
                eId("germanEidCard", "https://test.governikus-eid.de/AusweisAuskunft/WebServiceRequesterServlet")
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
                        cloudSecureAreaIdentifier = "CloudSecureArea?id=${documentId}&url=/csa",
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
        message(
            "message",
            message = """
                Your application is about to be sent the ID issuer for verification. You will
                get notified when the application is approved.
            """.trimIndent(),
            assets = mapOf(),
            acceptButtonText = "Continue",
            null
        )
        requestNotificationPermission(
            "notificationPermission",
            permissionNotAvailableMessage = """
                ## Receive notifications?
                
                If there are updates to your document the issuer will send an updated document
                to your device. If you are interested, we can send a notification to make you aware
                of when this happens. This requires granting a permission.
                
                If you previously denied this permission, attempting to grant it again might not do
                anything and you may need to manually go into Settings and manually enable
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
    collectedEvidence: Map<String, EvidenceResponse>
): CredentialConfiguration {
    val challenge = byteArrayOf(1, 2, 3)
    if (!collectedEvidence.containsKey("devmode_sa")) {
        return CredentialConfiguration(
            challenge,
            "AndroidKeystoreSecureArea",
            Cbor.encode(
                CborMap.builder()
                    .put("curve", EcCurve.P256.coseCurveIdentifier)
                    .put("purposes", KeyPurpose.encodeSet(setOf(KeyPurpose.SIGN)))
                    .put("userAuthenticationRequired", true)
                    .put("userAuthenticationTimeoutMillis", 0L)
                    .put("userAuthenticationTypes", 3 /* LSKF + Biometrics */)
                    .end().build()
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
            val (curve, purposes) = when ((collectedEvidence["devmode_sa_android_mdoc_auth"]
                    as EvidenceResponseQuestionMultipleChoice).answerId) {
                "devmode_sa_android_mdoc_auth_ecdsa_p256" -> Pair(
                    EcCurve.P256,
                    setOf(KeyPurpose.SIGN)
                )

                "devmode_sa_android_mdoc_auth_ed25519" -> Pair(
                    EcCurve.ED25519,
                    setOf(KeyPurpose.SIGN)
                )

                "devmode_sa_android_mdoc_auth_ed448" -> Pair(EcCurve.ED448, setOf(KeyPurpose.SIGN))
                "devmode_sa_android_mdoc_auth_ecdh_p256" -> Pair(
                    EcCurve.P256,
                    setOf(KeyPurpose.AGREE_KEY)
                )

                "devmode_sa_android_mdoc_auth_x25519" -> Pair(
                    EcCurve.X25519,
                    setOf(KeyPurpose.AGREE_KEY)
                )

                "devmode_sa_android_mdoc_auth_x448" -> Pair(
                    EcCurve.X448,
                    setOf(KeyPurpose.AGREE_KEY)
                )

                else -> throw IllegalStateException()
            }
            return CredentialConfiguration(
                challenge,
                "AndroidKeystoreSecureArea",
                Cbor.encode(
                    CborMap.builder()
                        .put("useStrongBox", useStrongBox)
                        .put("userAuthenticationRequired", (userAuthType != 0))
                        .put("userAuthenticationTimeoutMillis", 0L)
                        .put("userAuthenticationTypes", userAuthType)
                        .put("curve", curve.coseCurveIdentifier)
                        .put("purposes", KeyPurpose.encodeSet(purposes))
                        .end().build()
                )
            )

        }

        "devmode_sa_software" -> {
            val (curve, purposes) = when ((collectedEvidence["devmode_sa_software_mdoc_auth"]
                    as EvidenceResponseQuestionMultipleChoice).answerId) {
                "devmode_sa_software_mdoc_auth_ecdsa_p256" -> Pair(
                    EcCurve.P256,
                    setOf(KeyPurpose.SIGN)
                )

                "devmode_sa_software_mdoc_auth_ecdsa_p384" -> Pair(
                    EcCurve.P384,
                    setOf(KeyPurpose.SIGN)
                )

                "devmode_sa_software_mdoc_auth_ecdsa_p521" -> Pair(
                    EcCurve.P521,
                    setOf(KeyPurpose.SIGN)
                )

                "devmode_sa_software_mdoc_auth_ecdsa_brainpoolp256r1" -> Pair(
                    EcCurve.BRAINPOOLP256R1, setOf(
                        KeyPurpose.SIGN
                    )
                )

                "devmode_sa_software_mdoc_auth_ecdsa_brainpoolp320r1" -> Pair(
                    EcCurve.BRAINPOOLP320R1, setOf(
                        KeyPurpose.SIGN
                    )
                )

                "devmode_sa_software_mdoc_auth_ecdsa_brainpoolp384r1" -> Pair(
                    EcCurve.BRAINPOOLP384R1, setOf(
                        KeyPurpose.SIGN
                    )
                )

                "devmode_sa_software_mdoc_auth_ecdsa_brainpoolp512r1" -> Pair(
                    EcCurve.BRAINPOOLP512R1, setOf(
                        KeyPurpose.SIGN
                    )
                )

                "devmode_sa_software_mdoc_auth_ed25519" -> Pair(
                    EcCurve.ED25519,
                    setOf(KeyPurpose.SIGN)
                )

                "devmode_sa_software_mdoc_auth_ed448" -> Pair(EcCurve.ED448, setOf(KeyPurpose.SIGN))
                "devmode_sa_software_mdoc_auth_ecdh_p256" -> Pair(
                    EcCurve.P256,
                    setOf(KeyPurpose.AGREE_KEY)
                )

                "devmode_sa_software_mdoc_auth_ecdh_p384" -> Pair(
                    EcCurve.P384,
                    setOf(KeyPurpose.AGREE_KEY)
                )

                "devmode_sa_software_mdoc_auth_ecdh_p521" -> Pair(
                    EcCurve.P521,
                    setOf(KeyPurpose.AGREE_KEY)
                )

                "devmode_sa_software_mdoc_auth_ecdh_brainpoolp256r1" -> Pair(
                    EcCurve.BRAINPOOLP256R1, setOf(
                        KeyPurpose.AGREE_KEY
                    )
                )

                "devmode_sa_software_mdoc_auth_ecdh_brainpoolp320r1" -> Pair(
                    EcCurve.BRAINPOOLP320R1, setOf(
                        KeyPurpose.AGREE_KEY
                    )
                )

                "devmode_sa_software_mdoc_auth_ecdh_brainpoolp384r1" -> Pair(
                    EcCurve.BRAINPOOLP384R1, setOf(
                        KeyPurpose.AGREE_KEY
                    )
                )

                "devmode_sa_software_mdoc_auth_ecdh_brainpoolp512r1" -> Pair(
                    EcCurve.BRAINPOOLP512R1, setOf(
                        KeyPurpose.AGREE_KEY
                    )
                )

                "devmode_sa_software_mdoc_auth_x25519" -> Pair(
                    EcCurve.X25519,
                    setOf(KeyPurpose.AGREE_KEY)
                )

                "devmode_sa_software_mdoc_auth_x448" -> Pair(
                    EcCurve.X448,
                    setOf(KeyPurpose.AGREE_KEY)
                )

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
            val builder = CborMap.builder()
                .put("curve", curve.coseCurveIdentifier)
                .put("purposes", KeyPurpose.encodeSet(purposes))
            if (passphrase != null) {
                builder.put("passphrase", passphrase)
            }
            if (passphraseConstraints != null) {
                builder.put("passphraseConstraints", passphraseConstraints.toDataItem())
            }
            return CredentialConfiguration(
                challenge,
                "SoftwareSecureArea",
                Cbor.encode(builder.end().build())
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
            // Cloud can do both ECDSA and ECDH
            val purposes = setOf(KeyPurpose.SIGN, KeyPurpose.AGREE_KEY)
            return CredentialConfiguration(
                challenge,
                "CloudSecureArea?id=${documentId}&url=/csa",
                Cbor.encode(
                    CborMap.builder()
                        .put("passphraseRequired", true)
                        .put("userAuthenticationRequired", (userAuthType != 0))
                        .put("userAuthenticationTimeoutMillis", 0L)
                        .put("userAuthenticationTypes", userAuthType)
                        .put("purposes", KeyPurpose.encodeSet(purposes))
                        .end().build()
                )
            )
        }

        else -> {
            throw IllegalStateException("Unexpected value $chosenSa")
        }
    }
}