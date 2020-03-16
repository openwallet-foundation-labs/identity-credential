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

package com.ul.ims.gmdl.offlinetransfer.executorLayer.verifier

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.doctype.MdlDoctype
import com.ul.ims.gmdl.cbordata.interpreter.IDataInterpreter
import com.ul.ims.gmdl.cbordata.namespace.MdlNamespace
import com.ul.ims.gmdl.cbordata.request.Request
import com.ul.ims.gmdl.cbordata.response.BleTransferResponse
import com.ul.ims.gmdl.cbordata.response.IResponse
import com.ul.ims.gmdl.cbordata.response.Response
import com.ul.ims.gmdl.cbordata.security.IssuerNameSpaces
import com.ul.ims.gmdl.cbordata.security.mso.MobileSecurityObject
import com.ul.ims.gmdl.cbordata.security.sessionEncryption.SessionData
import com.ul.ims.gmdl.cbordata.security.sessionEncryption.SessionEstablishment
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer
import com.ul.ims.gmdl.offlinetransfer.utils.LivedataUtils
import com.ul.ims.gmdl.offlinetransfer.utils.Log
import com.ul.ims.gmdl.offlinetransfer.utils.Resource
import com.ul.ims.gmdl.security.issuerdataauthentication.IssuerDataAuthenticationException
import com.ul.ims.gmdl.security.issuerdataauthentication.IssuerDataAuthenticator
import com.ul.ims.gmdl.security.issuerdataauthentication.RootCertificateInitialiser
import com.ul.ims.gmdl.security.mdlauthentication.MdlAuthenticationException
import com.ul.ims.gmdl.security.mdlauthentication.MdlAuthenticator
import com.ul.ims.gmdl.security.sessionencryption.verifier.VerifierSessionManager
import java.util.*

