package org.multipaz.testapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import org.multipaz.testapp.provisioning.backend.ApplicationSupportLocal
import org.multipaz.testapp.provisioning.backend.ProvisioningBackendProviderLocal
import org.multipaz.testapp.provisioning.backend.ProvisioningBackendProviderRemote
import org.multipaz.testapp.provisioning.model.ProvisioningModel
import org.multipaz.testapp.provisioning.openid4vci.extractCredentialIssuerData
import org.multipaz.testapp.ui.AppTheme
import org.multipaz.testapp.ui.BarcodeScanningScreen
import org.multipaz.testapp.ui.CameraScreen
import org.multipaz.testapp.ui.FaceDetectionScreen
import org.multipaz.testapp.ui.SelfieCheckScreen
import org.multipaz.models.digitalcredentials.DigitalCredentials
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Cbor
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.EcPrivateKey
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509Cert
import org.multipaz.document.DocumentStore
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.PhotoID
import org.multipaz.documenttype.knowntypes.UtopiaMovieTicket
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.mdoc.vical.SignedVical
import org.multipaz.secure_area_test_app.ui.CloudSecureAreaScreen
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.cloud.CloudSecureArea
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.StorageTable
import org.multipaz.storage.StorageTableSpec
import org.multipaz.testapp.ui.AboutScreen
import org.multipaz.testapp.ui.AndroidKeystoreSecureAreaScreen
import org.multipaz.testapp.ui.CertificateScreen
import org.multipaz.testapp.ui.CertificateViewerExamplesScreen
import org.multipaz.testapp.ui.ConsentModalBottomSheetListScreen
import org.multipaz.testapp.ui.ConsentModalBottomSheetScreen
import org.multipaz.testapp.ui.CredentialClaimsViewerScreen
import org.multipaz.testapp.ui.CredentialViewerScreen
import org.multipaz.testapp.ui.DocumentStoreScreen
import org.multipaz.testapp.ui.DocumentViewerScreen
import org.multipaz.testapp.ui.IsoMdocMultiDeviceTestingScreen
import org.multipaz.testapp.ui.IsoMdocProximityReadingScreen
import org.multipaz.testapp.ui.IsoMdocProximitySharingScreen
import org.multipaz.testapp.ui.NfcScreen
import org.multipaz.testapp.ui.NotificationsScreen
import org.multipaz.testapp.ui.PassphraseEntryFieldScreen
import org.multipaz.testapp.ui.PassphrasePromptScreen
import org.multipaz.testapp.ui.PresentmentScreen
import org.multipaz.testapp.ui.ProvisioningTestScreen
import org.multipaz.testapp.ui.QrCodesScreen
import org.multipaz.testapp.ui.RichTextScreen
import org.multipaz.testapp.ui.ScreenLockScreen
import org.multipaz.testapp.ui.SecureEnclaveSecureAreaScreen
import org.multipaz.testapp.ui.SettingsScreen
import org.multipaz.testapp.ui.SoftwareSecureAreaScreen
import org.multipaz.testapp.ui.StartScreen
import org.multipaz.testapp.ui.VerifierType
import org.multipaz.util.Logger
import multipazproject.samples.testapp.generated.resources.Res
import multipazproject.samples.testapp.generated.resources.back_button
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.document.buildDocumentStore
import org.multipaz.mdoc.zkp.ZkSystemRepository
import org.multipaz.mdoc.zkp.longfellow.LongfellowZkSystem
import org.multipaz.models.presentment.PresentmentSource
import org.multipaz.models.presentment.SimplePresentmentSource
import org.multipaz.prompt.PromptModel
import org.multipaz.provisioning.WalletApplicationCapabilities
import org.multipaz.provisioning.evidence.Openid4VciCredentialOffer
import org.multipaz.storage.base.BaseStorageTable
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.util.Platform
import org.multipaz.testapp.ui.TrustManagerScreen
import org.multipaz.testapp.ui.TrustPointViewerScreen
import org.multipaz.trustmanagement.CompositeTrustManager
import org.multipaz.util.fromHex
import org.multipaz.trustmanagement.TrustManagerLocal
import org.multipaz.trustmanagement.TrustPointAlreadyExistsException
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.trustmanagement.VicalTrustManager
import org.multipaz.util.toHex

/**
 * Application singleton.
 */
class App private constructor (val promptModel: PromptModel) {

    lateinit var settingsModel: TestAppSettingsModel

    lateinit var documentTypeRepository: DocumentTypeRepository

    lateinit var secureAreaRepository: SecureAreaRepository
    lateinit var softwareSecureArea: SoftwareSecureArea
    lateinit var documentStore: DocumentStore
    lateinit var documentModel: DocumentModel

    lateinit var iacaKey: EcPrivateKey
    lateinit var iacaCert: X509Cert

    lateinit var readerRootKey: EcPrivateKey
    lateinit var readerRootCert: X509Cert

    lateinit var readerKey: EcPrivateKey
    lateinit var readerCert: X509Cert

    lateinit var issuerTrustManager: CompositeTrustManager

    lateinit var readerTrustManager: CompositeTrustManager

    private lateinit var provisioningModel: ProvisioningModel

    private val credentialOffers = Channel<Openid4VciCredentialOffer>()

    private val provisioningBackendProviderLocal = ProvisioningBackendProviderLocal()

    lateinit var zkSystemRepository: ZkSystemRepository

    private val initLock = Mutex()
    private var initialized = false

