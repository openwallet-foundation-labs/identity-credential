package com.android.identity_credential.wallet

import com.android.identity.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import com.android.identity.issuance.evidence.EvidenceRequestIcaoNfcTunnelType
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnel
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnelResult
import com.android.identity.issuance.simple.SimpleIcaoNfcTunnelDriver
import com.android.identity.mrtd.MrtdAccessData
import com.android.identity.mrtd.MrtdNfc
import com.android.identity.mrtd.MrtdNfcChipAccess
import com.android.identity.mrtd.MrtdNfcData
import com.android.identity.mrtd.MrtdNfcDataReader
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.io.bytestring.ByteString
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
import java.lang.Exception
import java.lang.UnsupportedOperationException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey

/**
 * Implements [SimpleIcaoNfcTunnelDriver] using JMRTD library.
 *
 * JMRTD is designed to be used in threading environment and blocking calls. Thus we create a
 * dedicated thread and do command and response dispatching.
 */
class NfcTunnelDriver : SimpleIcaoNfcTunnelDriver {
    private val requestChannel = Channel<OptionalRequest>()
    private val responseChannel = Channel<EvidenceResponseIcaoNfcTunnel>()
    private var dataGroups: List<Int>? = null
    private var processingThread: Thread? = null
    private var authenticating = true
    private var progressPercent: Int = 0
    private var chipAuthenticationDone = false
    private var activeAuthenticationDone = false
    private var accessData: MrtdAccessData? = null
    private var data: MrtdNfcData? = null

    override fun init(dataGroups: List<Int>, accessData: MrtdAccessData?) {
        this.dataGroups = dataGroups
        this.accessData = accessData
    }

    override suspend fun handleNfcTunnelResponse(
        evidence: EvidenceResponseIcaoNfcTunnel
    ): EvidenceRequestIcaoNfcTunnel? {
        if (processingThread == null) {
            // Handshake response
            val thread = Thread {
                handleTunnel()
            }
            processingThread = thread
            thread.name = "NFC Tunnel"
            thread.start()
        } else {
            responseChannel.send(evidence)
        }
        return requestChannel.receive().request
    }

    override fun collectEvidence(): EvidenceResponse {
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

        val data = MrtdNfcDataReader(dataGroups!!).read(rawService, service) { status ->
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
            return runBlocking {
                val requestType =
                    if (authenticating) {
                        EvidenceRequestIcaoNfcTunnelType.AUTHENTICATING
                    } else {
                        EvidenceRequestIcaoNfcTunnelType.READING
                    }
                requestChannel.send(OptionalRequest(
                    EvidenceRequestIcaoNfcTunnel(
                    requestType, passThrough(), progressPercent, ByteString(commandAPDU!!.bytes))
                ))
                val evidenceResponse = responseChannel.receive()
                ResponseAPDU(evidenceResponse.response.toByteArray())
            }
        }

        override fun getATR(): ByteArray {
            throw UnsupportedOperationException()
        }

        override fun close() {
            closed = true
            runBlocking {
                requestChannel.send(OptionalRequest(null))
            }
        }

        override fun isConnectionLost(e: Exception?): Boolean {
            return false
        }
    }

    // Channel does not accept null, so use a wrapper
    data class OptionalRequest(val request: EvidenceRequestIcaoNfcTunnel?)
}