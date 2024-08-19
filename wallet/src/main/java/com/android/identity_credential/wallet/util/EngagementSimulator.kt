package com.android.identity_credential.wallet.util

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.savedstate.SavedStateRegistryOwner
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.deviceretrieval.VerificationHelper
import com.android.identity.android.mdoc.engagement.QrEngagementHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.android.mdoc.transport.DataTransportBleCentralClientMode
import com.android.identity.android.mdoc.transport.DataTransportOptions
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.crypto.X509Cert
import com.android.identity.crypto.X509CertChain
import com.android.identity.crypto.javaPrivateKey
import com.android.identity.crypto.javaPublicKey
import com.android.identity.crypto.javaX509Certificate
import com.android.identity.documenttype.DocumentTypeRepository
import com.android.identity.documenttype.MdocDataElement
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.connectionmethod.ConnectionMethodBle
import com.android.identity.mdoc.request.DeviceRequestGenerator
import com.android.identity.util.Logger
import com.android.identity.util.UUID
import com.android.identity_credential.wallet.PresentationActivity
import com.android.identity_credential.wallet.R
import com.android.identity_credential.wallet.presentation.TAG
import com.android.identity_credential.wallet.util.CertificateGenerator.generateCertificate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier
import org.bouncycastle.cert.CertIOException
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.OperatorCreationException
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.IOException
import java.io.Serializable
import java.lang.ref.WeakReference
import java.math.BigInteger
import java.security.InvalidAlgorithmParameterException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import java.util.Optional
import java.util.concurrent.Executor


private object LooseRefs {
    var engagementSimulator: WeakReference<EngagementSimulator> = WeakReference(null)
}

/**
 * Initiate simulating a QR Engagement Presentment.
 *
 * @param activity needed for various operations referring to its context, mainExecutor, etc..
 * @param documentTypeRepository dependency for the various document types on the device.
 * @param mdlType one of listOf("Mandatory", "Full", "Transportation", "Micov") defining the type
 *      of document to request along with their corresponding credential data elements.
 */
fun startEngagementSimulator(
    activity: ComponentActivity,
    documentTypeRepository: DocumentTypeRepository,
    mdlType: String = "Mandatory"
) {
    LooseRefs.engagementSimulator = WeakReference(
        EngagementSimulator(
            activity,
            documentTypeRepository
        ).apply {
            start(mdlType)
        })
}

fun newEngagementSimulatorInstance(): EngagementSimulator? =
    LooseRefs.engagementSimulator.get()?.newInstance()

fun ComponentActivity.handleEngagementSimulation(deviceRetrievalHelperListener: DeviceRetrievalHelper.Listener) {
    // valid instance of EngagementSimulator
    LooseRefs.engagementSimulator.get()?.let { simulator ->
        // both device request bytes and session transcript bytes are present
        if (simulator.viewModel.engagementSimulationStarted) {
            // after a short delay, trigger the listener with the generated device request bytes
            lifecycleScope.launch {
                delay(300)
                deviceRetrievalHelperListener.onDeviceRequest(simulator.viewModel.developerModeDeviceRequestBytes!!)
            }
        }
    } ?: run {
        Logger.d(
            TAG,
            "Loose Ref of engagementSimulator is null, cannot handle engagement simulation"
        )
    }
}

fun engagementSimulationStarted() =
    LooseRefs.engagementSimulator.get()?.viewModel?.engagementSimulationStarted ?: false


fun getEngagementSimulationSessionTranscript(): ByteArray? =
    LooseRefs.engagementSimulator.get()?.viewModel?.developerModeSessionTranscriptBytes

/**
 * Engagement Simulator View Model bound by the [ComponentActivity]'s lifecycle to retain and recall
 * the device request bytes and session transcript bytes across new instances of [EngagementSimulator]
 */
