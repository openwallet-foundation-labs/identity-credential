package org.multipaz.testapp.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import org.multipaz.cbor.Cbor
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.X509Cert
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.mdoc.request.DeviceRequestGenerator
import org.multipaz.mdoc.request.DeviceRequestParser
import org.multipaz.mdoc.util.toMdocRequest
import org.multipaz.request.Requester
import multipazproject.samples.testapp.generated.resources.Res
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import org.multipaz.asn1.ASN1Integer
import org.multipaz.cbor.buildCborMap
import org.multipaz.claim.findMatchingClaim
import org.multipaz.compose.presentment.CredentialPresentmentModalBottomSheet
import org.multipaz.credential.Credential
import org.multipaz.crypto.Crypto
import org.multipaz.crypto.EcCurve
import org.multipaz.crypto.X500Name
import org.multipaz.crypto.X509CertChain
import org.multipaz.crypto.buildX509Cert
import org.multipaz.document.buildDocumentStore
import org.multipaz.presentment.SimpleCredentialPresentmentData
import org.multipaz.request.MdocRequest
import org.multipaz.request.RequestedClaim
import org.multipaz.securearea.CreateKeySettings
import org.multipaz.securearea.SecureAreaRepository
import org.multipaz.securearea.software.SoftwareSecureArea
import org.multipaz.storage.ephemeral.EphemeralStorage
import org.multipaz.testapp.platformAppIcon
import org.multipaz.testapp.platformAppName
import org.multipaz.trustmanagement.TrustManagerLocal
import org.multipaz.trustmanagement.TrustMetadata
import org.multipaz.trustmanagement.TrustPoint
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

private val READER_CERT_CHAIN = X509CertChain(listOf(
    X509Cert.fromPem(
        """
            -----BEGIN CERTIFICATE-----
            MIIB4jCCAWigAwIBAgIBATAKBggqhkjOPQQDAzAiMSAwHgYDVQQDDBdNdWx0aXBheiBURVNUIFJl
            YWRlciBDQTAeFw0yNTA2MjAwMzEzMTFaFw0yNTA2MjAwMzMzMTFaMD0xOzA5BgNVBAMMMk9XRiBN
            dWx0aXBheiBPbmxpbmUgVmVyaWZpZXIgU2luZ2xlLVVzZSBSZWFkZXIgS2V5MFkwEwYHKoZIzj0C
            AQYIKoZIzj0DAQcDQgAEpGa4s0rKJPLytpIExbic+QlVIMf6g1oyj5lFjX5Wtb9SaGble2GwuJr0
            rSbiy05qkv8UnviR7ziNvICWYBFy66N0MHIwHwYDVR0jBBgwFoAUlra41GmSXseiGQitI5x3d3ZZ
            TFkwDgYDVR0PAQH/BAQDAgeAMCAGA1UdEQQZMBeCFXZlcmlmaWVyLm11bHRpcGF6Lm9yZzAdBgNV
            HQ4EFgQUv92KXBPLy63mwj8Br8CrcEy8Z/4wCgYIKoZIzj0EAwMDaAAwZQIxAM6qJlyxgr2v7UDd
            EGz9mpO+5HGl4JDVb1d7hE5ugTTA5oZsuo1zqQrzbaTnrPFvrgIwK/JHcFhJulEjmayD38IoNqBk
            j6KNUIdq/fkbpAc2+HxFPNqcZYQ4C8ldhP9rAosM
            -----END CERTIFICATE-----
        """.trimIndent().trim()
    ),
    X509Cert.fromPem(
        """
            -----BEGIN CERTIFICATE-----
            MIICPjCCAcWgAwIBAgIQtWTIXXBQcLMeTdCCAgpzADAKBggqhkjOPQQDAzAiMSAwHgYDVQQDDBdN
            dWx0aXBheiBURVNUIFJlYWRlciBDQTAeFw0yNTA2MTkyMjI0MThaFw0zMDA2MTkyMjI0MThaMCIx
            IDAeBgNVBAMMF011bHRpcGF6IFRFU1QgUmVhZGVyIENBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAE
            w+bw6aoV0KZHllylRYD8YaguuspCPgzdnBu/oAiykfMNw7VhPBQkn4kzCACCcqf6c5KCrJqSkVB7
            ihOz+NV5wzrQ0VxHFWKS5N6whTKyQiOL02EEmzl8Zd0fOOx6x2t4o4G/MIG8MA4GA1UdDwEB/wQE
            AwIBBjASBgNVHRMBAf8ECDAGAQH/AgEAMFYGA1UdHwRPME0wS6BJoEeGRWh0dHBzOi8vZ2l0aHVi
            LmNvbS9vcGVud2FsbGV0LWZvdW5kYXRpb24tbGFicy9pZGVudGl0eS1jcmVkZW50aWFsL2NybDAd
            BgNVHQ4EFgQUlra41GmSXseiGQitI5x3d3ZZTFkwHwYDVR0jBBgwFoAUlra41GmSXseiGQitI5x3
            d3ZZTFkwCgYIKoZIzj0EAwMDZwAwZAIwSJLRt+J8CR9443Yhs8k0AMNuITjwOoLA4hpkZ3iwLQZ/
            Ettcd8aHm4l4SXO6ckEMAjB7EPfq66xBrJly02fDpAAxIlX3dCVCesuYmzLf9YdhdaQHJNftGHAE
            79htTIjxB8c=
            -----END CERTIFICATE-----
        """.trimIndent().trim()
    )
    )
)

