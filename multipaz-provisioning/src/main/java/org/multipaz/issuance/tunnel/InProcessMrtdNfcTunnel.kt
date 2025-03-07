package org.multipaz.issuance.tunnel

import org.multipaz.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import org.multipaz.issuance.evidence.EvidenceRequestIcaoNfcTunnelType
import org.multipaz.issuance.evidence.EvidenceResponse
import org.multipaz.issuance.evidence.EvidenceResponseIcaoNfcTunnel
import org.multipaz.issuance.evidence.EvidenceResponseIcaoNfcTunnelResult
import org.multipaz.mrtd.MrtdAccessData
import org.multipaz.mrtd.MrtdNfc
import org.multipaz.mrtd.MrtdNfcChipAccess
import org.multipaz.mrtd.MrtdNfcData
import org.multipaz.mrtd.MrtdNfcDataReader
import kotlinx.datetime.Clock
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.isEmpty
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CommandAPDU
import net.sf.scuba.smartcards.ISO7816
import net.sf.scuba.smartcards.ResponseAPDU
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.jmrtd.PassportService
import org.jmrtd.lds.ChipAuthenticationInfo
import org.jmrtd.lds.ChipAuthenticationPublicKeyInfo
import org.jmrtd.lds.SODFile
import org.jmrtd.lds.SecurityInfo
import org.jmrtd.lds.icao.DG14File
import org.jmrtd.lds.icao.DG15File
import java.io.ByteArrayInputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration
import kotlin.time.DurationUnit

/**
 * Implements [MrtdNfcTunnel] using JMRTD library.
 *
 * JMRTD is designed to be used in threading environment and blocking calls. Thus we create a
 * dedicated thread and do command and response dispatching.
 */
