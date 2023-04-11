package com.android.mdl.appreader.transfer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.MediaPlayer
import android.nfc.NfcAdapter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.identity.ConnectionMethod
import com.android.identity.ConnectionMethodHttp
import com.android.identity.DataTransportOptions
import com.android.identity.DeviceRequestGenerator
import com.android.identity.DeviceResponseParser
import com.android.identity.VerificationHelper
import com.android.mdl.appreader.R
import com.android.mdl.appreader.document.RequestDocumentList
import com.android.mdl.appreader.readercertgen.ReaderCertificateGenerator
import com.android.mdl.appreader.readercertgen.SupportedCurves.*
import com.android.mdl.appreader.util.KeysAndCertificates
import com.android.mdl.appreader.util.PreferencesHelper
import com.android.mdl.appreader.util.TransferStatus
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import java.util.concurrent.Executor


class TransferManager private constructor(private val context: Context) {

    companion object {
        private const val LOG_TAG = "TransferManager"

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: TransferManager? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: TransferManager(context).also { instance = it }
            }
    }

    var usingReverseEngagement: Boolean = false
    var readerEngagement: ByteArray? = null

    var mdocConnectionMethod: ConnectionMethod? = null
        private set
    private var hasStarted = false
    var responseBytes: ByteArray? = null
        private set
    private var verification: VerificationHelper? = null
    var availableMdocConnectionMethods: Collection<ConnectionMethod>? = null
        private set

    private var transferStatusLd = MutableLiveData<TransferStatus>()

    fun getTransferStatus(): LiveData<TransferStatus> = transferStatusLd

    fun initVerificationHelper() {
        val builder = VerificationHelper.Builder(context,
            responseListener,
            context.mainExecutor())
        val options = DataTransportOptions.Builder()
            .setBleUseL2CAP(PreferencesHelper.isBleL2capEnabled(context))
            .setBleClearCache(PreferencesHelper.isBleClearCacheEnabled(context))
            .build()
        builder.setDataTransportOptions(options)
        verification = builder.build()
        usingReverseEngagement = false
    }

    fun initVerificationHelperReverseEngagement() {
        val builder = VerificationHelper.Builder(context,
            responseListener,
            context.mainExecutor())
        val options = DataTransportOptions.Builder()
            .setBleUseL2CAP(PreferencesHelper.isBleL2capEnabled(context))
            .setBleClearCache(PreferencesHelper.isBleClearCacheEnabled(context))
            .build()
        builder.setDataTransportOptions(options)
        val methods = ArrayList<ConnectionMethod>()
        // Passing the empty URI in means that DataTransportHttp will use local IP as host
        // and the dynamically allocated TCP port as port. So the resulting ConnectionMethodHttp
        // which will be included in ReaderEngagement CBOR will contain an URI of the
        // form http://192.168.1.2:18013/mdocreader
        methods.add(ConnectionMethodHttp(""));
        builder.setUseReverseEngagement(methods)
        verification = builder.build()
        usingReverseEngagement = true
    }

    fun setQrDeviceEngagement(qrDeviceEngagement: String) {
        verification?.setDeviceEngagementFromQrCode(qrDeviceEngagement)
    }

    fun setNdefDeviceEngagement(adapter: NfcAdapter, activity: Activity) {
        adapter.enableReaderMode(
            activity, readerModeListener,
            NfcAdapter.FLAG_READER_NFC_A + NfcAdapter.FLAG_READER_NFC_B
                    + NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK + NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null)
    }

    private val readerModeListener = NfcAdapter.ReaderCallback { tag ->
        verification?.nfcProcessOnTagDiscovered(tag)
    }

    fun setAvailableTransferMethods(availableMdocConnectionMethods: Collection<ConnectionMethod>) {
        this.availableMdocConnectionMethods = availableMdocConnectionMethods
        // Select the first method as default, let the user select other transfer method
        // if there are more than one
        if (availableMdocConnectionMethods.isNotEmpty()) {
            this.mdocConnectionMethod = availableMdocConnectionMethods.first()
        }
    }

    fun connect() {
        if (hasStarted)
            throw IllegalStateException("Connection has already started. It is necessary to stop verification before starting a new one.")

        if (verification == null)
            throw IllegalStateException("It is necessary to start a new engagement.")

        if (mdocConnectionMethod == null)
            throw IllegalStateException("No mdoc connection method selected.")

        // Start connection
        verification?.let {
            mdocConnectionMethod?.let { dr ->
                it.connect(dr)
            }
            hasStarted = true
        }
    }

    fun stopVerification(
        sendSessionTerminationMessage: Boolean,
        useTransportSpecificSessionTermination: Boolean
    ) {
        verification?.setSendSessionTerminationMessage(sendSessionTerminationMessage)
        try {
            if (verification?.isTransportSpecificTerminationSupported == true && useTransportSpecificSessionTermination) {
                verification?.setUseTransportSpecificSessionTermination(true)
            }
        } catch (e: IllegalStateException) {
            Log.e(LOG_TAG, "Error ignored.", e)
        }
        disconnect()
    }

    fun disconnect(){
        try {
            verification?.disconnect()
        } catch (e: RuntimeException) {
            Log.e(LOG_TAG, "Error ignored.", e)
        }
        transferStatusLd = MutableLiveData<TransferStatus>()
        destroy()
        hasStarted = false
    }

    private fun destroy() {
        responseBytes = null
        verification = null
        mediaPlayer?.release()
        mediaPlayer = null
    }

    var mediaPlayer: MediaPlayer? = MediaPlayer.create(context, R.raw.nfc_connected)

    private val responseListener = object : VerificationHelper.Listener {
        override fun onReaderEngagementReady(readerEngagement: ByteArray) {
            this@TransferManager.readerEngagement = readerEngagement
            transferStatusLd.value = TransferStatus.READER_ENGAGEMENT_READY
        }

        override fun onDeviceEngagementReceived(connectionMethods: MutableList<ConnectionMethod>) {
            // Need to disambiguate the connection methods here to get e.g. two ConnectionMethods
            // if both BLE modes are available at the same time.
            mediaPlayer = mediaPlayer ?: MediaPlayer.create(context, R.raw.nfc_connected)
            mediaPlayer?.start()
            setAvailableTransferMethods(ConnectionMethod.disambiguate(connectionMethods))
            transferStatusLd.value = TransferStatus.ENGAGED
        }

        override fun onMoveIntoNfcField() {
            transferStatusLd.value = TransferStatus.MOVE_INTO_NFC_FIELD
        }

        override fun onDeviceConnected() {
            transferStatusLd.value = TransferStatus.CONNECTED
        }

        override fun onResponseReceived(deviceResponseBytes: ByteArray) {
            responseBytes = deviceResponseBytes
            transferStatusLd.value = TransferStatus.RESPONSE
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
            transferStatusLd.value = TransferStatus.DISCONNECTED
        }

        override fun onError(error: Throwable) {
            Log.e(LOG_TAG, "onError: ${error.message}")
            transferStatusLd.value = TransferStatus.ERROR
        }
    }

    private fun Context.mainExecutor(): Executor {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mainExecutor
        } else {
            ContextCompat.getMainExecutor(context)
        }
    }

    fun sendRequest(requestDocumentList: RequestDocumentList) {
        if (verification == null)
            throw IllegalStateException("It is necessary to start a new engagement.")

        verification?.let {
            var signature: Signature? = null
            var readerKeyCertificateChain: Collection<X509Certificate>? = null


//            SupportedCurves.values().forEach { curve ->
//                val keyPair =
//                    ReaderCertificateGenerator.generateECDSAKeyPair(curve.name)
//                val readerCA = IssuerKeys.getGoogleReaderCA(context)
//                val readerCertificate =
//                    ReaderCertificateGenerator.createReaderCertificate(keyPair, readerCA, getReaderCAPrivateKey())
//                Log.d(LOG_TAG, "${curve.name} - $readerCertificate")
//                //readerKeyCertificateChain = listOf(readerCertificate)
//            }

            val provider = BouncyCastleProvider()

            Log.d(LOG_TAG, "Curve used: ${PreferencesHelper.getReaderAuth(context)}")
            // Check in preferences if reader authentication should be used
            when (val curveName = PreferencesHelper.getReaderAuth(context)) {
                SECP256R1.name, BRAINPOOLP256R1.name -> {
                    val keyPair = ReaderCertificateGenerator.generateECDSAKeyPair(curveName)

                    signature = Signature.getInstance("SHA256withECDSA", provider)
                    signature.initSign(keyPair.private)

                    val readerCA = KeysAndCertificates.getGoogleReaderCA(context)
                    val readerCertificate =
                        ReaderCertificateGenerator.createReaderCertificate(
                            keyPair,
                            readerCA,
                            getReaderCAPrivateKey()
                        )
                    readerKeyCertificateChain = listOf(readerCertificate)
                }
                SECP384R1.name, BRAINPOOLP384R1.name -> {
                    val keyPair = ReaderCertificateGenerator.generateECDSAKeyPair(curveName)

                    signature = Signature.getInstance("SHA384withECDSA", provider)
                    signature.initSign(keyPair.private)

                    val readerCA = KeysAndCertificates.getGoogleReaderCA(context)
                    val readerCertificate =
                        ReaderCertificateGenerator.createReaderCertificate(
                            keyPair,
                            readerCA,
                            getReaderCAPrivateKey()
                        )
                    readerKeyCertificateChain = listOf(readerCertificate)
                }
                SECP521R1.name, BRAINPOOLP512R1.name -> {
                    val keyPair = ReaderCertificateGenerator.generateECDSAKeyPair(curveName)

                    signature = Signature.getInstance("SHA512withECDSA", provider)
                    signature.initSign(keyPair.private)

                    val readerCA = KeysAndCertificates.getGoogleReaderCA(context)
                    val readerCertificate =
                        ReaderCertificateGenerator.createReaderCertificate(
                            keyPair,
                            readerCA,
                            getReaderCAPrivateKey()
                        )
                    readerKeyCertificateChain = listOf(readerCertificate)
                }
                ED25519.name, ED448.name -> {
                    val keyPair = ReaderCertificateGenerator.generateECDSAKeyPair(curveName)

                    signature = Signature.getInstance(curveName, provider)
                    signature.initSign(keyPair.private)

                    val readerCA = KeysAndCertificates.getGoogleReaderCA(context)
                    val readerCertificate =
                        ReaderCertificateGenerator.createReaderCertificate(
                            keyPair, readerCA, getReaderCAPrivateKey()
                        )
                    readerKeyCertificateChain = listOf(readerCertificate)
                }
            }

            val generator = DeviceRequestGenerator()
            generator.setSessionTranscript(it.sessionTranscript)
            requestDocumentList.getAll().forEach { requestDocument ->
                generator.addDocumentRequest(
                    requestDocument.docType,
                    requestDocument.getItemsToRequest(),
                    null,
                    signature,
                    readerKeyCertificateChain
                )
            }
            verification?.sendRequest(generator.generate())
        }
    }

    private fun getReaderCAPrivateKey(): PrivateKey {
        // TODO: should get private key from KeysAndCertificates class instead of
        //  hard-coding it here.
        val keyBytes: ByteArray = Base64.getDecoder()
            .decode("ME4CAQAwEAYHKoZIzj0CAQYFK4EEACIENzA1AgEBBDCI6BG/yRDzi307Rqq2Ndw5mYi2y4MR+n6IDqjl2Qw/Sdy8D5eCzp8mlcL/vCWnEq0=")
        val spec = PKCS8EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("EC")
        return kf.generatePrivate(spec)
    }

    fun sendNewRequest(requestDocumentList: RequestDocumentList) {
        // reset transfer status
        transferStatusLd = MutableLiveData<TransferStatus>()
        sendRequest(requestDocumentList)
    }

    fun setMdocConnectionMethod(connectionMethod: ConnectionMethod) {
        this.mdocConnectionMethod = connectionMethod
    }

    fun getDeviceResponse(): DeviceResponseParser.DeviceResponse {
        responseBytes?.let { rb ->
            verification?.let { v ->
                val parser = DeviceResponseParser()
                parser.setSessionTranscript(v.sessionTranscript)
                parser.setEphemeralReaderKey(v.ephemeralReaderKey)
                parser.setDeviceResponse(rb)
                return parser.parse()
            } ?: throw IllegalStateException("Verification is null")
        } ?: throw IllegalStateException("Response not received")
    }
}