    fun getPresentmentSource(): PresentmentSource {
        val useAuth = settingsModel.presentmentRequireAuthentication.value
        return SimplePresentmentSource(
            documentStore = documentStore,
            documentTypeRepository = documentTypeRepository,
            readerTrustManager = readerTrustManager,
            zkSystemRepository = zkSystemRepository,
            preferSignatureToKeyAgreement = settingsModel.presentmentPreferSignatureToKeyAgreement.value,
            domainMdocSignature = if (useAuth) {
                TestAppUtils.CREDENTIAL_DOMAIN_MDOC_USER_AUTH
            } else {
                TestAppUtils.CREDENTIAL_DOMAIN_MDOC_NO_USER_AUTH
            },
            domainMdocKeyAgreement = if (useAuth) {
                TestAppUtils.CREDENTIAL_DOMAIN_MDOC_MAC_USER_AUTH
            } else {
                TestAppUtils.CREDENTIAL_DOMAIN_MDOC_MAC_NO_USER_AUTH
            },
            domainKeylessSdJwt = TestAppUtils.CREDENTIAL_DOMAIN_SDJWT_KEYLESS,
            domainKeyBoundSdJwt = if (useAuth) {
                TestAppUtils.CREDENTIAL_DOMAIN_SDJWT_USER_AUTH
            } else {
                TestAppUtils.CREDENTIAL_DOMAIN_SDJWT_NO_USER_AUTH
            },
        )
    }

    suspend fun init() {
        initLock.withLock {
            if (initialized) {
                return
            }
            val initFuncs = listOf<Pair<suspend () -> Unit, String>>(
                Pair(::platformInit, "platformInit"),
                Pair(::settingsInit, "settingsInit"),
                Pair(::platformCryptoInit, "platformCryptoInit"),
                Pair(::documentTypeRepositoryInit, "documentTypeRepositoryInit"),
                Pair(::documentStoreInit, "documentStoreInit"),
                Pair(::documentModelInit, "documentModelInit"),
                Pair(::keyStorageInit, "keyStorageInit"),
                Pair(::iacaInit, "iacaInit"),
                Pair(::readerRootInit, "readerRootInit"),
                Pair(::readerInit, "readerInit"),
                Pair(::trustManagersInit, "trustManagersInit"),
                Pair(::provisioningModelInit, "provisioningModelInit"),
                Pair(::zkSystemRepositoryInit, "zkSystemRepositoryInit")
            )

            val begin = Clock.System.now()
            for ((func, name) in initFuncs) {
                val funcBegin = Clock.System.now()
                func()
                val funcEnd = Clock.System.now()
                Logger.i(TAG, "$name initialization time: ${(funcEnd - funcBegin).inWholeMilliseconds} ms")
            }
            val end = Clock.System.now()
            Logger.i(TAG, "Total application initialization time: ${(end - begin).inWholeMilliseconds} ms")
            initialized = true
        }
    }

    private suspend fun platformCryptoInit() {
        platformCryptoInit(settingsModel)
    }

    private suspend fun settingsInit() {
        settingsModel = TestAppSettingsModel.create(Platform.storage)
    }

    private suspend fun documentTypeRepositoryInit() {
        documentTypeRepository = DocumentTypeRepository()
        documentTypeRepository.addDocumentType(DrivingLicense.getDocumentType())
        documentTypeRepository.addDocumentType(PhotoID.getDocumentType())
        documentTypeRepository.addDocumentType(EUPersonalID.getDocumentType())
        documentTypeRepository.addDocumentType(UtopiaMovieTicket.getDocumentType())
    }

    private suspend fun documentStoreInit() {
        softwareSecureArea = SoftwareSecureArea.create(Platform.storage)
        secureAreaRepository = SecureAreaRepository.Builder()
            .add(softwareSecureArea)
            .add(Platform.getSecureArea())
            .addFactory(CloudSecureArea.IDENTIFIER_PREFIX) { identifier ->
                val queryString = identifier.substring(CloudSecureArea.IDENTIFIER_PREFIX.length + 1)
                val params = queryString.split("&").associate {
                    val parts = it.split("=", ignoreCase = false, limit = 2)
                    parts[0] to parts[1].decodeURLPart()
                }
                val cloudSecureAreaUrl = params["url"]!!
                Logger.i(TAG, "Creating CSA with url $cloudSecureAreaUrl for $identifier")
                CloudSecureArea.create(
                    Platform.nonBackedUpStorage,
                    identifier,
                    cloudSecureAreaUrl,
                    platformHttpClientEngineFactory()
                )
            }
            .build()
        documentStore = buildDocumentStore(
            storage = Platform.storage,
            secureAreaRepository = secureAreaRepository
        ) {
            //setTableSpec(testDocumentTableSpec)
        }
    }

    private suspend fun documentModelInit() {
        documentModel = DocumentModel(
            scope = CoroutineScope(Dispatchers.IO),
            documentStore = documentStore
        )
        documentModel.initialize()
    }

    private suspend fun trustManagersInit() {
        generateTrustManagers()
    }

    private val useWalletServer = false

