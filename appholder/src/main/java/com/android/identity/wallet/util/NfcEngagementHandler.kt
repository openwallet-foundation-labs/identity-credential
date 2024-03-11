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

package com.android.identity.wallet.util

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.navigation.NavDeepLinkBuilder
import com.android.identity.android.mdoc.deviceretrieval.DeviceRetrievalHelper
import com.android.identity.android.mdoc.engagement.NfcEngagementHelper
import com.android.identity.android.mdoc.transport.DataTransport
import com.android.identity.android.util.HostApduServiceScoped
import com.android.identity.crypto.Crypto
import com.android.identity.crypto.EcPublicKey
import com.android.identity.wallet.R
import com.android.identity.wallet.transfer.Communication
import com.android.identity.wallet.transfer.ConnectionSetup
import com.android.identity.wallet.transfer.TransferManager

class NfcEngagementHandler : HostApduServiceScoped() {

    private lateinit var engagementHelper: NfcEngagementHelper
    private lateinit var communication: Communication
    private lateinit var transferManager: TransferManager

    private var deviceRetrievalHelper: DeviceRetrievalHelper? = null

    private val settings by lazy {
        PreferencesHelper.apply { initialize(applicationContext) }
    }
    private val eDeviceKey by lazy {
        Crypto.createEcPrivateKey(settings.getEphemeralKeyCurveOption())
    }
    private val nfcEngagementListener = object : NfcEngagementHelper.Listener {

        override fun onTwoWayEngagementDetected() {
            log("Engagement Listener: Two Way Engagement Detected.")
        }

        override fun onHandoverSelectMessageSent() {
            log("Engagement Listener: Handover Select Message Sent.")
        }

        override fun onDeviceConnecting() {
            log("Engagement Listener: Device Connecting. Launching Transfer Screen")
            val pendingIntent = NavDeepLinkBuilder(applicationContext)
                .setGraph(R.navigation.navigation_graph)
                .setDestination(R.id.transferDocumentFragment)
                .setComponentName(com.android.identity.wallet.MainActivity::class.java)
                .createPendingIntent()
            pendingIntent.send(applicationContext, 0, null)
            transferManager.updateStatus(TransferStatus.CONNECTING)
        }

        override fun onDeviceConnected(transport: DataTransport) {
            if (deviceRetrievalHelper != null) {
                log("Engagement Listener: Device Connected -> ignored due to active presentation")
                return
            }

            log("Engagement Listener: Device Connected via NFC")

            deviceRetrievalHelper = DeviceRetrievalHelper.Builder(
                context = applicationContext,
                listener = presentationListener,
                scope = serviceScope,
                eDeviceKey =eDeviceKey,
                transport = transport
            ).apply{
                useForwardEngagement(
                    engagementHelper.deviceEngagement,
                    engagementHelper.handover
                )
            }.build()
            communication.deviceRetrievalHelper = deviceRetrievalHelper
            engagementHelper.close()
            transferManager.updateStatus(TransferStatus.CONNECTED)
        }

        override fun onError(error: Throwable) {
            log("Engagement Listener: onError -> ${error.message}")
            transferManager.updateStatus(TransferStatus.ERROR)
            engagementHelper.close()
        }
    }

    private val presentationListener = object : DeviceRetrievalHelper.Listener {

        override fun onEReaderKeyReceived(eReaderKey: EcPublicKey) {
            log("DeviceRetrievalHelper Listener (NFC): OnEReaderKeyReceived")
        }

        override fun onDeviceRequest(deviceRequestBytes: ByteArray) {
            log("Presentation Listener: OnDeviceRequest")
            communication.setDeviceRequest(deviceRequestBytes)
            transferManager.updateStatus(TransferStatus.REQUEST)
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
            log("Presentation Listener: onDeviceDisconnected")
            transferManager.updateStatus(TransferStatus.DISCONNECTED)
        }

        override fun onError(error: Throwable) {
            log("Presentation Listener: onError -> ${error.message}")
            transferManager.updateStatus(TransferStatus.ERROR)
        }
    }

    override fun onCreate() {
        super.onCreate()
        log("onCreate")
        communication = Communication.getInstance(applicationContext)
        transferManager = TransferManager.getInstance(applicationContext)
        transferManager.setCommunication(communication)
        val connectionSetup = ConnectionSetup(applicationContext)
        val builder = NfcEngagementHelper.Builder(
            context = applicationContext,
            eDeviceKey =eDeviceKey.publicKey,
            options = connectionSetup.getConnectionOptions(),
            listener = nfcEngagementListener,
            scope = serviceScope
        )
        if (PreferencesHelper.shouldUseStaticHandover()) {
            builder.useStaticHandover(connectionSetup.getConnectionMethods())
        } else {
            builder.useNegotiatedHandover()
        }
        engagementHelper = builder.build()

        val launchAppIntent = Intent(applicationContext, com.android.identity.wallet.MainActivity::class.java)
        launchAppIntent.action = Intent.ACTION_VIEW
        launchAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        launchAppIntent.addCategory(Intent.CATEGORY_DEFAULT)
        launchAppIntent.addCategory(Intent.CATEGORY_BROWSABLE)
        applicationContext.startActivity(launchAppIntent)
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray? {
        log("processCommandApdu: Command-> ${FormatUtil.encodeToString(commandApdu)}")
        return engagementHelper.nfcProcessCommandApdu(commandApdu)
    }

    override fun onDeactivated(reason: Int) {
        log("onDeactivated: reason-> $reason")
        engagementHelper.nfcOnDeactivated(reason)

        // We need to close the NfcEngagementHelper but if we're doing it as the reader moves
        // out of the field, it's too soon as it may take a couple of seconds to establish
        // the connection, triggering onDeviceConnected() callback above.
        //
        // In fact, the reader _could_ actually take a while to establish the connection...
        // for example the UI in the mdoc doc reader might have the operator pick the
        // transport if more than one is offered. In fact this is exactly what we do in
        // our mdoc reader.
        //
        // So we give the reader 15 seconds to do this...
        //
        val timeoutSeconds = 15
        Handler(Looper.getMainLooper()).postDelayed({
            if (deviceRetrievalHelper == null) {
                logWarning("reader didn't connect inside $timeoutSeconds seconds, closing")
                engagementHelper.close()
            }
        }, timeoutSeconds * 1000L)
    }
}

