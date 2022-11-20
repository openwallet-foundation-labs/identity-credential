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

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import androidx.navigation.NavDeepLinkBuilder
import com.android.identity.DataTransport
import com.android.identity.NfcApduRouter
import com.android.identity.NfcEngagementHelper
import com.android.identity.PresentationHelper
import com.android.identity.PresentationSession
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
    private var presentation: PresentationHelper? = null

    private val nfcApduRouter: NfcApduRouter = object : NfcApduRouter() {
        override fun sendResponseApdu(responseApdu: ByteArray) {
            this@NfcEngagementHandler.sendResponseApdu(responseApdu)
        }
    }

    private val nfcEngagementListener = object : NfcEngagementHelper.Listener {

        override fun onDeviceConnecting() {
            log("Engagement Listener: Device Connecting. Launching Transfer Screen")
            val pendingIntent = NavDeepLinkBuilder(applicationContext)
                .setGraph(R.navigation.navigation_graph)
                .setDestination(R.id.transferDocumentFragment)
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
            val builder = PresentationHelper.Builder(
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
        }
    }

    private val presentationListener = object : PresentationHelper.Listener {

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
        session = SessionSetup(CredentialStore(applicationContext)).createSession()
        communication = Communication.getInstance(applicationContext)
        transferManager = TransferManager.getInstance(applicationContext)
        transferManager.setCommunication(session, communication)
        val connectionSetup = ConnectionSetup(applicationContext)
        engagementHelper = NfcEngagementHelper(
            applicationContext,
            session,
            connectionSetup.getConnectionMethods(),
            connectionSetup.getConnectionOptions(),
            nfcApduRouter,
            nfcEngagementListener,
            applicationContext.mainExecutor()
        )
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray? {
        log("processCommandApdu: Command-> ${FormatUtil.encodeToString(commandApdu)}")
        nfcApduRouter.addReceivedApdu(AID_FOR_TYPE_4_TAG_NDEF_APPLICATION, commandApdu)
        return null
    }

    override fun onDeactivated(reason: Int) {
        log("onDeactivated: reason-> $reason")
        nfcApduRouter.addDeactivated(AID_FOR_TYPE_4_TAG_NDEF_APPLICATION, reason)
    }

    companion object {
        private val AID_FOR_TYPE_4_TAG_NDEF_APPLICATION: ByteArray = byteArrayOf(
            0xD2.toByte(),
            0x76.toByte(),
            0x00.toByte(),
            0x00.toByte(),
            0x85.toByte(),
            0x01.toByte(),
            0x01.toByte()
        )
    }
}
