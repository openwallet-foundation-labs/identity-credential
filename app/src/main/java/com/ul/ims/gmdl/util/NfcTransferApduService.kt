/*
 * Copyright (C) 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.ul.ims.gmdl.util

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import androidx.security.identity.IdentityCredential
import androidx.security.identity.IdentityCredentialException
import androidx.security.identity.NoAuthenticationKeyAvailableException
import com.ul.ims.gmdl.cbordata.interpreter.CborDataInterpreter
import com.ul.ims.gmdl.cbordata.model.UserCredential
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.cbordata.request.DataElements
import com.ul.ims.gmdl.cbordata.request.IRequest
import com.ul.ims.gmdl.cbordata.response.DeviceAuth
import com.ul.ims.gmdl.cbordata.response.IResponse
import com.ul.ims.gmdl.cbordata.response.Response
import com.ul.ims.gmdl.cbordata.security.CoseSign1
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.CoseMac0
import com.ul.ims.gmdl.cbordata.security.mdlauthentication.SessionTranscript
import com.ul.ims.gmdl.cbordata.security.sessionEncryption.SessionEstablishment
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import com.ul.ims.gmdl.issuerauthority.MockIssuerAuthority
import com.ul.ims.gmdl.nfcengagement.twoBytesToInt
import com.ul.ims.gmdl.nfcofflinetransfer.holder.NfcTransferHolder
import com.ul.ims.gmdl.nfcofflinetransfer.model.ApduCommand
import com.ul.ims.gmdl.nfcofflinetransfer.model.ApduResponse
import com.ul.ims.gmdl.nfcofflinetransfer.model.DataField
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.selectAid
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.statusWordChainingResponse
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.statusWordFileNotFound
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.statusWordInstructionNotSupported
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.statusWordOK
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcConstants.Companion.statusWrongLength
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.createBERTLV
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.getBERTLVValue
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.toHexString
import com.ul.ims.gmdl.nfcofflinetransfer.utils.NfcUtils.toInt
import com.ul.ims.gmdl.provisioning.ProvisioningManager
import com.ul.ims.gmdl.security.sessionencryption.holder.HolderSessionManager
import kotlinx.coroutines.runBlocking

class NfcTransferApduService : HostApduService() {

    companion object {
        private const val LOG_TAG = "NfcTransferApduService"
        const val EXTRA_NFC_TRANSFER_DEVICE_ENGAGEMENT =
            "com.ul.ims.gmdl.EXTRA_NFC_TRANSFER_DEVICE_ENGAGEMENT"
        const val EXTRA_NFC_TRANSFER_USER_CONSENT =
            "com.ul.ims.gmdl.EXTRA_NFC_TRANSFER_USER_CONSENT"

        private const val defaultMaxLength = 255
    }

    // CBor Interpreter
    private val interpreter = CborDataInterpreter()
    private var sessionManager: HolderSessionManager? = null
    private var deviceEngagement: ByteArray? = null
    private var icAPI: IdentityCredential? = null
    private lateinit var issuerAuthority: MockIssuerAuthority
    private var sessionEstablishment: SessionEstablishment? = null
    private var sessionTranscript: SessionTranscript? = null
    private var request: IRequest? = null
    private var userConsentMap: HashMap<String, Boolean>? = null
    private var maxCommandLength: Int = defaultMaxLength

    enum class CommandType {
        SELECT_BY_AID,
        SELECT_FILE,
        ENVELOPE,
        RESPONSE,
        READ_BINARY,
        UPDATE_BINARY,
        OTHER
    }

    private var requestDataField = mutableListOf<Byte>()
    private var responseDataField: DataField? = null

    @Suppress("UNCHECKED_CAST")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOG_TAG, "onStartCommand(intent: $intent, flags: $flags, startId: $startId)")

        sessionManager =
            HolderSessionManager.getInstance(applicationContext, UserCredential.CREDENTIAL_NAME)
        if (intent?.hasExtra(EXTRA_NFC_TRANSFER_DEVICE_ENGAGEMENT) == true) {
            deviceEngagement = intent.getByteArrayExtra(EXTRA_NFC_TRANSFER_DEVICE_ENGAGEMENT)
        }
        if (intent?.hasExtra(EXTRA_NFC_TRANSFER_USER_CONSENT) == true) {
            userConsentMap =
                intent.getSerializableExtra(EXTRA_NFC_TRANSFER_USER_CONSENT) as HashMap<String, Boolean>
        }

        icAPI = ProvisioningManager.getIdentityCredential(
            applicationContext,
            UserCredential.CREDENTIAL_NAME
        )

        issuerAuthority = MockIssuerAuthority.getInstance(applicationContext)
        requestDataField = mutableListOf()
        responseDataField = null

        return super.onStartCommand(intent, flags, startId)
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        Log.d(LOG_TAG, "Command -> " + toHexString(commandApdu))

        val response = when (getCommandType(commandApdu)) {
            CommandType.SELECT_BY_AID -> handleSelect(commandApdu)
            CommandType.SELECT_FILE -> statusWordInstructionNotSupported
            CommandType.READ_BINARY -> statusWordInstructionNotSupported
            CommandType.UPDATE_BINARY -> statusWordInstructionNotSupported
            CommandType.OTHER -> statusWordInstructionNotSupported
            CommandType.ENVELOPE -> handleEnvelope(commandApdu)
            CommandType.RESPONSE -> handleResponse(commandApdu)
        }

        Log.d(LOG_TAG, "Response -> " + toHexString(response))
        return response
    }

    private fun handleSelect(commandApdu: ByteArray?): ByteArray {
        val selectCommand = ApduCommand.Builder().decode(commandApdu).build()
        if (selectCommand.dataField == null) {
            return statusWrongLength
        } else {
            if (!selectAid.contentEquals(selectCommand.dataField!!)) {
                return statusWordFileNotFound
            }
        }
        return statusWordOK
    }

    private fun handleEnvelope(commandApdu: ByteArray?): ByteArray {
        val envelopCommand = ApduCommand.Builder().decode(commandApdu).build()
        if (envelopCommand.lc == null) {
            return statusWrongLength
        }
        if (envelopCommand.dataField == null) {
            return statusWordFileNotFound
        }
        envelopCommand.dataField?.let { dataField ->

            requestDataField.addAll(dataField.toList())
            Log.d(LOG_TAG, "requestDataField -> " + toHexString(requestDataField.toByteArray()))

            // Check if envelope command is not the last command
            if (envelopCommand.isChain()) {
                return statusWordOK
            }
            maxCommandLength =
                envelopCommand.le?.let { le ->
                    when (le.size) {
                        3 -> {
                            val len = twoBytesToInt(le.copyOfRange(1, 3))
                            if (len == 0) 65536 else len
                        }
                        2 -> {
                            val len = twoBytesToInt(le)
                            if (len == 0) 65536 else len
                        }
                        1 -> {
                            val len = toInt(le[0])
                            if (len == 0) 256 else len
                        }
                        else -> defaultMaxLength
                    }
                } ?: defaultMaxLength

            // Value from BER data object as mdl CBOR request
            val receivedRequest = getBERTLVValue(requestDataField.toByteArray())
            Log.d(
                LOG_TAG,
                "receivedRequest -> " + toHexString(getBERTLVValue(requestDataField.toByteArray()))
            )

            val mdlResponse = runBlocking {
                return@runBlocking onReceive(receivedRequest)
            }

            // Create a DO'53' with the mdl CBOR response
            responseDataField =
                DataField(
                    createBERTLV(mdlResponse)
                )

            return getDataCommandResponse()
        }

        return statusWordFileNotFound
    }

    private fun handleResponse(commandApdu: ByteArray?): ByteArray {
        Log.d(LOG_TAG, "handleResponse: (${commandApdu?.size}) ${toHexString(commandApdu)}")

        val responseCommand = ApduCommand.Builder().decode(commandApdu).build()

        maxCommandLength =
            responseCommand.le?.let { le ->
                when (le.size) {
                    3 -> {
                        val len = twoBytesToInt(le.copyOfRange(1, 3))
                        if (len == 0) 65536 else len
                    }
                    2 -> {
                        val len = twoBytesToInt(le)
                        if (len == 0) 65536 else len
                    }
                    1 -> {
                        val len = toInt(le[0])
                        if (len == 0) 256 else len
                    }
                    else -> defaultMaxLength
                }
            } ?: defaultMaxLength

        return getDataCommandResponse()
    }

    private fun getDataCommandResponse(): ByteArray {
        responseDataField?.let { respDataField ->
            val resp = respDataField.getNextChunk(maxCommandLength)
            val sw1sw2 = if (respDataField.hasMoreBytes()) {
                // Send the size of the next chunk
                // if not extended length status should have the length of remaining bytes
                if (respDataField.size() <= 255) {
                    val sw2 = if (respDataField.size() > maxCommandLength)
                        maxCommandLength
                    else
                        respDataField.size()
                    // Add SW1 - SW2 to response ('61XX' more data available)
                    byteArrayOf(statusWordChainingResponse[0], sw2.toByte())
                } else {
                    // Add SW1 - SW2 to response ('6100' more data available)
                    statusWordChainingResponse
                }
            } else statusWordOK // Add SW1 - SW2 to response ('9000' No further qualification)

            if (statusWordOK.contentEquals(sw1sw2)) {
                // when there is no more data to be sent
                updateUITransferComplete()
            }

            return ApduResponse.Builder().setResponse(resp, sw1sw2).build().encode()
        }

        // in case of any error inform UI that this transfer is complete
        updateUITransferComplete()
        return statusWordFileNotFound
    }

    private fun updateUITransferComplete() {
        Intent().also { intent ->
            intent.action = NfcTransferHolder.ACTION_NFC_TRANSFER_CALLBACK
            intent.setPackage(NfcTransferHolder.PACKAGE_NFC_TRANSFER_CALLBACK)
            sendBroadcast(intent)
        }
    }

    private fun getCommandType(commandApdu: ByteArray?): CommandType {
        commandApdu?.let { apdu ->
            if (apdu.size < 3) {
                return CommandType.OTHER
            }

            val ins = toInt(apdu[1])
            val p1 = toInt(apdu[2])

            if (ins == 0xA4) {
                if (p1 == 0x04) {
                    return CommandType.SELECT_BY_AID
                } else if (p1 == 0x00) {
                    return CommandType.SELECT_FILE
                }
            } else if (ins == 0xB0) {
                return CommandType.READ_BINARY
            } else if (ins == 0xD6) {
                return CommandType.UPDATE_BINARY
            } else if (ins == 0xC3) {
                return CommandType.ENVELOPE
            } else if (ins == 0xC0) {
                return CommandType.RESPONSE
            }
        }

        return CommandType.OTHER
    }

    override fun onDeactivated(p0: Int) {
        Log.d(LOG_TAG, "onDeactivated: $p0 ")
    }

    private suspend fun onCommand(dataParam: ByteArray?): ByteArray {
        val data: ByteArray = dataParam ?: return sendResponse(errorResponse())
        val request: IRequest?
        var decryptedData: ByteArray? = null

        //Decrypt the message using SessionManager
        sessionManager?.let { session ->
            // The first request is a SessionEstablishment obj with the CBor request wrapped in it
            if (sessionEstablishment == null) {
                sessionEstablishment = SessionEstablishment.Builder()
                    .decode(data)
                    .build()

                sessionEstablishment?.let { se ->

                    if (sessionTranscript == null) {
                        se.readerKey?.let { rKey ->
                            deviceEngagement?.let { de ->

                                sessionTranscript = SessionTranscript.Builder()
                                    .setReaderKey(rKey.encode())
                                    .setDeviceEngagement(de)
                                    .build()

                                sessionTranscript?.let {
                                    com.ul.ims.gmdl.cbordata.utils.Log.d(
                                        "SessionTranscript",
                                        it.encodeToString()
                                    )
                                    sessionManager?.setSessionTranscript(it.encode())
                                }
                            }
                        }
                    }
                    decryptedData = session.decryptSessionEstablishment(se)
                }

            } else {
                decryptedData = session.decryptData(data)
            }

            decryptedData?.let { decrypt ->
                Log.d(LOG_TAG, "Decrypted Request")
                Log.d(LOG_TAG, CborUtils.encodeToString(decrypt))
                Log.d(LOG_TAG, CborUtils.cborPrettyPrint(decrypt))
            } ?: run {
                Log.d(LOG_TAG, "Unable to Decrypt Request")
            }
        }

        // Interpret the received bytes
        return if (decryptedData?.isNotEmpty() == true) {
            request = interpreter.interpret(decryptedData) as? IRequest

            // So Far the holder is only able to receive requests and send a response upon it
            onRequest(request)
        } else {
            onDecryptError()
        }
    }

    private suspend fun onRequest(req: IRequest?): ByteArray {
        if (req?.isValid() == true) {
            request = req
            request?.let {
                it.getConsentRequestItems()?.let { requestItems ->
                    return askForUserConsent(requestItems)
                }
            }
        }
        return sendResponse(errorResponse())
    }

    private suspend fun askForUserConsent(requestItems: Map<String, Boolean>): ByteArray {
        val selectedItems = HashMap<String, Boolean>()
        requestItems.forEach { reqName ->
            selectedItems[reqName.key] = userConsentMap?.get(reqName.key) ?: false
        }
        return onUserConsent(selectedItems)
    }

    private suspend fun onReceive(data: ByteArray?): ByteArray {
        data?.let {
            Log.d(LOG_TAG, "Received Session Establishment")
            Log.d(LOG_TAG, CborUtils.cborPrettyPrint(it))
        }
        return onCommand(data)
    }

    private fun errorResponse(): IResponse {
        return Response.Builder()
            .isError()
            .build()
    }

    private fun onDecryptError(): ByteArray {
        val sm = sessionManager ?: HolderSessionManager.getInstance(
            applicationContext,
            UserCredential.CREDENTIAL_NAME
        )

        // Send response
        return sm.generateResponse(null)
    }

    private suspend fun generateResponse(userConsentMap: Map<String, Boolean>?): IResponse {
        request?.let { req ->
            userConsentMap?.let {
                val requestItems = req.getRequestParams()
                val notConsentedItems = userConsentMap.filter { !it.value }.map { it.key }

                var elements: DataElements? = null
                requestItems.forEach {
                    val namespaces = it.namespaces
                    namespaces.namespaces.forEach { nspace ->
                        if (nspace.key == MdlNamespace.namespace) {
                            elements = nspace.value
                        }
                    }
                }

                elements?.let { element ->

                    // Remove Items that user did not give consent
                    if (notConsentedItems.isNotEmpty()) {
                        Log.d(
                            LOG_TAG, "mDL Holder did not gave consent for the " +
                                    "following items"
                        )
                        Log.d(LOG_TAG, notConsentedItems.toString())
                    }

                    val reqItems = element.dataElements.keys.toMutableList()
                    notConsentedItems.forEach { item ->
                        reqItems.remove(item)
                    }

                    // TODO: This is basically copy-paste of HolderExecutor.kt's generateResponse().
                    //  method. Instead of doing that, use that code instead through inheritance or
                    //  composition.
                    sessionTranscript?.let { sTranscript ->

                        try {
                            sessionManager?.let { session ->
                                // Request data items which will appear in IssuerSigned
                                val entriesToRequestIssuerSigned =
                                    HashMap<String, Collection<String>>()
                                entriesToRequestIssuerSigned[MdlNamespace.namespace] = reqItems

                                val resultDataIssuerSigned = session.getEntries(
                                    entriesToRequestIssuerSigned
                                ) ?: return errorResponse()

                                // We currently don't use DeviceSigned so make an empty
                                // request for that.
                                val entriesToRequestDeviceSigned =
                                    HashMap<String, Collection<String>>()
                                val resultDataDeviceSigned = session.getEntries(
                                    entriesToRequestDeviceSigned
                                ) ?: return errorResponse()

                                val ecdsaSignatureBytes = resultDataDeviceSigned.ecdsaSignature
                                val macBytes = resultDataDeviceSigned.messageAuthenticationCode
                                val issuerAuthBytes =
                                    resultDataDeviceSigned.staticAuthenticationData

                                ecdsaSignatureBytes?.let { signature ->
                                    val coseSign1 = CoseSign1.Builder()
                                        .decode(signature)
                                        .build()
                                    com.ul.ims.gmdl.offlinetransfer.utils.Log.d(
                                        "ECDSA Signature",
                                        coseSign1.encodeToString()
                                    )

                                    val deviceAuth = DeviceAuth.Builder()
                                        .setCoseSign1(coseSign1)
                                        .build()
                                    com.ul.ims.gmdl.offlinetransfer.utils.Log.d(
                                        "Device Auth",
                                        deviceAuth.encodeToString()
                                    )

                                    issuerAuthBytes.let { iAuth ->

                                        val issuerAuth = CoseSign1.Builder()
                                            .decode(iAuth)
                                            .build()

                                        // Retrieve the List of IssuerSignedItems used to create the
                                        // MSO contained in the retrieved Cose_Sign1
                                        val issuerNamespaces =
                                            issuerAuthority.getIssuerNamespaces(iAuth)

                                        issuerNamespaces?.let {
                                            return Response.Builder()
                                                .responseForRequest(
                                                    reqItems,
                                                    resultDataIssuerSigned,
                                                    deviceAuth,
                                                    issuerAuth,
                                                    issuerNamespaces
                                                )
                                                .build()
                                        }
                                    }
                                }

                                macBytes?.let { mac ->
                                    val coseMac0 = CoseMac0.Builder()
                                        .decodeEncoded(mac)
                                        .build()

                                    val deviceAuth = DeviceAuth.Builder()
                                        .setCoseMac0(coseMac0)
                                        .build()

                                    issuerAuthBytes.let { iAuth ->

                                        val issuerAuth = CoseSign1.Builder()
                                            .decode(iAuth)
                                            .build()

                                        // Retrieve the List of IssuerSignedItems used to create the
                                        // MSO contained in the retrieved Cose_Sign1
                                        val issuerNamespaces =
                                            issuerAuthority.getIssuerNamespaces(iAuth)

                                        issuerNamespaces?.let {
                                            return Response.Builder()
                                                .responseForRequest(
                                                    reqItems,
                                                    resultDataIssuerSigned,
                                                    deviceAuth,
                                                    issuerAuth,
                                                    issuerNamespaces
                                                )
                                                .build()
                                        }
                                    }
                                }
                            }

                        } catch (ex: NoAuthenticationKeyAvailableException) {
                            Log.e(LOG_TAG, ex.message, ex)
                        } catch (ex: IdentityCredentialException) {
                            Log.e(LOG_TAG, ex.message, ex)
                        } catch (ex: NoClassDefFoundError) {
                            Log.e(LOG_TAG, ex.message, ex)
                        }
                    }
                } ?: kotlin.run {
                    return errorResponse()
                }
            } ?: kotlin.run {
                return errorResponse()
            }
        }
        return errorResponse()

    }

    private suspend fun onUserConsent(userConsentMap: Map<String, Boolean>?): ByteArray {
        // Generate a response to the reader
        val response = generateResponse(userConsentMap)
        val responseBytes = response.encode()

        Log.d(LOG_TAG, "Response Cbor")
        Log.d(LOG_TAG, CborUtils.encodeToString(responseBytes))
        Log.d(LOG_TAG, CborUtils.cborPrettyPrint(responseBytes))

        sessionManager?.let {
            // Send response
            return it.generateResponse(responseBytes)
        }
        return responseBytes
    }

    private fun sendResponse(response: IResponse): ByteArray {
        val responseBytes = response.encode()

        Log.d(LOG_TAG, "Response Cbor")
        Log.d(LOG_TAG, CborUtils.cborPrettyPrint(responseBytes))

        sessionManager?.let {
            // Send response
            return it.generateResponse(responseBytes)
        }
        return responseBytes
    }

}
