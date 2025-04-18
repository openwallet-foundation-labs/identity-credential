package org.multipaz.testapp

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.android.identity.testapp.ui.AppTheme
import com.android.identity.testapp.ui.CameraScreen
import org.multipaz.models.digitalcredentials.DigitalCredentials
import org.multipaz.models.presentment.PresentmentModel
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.Cbor
import org.multipaz.credential.CredentialLoader
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
import org.multipaz.mdoc.credential.MdocCredential
import org.multipaz.mdoc.util.MdocUtil
import org.multipaz.mdoc.vical.SignedVical
import org.multipaz.prompt.PromptModel
import org.multipaz.sdjwt.credential.KeyBoundSdJwtVcCredential
import org.multipaz.sdjwt.credential.KeylessSdJwtVcCredential
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
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustPoint
import org.multipaz.util.Logger
import multipazproject.samples.testapp.generated.resources.Res
import multipazproject.samples.testapp.generated.resources.back_button
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.multipaz.compose.prompt.PromptDialogs
import org.multipaz.storage.base.BaseStorageTable
import org.multipaz.util.fromHex

/**
 * Application singleton.
 *
 * Use [App.Companion.getInstance] to get an instance.
 */
class App private constructor(val promptModel: PromptModel) {

    lateinit var settingsModel: TestAppSettingsModel

    lateinit var documentTypeRepository: DocumentTypeRepository

    lateinit var softwareSecureArea: SoftwareSecureArea
    lateinit var documentStore: DocumentStore
    lateinit var documentModel: DocumentModel

    lateinit var iacaKey: EcPrivateKey
    lateinit var iacaCert: X509Cert

    lateinit var readerRootKey: EcPrivateKey
    lateinit var readerRootCert: X509Cert

    lateinit var readerKey: EcPrivateKey
    lateinit var readerCert: X509Cert

    lateinit var issuerTrustManager: TrustManager

    lateinit var readerTrustManager: TrustManager

    private suspend fun init() {
        val initFuncs = listOf<Pair<suspend () -> Unit, String>>(
            Pair(::platformInit, "platformInit"),
            Pair(::settingsInit, "settingsInit"),
            Pair(::documentTypeRepositoryInit, "documentTypeRepositoryInit"),
            Pair(::documentStoreInit, "documentStoreInit"),
            Pair(::documentModelInit, "documentModelInit"),
            Pair(::keyStorageInit, "keyStorageInit"),
            Pair(::iacaInit, "iacaInit"),
            Pair(::readerRootInit, "readerRootInit"),
            Pair(::readerInit, "readerInit"),
            Pair(::trustManagersInit, "trustManagersInit"),
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
    }

    private suspend fun settingsInit() {
        settingsModel = TestAppSettingsModel.create(platformStorage())
    }

    private suspend fun documentTypeRepositoryInit() {
        documentTypeRepository = DocumentTypeRepository()
        documentTypeRepository.addDocumentType(DrivingLicense.getDocumentType())
        documentTypeRepository.addDocumentType(PhotoID.getDocumentType())
        documentTypeRepository.addDocumentType(EUPersonalID.getDocumentType())
        documentTypeRepository.addDocumentType(UtopiaMovieTicket.getDocumentType())
    }

    private suspend fun documentStoreInit() {
        softwareSecureArea = SoftwareSecureArea.create(platformStorage())
        val secureAreaRepository: SecureAreaRepository = SecureAreaRepository.build {
            add(softwareSecureArea)
            add(platformSecureAreaProvider().get())
            addFactory(CloudSecureArea.IDENTIFIER_PREFIX) { identifier ->
                val queryString = identifier.substring(CloudSecureArea.IDENTIFIER_PREFIX.length + 1)
                val params = queryString.split("&").map {
                    val parts = it.split("=", ignoreCase = false, limit = 2)
                    parts[0] to parts[1].decodeURLPart()
                }.toMap()
                val cloudSecureAreaUrl = params["url"]!!
                Logger.i(TAG, "Creating CSA with url $cloudSecureAreaUrl for $identifier")
                CloudSecureArea.create(
                    platformStorage(),
                    identifier,
                    cloudSecureAreaUrl,
                    platformHttpClientEngineFactory()
                )
            }
        }
        val credentialLoader: CredentialLoader = CredentialLoader()
        credentialLoader.addCredentialImplementation(MdocCredential::class) {
            document -> MdocCredential(document)
        }
        credentialLoader.addCredentialImplementation(KeyBoundSdJwtVcCredential::class) {
            document -> KeyBoundSdJwtVcCredential(document)
        }
        credentialLoader.addCredentialImplementation(KeylessSdJwtVcCredential::class) {
            document -> KeylessSdJwtVcCredential(document)
        }
        documentStore = DocumentStore(
            storage = platformStorage(),
            secureAreaRepository = secureAreaRepository,
            credentialLoader = credentialLoader,
            documentMetadataFactory = TestAppDocumentMetadata::create,
            documentTableSpec = testDocumentTableSpec
        )
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
            serial = ASN1Integer("26457B125F0AD75217A98EE6CFDEA7FC486221".fromHex()),
            validFrom = certsValidFrom,
            validUntil = certsValidUntil,
            issuerAltNameUrl = "https://github.com/openwallet-foundation-labs/identity-credential",
            crlUrl = "https://github.com/openwallet-foundation-labs/identity-credential"
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
            subject = X500Name.fromName("CN=OWF IC TestApp Reader Root"),
            serial = ASN1Integer(1L),
            validFrom = certsValidFrom,
            validUntil = certsValidUntil,
        )
    }

