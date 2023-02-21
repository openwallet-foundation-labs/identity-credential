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

package com.android.mdl.app.util

import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.navigation.NavDeepLinkBuilder
import com.android.identity.DataTransport
import com.android.identity.NfcEngagementHelper
import com.android.identity.DeviceRetrievalHelper
import com.android.identity.PresentationSession
import com.android.mdl.app.MainActivity
import com.android.mdl.app.R
import com.android.mdl.app.transfer.Communication
import com.android.mdl.app.transfer.ConnectionSetup
import com.android.mdl.app.transfer.CredentialStore
import com.android.mdl.app.transfer.SessionSetup
import com.android.mdl.app.transfer.TransferManager


class NfcEngagementHandler : HostApduService() {

    private lateinit var engagementHelper: NfcEngagementHelper
    private lateinit var session: PresentationSession
    private lateinit var communication: Communication
    private lateinit var transferManager: TransferManager
    private var presentation: DeviceRetrievalHelper? = null

    private val nfcEngagementListener = object : NfcEngagementHelper.Listener {

        override fun onDeviceConnecting() {
            log("Engagement Listener: Device Connecting. Launching Transfer Screen")
            val launchAppIntent = Intent(applicationContext, MainActivity::class.java)
            launchAppIntent.action = Intent.ACTION_VIEW
            launchAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            launchAppIntent.addCategory(Intent.CATEGORY_DEFAULT)
            launchAppIntent.addCategory(Intent.CATEGORY_BROWSABLE)
            applicationContext.startActivity(launchAppIntent)

            val pendingIntent = NavDeepLinkBuilder(applicationContext)
                .setGraph(R.navigation.navigation_graph)
                .setDestination(R.id.transferDocumentFragment)
                .setComponentName(MainActivity::class.java)
                .createPendingIntent()
            pendingIntent.send(applicationContext, 0, null)
            transferManager.updateStatus(TransferStatus.CONNECTING)
        }

        override fun onDeviceConnected(transport: DataTransport) {
            if (presentation != null) {
                log("Engagement Listener: Device Connected -> ignored due to active presentation")
                return
            }

            log("Engagement Listener: Device Connected via NFC")
            val builder = DeviceRetrievalHelper.Builder(
                applicationContext,
                presentationListener,
                applicationContext.mainExecutor(),
                session
            )
            builder.useForwardEngagement(
                transport,
                engagementHelper.deviceEngagement,
                engagementHelper.handover
            )
            presentation = builder.build()
            presentation?.setSendSessionTerminationMessage(true)
            communication.setupPresentation(presentation!!)
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
        session = SessionSetup(CredentialStore(applicationContext)).createSession()
        communication = Communication.getInstance(applicationContext)
        transferManager = TransferManager.getInstance(applicationContext)
        transferManager.setCommunication(session, communication)
        val connectionSetup = ConnectionSetup(applicationContext)
        val builder = NfcEngagementHelper.Builder(
            applicationContext,
            session,
            connectionSetup.getConnectionOptions(),
            nfcEngagementListener,
            applicationContext.mainExecutor())
        if (PreferencesHelper.shouldUseStaticHandover()) {
            builder.useStaticHandover(connectionSetup.getConnectionMethods())
        } else {
            builder.useNegotiatedHandover()
        }
        engagementHelper = builder.build()
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
            if (presentation == null) {
                logWarning("reader didn't connect inside $timeoutSeconds seconds, closing")
                engagementHelper.close()
            }
        }, timeoutSeconds*1000L)
    }
}