    private suspend fun provisioningModelInit() {
        val backendProvider = if (useWalletServer) {
            ProvisioningBackendProviderRemote(
                baseUrl = "http://localhost:8080/server"
            ) {
                WalletApplicationCapabilities(
                    generatedAt = Clock.System.now(),
                    androidKeystoreAttestKeyAvailable = true,
                    androidKeystoreStrongBoxAvailable = true,
                    androidIsEmulator = platformIsEmulator,
                    directAccessSupported = false,
                )
            }
        } else {
            provisioningBackendProviderLocal
        }
        provisioningModel = ProvisioningModel(
            backendProvider,
            documentStore,
            promptModel,
            secureAreaRepository
        )
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun zkSystemRepositoryInit() {
        val circuitsToAdd = listOf(
            "files/longfellow-libzk-v1/3_1_bd3168ea0a9096b4f7b9b61d1c210dac1b7126a9ec40b8bc770d4d485efce4e9",
            "files/longfellow-libzk-v1/3_2_40b2b68088f1d4c93a42edf01330fed8cac471cdae2b192b198b4d4fc41c9083",
            "files/longfellow-libzk-v1/3_3_99a5da3739df68c87c7a380cc904bb275dbd4f1b916c3d297ba9d15ee86dd585",
            "files/longfellow-libzk-v1/3_4_5249dac202b61e03361a2857867297ee7b1d96a8a4c477d15a4560bde29f704f",
        )

        val longfellowSystem = LongfellowZkSystem()
        for (circuit in circuitsToAdd) {
            val circuitBytes = Res.readBytes(circuit)
            val pathParts = circuit.split("/")
            longfellowSystem.addCircuit(pathParts[pathParts.size - 1], ByteString(circuitBytes))
        }
        zkSystemRepository = ZkSystemRepository().apply {
            add(longfellowSystem)
        }
    }

    private val certsValidFrom = LocalDate.parse("2024-12-01").atStartOfDayIn(TimeZone.UTC)
    private val certsValidUntil = LocalDate.parse("2034-12-01").atStartOfDayIn(TimeZone.UTC)

    private val bundledIacaKey: EcPrivateKey by lazy {
        val iacaKeyPub = EcPublicKey.fromPem(
            """
                    -----BEGIN PUBLIC KEY-----
                    MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE+QDye70m2O0llPXMjVjxVZz3m5k6agT+
                    wih+L79b7jyqUl99sbeUnpxaLD+cmB3HK3twkA7fmVJSobBc+9CDhkh3mx6n+YoH
                    5RulaSWThWBfMyRjsfVODkosHLCDnbPV
                    -----END PUBLIC KEY-----
                """.trimIndent().trim(),
            EcCurve.P384
        )
        EcPrivateKey.fromPem(
            """
                    -----BEGIN PRIVATE KEY-----
                    MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDCcRuzXW3pW2h9W8pu5
                    /CSR6JSnfnZVATq+408WPoNC3LzXqJEQSMzPsI9U1q+wZ2yhZANiAAT5APJ7vSbY
                    7SWU9cyNWPFVnPebmTpqBP7CKH4vv1vuPKpSX32xt5SenFosP5yYHccre3CQDt+Z
                    UlKhsFz70IOGSHebHqf5igflG6VpJZOFYF8zJGOx9U4OSiwcsIOds9U=
                    -----END PRIVATE KEY-----
                """.trimIndent().trim(),
            iacaKeyPub
        )
    }

    val bundledIacaCert: X509Cert by lazy {
        MdocUtil.generateIacaCertificate(
            iacaKey = iacaKey,
            subject = X500Name.fromName("C=US,CN=OWF Multipaz TEST IACA"),
            serial = ASN1Integer.fromRandom(numBits = 128),
            validFrom = certsValidFrom,
            validUntil = certsValidUntil,
            issuerAltNameUrl = "https://github.com/openwallet-foundation-labs/identity-credential",
            crlUrl = "https://github.com/openwallet-foundation-labs/identity-credential/crl"
        )
    }

    private val bundledReaderRootKey: EcPrivateKey by lazy {
        val readerRootKeyPub = EcPublicKey.fromPem(
            """
                    -----BEGIN PUBLIC KEY-----
                    MHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE+QDye70m2O0llPXMjVjxVZz3m5k6agT+
                    wih+L79b7jyqUl99sbeUnpxaLD+cmB3HK3twkA7fmVJSobBc+9CDhkh3mx6n+YoH
                    5RulaSWThWBfMyRjsfVODkosHLCDnbPV
                    -----END PUBLIC KEY-----
                """.trimIndent().trim(),
            EcCurve.P384
        )
        EcPrivateKey.fromPem(
            """
                    -----BEGIN PRIVATE KEY-----
                    MIG2AgEAMBAGByqGSM49AgEGBSuBBAAiBIGeMIGbAgEBBDCcRuzXW3pW2h9W8pu5
                    /CSR6JSnfnZVATq+408WPoNC3LzXqJEQSMzPsI9U1q+wZ2yhZANiAAT5APJ7vSbY
                    7SWU9cyNWPFVnPebmTpqBP7CKH4vv1vuPKpSX32xt5SenFosP5yYHccre3CQDt+Z
                    UlKhsFz70IOGSHebHqf5igflG6VpJZOFYF8zJGOx9U4OSiwcsIOds9U=
                    -----END PRIVATE KEY-----
                """.trimIndent().trim(),
            readerRootKeyPub
        )
    }

    private val bundledReaderRootCert: X509Cert by lazy {
        MdocUtil.generateReaderRootCertificate(
            readerRootKey = bundledReaderRootKey,
            subject = X500Name.fromName("CN=OWF Multipaz TestApp Reader Root"),
            serial = ASN1Integer.fromRandom(numBits = 128),
            validFrom = certsValidFrom,
            validUntil = certsValidUntil,
            crlUrl = "https://github.com/openwallet-foundation-labs/identity-credential/crl"
        )
    }

    private lateinit var keyStorage: StorageTable

    private suspend fun keyStorageInit() {
        keyStorage = Platform.storage.getTable(
            StorageTableSpec(
                name = "TestAppKeys",
                supportPartitions = false,
                supportExpiration = false
            )
        )
    }

    private suspend fun iacaInit() {
        iacaKey = keyStorage.get("iacaKey")?.let { EcPrivateKey.fromDataItem(Cbor.decode(it.toByteArray())) }
            ?: run {
                keyStorage.insert("iacaKey", ByteString(Cbor.encode(bundledIacaKey.toDataItem())))
                bundledIacaKey
            }
        iacaCert = keyStorage.get("iacaCert")?.let { X509Cert.fromDataItem(Cbor.decode(it.toByteArray())) }
            ?: run {
                keyStorage.insert("iacaCert", ByteString(Cbor.encode(bundledIacaCert.toDataItem())))
                bundledIacaCert
            }
    }

    private suspend fun readerRootInit() {
        readerRootKey = keyStorage.get("readerRootKey")?.let { EcPrivateKey.fromDataItem(Cbor.decode(it.toByteArray())) }
            ?: run {
                keyStorage.insert("readerRootKey", ByteString(Cbor.encode(bundledReaderRootKey.toDataItem())))
                bundledReaderRootKey
            }
        readerRootCert = keyStorage.get("readerRootCert")?.let { X509Cert.fromDataItem(Cbor.decode(it.toByteArray())) }
            ?: run {
                keyStorage.insert("readerRootCert", ByteString(Cbor.encode(bundledReaderRootCert.toDataItem())))
                bundledReaderRootCert
            }
        println("readerRootCert: ${readerRootCert.toPem()}")
    }

    private suspend fun readerInit() {
        readerKey = keyStorage.get("readerKey")?.let { EcPrivateKey.fromDataItem(Cbor.decode(it.toByteArray())) }
            ?: run {
                val key = Crypto.createEcPrivateKey(EcCurve.P256)
                keyStorage.insert("readerKey", ByteString(Cbor.encode(key.toDataItem())))
                key
            }
        readerCert = keyStorage.get("readerCert")?.let { X509Cert.fromDataItem(Cbor.decode(it.toByteArray())) }
            ?: run {
                val cert = MdocUtil.generateReaderCertificate(
                    readerRootCert = readerRootCert,
                    readerRootKey = readerRootKey,
                    readerKey = readerKey.publicKey,
                    subject = X500Name.fromName("CN=OWF Multipaz TestApp Reader Cert"),
                    serial = ASN1Integer.fromRandom(numBits = 128),
                    validFrom = certsValidFrom,
                    validUntil = certsValidUntil,
                )
                keyStorage.insert("readerCert", ByteString(Cbor.encode(cert.toDataItem())))
                cert
            }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun generateTrustManagers() {
        val builtInIssuerTrustManager = TrustManagerLocal(
            storage = EphemeralStorage(),
            partitionId = "BuiltInTrustedIssuers",
            identifier = "Built-in Trusted Issuers"
        )
        builtInIssuerTrustManager.addX509Cert(
            certificate = iacaCert,
            metadata = TrustMetadata(displayName = "OWF Multipaz TestApp Issuer"),
        )
        val signedVical = SignedVical.parse(Res.readBytes("files/20250225 RDW Test Vical.vical"))
        // TODO: validate the Vical is signed by someone we trust, probably force this
        //   by having the caller pass in the public key
        val vicalTrustManager = VicalTrustManager(signedVical)
        issuerTrustManager = CompositeTrustManager(listOf(vicalTrustManager, builtInIssuerTrustManager))


        val readerCertFileNames = listOf(
            "Animo Reader CA.cer",
            "Bundesdruckerei Reader CA.cer",
            "CLR Labs Reader CA.cer",
            "Credence ID Reader CA.cer",
            "Fast Enterprises Reader CA.cer",
            "Fime Reader CA 1.cer",
            "Fime Reader CA 2.cer",
            "Google Reader CA.cer",
            "Idakto Reader CA.cer",
            "Idemia Reader CA.cer",
            "LapID Reader CA.cer",
            "MATTR Reader CA.cer",
            "Nearform Reader CA.cer",
            "OGCIO Reader CA.cer",
            "RDW Test Reader CA.cer",
            "Scytales Reader CA.cer",
            "SpruceID Reader CA.cer",
            "Thales Reader CA 1.cer",
            "Thales Reader CA 2.cer",
            "Thales Root CA.cer",
            "Toppan Reader CA.cer",
            "Zetes Reader CA.cer"
        )
        val builtInReaderTrustManager = TrustManagerLocal(
            storage = EphemeralStorage(),
            partitionId = "BuiltInTrustedReaders",
            identifier = "Built-in Trusted Readers"
        )
        readerTrustManager = CompositeTrustManager(listOf(builtInReaderTrustManager))
        if (builtInReaderTrustManager.getTrustPoints().isEmpty()) {
            try {
                builtInReaderTrustManager.addX509Cert(
                    certificate = readerRootCert,
                    metadata = TrustMetadata(
                        displayName = "Multipaz TestApp",
                        displayIcon = ByteString(Res.readBytes("files/utopia-brewery.png")),
                        privacyPolicyUrl = "https://apps.multipaz.org"
                    )
                )
            } catch (e: TrustPointAlreadyExistsException) {
                // Do nothing, it's possible our certificate is in the list above.
            }
            for (readerCertFileName in readerCertFileNames) {
                val certData = Res.readBytes("files/20250225 Reader CA Certificates/" + readerCertFileName)
                val readerCert = X509Cert.fromPem(certData.decodeToString())
                try {
                    builtInReaderTrustManager.addX509Cert(
                        certificate = readerCert,
                        metadata = TrustMetadata(
                            displayName = readerCertFileName.substringBeforeLast(".")
                        ),
                    )
                } catch (e: TrustPointAlreadyExistsException) {
                    val existingTrustPoint = builtInIssuerTrustManager.getTrustPoints().first {
                        it.certificate.subjectKeyIdentifier!!.toHex() == readerCert.subjectKeyIdentifier!!.toHex()
                    }
                    Logger.w(TAG, "builtInReaderTrustManager: Error adding certificate with subject " +
                            "${readerCert.subject.name} - already contains a certificate with " +
                            "subject ${existingTrustPoint.certificate.subject.name} with the same " +
                            "Subject Key Identifier", e)
                }
            }
            // This is for https://verifier.multipaz.org website.
            try {
                builtInReaderTrustManager.addX509Cert(
                    certificate = X509Cert(
                        "30820269308201efa0030201020210b7352f14308a2d40564006785270b0e7300a06082a8648ce3d0403033037310b300906035504060c0255533128302606035504030c1f76657269666965722e6d756c746970617a2e6f726720526561646572204341301e170d3235303631393232313633325a170d3330303631393232313633325a3037310b300906035504060c0255533128302606035504030c1f76657269666965722e6d756c746970617a2e6f7267205265616465722043413076301006072a8648ce3d020106052b81040022036200046baa02cc2f2b7c77f054e9907fcdd6c87110144f07acb2be371b2e7c90eb48580c5e3851bcfb777c88e533244069ff78636e54c7db5783edbc133cc1ff11bbabc3ff150f67392264c38710255743fee7cde7df6e55d7e9d5445d1bde559dcba8a381bf3081bc300e0603551d0f0101ff04040302010630120603551d130101ff040830060101ff02010030560603551d1f044f304d304ba049a047864568747470733a2f2f6769746875622e636f6d2f6f70656e77616c6c65742d666f756e646174696f6e2d6c6162732f6964656e746974792d63726564656e7469616c2f63726c301d0603551d0e04160414b18439852f4a6eeabfea62adbc51d081f7488729301f0603551d23041830168014b18439852f4a6eeabfea62adbc51d081f7488729300a06082a8648ce3d040303036800306502302a1f3bb0afdc31bcee73d3c5bf289245e76bd91a0fd1fb852b45fc75d3a98ba84430e6a91cbfc6b3f401c91382a43a64023100db22d2243644bb5188f2e0a102c0c167024fb6fe4a1d48ead55a6893af52367fb3cdbd66369aa689ecbeb5c84f063666".fromHex()
                    ),
                    metadata = TrustMetadata(
                        displayName = "Multipaz Verifier",
                        displayIcon = ByteString(Res.readBytes("drawable/app_icon.webp")),
                        privacyPolicyUrl = "https://apps.multipaz.org"
                    )
                )
            } catch (e: TrustPointAlreadyExistsException) {
                // Do nothing, it's possible our certificate is in the list above.
            }
            // This is for Multipaz Identity Reader app from https://apps.multipaz.org on devices in
            // the GREEN boot state.
            try {
                builtInReaderTrustManager.addX509Cert(
                    certificate = X509Cert(
                        "30820261308201E7A00302010202103925792727AC38B28778373ED2A9ADB9300A06082A8648CE3D0403033033310B300906035504060C0255533124302206035504030C1B4D756C746970617A204964656E7469747920526561646572204341301E170D3235303730353132323032315A170D3330303730353132323032315A3033310B300906035504060C0255533124302206035504030C1B4D756C746970617A204964656E74697479205265616465722043413076301006072A8648CE3D020106052B81040022036200043E145F98DA6C32EE4688C4A7DAEC6640046CFF0872E8F7A8DE3005462AE9488E92850B30E2D46FEEFC620A279BEB09470AB20C9F66C584E396A9625BC3E90DFBA54197A3668D901AAA41F493C89E4AC20689794FED1352CD2086413965006C54A381BF3081BC300E0603551D0F0101FF04040302010630120603551D130101FF040830060101FF02010030560603551D1F044F304D304BA049A047864568747470733A2F2F6769746875622E636F6D2F6F70656E77616C6C65742D666F756E646174696F6E2D6C6162732F6964656E746974792D63726564656E7469616C2F63726C301D0603551D0E04160414CFA4AF87907312962E4D7A17646ACC1C45719B21301F0603551D23041830168014CFA4AF87907312962E4D7A17646ACC1C45719B21300A06082A8648CE3D040303036800306502310090FB8F814BCC87DB42957D22B54D20BF45F44CE0CF5734167ED27F5E3E0F5FB57505B797B894175D2BD98BF16CE726EA02305BA4F1ECB894A9DBE27B9BBF988F233C2E0BB0B4BADAA3EC5B3EA9D99C58DAD26128A4B363849E32626A9D5C3CE3E4DA".fromHex()
                    ),
                    metadata = TrustMetadata(
                        displayName = "Multipaz Identity Reader",
                        displayIcon = ByteString(Res.readBytes("drawable/app_icon.webp")),
                        privacyPolicyUrl = "https://apps.multipaz.org"
                    )
                )
            } catch (e: TrustPointAlreadyExistsException) {
                // Do nothing, it's possible our certificate is in the list above.
            }
            // This is for Multipaz Identity Reader either compiled locally or the APK from https://apps.multipaz.org
            // but running on a device that isn't in the GREEN boot state.
            try {
                builtInReaderTrustManager.addX509Cert(
                    certificate = X509Cert(
                        "308202893082020FA003020102021041DFFB3D7133B2623E535E09D9C3B56E300A06082A8648CE3D0403033047310B300906035504060C0255533138303606035504030C2F4D756C746970617A204964656E74697479205265616465722043412028556E74727573746564204465766963657329301E170D3235303731393233303831345A170D3330303731393233303831345A3047310B300906035504060C0255533138303606035504030C2F4D756C746970617A204964656E74697479205265616465722043412028556E747275737465642044657669636573293076301006072A8648CE3D020106052B8104002203620004EA8A139ED395B79C877255FEF2138987262CFBB6CA1F72688D4E89F062C3CA05B2704531DAEC0304F93A007CD84F31A119F3794151306082C4D4352855A752F9C733D2FA32B4B462644769F2F7E53280F1AD519C443AE9462B923C64877EDF91A381BF3081BC300E0603551D0F0101FF04040302010630120603551D130101FF040830060101FF02010030560603551D1F044F304D304BA049A047864568747470733A2F2F6769746875622E636F6D2F6F70656E77616C6C65742D666F756E646174696F6E2D6C6162732F6964656E746974792D63726564656E7469616C2F63726C301D0603551D0E041604149BCFDAFD2059978E21869C7DD28AAF7481EBABC5301F0603551D230418301680149BCFDAFD2059978E21869C7DD28AAF7481EBABC5300A06082A8648CE3D0403030368003065023100A26AA37C97B6935EB64B959ACB7B04053723EFE0CFBDA2C972C96812C8FF1DA4E122C296A909502B180DBB5AC4FD7AF202307F1AAE9412B8162A5B29A7E2A9CEE00059A2A4F9B32370CE1A28E28E5378AD981FBD8D74D0DBDD0373C327595C1006CE".fromHex()
                    ),
                    metadata = TrustMetadata(
                        displayName = "Multipaz Identity Reader (Untrusted Devices)",
                        displayIcon = ByteString(Res.readBytes("drawable/app_icon.webp")),
                        privacyPolicyUrl = "https://apps.multipaz.org"
                    )
                )
            } catch (e: TrustPointAlreadyExistsException) {
                // Do nothing, it's possible our certificate is in the list above.
            }
        }
    }

    /**
     * Starts export documents via the W3C Digital Credentials API on the platform, if available.
     *
     * This should be called when the main wallet application UI is running.
     */
    fun startExportDocumentsToDigitalCredentials() {
        CoroutineScope(Dispatchers.IO).launch {
            if (DigitalCredentials.Default.available) {
                DigitalCredentials.Default.startExportingCredentials(
                    documentStore = documentStore,
                    documentTypeRepository = documentTypeRepository,
                )
            }
        }
    }

    /**
     * Handle a link (either a app link, universal link, or custom URL schema link).
     */
    fun handleUrl(url: String) {
        if (url.startsWith(OID4VCI_CREDENTIAL_OFFER_URL_SCHEME)
            || url.startsWith(HAIP_URL_SCHEME)) {
            val queryIndex = url.indexOf('?')
            if (queryIndex >= 0) {
                val query = url.substring(queryIndex + 1)
                CoroutineScope(Dispatchers.Default).launch {
                    val offer = extractCredentialIssuerData(query)
                    if (offer != null) {
                        Logger.i(TAG, "Process credential offer '$query'")
                        credentialOffers.send(offer)
                    }
                }
            }
        } else if (url.startsWith(ApplicationSupportLocal.APP_LINK_BASE_URL)) {
            CoroutineScope(Dispatchers.Default).launch {
                withContext(provisioningModel.coroutineContext) {
                    provisioningBackendProviderLocal.getApplicationSupport()
                        .onLandingUrlNavigated(url)
                }
            }
        }
    }

    companion object {
        private const val TAG = "App"

        // OID4VCI url scheme used for filtering OID4VCI Urls from all incoming URLs (deep links or QR)
        private const val OID4VCI_CREDENTIAL_OFFER_URL_SCHEME = "openid-credential-offer://"
        private const val HAIP_URL_SCHEME = "haip://"

        private var app: App? = null
        fun getInstance(): App {
            if (app == null) {
                app = App(platformPromptModel)
            }
            return app!!
        }

        private val testDocumentTableSpec = object: StorageTableSpec(
            name = "TestAppDocuments",
            supportExpiration = false,
            supportPartitions = false,
            schemaVersion = 1L,           // Bump every time incompatible changes are made
        ) {
            override suspend fun schemaUpgrade(oldTable: BaseStorageTable) {
                oldTable.deleteAll()
            }
        }
    }

    private lateinit var snackbarHostState: SnackbarHostState

    private val presentmentModel = PresentmentModel().apply {
        setPromptModel(promptModel)
    }

    @Composable
    @Preview
    fun Content(navController: NavHostController = rememberNavController()) {
        var isInitialized = remember { mutableStateOf<Boolean>(false) }
        if (!isInitialized.value) {
            CoroutineScope(Dispatchers.Main).launch {
                init()
                isInitialized.value = true
            }
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Initializing...")
            }
            return
        }

        val backStackEntry by navController.currentBackStackEntryAsState()
        val routeWithoutArgs = backStackEntry?.destination?.route?.substringBefore('/')

        val currentDestination = appDestinations.find {
            it.route == routeWithoutArgs
        } ?: StartDestination

        LaunchedEffect(true) {
            while (true) {
                val credentialOffer = credentialOffers.receive()
                Logger.i(TAG, "Process credential offer from ${credentialOffer.issuerUri}")
                provisioningModel.startProvisioning(credentialOffer)
                navController.navigate(ProvisioningTestDestination.route)
            }
        }

        snackbarHostState = remember { SnackbarHostState() }
        AppTheme {
            // A surface container using the 'background' color from the theme
            Scaffold(
                topBar = {
                    AppBar(
                        currentDestination = currentDestination,
                        canNavigateBack = navController.previousBackStackEntry != null,
                        navigateUp = { navController.navigateUp() },
                        navigateToSettings = { navController.navigate(SettingsDestination.route) },
                        includeSettingsIcon = (currentDestination != SettingsDestination)
                    )
                },
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            ) { innerPadding ->

                PromptDialogs(promptModel)

                NavHost(
                    navController = navController,
                    startDestination = StartDestination.route,
                    modifier = Modifier
                        .fillMaxSize()
                        //.verticalScroll(rememberScrollState())
                        .padding(innerPadding)
                ) {
                    composable(route = StartDestination.route) {
                        StartScreen(
                            documentModel = documentModel,
                            onClickAbout = { navController.navigate(AboutDestination.route) },
                            onClickDocumentStore = { navController.navigate(DocumentStoreDestination.route) },
                            onClickTrustedIssuers = { navController.navigate(TrustedIssuersDestination.route) },
                            onClickTrustedVerifiers = { navController.navigate(TrustedVerifiersDestination.route) },
                            onClickSoftwareSecureArea = { navController.navigate(SoftwareSecureAreaDestination.route) },
                            onClickAndroidKeystoreSecureArea = { navController.navigate(AndroidKeystoreSecureAreaDestination.route) },
                            onClickCloudSecureArea = { navController.navigate(CloudSecureAreaDestination.route) },
                            onClickSecureEnclaveSecureArea = { navController.navigate(SecureEnclaveSecureAreaDestination.route) },
                            onClickPassphraseEntryField = { navController.navigate(PassphraseEntryFieldDestination.route) },
                            onClickPassphrasePrompt = { navController.navigate(PassphrasePromptDestination.route) },
                            onClickProvisioningTestField = { navController.navigate(ProvisioningTestDestination.route) },
                            onClickConsentSheetList = { navController.navigate(ConsentModalBottomSheetListDestination.route) },
                            onClickQrCodes = { navController.navigate(QrCodesDestination.route) },
                            onClickNfc = { navController.navigate(NfcDestination.route) },
                            onClickIsoMdocProximitySharing = { navController.navigate(IsoMdocProximitySharingDestination.route) },
                            onClickIsoMdocProximityReading = { navController.navigate(IsoMdocProximityReadingDestination.route) },
                            onClickMdocTransportMultiDeviceTesting = { navController.navigate(IsoMdocMultiDeviceTestingDestination.route) },
                            onClickCertificatesViewerExamples = { navController.navigate(CertificatesViewerExamplesDestination.route) },
                            onClickRichText = { navController.navigate(RichTextDestination.route) },
                            onClickNotifications = { navController.navigate(NotificationsDestination.route) },
                            onClickScreenLock = { navController.navigate(ScreenLockDestination.route) },
                            onClickCamera = { navController.navigate(CameraDestination.route) },
                            onClickFaceDetection = { navController.navigate(FaceDetectionDestination.route) },
                            onClickBarcodeScanning = { navController.navigate(BarcodeScanningDestination.route) },
                            onClickSelfieCheck = { navController.navigate(SelfieCheckScreenDestination.route) }
                        )
                    }
                    composable(route = SettingsDestination.route) {
                        SettingsScreen(
                            app = this@App,
                            showToast = { message: String -> showToast(message) }
                        )
                    }
                    composable(route = AboutDestination.route) {
                        AboutScreen()
                    }
                    composable(route = DocumentStoreDestination.route) {
                        DocumentStoreScreen(
                            documentStore = documentStore,
                            documentModel = documentModel,
                            softwareSecureArea = softwareSecureArea,
                            settingsModel = settingsModel,
                            iacaKey = iacaKey,
                            iacaCert = iacaCert,
                            showToast = { message: String -> showToast(message) },
                            onViewDocument = { documentId ->
                                navController.navigate(DocumentViewerDestination.route + "/${documentId}")
                            }
                        )
                    }
                    composable(
                        route = DocumentViewerDestination.routeWithArgs,
                        arguments = DocumentViewerDestination.arguments
                    ) { backStackEntry ->
                        val documentId = backStackEntry.arguments?.getString(
                            DocumentViewerDestination.DOCUMENT_ID
                        )!!
                        DocumentViewerScreen(
                            documentModel = documentModel,
                            documentId = documentId,
                            showToast = ::showToast,
                            onViewCredential = { documentId, credentialId ->
                                navController.navigate(CredentialViewerDestination.route + "/${documentId}/${credentialId}")
                            }
                        )
                    }
                    composable(
                        route = CredentialViewerDestination.routeWithArgs,
                        arguments = CredentialViewerDestination.arguments
                    ) { backStackEntry ->
                        val documentId = backStackEntry.arguments?.getString(
                            CredentialViewerDestination.DOCUMENT_ID
                        )!!
                        val credentialId = backStackEntry.arguments?.getString(
                            CredentialViewerDestination.CREDENTIAL_ID
                        )!!
                        CredentialViewerScreen(
                            documentModel = documentModel,
                            documentId = documentId,
                            credentialId = credentialId,
                            showToast = ::showToast,
                            onViewCertificateChain = { encodedCertificateData: String ->
                                navController.navigate(CertificateViewerDestination.route + "/${encodedCertificateData}")
                            },
                            onViewCredentialClaims = { documentId, credentialId ->
                                navController.navigate(CredentialClaimsViewerDestination.route + "/${documentId}/${credentialId}")
                            }
                        )
                    }
                    composable(
                        route = CredentialClaimsViewerDestination.routeWithArgs,
                        arguments = CredentialClaimsViewerDestination.arguments
                    ) { backStackEntry ->
                        val documentId = backStackEntry.arguments?.getString(
                            CredentialViewerDestination.DOCUMENT_ID
                        )!!
                        val credentialId = backStackEntry.arguments?.getString(
                            CredentialViewerDestination.CREDENTIAL_ID
                        )!!
                        CredentialClaimsViewerScreen(
                            documentModel = documentModel,
                            documentTypeRepository = documentTypeRepository,
                            documentId = documentId,
                            credentialId = credentialId,
                            showToast = ::showToast,
                        )
                    }
                    composable(route = TrustedIssuersDestination.route) {
                        TrustManagerScreen(
                            compositeTrustManager = issuerTrustManager,
                            onViewTrustPoint = { trustPoint ->
                                navController.navigate(TrustPointViewerDestination.route +
                                        "/issuers/${trustPoint.certificate.subjectKeyIdentifier!!.toHex()}")
                            },
                            showToast = ::showToast
                        )
                    }
                    composable(route = TrustedVerifiersDestination.route) {
                        TrustManagerScreen(
                            compositeTrustManager = readerTrustManager,
                            onViewTrustPoint = { trustPoint ->
                                navController.navigate(TrustPointViewerDestination.route +
                                        "/readers/${trustPoint.certificate.subjectKeyIdentifier!!.toHex()}")
                            },
                            showToast = ::showToast
                        )
                    }
                    composable(
                        route = TrustPointViewerDestination.routeWithArgs,
                        arguments = TrustPointViewerDestination.arguments
                    ) { backStackEntry ->
                        val trustManagerId = backStackEntry.arguments?.getString(
                            TrustPointViewerDestination.TRUST_MANAGER_ID
                        )!!
                        val trustPointId = backStackEntry.arguments?.getString(
                            TrustPointViewerDestination.TRUST_POINT_ID
                        )!!
                        val trustManager = when (trustManagerId) {
                            "issuers" -> issuerTrustManager
                            "readers" -> readerTrustManager
                            else -> throw IllegalStateException()
                        }
                        TrustPointViewerScreen(
                            app = this@App,
                            trustManager = trustManager,
                            trustPointId = trustPointId,
                            showToast = ::showToast,
                        )
                    }
                    composable(route = SoftwareSecureAreaDestination.route) {
                        SoftwareSecureAreaScreen(
                            softwareSecureArea = softwareSecureArea,
                            promptModel = promptModel,
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = AndroidKeystoreSecureAreaDestination.route) {
                        AndroidKeystoreSecureAreaScreen(
                            promptModel = promptModel,
                            showToast = { message -> showToast(message) },
                            onViewCertificate = { encodedCertificateData ->
                                navController.navigate(CertificateViewerDestination.route + "/${encodedCertificateData}")
                            }
                        )
                    }
                    composable(route = SecureEnclaveSecureAreaDestination.route) {
                        SecureEnclaveSecureAreaScreen(showToast = { message -> showToast(message) })
                    }
                    composable(route = CloudSecureAreaDestination.route) {
                        CloudSecureAreaScreen(
                            app = this@App,
                            showToast = { message -> showToast(message) },
                            onViewCertificate = { encodedCertificateData ->
                                navController.navigate(CertificateViewerDestination.route + "/${encodedCertificateData}")
                            }
                        )
                    }
                    composable(route = PassphraseEntryFieldDestination.route) {
                        PassphraseEntryFieldScreen(showToast = { message -> showToast(message) })
                    }
                    composable(route = PassphrasePromptDestination.route) {
                        PassphrasePromptScreen(showToast = { message -> showToast(message) })
                    }
                    composable(route = ProvisioningTestDestination.route) {
                        ProvisioningTestScreen(
                            app = this@App,
                            provisioningModel = provisioningModel,
                            presentmentModel = presentmentModel
                        )
                    }
                    composable(route = ConsentModalBottomSheetListDestination.route) {
                        ConsentModalBottomSheetListScreen(
                            onConsentModalBottomSheetClicked =
                            { mdlSampleRequest, verifierType ->
                                navController.navigate(
                                    ConsentModalBottomSheetDestination.route + "/$mdlSampleRequest/$verifierType")
                            }
                        )
                    }
                    composable(
                        route = ConsentModalBottomSheetDestination.routeWithArgs,
                        arguments = ConsentModalBottomSheetDestination.arguments
                    ) { navBackStackEntry ->
                        val mdlSampleRequest = navBackStackEntry.arguments?.getString(
                            ConsentModalBottomSheetDestination.mdlSampleRequestArg
                        )!!
                        val verifierType = VerifierType.valueOf(navBackStackEntry.arguments?.getString(
                            ConsentModalBottomSheetDestination.verifierTypeArg
                        )!!)
                        ConsentModalBottomSheetScreen(
                            mdlSampleRequest = mdlSampleRequest,
                            verifierType = verifierType,
                            showToast = { message -> showToast(message) },
                            onSheetConfirmed = { navController.popBackStack() },
                            onSheetDismissed = { navController.popBackStack() },
                        )
                    }
                    composable(route = QrCodesDestination.route) {
                        QrCodesScreen(
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = NfcDestination.route) {
                        NfcScreen(
                            promptModel = promptModel,
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = IsoMdocProximitySharingDestination.route) {
                        IsoMdocProximitySharingScreen(
                            presentmentModel = presentmentModel,
                            settingsModel = settingsModel,
                            promptModel = promptModel,
                            onNavigateToPresentmentScreen = {
                                navController.navigate(PresentmentDestination.route)
                            },
                            showToast = { message -> showToast(message) },
                        )
                    }
                    composable(route = IsoMdocProximityReadingDestination.route) {
                        IsoMdocProximityReadingScreen(
                            app = this@App,
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = IsoMdocMultiDeviceTestingDestination.route) {
                        IsoMdocMultiDeviceTestingScreen(
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = PresentmentDestination.route) {
                        PresentmentScreen(
                            app = this@App,
                            presentmentModel = presentmentModel,
                            onPresentationComplete = { navController.popBackStack() },
                        )
                    }
                    composable(route = CertificatesViewerExamplesDestination.route) {
                        CertificateViewerExamplesScreen(
                            onViewCertificate = { encodedCertificateData ->
                                navController.navigate(CertificateViewerDestination.route + "/${encodedCertificateData}")
                            }
                        )
                    }
                    composable(
                        route = CertificateViewerDestination.routeWithArgs,
                        arguments = CertificateViewerDestination.arguments
                    ) { backStackEntry ->
                        val certData = backStackEntry.arguments?.getString(
                            CertificateViewerDestination.CERTIFICATE_DATA
                        )!!
                        CertificateScreen(certData)
                    }
                    composable(route = RichTextDestination.route) {
                        RichTextScreen()
                    }
                    composable(route = NotificationsDestination.route) {
                        NotificationsScreen(
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = ScreenLockDestination.route) {
                        ScreenLockScreen(
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = CameraDestination.route) {
                        CameraScreen(
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = FaceDetectionDestination.route) {
                        FaceDetectionScreen(
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = BarcodeScanningDestination.route) {
                        BarcodeScanningScreen(
                            showToast = { message -> showToast(message) }
                        )
                    }
                    composable(route = SelfieCheckScreenDestination.route) {
                        SelfieCheckScreen(
                            showToast = { message -> showToast(message) }
                        )
                    }
                }
            }
        }
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            when (snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "OK",
                duration = SnackbarDuration.Short,
            )) {
                SnackbarResult.Dismissed -> {
                }

                SnackbarResult.ActionPerformed -> {
                }
            }
        }
    }
}

/**
 * Composable that displays the topBar and displays back button if back navigation is possible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(
    currentDestination: Destination,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    navigateToSettings: () -> Unit,
    includeSettingsIcon: Boolean,
    modifier: Modifier = Modifier
) {
    val title = currentDestination.title?.let { stringResource(it) } ?: platformAppName
    TopAppBar(
        title = { Text(text = title) },
        colors = TopAppBarDefaults.mediumTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.back_button)
                    )
                }
            }
        },
        actions = {
            if (includeSettingsIcon) {
                IconButton(onClick = navigateToSettings) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null
                    )
                }
            }
        },
    )
}