class EngagementSimulatorViewModel(private val savedStateHandle: SavedStateHandle) :
    ViewModel() {
    companion object {
        private const val KEY_DEVICE_REQUEST_BYTES =
            "developer-mode-engagement-sim-device-request-bytes"
        private const val KEY_SESSION_TRANSCRIPT =
            "developer-mode-engagement-sim-session-transcript"
    }

    var developerModeDeviceRequestBytes: ByteArray?
        get() = savedStateHandle[KEY_DEVICE_REQUEST_BYTES]
        set(value) = savedStateHandle.set(KEY_DEVICE_REQUEST_BYTES, value)

    var developerModeSessionTranscriptBytes: ByteArray?
        get() = savedStateHandle[KEY_SESSION_TRANSCRIPT]
        set(value) = savedStateHandle.set(KEY_SESSION_TRANSCRIPT, value)


    // boolean indicating that developer mode is enabled and user started an engagement simulation
    val engagementSimulationStarted: Boolean
        get() = developerModeSessionTranscriptBytes != null
                && developerModeDeviceRequestBytes != null
}

/**
 * Create the EngagementSimulatorViewModel instance that is aware of the SavedStateHandle.
 */
class EngagementSimulatorViewModelFactory(
    owner: SavedStateRegistryOwner,
    defaultArgs: Bundle? = null
) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        return EngagementSimulatorViewModel(handle) as T
    }
}

