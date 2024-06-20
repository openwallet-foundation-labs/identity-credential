package com.android.identity_credential.wallet.ui.destination.provisioncredential

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.nfc.NfcAdapter
import android.os.Bundle
import android.os.IBinder
import androidx.compose.runtime.MutableState
import androidx.navigation.NavController
import com.android.identity.issuance.evidence.EvidenceResponseGermanEid
import com.android.identity.util.Logger
import com.android.identity_credential.wallet.util.getActivity
import com.governikus.ausweisapp2.IAusweisApp2Sdk
import com.governikus.ausweisapp2.IAusweisApp2SdkCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Object that implements reading Germain eID card using Ausweis SDK.
 */
class AusweisModel(
    private val context: Context,
    private val status: MutableState<Status?>,
    private val navController: NavController,
    private val requiredComponents: List<String>,
    private val coroutineScope: CoroutineScope,
    private val onResult: (result: EvidenceResponseGermanEid) -> Unit
) {
    companion object {
        const val TAG = "AusweisModel"
    }

    enum class Route(val route: String) {
        INITIAL("initial"),
        ACCESS_RIGHTS("access"),
        CARD_SCAN("card"),
        PIN_ENTRY("pin")
    }

    sealed class Status

    data object Ready : Status()
    data class Initial(val emulation: Boolean) : Status()
    data class AccessRights(val components: List<String>) : Status()
    data object WaitingForPin: Status()
    data class Auth(val progress: Int): Status()

    private lateinit var sdk: IAusweisApp2Sdk
    private lateinit var sessionId: String
    private var job: Job? = null
    private var useSimulatedCard = false
    private val messageChannel = Channel<JsonObject>(10)
    private val acceptChanel = Channel<Boolean>(1)
    private val pinChanel = Channel<String>(1)

    val callback = object : IAusweisApp2SdkCallback.Stub() {
        override fun sessionIdGenerated(newSessionId: String, isSecureSessionId: Boolean) {
            Logger.i(TAG, "New session, secure: $isSecureSessionId")
            sessionId = newSessionId
        }

        override fun receive(jsonText: String) {
            Logger.i(TAG, "Received: $jsonText")
            val parsedJson = Json.parseToJsonElement(jsonText)
            val result = messageChannel.trySend(parsedJson.jsonObject)
            if (result.isClosed) {
                Logger.e(TAG, "Message after SDK job was completed")
            } else if (result.isFailure) {
                Logger.e(TAG, "SDK job did not process messages")
            }
        }

        override fun sdkDisconnected() {
            Logger.i(TAG, "Session disconnected")
        }
    }

    val serviceConnection = object: ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Logger.d(TAG, "Connected")
            sdk = IAusweisApp2Sdk.Stub.asInterface(service)
            sdk.connectSdk(callback)
            coroutineScope.launch {
                status.value = Ready
            }
        }
        override fun onServiceDisconnected(className: ComponentName) {
            Logger.d(TAG, "Disconnected")
        }
    }

    init {
        val serviceIntent = Intent("com.governikus.ausweisapp2.START_SERVICE")
        serviceIntent.setPackage(context.packageName)
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun startWorkflow(simulatedCard: Boolean) {
        useSimulatedCard = simulatedCard
        job = coroutineScope.launch {
            runAusweisSdk()
        }
    }

    fun acceptAccess() {
        coroutineScope.launch {
            acceptChanel.send(true)
        }
    }

    fun providePin(pin: String) {
        coroutineScope.launch {
            pinChanel.send(pin)
        }
    }

    private suspend fun runAusweisSdk() {
        var completed = false
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        try {
            sdk.send(sessionId, """
                {
                  "cmd": "SET_API_LEVEL",
                  "level": 2
                }
            """.trimMargin())
            val apiLevel = readMessage(setOf("API_LEVEL"))
            if (apiLevel["current"]!!.jsonPrimitive.intOrNull != 2) {
                throw IllegalStateException("Could not obtain API Level 2 from AusweisSDK")
            }
            sdk.send(sessionId, """
                {
                  "cmd": "RUN_AUTH",
                  "tcTokenURL": "https://test.governikus-eid.de/AusweisAuskunft/WebServiceRequesterServlet",
                  "developerMode": $useSimulatedCard,
                  "handleInterrupt": false,
                  "status": true
                }
            """)

            enableCardScanning(nfcAdapter)

            var sendSetRightsCommand = true

            while (!completed) {
                val message =
                    readMessage(setOf("ACCESS_RIGHTS", "INSERT_CARD", "ENTER_PIN", "AUTH"))
                when (message["msg"]?.jsonPrimitive?.content) {
                    "ACCESS_RIGHTS" -> {
                        if (sendSetRightsCommand) {
                            sendSetRightsCommand = false
                            val jsonRights = JsonObject(
                                mapOf(
                                    "cmd" to JsonPrimitive("SET_ACCESS_RIGHTS"),
                                    "chat" to JsonArray(requiredComponents.map { str ->
                                        JsonPrimitive(
                                            str
                                        )
                                    })
                                )
                            )
                            Logger.i(TAG, "Sending command: $jsonRights")
                            sdk.send(sessionId, jsonRights.toString())
                        } else {
                            val effective = message["chat"]!!.jsonObject["effective"]!!.jsonArray
                            status.value = AccessRights(effective.map { it.jsonPrimitive.content })
                            navController.navigate(Route.ACCESS_RIGHTS.route)
                            acceptChanel.receive()
                            sdk.send(sessionId, "{\"cmd\": \"ACCEPT\"}")
                        }
                    }

                    "INSERT_CARD" -> {
                        navController.navigate(Route.CARD_SCAN.route)
                        if (useSimulatedCard) {
                            sdk.send(sessionId, "{\"cmd\": \"SET_CARD\", \"name\": \"Simulator\"}")
                        }
                    }

                    "ENTER_PIN" -> {
                        navController.navigate(Route.PIN_ENTRY.route)
                        if (useSimulatedCard) {
                            sdk.send(sessionId, "{\"cmd\": \"SET_PIN\"}")
                        } else {
                            status.value = WaitingForPin
                            val pin = pinChanel.receive()
                            if (!pin.matches(Regex("^\\d{6}\$"))) {
                                // This is just hygiene, actual pin validation should happen in UI!
                                throw IllegalStateException("Invalid pin value")
                            }
                            sdk.send(sessionId, "{\"cmd\": \"SET_PIN\", \"value\": \"$pin\"}")
                        }
                        navController.navigate(Route.CARD_SCAN.route)
                    }

                    "AUTH" -> {
                        val result = message["result"]
                        if (result != null && result is JsonObject) {
                            completed = true
                            onResult(
                                EvidenceResponseGermanEid(
                                    true,
                                    result["major"]?.jsonPrimitive?.content,
                                    message["url"]?.jsonPrimitive?.content,
                                )
                            )
                        }
                    }
                }
            }
        } finally {
            if (!completed) {
                onResult(EvidenceResponseGermanEid(false))
            }
            messageChannel.close()
            disableCardScanning(nfcAdapter)
            context.unbindService(serviceConnection)
        }
    }

    private suspend fun readMessage(expectedTypes: Set<String>): JsonObject {
        while (true) {
            val message = messageChannel.receive()
            val type = message["msg"]?.jsonPrimitive?.content
            if (expectedTypes.contains(type)) {
                return message
            }
            if (type == "STATUS") {
                val workflow = message["workflow"]?.jsonPrimitive?.content
                if (workflow == "AUTH") {
                    status.value = Auth(progress = message["progress"]!!.jsonPrimitive.int)
                }
            } else if (type == "READER") {
                // TODO: process READER
            } else {
                throw IllegalStateException("Unexpected message type: $type")
            }
        }
    }

    private fun enableCardScanning(nfcAdapter: NfcAdapter) {
        val options = Bundle()
        options.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 300)
        val callback = NfcAdapter.ReaderCallback { tag ->
            sdk.updateNfcTag(sessionId, tag)
        }
        nfcAdapter.enableReaderMode(
            context.getActivity(),
            callback,
            NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or
                    NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NFC_BARCODE or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            options
        )
    }

    private fun disableCardScanning(nfcAdapter: NfcAdapter) {
        nfcAdapter.disableReaderMode(context.getActivity())
    }
}