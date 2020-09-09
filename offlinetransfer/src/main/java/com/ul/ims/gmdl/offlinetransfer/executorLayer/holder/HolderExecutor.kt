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

package com.ul.ims.gmdl.offlinetransfer.executorLayer.holder

import androidx.biometric.BiometricPrompt
import androidx.security.identity.IdentityCredentialException
import androidx.security.identity.NoAuthenticationKeyAvailableException
import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype
import com.ul.ims.gmdl.cbordata.interpreter.IDataInterpreter
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
import com.ul.ims.gmdl.issuerauthority.IIssuerAuthority
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer
import com.ul.ims.gmdl.offlinetransfer.utils.Log
import com.ul.ims.gmdl.security.sessionencryption.holder.HolderSessionManager

class HolderExecutor(
    interpreter: IDataInterpreter,
    transportLayer: ITransportLayer,
    sessionManager: HolderSessionManager,
    transportEventListener: ITransportEventListener,
    deviceEngagement: ByteArray,
    issuerAuthority: IIssuerAuthority
) : IHolderExecutor, IExecutorEventListener {

    companion object {
        val LOG_TAG = HolderExecutor::class.java.simpleName
    }

    override var interpreter: IDataInterpreter? = interpreter
    override var transportLayer: ITransportLayer? = transportLayer
    override val supportedDoctype = MdlDoctype
    private var sessionEstablishment : SessionEstablishment? = null
    private var sessionManager: HolderSessionManager? = sessionManager
    private var transportEventListener: ITransportEventListener? = transportEventListener
    private var request : IRequest? = null
    private var deviceEngagement: ByteArray? = deviceEngagement
    private var sessionTranscript : SessionTranscript? = null
    private var issuerAuthority: IIssuerAuthority? = issuerAuthority

    private fun initTransportLayer() {
        sessionManager?.let {session ->
            val hash = session.getHolderPkHash()
            transportLayer?.let {transport->
                hash?.let {
                    transport.inititalize(it)
                }
            }
        }
    }

    override fun onCommand(data: ByteArray) {
        var request: IRequest?
        var decryptedData : ByteArray? = null

        //Decrypt the message using SessionManager
        sessionManager?.let {session ->
            // The first request is a SessionEstablishment obj with the CBor request wrapped in it
            if (sessionEstablishment == null) {
                sessionEstablishment = SessionEstablishment.Builder()
                    .decode(data)
                    .build()

                sessionEstablishment?.let {se ->
                    if (sessionTranscript == null) {
                        se.readerKey?.let {rKey ->
                            deviceEngagement?.let {de ->

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
        interpreter?.let {translator ->
            if (decryptedData?.isNotEmpty() == true) {
                request = translator.interpret(decryptedData) as? IRequest

                // So Far the holder is only able to receive requests and send a response upon it
                onRequest(request)
            } else {
                onDecryptError()
            }
        }
    }

    override fun onRequest(req: IRequest?) {
        if (req?.isValid() == true) {
            request = req
            request?.let {
                it.getConsentRequestItems()?.let {consentList ->
                    askForUserConsent(consentList)
                }
            }
        } else {
            sendResponse(errorResponse())
        }
    }

    override fun onReceive(data: ByteArray?) {
        data?.let {
            Log.d(LOG_TAG, "Received Session Establishment")
            Log.d(LOG_TAG, CborUtils.cborPrettyPrint(it))
            onCommand(it)
        }
    }

    override fun onEvent(string: String?, i: Int) {
        Log.d(LOG_TAG, "onEvent = $string i = $i")
    }

    override fun sendData(bytes : ByteArray) {
        transportLayer?.write(bytes)
    }

    private suspend fun generateResponse(userConsentMap: Map<String, Boolean>?): IResponse {
        request?.let {req ->
            userConsentMap?.let {
                val requestItems = req.getRequestParams()
                val notConsentedItems = userConsentMap.filter { !it.value }.map { it.key }

                var elements: DataElements? = null
                requestItems.forEach {
                    val namespaces = it.namespaces
                    namespaces.namespaces.forEach {nspace ->
                        if (nspace.key == MdlNamespace.namespace) {
                            elements = nspace.value
                        }
                    }
                }

                elements?.let { element ->

                    // Remove Items that user did not give consent
                    if (notConsentedItems.isNotEmpty()) {
                        Log.d(LOG_TAG, "mDL Holder did not gave consent for the " +
                                "following items")
                        Log.d(LOG_TAG, notConsentedItems.toString())
                    }

                    val reqItems = element.dataElements.keys.toMutableList()
                    notConsentedItems.forEach { item ->
                        reqItems.remove(item)
                    }

                    sessionTranscript?.let {sTranscript ->
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
                                    Log.d("ECDSA Signature", coseSign1.encodeToString())

                                    val deviceAuth = DeviceAuth.Builder()
                                        .setCoseSign1(coseSign1)
                                        .build()
                                    Log.d("Device Auth", deviceAuth.encodeToString())

                                    issuerAuthBytes.let { iAuth ->

                                        val issuerAuth = CoseSign1.Builder()
                                            .decode(iAuth)
                                            .build()
                                        Log.d("Issuer Auth", issuerAuth.encodeToString())

                                        // Retrieve the List of IssuerSignedItems used to create the
                                        // MSO contained in the retrieved Cose_Sign1
                                        val issuerNamespaces =
                                            issuerAuthority?.getIssuerNamespaces(iAuth)

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
                                    Log.d("MAC", coseMac0.encodeToString())

                                    val deviceAuth = DeviceAuth.Builder()
                                        .setCoseMac0(coseMac0)
                                        .build()
                                    Log.d("Device Auth", deviceAuth.encodeToString())

                                    issuerAuthBytes.let { iAuth ->

                                        val issuerAuth = CoseSign1.Builder()
                                            .decode(iAuth)
                                            .build()
                                        Log.d("Issuer Auth", issuerAuth.encodeToString())

                                        // Retrieve the List of IssuerSignedItems used to create the
                                        // MSO contained in the retrieved Cose_Sign1
                                        val issuerNamespaces =
                                            issuerAuthority?.getIssuerNamespaces(iAuth)

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
                            android.util.Log.e(LOG_TAG, ex.message, ex)
                        } catch (ex : IdentityCredentialException) {
                            android.util.Log.e(LOG_TAG, ex.message, ex)
                        } catch(ex: NoClassDefFoundError) {
                            android.util.Log.e(LOG_TAG, ex.message, ex)
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

    private fun errorResponse() : IResponse {
        return Response.Builder()
                .isError()
                .build()
    }

    override fun onDecryptError() {
        sessionManager?.let {
            // Send response
            sendData(it.generateResponse(null))
        }
    }

    override fun askForUserConsent(requestItems: List<String>) {
        transportEventListener?.askForUserConsent(requestItems)
    }

    override suspend fun onUserConsent(userConsentMap: Map<String, Boolean>?) {
        // Generate a response to the reader
        val response = generateResponse(userConsentMap)
        val responseBytes = response.encode()

        Log.d(LOG_TAG, "Response Cbor")
        Log.d(LOG_TAG, CborUtils.encodeToString(responseBytes))
        Log.d(LOG_TAG, CborUtils.cborPrettyPrint(responseBytes))

        sessionManager?.let {
            // Send response
            sendData(it.generateResponse(responseBytes))
        }
    }

    private fun sendResponse(response: IResponse) {
        val responseBytes = response.encode()

        Log.d(LOG_TAG, "Response Cbor")
        Log.d(LOG_TAG, CborUtils.cborPrettyPrint(responseBytes))

        sessionManager?.let {
            // Send response
            sendData(it.generateResponse(responseBytes))
        }
    }

    override fun getCryptoObject(): BiometricPrompt.CryptoObject? {
        val credential = sessionManager?.getIdentityCredential()
        return credential?.cryptoObject
    }

    init {
        this.transportLayer?.setEventListener(this)
        initTransportLayer()
    }
}