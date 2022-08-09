package com.android.mdl.appreader.transfer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.android.identity.DataRetrievalAddress
import com.android.identity.DeviceRequestGenerator
import com.android.identity.DeviceResponseParser
import com.android.identity.VerificationHelper
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


    var mdocAddress: DataRetrievalAddress? = null
        private set
    private var hasStarted = false
    var responseBytes: ByteArray? = null
        private set
    private var verification: VerificationHelper? = null
    var availableMdocAddresses: Collection<DataRetrievalAddress>? = null
        private set

    private var transferStatusLd = MutableLiveData<TransferStatus>()

    fun getTransferStatus(): LiveData<TransferStatus> = transferStatusLd

    fun initVerificationHelper() {
        verification = VerificationHelper(context)
        verification?.setListener(responseListener, context.mainExecutor())
        verification?.setLoggingFlags(PreferencesHelper.getLoggingFlags(context))
        verification?.setUseL2CAP(PreferencesHelper.isBleL2capEnabled(context))
    }

    fun setQrDeviceEngagement(qrDeviceEngagement: String) {
        verification?.setDeviceEngagementFromQrCode(qrDeviceEngagement)
    }

    fun setNdefDeviceEngagement(adapter: NfcAdapter, activity: Activity) {
        adapter.enableReaderMode(
            activity, readerModeListener,
            NfcAdapter.FLAG_READER_NFC_A + NfcAdapter.FLAG_READER_NFC_B,
            null)
        verification?.startListening()
    }

    private val readerModeListener = NfcAdapter.ReaderCallback { tag ->
        verification?.nfcProcessOnTagDiscovered(tag)
    }

    fun setAvailableTransferMethods(availableMdocAddresses: Collection<DataRetrievalAddress>) {
        this.availableMdocAddresses = availableMdocAddresses
        // Select the first method as default, let the user select other transfer method
        // if there are more than one
        if (availableMdocAddresses.isNotEmpty()) {
            this.mdocAddress = availableMdocAddresses.first()
        }
    }

    fun connect() {
        if (hasStarted)
            throw IllegalStateException("Connection has already started. It is necessary to stop verification before starting a new one.")

        if (verification == null)
            throw IllegalStateException("It is necessary to start a new engagement.")

        if (mdocAddress == null)
            throw IllegalStateException("No mdoc address selected.")

        // Start connection
        verification?.let {
            mdocAddress?.let { dr ->
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
        verification?.setListener(null, null)
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
    }


    private val responseListener = object : VerificationHelper.Listener {
        override fun onDeviceEngagementReceived(addresses: MutableList<DataRetrievalAddress>) {
            setAvailableTransferMethods(addresses)
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

    fun setMdocAddress(address: DataRetrievalAddress) {
        this.mdocAddress = address
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