private const val IACA_CERT_PEM =
    """
-----BEGIN CERTIFICATE-----
MIICujCCAj+gAwIBAgIQWlUtc8+HqDS3PvCqXIlyYDAKBggqhkjOPQQDAzA5MSowKAYDVQQDDCFP
V0YgSWRlbnRpdHkgQ3JlZGVudGlhbCBURVNUIElBQ0ExCzAJBgNVBAYTAlpaMB4XDTI0MDkxNzE2
NTEzN1oXDTI5MDkxNzE2NTEzN1owOTEqMCgGA1UEAwwhT1dGIElkZW50aXR5IENyZWRlbnRpYWwg
VEVTVCBJQUNBMQswCQYDVQQGEwJaWjB2MBAGByqGSM49AgEGBSuBBAAiA2IABJUHWyr1+ZlNvYEv
sR/1y2uYUkUczBqXTeTwiyRyiEGFFnZ0UR+gNKC4grdCP4F/dA+TWTduy2NlRmog5IByPSdwlvfW
B2f+Tf+MdbgZM+1+ukeaCgDhT/ZwgCoTNgvjyKOCAQowggEGMB0GA1UdDgQWBBQzCQV8RylodOk8
Yq6AwLDQhC7fUDAfBgNVHSMEGDAWgBQzCQV8RylodOk8Yq6AwLDQhC7fUDAOBgNVHQ8BAf8EBAMC
AQYwTAYDVR0SBEUwQ4ZBaHR0cHM6Ly9naXRodWIuY29tL29wZW53YWxsZXQtZm91bmRhdGlvbi1s
YWJzL2lkZW50aXR5LWNyZWRlbnRpYWwwEgYDVR0TAQH/BAgwBgEB/wIBADBSBgNVHR8ESzBJMEeg
RaBDhkFodHRwczovL2dpdGh1Yi5jb20vb3BlbndhbGxldC1mb3VuZGF0aW9uLWxhYnMvaWRlbnRp
dHktY3JlZGVudGlhbDAKBggqhkjOPQQDAwNpADBmAjEAil9jZ+deFSg1/ESWDEuA3gSU43XCO2t4
MirhUlQqSRYlOVBlD0sel7tyuiSPxEldAjEA1eTa/5yCZ67jjg6f2gbbJ8ZzMbff+DlHy77+wXIS
b35NiZ8FdVHgC2ut4fDQTRN4
-----END CERTIFICATE-----        
    """