class EngagementSimulator(
    private val activity: ComponentActivity,
    private val documentTypeRepository: DocumentTypeRepository,
) {
    companion object {
        const val TAG = "EngagementSimulator"
    }

    // define factory for getting a EngagementSimulatorViewModel from the activity
    private val viewModelFactory = EngagementSimulatorViewModelFactory(activity)

    // Get the actual ViewModel instance from the activity
    val viewModel: EngagementSimulatorViewModel by activity.viewModels { viewModelFactory }

    private val verification: VerificationHelper
        get() = _verification!!
    private var _verification: VerificationHelper? = null

    private var mdocConnectionMethod: ConnectionMethod? = null

    /**
     * Get a "fresh" instance of SimulatePresentmentEngagement as long as it's been instantiated once
     * with valid Activity and documentTypeRepository dependencies.
     */
    fun newInstance(): EngagementSimulator {
        val instance = EngagementSimulator(activity, documentTypeRepository)
        LooseRefs.engagementSimulator = WeakReference(instance)
        return instance
    }

    private val verificationHelperResponseListener: VerificationHelper.Listener =
        object : VerificationHelper.Listener {
            override fun onReaderEngagementReady(readerEngagement: ByteArray) {
                Logger.d(TAG, "onReaderEngagementReady $readerEngagement")
            }

            override fun onDeviceEngagementReceived(connectionMethods: List<ConnectionMethod>) {
                Logger.d(TAG, "onDeviceEngagementReceived ${connectionMethods.size}")

                val availableMdocConnectionMethods =
                    ConnectionMethod.disambiguate(connectionMethods)
                if (availableMdocConnectionMethods.isNotEmpty()) {
                    mdocConnectionMethod =
                        availableMdocConnectionMethods.first()
                }
                mdocConnectionMethod?.let { verification.connect(it) }
                Logger.d(
                    TAG,
                    "mdocConnectionMethod: $mdocConnectionMethod, verification $verification"
                )
            }

            override fun onMoveIntoNfcField() {
                Logger.d(TAG, "onMoveIntoNfcField")
            }

            override fun onDeviceConnected() {
                Logger.d(TAG, "onDeviceConnected")
            }

            override fun onResponseReceived(deviceResponseBytes: ByteArray) {
                Logger.d(TAG, "onResponseReceived: $deviceResponseBytes")
            }

            override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
                Logger.d(
                    TAG,
                    "onDeviceDisconnected, transportSpecificTermination: $transportSpecificTermination"
                )
                disconnect()
            }

            override fun onError(error: Throwable) {
                Logger.d(TAG, "onError")
                disconnect()
            }
        }

    val connectionMethods = listOf(
        ConnectionMethodBle(
            supportsPeripheralServerMode = false,
            supportsCentralClientMode = true,
            peripheralServerModeUuid = null,
            centralClientModeUuid = UUID.randomUUID()
        ),

        /*
        ConnectionMethodBle(
            supportsPeripheralServerMode = true,
            supportsCentralClientMode = false,
            peripheralServerModeUuid = UUID.randomUUID(),
            centralClientModeUuid = null
        ),
        ConnectionMethodNfc(
            commandDataFieldMaxLength = 0xffff,
            responseDataFieldMaxLength = 0x10000
        ),
        ConnectionMethodWifiAware(
            passphraseInfoPassphrase = null,
            channelInfoChannelNumber = null,
            channelInfoOperatingClass = null,
            bandInfoSupportedBands = null
        )
         */
    )
    private val dataTransportOptions = DataTransportOptions.Builder()
        .setBleUseL2CAP(true)
        .setBleClearCache(false)
        .build()

    init {
        _verification = VerificationHelper.Builder(
            activity,
            verificationHelperResponseListener,
            activity.mainExecutor()
        )
            .setDataTransportOptions(dataTransportOptions)
            .setNegotiatedHandoverConnectionMethods(connectionMethods)
            .build()
    }

    private val curve = EcCurve.P256

    object ReaderCertificateGenerator {
        fun generateECDSAKeyPair(curve: String): KeyPair {
            return try {
                // NOTE older devices may not have the right BC installed for this to work
                val kpg: KeyPairGenerator
                if (listOf("Ed25519", "Ed448").any { it.equals(curve, ignoreCase = true) }) {
                    kpg = KeyPairGenerator.getInstance(curve, BouncyCastleProvider())
                } else {
                    kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider())
                    kpg.initialize(ECGenParameterSpec(curve))
                }
                println(kpg.provider.info)
                kpg.generateKeyPair()
            } catch (e: NoSuchAlgorithmException) {
                throw RuntimeException(e)
            } catch (e: InvalidAlgorithmParameterException) {
                throw RuntimeException(e)
            }
        }

        fun createReaderCertificate(
            readerKey: EcPrivateKey, //dsKeyPair: KeyPair,
            readerRootCert: X509Cert, // issuerCert: X509Certificate,
            readerRootKey: EcPrivateKey // issuerPrivateKey: PrivateKey
        ): java.security.cert.X509Certificate {
            val data = DataMaterial(
                subjectDN = "C=UT, CN=OWF Identity Credential mDoc Reader",

                // must match DN of issuer character-by-character
                // TODO change for other generators
                issuerDN = readerRootCert.javaX509Certificate.subjectX500Principal.name,
                // reorders string, do not use
                // return issuerCert.getSubjectX500Principal().getName();

                // NOTE always interpreted as URL for now
                issuerAlternativeName = Optional.of("https://www.google.com/")
            )
            val certData = CertificateMaterial(
                // TODO change
                serialNumber = BigInteger("476f6f676c655f546573745f44535f31", 16),
                startDate = readerRootCert.javaX509Certificate.notBefore,
                endDate = readerRootCert.javaX509Certificate.notAfter,
                pathLengthConstraint = CertificateMaterial.PATHLENGTH_NOT_A_CA,
                keyUsage = KeyUsage.digitalSignature,
                // TODO change for reader cert
                extendedKeyUsage = Optional.of("1.0.18013.5.1.6")
            )

            val signingAlgorithm = when (readerRootKey.curve.defaultSigningAlgorithm) {
                Algorithm.ES256 -> "SHA256withECDSA"
                Algorithm.ES384 -> "SHA384withECDSA"
                Algorithm.ES512 -> "SHA512withECDSA"
                else -> throw IllegalStateException("Unsupported algorithm for reader root")
            }

            val keyData = KeyMaterial(
                publicKey = readerKey.publicKey.javaPublicKey,
                signingAlgorithm = signingAlgorithm,
                signingKey = readerRootKey.javaPrivateKey,
                issuerCertificate = Optional.of(readerRootCert.javaX509Certificate)
            )

            // C.1.7.2
            return generateCertificate(data, certData, keyData)
        }
    }

    private var transport: DataTransport? = null
    private val qrEngagementListener = object : QrEngagementHelper.Listener {

        override fun onDeviceConnecting() {
            Logger.d(TAG, "QR Engagement Listener ::: device connecting...")
        }

        override fun onDeviceConnected(transport: DataTransport) {
            Logger.d(TAG, "QR Engagement Listener ::: ON DEVICE CONNECTED $transport")
            this@EngagementSimulator.transport = transport
        }

        override fun onError(error: Throwable) {
            Logger.d(TAG, "QR Engagement Listener ::: Errror: $error")
        }
    }


    /**
     * Generates the dependencies needed to perform a QR Engagement to generate the
     * DeviceRequest CBOR bytes. This process emulates features from the Verifier app.
     * The DeviceRequest CBOR is passed to PresentationActivity's companion object along with the
     * @param mdlType one of listOf("Mandatory", "Full", "Transportation", "Micov") defining the type
     *      of document to request along with their credential data elements.
     */
    fun start(mdlType: String) {
        val requestDocument = when (mdlType) {
            "Mandatory" -> getRequestDocument(
                RequestDocument.MDL_DOCTYPE,
                intentToRetain = true,
                // request only mandatory fields
                filterElement = { el -> el.mandatory }
            )

            "Full" -> getRequestDocument(
                RequestDocument.MDL_DOCTYPE,
                intentToRetain = true,
            )

            "Transportation" -> getRequestDocument(
                RequestDocument.MDL_DOCTYPE,
                intentToRetain = true,
                filterElement = { el ->
                    listOf(
                        "sex",
                        "portrait",
                        "given_name",
                        "issue_date",
                        "expiry_date",
                        "family_name",
                        "document_number",
                        "issuing_authority",
                        "DHS_compliance",
                        "EDL_credential"
                    ).contains(el.attribute.identifier)
                }
            )

            "Micov" -> getRequestDocument(
                RequestDocument.MICOV_DOCTYPE,
                intentToRetain = true,
                filterNamespace = { ns -> ns == RequestDocument.MICOV_ATT_NAMESPACE }
            )

            else -> getRequestDocument(
                RequestDocument.MDL_DOCTYPE,
                intentToRetain = true,
            )
        }


        var readerKey: EcPrivateKey = Crypto.createEcPrivateKey(curve)
        var signatureAlgorithm = curve.defaultSigningAlgorithm // Algorithm.UNSET
        var readerCertificateChain: X509CertChain? = null

        val (readerCaCert, readerCaPrivateKey) = getReaderAuthority(activity)
        val readerCertificate =
            ReaderCertificateGenerator.createReaderCertificate(
                readerKey,
                readerCaCert,
                readerCaPrivateKey
            )
        readerCertificateChain = X509CertChain(
            listOf(X509Cert(readerCertificate.encoded))
        )
        Logger.d(TAG, "requestDocument: $requestDocument")
        // simulate a QR code engagement on Ble and set VerificationHelper engagement from qr code
        val qrEngagement =
            QrEngagementHelper
                .Builder(
                    context = activity,
                    eDeviceKey = readerKey.publicKey,
                    options = dataTransportOptions,
                    listener = qrEngagementListener,
                    executor = activity.mainExecutor(),
                )
                .setConnectionMethods(connectionMethods)
                .build()

        qrEngagement.close()
        verification.setDeviceEngagementFromQrCode(qrEngagement.deviceEngagementUriEncoded)


        // generate the DeviceRequest CBOR bytes
        val generator = DeviceRequestGenerator(verification.sessionTranscript)
        generator.addDocumentRequest(
            requestDocument.docType,
            requestDocument.itemsToRequest,
            null,
            readerKey,
            signatureAlgorithm, //Algorithm.UNSET,
            readerCertificateChain
        )

        val deviceRequestBytes = generator.generate()
        if (transport == null) {
            transport = DataTransport.fromConnectionMethod(
                activity,
                connectionMethods.get(0),
                DataTransport.Role.MDOC,
                dataTransportOptions
            ) as DataTransportBleCentralClientMode
        }
        Logger.d(TAG, "Device request bytes: $deviceRequestBytes, transport: $transport")

        if (transport != null) {
            viewModel.developerModeDeviceRequestBytes = deviceRequestBytes
            viewModel.developerModeSessionTranscriptBytes = verification.sessionTranscript
            PresentationActivity.startPresentation(
                context = activity,
                transport = transport!!,
                handover = qrEngagement.handover,
                eDeviceKey = readerKey,
                deviceEngagement = qrEngagement.deviceEngagement,
            )
        }

        Logger.d(
            TAG,
            "Verification engagement method ${verification.engagementMethod}, session transcript: ${verification.sessionTranscript}"
        )
    }

    private fun getReaderAuthority(context: Context): Pair<X509Cert, EcPrivateKey> {
        val certificate = X509Cert.fromPem(
            String(
                context.resources.openRawResource(R.raw.owf_identity_credential_reader_cert)
                    .readBytes()
            )
        )
        val privateKey = EcPrivateKey.fromPem(
            String(
                context.resources.openRawResource(R.raw.owf_identity_credential_reader_private_key)
                    .readBytes()
            ),
            certificate.ecPublicKey
        )
        return Pair(certificate, privateKey)
    }


    private fun getRequestDocument(
        docType: String,
        intentToRetain: Boolean,
        filterNamespace: (String) -> Boolean = { _ -> true },
        filterElement: (MdocDataElement) -> Boolean = { _ -> true }
    ): RequestDocument {
        val mdocDocumentType = documentTypeRepository
            .getDocumentTypeForMdoc(docType)!!.mdocDocumentType!!
        return RequestDocument(
            docType,
            mdocDocumentType.namespaces.values.filter { filterNamespace(it.namespace) }
                .map {
                    Pair(
                        it.namespace,
                        it.dataElements.values.filter { el -> filterElement(el) }
                            .map { el -> Pair(el.attribute.identifier, intentToRetain) }
                            .toMap()
                    )
                }.toMap()
        )
    }

    private fun disconnect() {
        try {
            verification.disconnect()

        } catch (e: RuntimeException) {
            Logger.d("ReaderUtil", "Error ignored.", e)
        }
    }
}