internal class InProcessMrtdNfcTunnel(
    override val token: String,
    private val timeout: Duration,
    private val dataGroups: List<Int>,
    private val accessData: MrtdAccessData?,
    private val onStart: (tunnel: InProcessMrtdNfcTunnel) -> Unit,
    private val onComplete: () -> Unit
) : MrtdNfcTunnel {

    private var authenticating = true
    private var handshaked = false
    private var progressPercent: Int = 0
    private var chipAuthenticationDone = false
    private var activeAuthenticationDone = false
    private var data: MrtdNfcData? = null

    private val processingThread = Thread {
        runTunnel()
    }.apply {
        name = "NFC-Tunnel-$token"
    }

    // protected by synchronizing on lock
    private val lock = Object()
    private var complete = false
    private var unprocessedEvidence: EvidenceResponseIcaoNfcTunnel? = null
    private var suspendedContinuation: Continuation<EvidenceRequestIcaoNfcTunnel?>? = null

    override suspend fun handleNfcTunnelResponse(
        evidence: EvidenceResponseIcaoNfcTunnel
    ): EvidenceRequestIcaoNfcTunnel? {
        val evidenceToSend = if (handshaked) {
            evidence
        } else {
            handshaked = true
            // Currently expect handshake message to be empty, but if/once we define parameters
            // for it, we will set it up here, before thread is started
            check(evidence.response.isEmpty())
            processingThread.start()
            onStart(this)
            null
        }
        return suspendCoroutine { continuation ->
            try {
                synchronized(lock) {
                    if (suspendedContinuation != null) {
                        throw IllegalStateException("Out of order handleNfcTunnelResponse call")
                    }
                    unprocessedEvidence = evidenceToSend
                    suspendedContinuation = continuation
                    lock.notify()
                }
            } catch (err: Exception) {
                continuation.resumeWithException(err)
            }
        }
    }

    override fun complete(): EvidenceResponse {
        val authenticationType = if (chipAuthenticationDone)
            EvidenceResponseIcaoNfcTunnelResult.AdvancedAuthenticationType.CHIP
        else if (activeAuthenticationDone)
            EvidenceResponseIcaoNfcTunnelResult.AdvancedAuthenticationType.ACTIVE
        else
            EvidenceResponseIcaoNfcTunnelResult.AdvancedAuthenticationType.NONE
        return EvidenceResponseIcaoNfcTunnelResult(authenticationType, data!!.dataGroups, data!!.sod)
    }

    private fun handleChipAuthentication(service: PassportService): ByteString? {
        val chipAuthenticationByteBudget = 56
        val dg14bytes: ByteString
        try {
            val input14 = service.getInputStream(PassportService.EF_DG14, PassportService.DEFAULT_MAX_BLOCKSIZE)
            val bytesTotal = input14.length + chipAuthenticationByteBudget
            dg14bytes = MrtdNfcDataReader.readStream(0, bytesTotal, input14) { progress ->
                progressPercent = progress
            }
        } catch (err: Exception) {
            return null
        }

        val dg14 = DG14File(ByteArrayInputStream(dg14bytes.toByteArray()))
        var capk: ChipAuthenticationPublicKeyInfo? = null
        var ca: ChipAuthenticationInfo? = null
        for (securityInfo in dg14.securityInfos) {
            when (securityInfo) {
                is ChipAuthenticationPublicKeyInfo -> capk = securityInfo
                is ChipAuthenticationInfo -> ca = securityInfo
            }
        }
        if (capk == null) {
            throw IllegalStateException("ChipAuthenticationPublicKeyInfo not present")
        }
        try {
            // Newer versions of JMRTD have method inferChipAuthenticationOIDfromPublicKeyOID
            // which allows us to pass null is protocol oid
            // Need this fix in some cases: https://sourceforge.net/p/jmrtd/bugs/76/
            val oid = if (ca != null) ca.objectIdentifier else SecurityInfo.ID_CA_ECDH_3DES_CBC_CBC
            service.doEACCA(capk.keyId, oid, capk.objectIdentifier, capk.subjectPublicKey)
            chipAuthenticationDone = true
        } catch (err: Exception) {
            val out = StringWriter()
            err.printStackTrace(PrintWriter(out))
            throw err
        }

        return dg14bytes
    }

    private fun handleActiveAuthentication(service: PassportService): ByteString? {
        val activeAuthenticationByteBudget = 56
        val dg15bytes: ByteString
        try {
            val input14 = service.getInputStream(PassportService.EF_DG15, PassportService.DEFAULT_MAX_BLOCKSIZE)
            val bytesTotal = input14.length + activeAuthenticationByteBudget
            dg15bytes = MrtdNfcDataReader.readStream(0, bytesTotal, input14) { progress ->
                progressPercent = progress
            }
        } catch (err: Exception) {
            return null
        }

        val dg15 = DG15File(ByteArrayInputStream(dg15bytes.toByteArray()))

        val buffer = ByteBuffer.allocate(8)
        buffer.putLong(System.currentTimeMillis())
        val publicKey = dg15.publicKey
        if (publicKey !is ECPublicKey) {
            return null
        }

        val bits = publicKey.params.curve.field.fieldSize
        val digest = if (bits >= 512) {
            "SHA512"
        } else if (bits >= 384) {
            "SHA384"
        } else if (bits >= 256) {
            "SHA256"
        } else {
            "SHA224"
        }
        val res = service.doAA(publicKey, digest, "ECDSA", buffer.array())
        val dsa = Signature.getInstance(digest + "withPLAIN-ECDSA", BouncyCastleProvider.PROVIDER_NAME)
        dsa.initVerify(dg15.publicKey)
        dsa.update(buffer.array())
        try {
            dsa.verify(res.response)
            activeAuthenticationDone = true
        } catch (err: Exception) {
            throw err  // fake passport
        }

        return dg15bytes
    }

    private fun runTunnel() {
        try {
            println("Started tunnel thread: $token")
            handleTunnel()
        } catch (err: Throwable) {
            err.printStackTrace()
            throw err
        } finally {
            println("Tunnel thread complete: $token")
            val continuation: Continuation<EvidenceRequestIcaoNfcTunnel>?
            synchronized(lock) {
                continuation = suspendedContinuation
                suspendedContinuation = null
                complete = true
            }
            continuation?.resumeWithException(IllegalStateException("NfcTunnel already completed"))
            onComplete()
        }
    }

    private fun handleTunnel() {
        var service: PassportService? = null
        val accessData = this.accessData
        val rawService = TunnelCardService {
            // if access data is null, mark transmission as encrypted, so that there
            // is not encryption applied downstream.
            accessData != null || service?.wrapper != null
        }

        if (accessData == null) {
            // Assume that the client have already performed basic access control step and
            // a secure channel was established.
            service = PassportService(
                rawService, PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
                false,
                false
            )

            // This is needed so that our PassportService is in the right state (as this command
            // was issued on the client). We do not send actual commands to the tunnel.
            service.sendSelectApplet(false)

            rawService.connect()
        } else {
            // Raw tunnel, perform BAC or PACE first.
            rawService.connect()

            val chipAccess = MrtdNfcChipAccess(false)  // TODO: enable mac?
            service = chipAccess.open(rawService, accessData) { status ->
                if (status is MrtdNfc.ReadingData) {
                    progressPercent = status.progressPercent
                }
            }
        }

        val dg14bytes = handleChipAuthentication(service)
        val dg15bytes = if (chipAuthenticationDone) null else handleActiveAuthentication(service)

        progressPercent = 0
        authenticating = false

        val data = MrtdNfcDataReader(dataGroups).read(rawService, service) { status ->
            progressPercent = (status as MrtdNfc.ReadingData).progressPercent
        }

        if (chipAuthenticationDone || activeAuthenticationDone) {
            val sod = SODFile(ByteArrayInputStream(data.sod.toByteArray()))
            val messageDigest = MessageDigest.getInstance(sod.digestAlgorithm)
            if (dg14bytes != null) {
                val digest = messageDigest.digest(dg14bytes.toByteArray())
                if (!digest.contentEquals(sod.dataGroupHashes[14])) {
                    chipAuthenticationDone = false
                    throw IllegalStateException("DG14 (Chip Authentication) stream cannot be validated")
                }
            } else if (dg15bytes != null) {
                val digest = messageDigest.digest(dg15bytes.toByteArray())
                if (!digest.contentEquals(sod.dataGroupHashes[15])) {
                    activeAuthenticationDone = false
                    throw IllegalStateException("DG15 (Active Authentication) stream cannot be validated")
                }
            } else {
                throw IllegalStateException("Should not happen")
            }
        }

        this.data = data
        rawService.close()
    }

    /**
     * Emulates NFC CardService using the evidence collection requests/responses as a tunnel.
     */
    inner class TunnelCardService(private val passThrough: () -> Boolean) : CardService() {
        private var closed = false
        private var connected = false

        fun connect() {
            connected = true
        }

        override fun open() {
        }

        override fun isOpen(): Boolean {
            return !closed
        }

        override fun transmit(commandAPDU: CommandAPDU?): ResponseAPDU {
            if (!connected) {
                // If not connected, just send success response.
                val code = ISO7816.SW_NO_ERROR
                return ResponseAPDU(byteArrayOf(((code.toInt()) ushr 8).toByte(), code.toByte()))
            }
            val requestType =
                if (authenticating) {
                    EvidenceRequestIcaoNfcTunnelType.AUTHENTICATING
                } else {
                    EvidenceRequestIcaoNfcTunnelType.READING
                }
            val evidenceResponse = transmitCore(
                EvidenceRequestIcaoNfcTunnel(
                requestType, passThrough(), progressPercent, ByteString(commandAPDU!!.bytes))
            )
            return ResponseAPDU(evidenceResponse!!.response.toByteArray())
        }

        override fun getATR(): ByteArray {
            throw UnsupportedOperationException()
        }

        override fun close() {
            closed = true
            transmitCore(null)
        }

        private fun transmitCore(request: EvidenceRequestIcaoNfcTunnel?): EvidenceResponseIcaoNfcTunnel? {
            val continuation = synchronized(lock) {
                val maxTimeTransmitting = Clock.System.now() + timeout
                while (suspendedContinuation == null) {
                    if (Clock.System.now() > maxTimeTransmitting) {
                        throw IllegalStateException("transmitting NFC command timed out [${timeout}]")
                    }
                    lock.wait(timeout.toLong(DurationUnit.MILLISECONDS))
                }
                val continuation = suspendedContinuation!!
                suspendedContinuation = null
                continuation
            }
            continuation.resume(request)
            synchronized(lock) {
                // Do not wait for evidence to be supplied for close (null) request
                if (request != null) {
                    val maxTimeReceiving = Clock.System.now() + timeout
                    while (unprocessedEvidence == null) {
                        if (Clock.System.now() > maxTimeReceiving) {
                            throw IllegalStateException("receiving NFC response timed out [${timeout}]")
                        }
                        lock.wait(timeout.toLong(DurationUnit.MILLISECONDS))
                    }
                }
                val evidence = unprocessedEvidence
                unprocessedEvidence = null
                return evidence
            }
        }

        override fun isConnectionLost(e: Exception?): Boolean {
            return false
        }
    }
}