@OptIn(ExperimentalMaterial3Api::class, ExperimentalResourceApi::class)
@Composable
fun CredentialPresentmentModalBottomSheetScreen(
    mdlSampleRequest: String,
    verifierType: VerifierType,
    documentTypeRepository: DocumentTypeRepository,
    secureAreaRepository: SecureAreaRepository,
    showToast: (message: String) -> Unit,
    onSheetConfirmed: () -> Unit,
    onSheetDismissed: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // TODO: use sheetGesturesEnabled=false when available - see
    //  https://issuetracker.google.com/issues/288211587 for details
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    var cardArt by remember {
        mutableStateOf(ByteArray(0))
    }
    var relyingPartyDisplayIcon by remember {
        mutableStateOf(ByteString())
    }
    var credential by remember {
        mutableStateOf<Credential?>(null)
    }
    LaunchedEffect(Unit) {
        cardArt = Res.readBytes("files/utopia_driving_license_card_art.png")
        relyingPartyDisplayIcon = ByteString(Res.readBytes("files/utopia-brewery.png"))

        val st = EphemeralStorage()
        val ds = buildDocumentStore(st, secureAreaRepository) {}
        val document = ds.createDocument(
            displayName = "Erika's Driving License",
            typeDisplayName = "Utopia Driving License",
            cardArt = ByteString(cardArt)
        )
        val dsKey = Crypto.createEcPrivateKey(EcCurve.P256)
        val validFrom = Clock.System.now()
        val validUntil = Clock.System.now() + 10.days
        val dsCert = buildX509Cert(
            publicKey = dsKey.publicKey,
            signingKey = dsKey,
            signatureAlgorithm = dsKey.curve.defaultSigningAlgorithmFullySpecified,
            serialNumber = ASN1Integer.fromRandom(numBits = 128),
            subject = X500Name.fromName("CN=Test"),
            issuer = X500Name.fromName("CN=Test"),
            validFrom = validFrom,
            validUntil = validUntil,
        ) {}
        credential = DrivingLicense.getDocumentType().createMdocCredentialWithSampleData(
            document = document,
            secureArea = SoftwareSecureArea.create(st),
            createKeySettings = CreateKeySettings(),
            dsKey = dsKey,
            dsCertChain = X509CertChain(listOf(dsCert)),
            signedAt = validFrom,
            validFrom = validFrom,
            validUntil = validUntil,
            expectedUpdate = null,
            domain = "mdoc"
        )
        sheetState.show()
    }

    val trustManager = TrustManagerLocal(storage = EphemeralStorage())

    val (requester, trustPoint) = when (verifierType) {
        VerifierType.KNOWN_VERIFIER_WITH_POLICY_PROXIMITY -> {
            Pair(
                Requester(
                    certChain = READER_CERT_CHAIN,
                ),
                TrustPoint(
                    certificate = READER_CERT_CHAIN.certificates.last(),
                    metadata = TrustMetadata(
                        displayName = "Utopia Brewery",
                        displayIcon = relyingPartyDisplayIcon,
                        privacyPolicyUrl = "https://apps.multipaz.org",
                    ),
                    trustManager = trustManager
                )
            )
        }
        VerifierType.KNOWN_VERIFIER_PROXIMITY -> {
            Pair(
                Requester(
                    certChain = READER_CERT_CHAIN,
                ),
                TrustPoint(
                    certificate = READER_CERT_CHAIN.certificates.last(),
                    metadata = TrustMetadata(
                        displayName = "Utopia Brewery",
                        displayIcon = relyingPartyDisplayIcon,
                        privacyPolicyUrl = "https://apps.multipaz.org",
                    ),
                    trustManager = trustManager
                )
            )
        }
        VerifierType.UNKNOWN_VERIFIER_PROXIMITY ->  {
            Pair(
                Requester(
                    certChain = READER_CERT_CHAIN,
                ),
                null
            )
        }
        VerifierType.ANONYMOUS_VERIFIER_PROXIMITY ->  {
            Pair(
                Requester(),
                null
            )
        }

        VerifierType.KNOWN_VERIFIER_WITH_POLICY_WEBSITE -> {
            Pair(
                Requester(
                    certChain = READER_CERT_CHAIN,
                    appId = "com.android.chrome",
                    websiteOrigin = "https://www.example.com",
                ),
                TrustPoint(
                    certificate = READER_CERT_CHAIN.certificates.last(),
                    metadata = TrustMetadata(
                        displayName = "Utopia Brewery",
                        displayIcon = relyingPartyDisplayIcon,
                        privacyPolicyUrl = "https://apps.multipaz.org",
                    ),
                    trustManager = trustManager
                )
            )
        }
        VerifierType.KNOWN_VERIFIER_WEBSITE -> {
            Pair(
                Requester(
                    certChain = READER_CERT_CHAIN,
                    appId = "com.android.chrome",
                    websiteOrigin = "https://www.example.com"
                ),
                TrustPoint(
                    certificate = READER_CERT_CHAIN.certificates.last(),
                    metadata = TrustMetadata(
                        displayName = "Utopia Brewery",
                        displayIcon = relyingPartyDisplayIcon,
                        privacyPolicyUrl = "https://apps.multipaz.org",
                    ),
                    trustManager = trustManager
                )
            )
        }
        VerifierType.UNKNOWN_VERIFIER_WEBSITE ->  {
            Pair(
                Requester(
                    certChain = READER_CERT_CHAIN,
                    appId = "com.android.chrome",
                    websiteOrigin = "https://www.example.com"
                ),
                null
            )
        }
        VerifierType.ANONYMOUS_VERIFIER_WEBSITE ->  {
            Pair(
                Requester(
                    appId = "com.android.chrome",
                    websiteOrigin = "https://www.example.com"
                ),
                null
            )
        }

        VerifierType.KNOWN_VERIFIER_WITH_POLICY_APP -> {
            Pair(
                Requester(
                    certChain = READER_CERT_CHAIN,
                    appId = "com.google.android.apps.messaging",
                ),
                TrustPoint(
                    certificate = READER_CERT_CHAIN.certificates.last(),
                    metadata = TrustMetadata(
                        displayName = "Utopia Brewery",
                        displayIcon = relyingPartyDisplayIcon,
                        privacyPolicyUrl = "https://apps.multipaz.org",
                    ),
                    trustManager = trustManager
                )
            )
        }
        VerifierType.KNOWN_VERIFIER_APP -> {
            Pair(
                Requester(
                    certChain = READER_CERT_CHAIN,
                    appId = "com.google.android.apps.messaging",
                ),
                TrustPoint(
                    certificate = READER_CERT_CHAIN.certificates.last(),
                    metadata = TrustMetadata(
                        displayName = "Utopia Brewery",
                        displayIcon = relyingPartyDisplayIcon,
                        privacyPolicyUrl = "https://apps.multipaz.org",
                    ),
                    trustManager = trustManager
                )
            )
        }
        VerifierType.UNKNOWN_VERIFIER_APP ->  {
            Pair(
                Requester(
                    certChain = READER_CERT_CHAIN,
                    appId = "com.google.android.apps.messaging",
                ),
                null
            )
        }
        VerifierType.ANONYMOUS_VERIFIER_APP ->  {
            Pair(
                Requester(
                    appId = "com.google.android.apps.messaging",
                ),
                null
            )
        }
    }

    val request = remember { calculateMdocRequest(mdlSampleRequest, verifierType, requester) }

    if (credential == null) {
        return
    }
    val claims = credential!!.getClaims(documentTypeRepository)
    val claimsToShow = buildMap {
        for (requestedClaim in request.requestedClaims) {
            claims.findMatchingClaim(requestedClaim)?.let {
                put(requestedClaim as RequestedClaim, it)
            }
        }
    }

    if (sheetState.isVisible && cardArt.size > 0) {
        CredentialPresentmentModalBottomSheet(
            sheetState = sheetState,
            requester = request.requester,
            trustPoint = trustPoint,
            credentialPresentmentData = SimpleCredentialPresentmentData(listOf(
                Pair(credential!!, claimsToShow)
            )),
            preselectedDocuments = emptyList(),
            appName = platformAppName,
            appIconPainter = painterResource(platformAppIcon),
            onConfirm = { selection ->
                scope.launch {
                    sheetState.hide()
                    onSheetConfirmed()
                }
            },
            onCancel = {
                scope.launch {
                    sheetState.hide()
                    showToast("The sheet was dismissed")
                    onSheetDismissed()
                }
            },
            showCancelAsBack = false
        )
    }
}