data class RequestDocument(
    val docType: String,
    var itemsToRequest: Map<String, Map<String, Boolean>>
) : Serializable {
    companion object {
        const val MDL_DOCTYPE = "org.iso.18013.5.1.mDL"
        const val MDL_NAMESPACE = "org.iso.18013.5.1"
        const val MVR_DOCTYPE = "nl.rdw.mekb.1"
        const val MICOV_DOCTYPE = "org.micov.1"
        const val MICOV_VTR_NAMESPACE = "org.micov.vtr.1"
        const val MICOV_ATT_NAMESPACE = "org.micov.attestation.1"
        const val EU_PID_DOCTYPE = "eu.europa.ec.eudi.pid.1"
    }
}

private fun Context.mainExecutor(): Executor =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        mainExecutor
    } else {
        ContextCompat.getMainExecutor(this)
    }

object CertificateGenerator {
    private const val CRITICAL = true
    private const val NOT_CRITICAL = false

    @JvmStatic
    @Throws(
        CertIOException::class,
        CertificateException::class,
        OperatorCreationException::class
    )
    fun generateCertificate(
        data: DataMaterial,
        certMaterial: CertificateMaterial,
        keyMaterial: KeyMaterial
    ): X509Certificate {
        val issuerCert: Optional<X509Certificate> = keyMaterial.issuerCertificate
        val subjectDN = X500Name(data.subjectDN)
        // doesn't work, get's reordered
        // issuerCert.isPresent() ? new X500Name(issuerCert.get().getSubjectX500Principal().getName()) : subjectDN;
        val issuerDN = X500Name(data.issuerDN)
        val contentSigner =
            JcaContentSignerBuilder(keyMaterial.signingAlgorithm).build(keyMaterial.signingKey)
        val certBuilder = JcaX509v3CertificateBuilder(
            issuerDN,
            certMaterial.serialNumber,
            certMaterial.startDate, certMaterial.endDate,
            subjectDN,
            keyMaterial.publicKey
        )


        // Extensions --------------------------
        val jcaX509ExtensionUtils = JcaX509ExtensionUtils()
        if (issuerCert.isPresent) {
            try {
                // adds 3 more fields, not present in other cert
                //				AuthorityKeyIdentifier authorityKeyIdentifier = jcaX509ExtensionUtils.createAuthorityKeyIdentifier(issuerCert.get());
                val authorityKeyIdentifier =
                    jcaX509ExtensionUtils.createAuthorityKeyIdentifier(issuerCert.get().publicKey)
                certBuilder.addExtension(
                    Extension.authorityKeyIdentifier,
                    NOT_CRITICAL,
                    authorityKeyIdentifier
                )
            } catch (e: IOException) { // CertificateEncodingException |
                throw RuntimeException(e)
            }
        }
        val subjectKeyIdentifier: SubjectKeyIdentifier =
            jcaX509ExtensionUtils.createSubjectKeyIdentifier(keyMaterial.publicKey)
        certBuilder.addExtension(
            Extension.subjectKeyIdentifier,
            NOT_CRITICAL,
            subjectKeyIdentifier
        )
        val keyUsage = KeyUsage(certMaterial.keyUsage)
        certBuilder.addExtension(Extension.keyUsage, CRITICAL, keyUsage)

        // IssuerAlternativeName
        val issuerAlternativeName: Optional<String> = data.issuerAlternativeName
        if (issuerAlternativeName.isPresent) {
            val issuerAltName = GeneralNames(
                GeneralName(
                    GeneralName.uniformResourceIdentifier,
                    issuerAlternativeName.get()
                )
            )
            certBuilder.addExtension(
                Extension.issuerAlternativeName,
                NOT_CRITICAL,
                issuerAltName
            )
        }

        // Basic Constraints
        val pathLengthConstraint: Int = certMaterial.pathLengthConstraint
        if (pathLengthConstraint != CertificateMaterial.PATHLENGTH_NOT_A_CA) {
            // TODO doesn't work for certificate chains != 2 in size
            val basicConstraints = BasicConstraints(pathLengthConstraint)
            certBuilder.addExtension(Extension.basicConstraints, CRITICAL, basicConstraints)
        }
        val extendedKeyUsage: Optional<String> = certMaterial.extendedKeyUsage
        if (extendedKeyUsage.isPresent) {
            val keyPurpose =
                KeyPurposeId.getInstance(ASN1ObjectIdentifier(extendedKeyUsage.get()))
            val extKeyUsage = ExtendedKeyUsage(arrayOf(keyPurpose))
            certBuilder.addExtension(Extension.extendedKeyUsage, CRITICAL, extKeyUsage)
        }

        // DEBUG setProvider(bcProvider) removed before getCertificate
        return JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner))
    }
}

data class CertificateMaterial(
    val serialNumber: BigInteger,
    val startDate: Date,
    val endDate: Date,
    val keyUsage: Int,
    val extendedKeyUsage: Optional<String>,
    val pathLengthConstraint: Int
) {
    companion object {
        const val PATHLENGTH_NOT_A_CA = -1
    }
}

data class DataMaterial(
    val subjectDN: String,
    val issuerDN: String,
    val issuerAlternativeName: Optional<String>
)

data class KeyMaterial(
    val publicKey: PublicKey,
    val signingAlgorithm: String,
    val issuerCertificate: Optional<X509Certificate>,
    val signingKey: PrivateKey
)