    private lateinit var keyStorage: StorageTable

    private suspend fun keyStorageInit() {
        keyStorage = platformStorage().getTable(
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
                    subject = X500Name.fromName("CN=OWF IC TestApp Reader Cert"),
                    serial = ASN1Integer(1L),
                    validFrom = certsValidFrom,
                    validUntil = certsValidUntil,
                )
                keyStorage.insert("readerCert", ByteString(Cbor.encode(cert.toDataItem())))
                cert
            }
    }

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun generateTrustManagers() {
        issuerTrustManager = TrustManager()
        val signedVical = SignedVical.parse(Res.readBytes("files/20250225 RDW Test Vical.vical"))
        for (certInfo in signedVical.vical.certificateInfos) {
            issuerTrustManager.addTrustPoint(
                TrustPoint(
                    certInfo.certificate,
                    null,
                    null
                )
            )
        }
        issuerTrustManager.addTrustPoint(
            TrustPoint(
                certificate = iacaCert,
                displayName = "OWF IC TestApp Issuer",
                displayIcon = null
            )
        )

        readerTrustManager = TrustManager()
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
        for (readerCertFileName in readerCertFileNames) {
            val certData = Res.readBytes("files/20250225 Reader CA Certificates/" + readerCertFileName)
            val x509Cert = X509Cert.fromPem(certData.decodeToString())
            readerTrustManager.addTrustPoint(
                TrustPoint(
                    certificate = x509Cert,
                    displayName = readerCertFileName.substringBeforeLast("."),
                    displayIcon = null
                )
            )
        }
        readerTrustManager.addTrustPoint(
            TrustPoint(
                certificate = readerRootCert,
                displayName = "OWF IC TestApp",
                displayIcon = Res.readBytes("files/utopia-brewery.png")
            )
        )
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

    companion object {
        private const val TAG = "App"

        private var app: App? = null
        private val appLock = Mutex()

        suspend fun getInstance(promptModel: PromptModel): App {
            appLock.withLock {
                if (app == null) {
                    app = App(promptModel)
                    app!!.init()
                } else {
                    check(app!!.promptModel === promptModel)
                }
            }
            return app!!
        }

        // TODO: Only used in MainViewController.kt because of deadlocks when doing
        //       `val app = runBlocking { App.getInstance() }`. Investigate.
        //
        fun getInstanceAndInitializeInBackground(promptModel: PromptModel): App {
            if (app != null) {
                check(app!!.promptModel === promptModel)
                return app!!
            }
            app = App(promptModel)
            CoroutineScope(Dispatchers.IO).launch {
                app!!.init()
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

    private val presentmentModel = PresentmentModel().apply { setPromptModel(promptModel) }

    @Composable
    @Preview
    fun Content(navController: NavHostController = rememberNavController()) {
        val backStackEntry by navController.currentBackStackEntryAsState()
        val routeWithoutArgs = backStackEntry?.destination?.route?.substringBefore('/')

        val currentDestination = appDestinations.find {
            it.route == routeWithoutArgs
        } ?: StartDestination

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
                            onClickCamera = { navController.navigate(CameraDestination.route) }
                        )
                    }
                    composable(route = SettingsDestination.route) {
                        SettingsScreen(this@App)
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
                    composable(route = SoftwareSecureAreaDestination.route) {
                        SoftwareSecureAreaScreen(
                            softwareSecureArea = app!!.softwareSecureArea,
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
                        ProvisioningTestScreen()
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