private fun calculateMdocRequest(
    mdlSampleRequest: String,
    verifierType: VerifierType,
    requester: Requester
): MdocRequest {
    val request = DrivingLicense.getDocumentType().cannedRequests.find { it.id == mdlSampleRequest }!!
    val namespacesToRequest = mutableMapOf<String, Map<String, Boolean>>()
    for (ns in request.mdocRequest!!.namespacesToRequest) {
        val dataElementsToRequest = mutableMapOf<String, Boolean>()
        for ((de, intentToRetain) in ns.dataElementsToRequest) {
            dataElementsToRequest[de.attribute.identifier] = intentToRetain
        }
        namespacesToRequest[ns.namespace] = dataElementsToRequest
    }
    val encodedSessionTranscript = Cbor.encode(buildCborMap { put("doesn't", "matter") })
    val encodedDeviceRequest = DeviceRequestGenerator(encodedSessionTranscript)
        .addDocumentRequest(
            request.mdocRequest!!.docType,
            namespacesToRequest,
            null,
            null,
            Algorithm.UNSET,
            null
        ).generate()
    val deviceRequest = DeviceRequestParser(encodedDeviceRequest, encodedSessionTranscript)
        .parse()

    val docTypeRepo = DocumentTypeRepository()
    docTypeRepo.addDocumentType(DrivingLicense.getDocumentType())
    val mdocRequest = deviceRequest.docRequests[0].toMdocRequest(
        documentTypeRepository = docTypeRepo,
        mdocCredential = null,
        requesterAppId = requester.appId,
        requesterWebsiteOrigin = requester.websiteOrigin,
    )
    return MdocRequest(
        requester = requester,
        requestedClaims = mdocRequest.requestedClaims,
        docType = mdocRequest.docType
    )
}