class VerifierExecutor(
    interpreter: IDataInterpreter,
    transportLayer: ITransportLayer,
    data: MutableLiveData<Resource<Any>>,
    sessionManager: VerifierSessionManager,
    requestItems: Array<String>,
    deviceEngagement: DeviceEngagement,
    context: Context
) : IVerifierExecutor, IExecutorEventListener {

    companion object {
        val LOG_TAG = VerifierExecutor::class.java.simpleName
    }

    override var data: MutableLiveData<Resource<Any>>? = null
    override var interpreter: IDataInterpreter? = null
    override var transportLayer : ITransportLayer? = null
    private var sessionManager : VerifierSessionManager? = null
    private var sessionEstablishment : SessionEstablishment? = null
    private var requestItems: Array<String>? = null
    private var deviceEngagement: DeviceEngagement? = null
    private var context: Context? = null

    // Supported namespace
    private val namespace = MdlNamespace

    // Supported docType
    private val docType = MdlDoctype

    private fun initTransportLayer() {
        val sha256Hash = sessionManager?.getHolderPkHash()

        sha256Hash?.let {hash ->
            Log.d(LOG_TAG, "holder pk hash = ${encodeToString(hash)}")

            transportLayer?.inititalize(hash)
        }
    }
    override fun getInitialRequest(): Request {
        return Request.Builder()
            .dataItemsToRequest(requestItems)
            .build()
    }

    override fun onCommand(data: ByteArray) {
        var res : IResponse? = null
        Log.d(LOG_TAG, "Received Session Data")
        Log.d(LOG_TAG, CborUtils.cborPrettyPrint(data))

        Log.d(LOG_TAG, "Extract Response from Session Data")
        val sessionData = SessionData.Builder()
            .decode(data)
            .build()

        var decryptedBytes : ByteArray? = null
        sessionManager?.let { session ->
            sessionData.encryptedData?.let { encryptedRes ->
                Log.d(LOG_TAG, encodeToString(encryptedRes))
                Log.d(LOG_TAG, "Decrypt Response")
                decryptedBytes = session.decryptData(encryptedRes)
                decryptedBytes?.let {
                    Log.d(LOG_TAG, CborUtils.cborPrettyPrint(it))
                }
            } ?:run {
                Log.d(LOG_TAG, "Unable to Extract Response from Session Data")
            }
        }

        interpreter?.let {
            decryptedBytes?.let {bytes ->
                res = it.interpret(bytes) as? IResponse
            }
        }

        onResponse(res)
    }

    override fun onResponse(res : IResponse?) {
        // Handle response sent by the Holder
        // validate response
        getResponseforNamespace(res)

        closeConnection()
    }

    override fun onReceive(data: ByteArray?) {
        // Here we should check the response received by the holder
        // for now we're always assuming that it's complete
        data?.let {bytesData ->
            onCommand(bytesData)
        }
    }

    override fun onEvent(string: String?, i: Int) {
        Log.d(LOG_TAG, "onEvent = $string i = $i")

        when(string) {
            EventType.STATE_READY_FOR_TRANSMISSION.description -> {
                // event = Holder ready to read
                // Send initial request to Holder
                val req = getInitialRequest()
                val encoded = req.encode()
                Log.d(LOG_TAG, "Cbor Request")
                Log.d(LOG_TAG, CborUtils.cborPrettyPrint(encoded))

                // First Request is a SessionEstablishment Obj
                var encryptedRequest: ByteArray?
                sessionManager?.let { session ->

                    // Encrypt the request msg
                    encryptedRequest = session.encryptData(encoded)

                    Log.d(LOG_TAG, "Encrypted Request")
                    encryptedRequest?.let {req ->

                        // Create the Session Establishment Obj
                        Log.d(LOG_TAG, encodeToString(req))
                        sessionEstablishment = session.createSessionEstablishment(req)
                    }
                }

                sessionEstablishment?.let {
                    val encodedSessionEstablishment = it.encode()

                    Log.d(LOG_TAG, "Session Establishment")
                    Log.d(LOG_TAG, CborUtils.cborPrettyPrint(encodedSessionEstablishment))
                    sendData(encodedSessionEstablishment)
                }
            }
        }
    }

    override fun sendData(bytes : ByteArray) {
        transportLayer?.write(bytes)
    }

    private fun getResponseforNamespace(res : IResponse?) {
        val builder = BleTransferResponse.Builder()
        res?.let { iRes ->
            (iRes as? Response)?.let { r ->
                // Check if there is a general error
                builder.setResponseStatus(r.status)

                if (!r.isError()) {
                    r.documents.forEach { document ->
                        // Currently we support only one namespace
                        val myDoc = document[docType.docType]
                        myDoc?.let { doc ->
                            // Look for errors in a certain namespace item
                            val namespaceItemsError = doc.erros.errors[namespace.namespace]
                            builder.setErrorItems(namespaceItemsError)

                            // Get response for the requested namespace items
                            val namespaceItemsResponse =
                                doc.issuerSigned.nameSpaces?.get(namespace.namespace)
                            builder.setIssuerSignedItems(namespaceItemsResponse)

                            // Get the Issuer Authentication Structure
                            val issuerAuth = doc.issuerSigned.issuerAuth

                            // Get the device Signed Structure
                            val deviceSigned = doc.deviceSigned

                            // Issuer Data Authentication
                            issuerAuth.let {
                                namespaceItemsResponse?.let {
                                    val issuerNamespace = IssuerNameSpaces.Builder()
                                        .setNamespace(
                                            MdlNamespace.namespace,
                                            namespaceItemsResponse.toMutableList()
                                        )
                                        .build()
                                    val mso = MobileSecurityObject.Builder()
                                        .decode(issuerAuth.payloadData)
                                        .build()

                                    mso?.let { mobSo ->
                                        context?.let { ctx ->
                                            val rootCertificateInitialiser =
                                                RootCertificateInitialiser(ctx)

                                            val issuerDataAuthenticator = IssuerDataAuthenticator(
                                                rootCertificateInitialiser.rootCertificatesAndPublicKeys,
                                                issuerAuth,
                                                issuerNamespace,
                                                mobSo.documentType,
                                                null
                                            )

                                            var result = false
                                            try {
                                                result =
                                                    issuerDataAuthenticator.isDataAuthentic(Date())
                                            } catch (ex: IssuerDataAuthenticationException) {
                                                android.util.Log.e(LOG_TAG, ex.message, ex)
                                            } finally {
                                                builder.setIssuerDataValidation(result)
                                            }

                                            // Device Sign
                                            deviceEngagement?.let { de ->
                                                val deviceAuthentication = MdlAuthenticator(
                                                    de,
                                                    deviceSigned.deviceNameSpaces,
                                                    MdlDoctype,
                                                    sessionManager?.getReaderCoseKey(),
                                                    deviceSigned.deviceAuth,
                                                    sessionManager?.getVerifierPrivateKey(),
                                                    mobSo.coseKey
                                                )

                                                result = false
                                                try {
                                                    result = deviceAuthentication.isMdlAuthentic()
                                                } catch (ex: MdlAuthenticationException) {
                                                    android.util.Log.e(LOG_TAG, ex.message, ex)
                                                } finally {
                                                    builder.setDeviceSignValidation(result)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                }
            }
        }

        val bleTransferResponse = builder.build()

        data?.let { live->
            LivedataUtils.updateLiveData(Resource.success(bleTransferResponse), live)
        }
    }

    private fun closeConnection() {
        transportLayer?.closeConnection()
    }


    fun encodeStringDebug(encoded : ByteArray): String {
        val sb = StringBuilder(encoded.size * 2)

        val iterator = encoded.iterator().withIndex()
        var newLineCounter = 0
        iterator.forEach { b ->
            sb.append("0x")
            sb.append(String.format("%02x", b.value))
            sb.append(".toByte()")

            if (iterator.hasNext()) {
                newLineCounter++
                sb.append(", ")

                if (newLineCounter == 5) {
                    sb.append("\n")
                    newLineCounter = 0
                }
            }
        }

        return sb.toString()
    }

    init {
        try {
            this.transportLayer = transportLayer
            this.transportLayer?.setEventListener(this)
            this.data = data
            this.interpreter = interpreter
            this.sessionManager = sessionManager
            this.requestItems = requestItems
            this.deviceEngagement = deviceEngagement
            this.context = context

            initTransportLayer()
        } catch (ex: Exception) {
            android.util.Log.e(LOG_TAG, ex.localizedMessage, ex)
        }
    }
}