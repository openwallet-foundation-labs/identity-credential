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
import androidx.navigation.NavDeepLinkBuilder
import com.android.identity.ConnectionMethod
import com.android.identity.ConnectionMethodBle
import com.android.identity.ConnectionMethodNfc
import com.android.identity.ConnectionMethodWifiAware
import com.android.identity.DataTransport
import com.android.identity.DataTransportOptions
import com.android.identity.IdentityCredentialStore
import com.android.identity.IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256
import com.android.identity.NfcApduRouter
import com.android.identity.NfcEngagementHelper
import com.android.identity.PresentationHelper
import com.android.identity.PresentationSession
import com.android.mdl.app.R
import com.android.mdl.app.transfer.NfcCommunication
import com.android.mdl.app.transfer.TransferManager
import java.util.OptionalLong
import java.util.UUID

class NfcEngagementHandler : HostApduService() {

    private lateinit var engagementHelper: NfcEngagementHelper
    private lateinit var session: PresentationSession
    private lateinit var nfcCommunication: NfcCommunication
    private lateinit var transferManager: TransferManager
    private var presentation: PresentationHelper? = null

    private val nfcApduRouter: NfcApduRouter = object : NfcApduRouter() {
        override fun sendResponseApdu(responseApdu: ByteArray) {
            this@NfcEngagementHandler.sendResponseApdu(responseApdu)
        }
    }

    private val nfcEngagementListener = object : NfcEngagementHelper.Listener {

        override fun onDeviceConnecting() {
            log("Engagement Listener: Device Connecting")
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
            nfcCommunication.setupPresentation(presentation!!)
            engagementHelper.close()
        }

        override fun onError(error: Throwable) {
            log("Engagement Listener: onError -> ${error.message}")
        }
    }

    private val presentationListener = object : PresentationHelper.Listener {

        override fun onDeviceRequest(deviceRequestBytes: ByteArray) {
            log("Presentation Listener: OnDeviceRequest")
            nfcCommunication.setDeviceRequest(deviceRequestBytes)
            log("Presentation Listener: Launching Transfer Screen")
            val pendingIntent = NavDeepLinkBuilder(applicationContext)
                .setGraph(R.navigation.navigation_graph)
                .setDestination(R.id.transferDocumentFragment)
                .createPendingIntent()
            pendingIntent.send(applicationContext, 0, Intent())
        }

        override fun onDeviceDisconnected(transportSpecificTermination: Boolean) {
            log("Presentation Listener: onDeviceDisconnected")
        }

        override fun onError(error: Throwable) {
            log("Presentation Listener: onError -> ${error.message}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        val store = createIdentityCredentialStore()
        session = store.createPresentationSession(CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256)
        nfcCommunication = NfcCommunication.getInstance(applicationContext)
        transferManager = TransferManager.getInstance(applicationContext)
        transferManager.setSession(session)
        engagementHelper = NfcEngagementHelper(
            applicationContext,
            session,
            getConnectionMethods(),
            getConnectionOptions(),
            nfcApduRouter,
            nfcEngagementListener,
            applicationContext.mainExecutor()
        )
    }

    private fun createIdentityCredentialStore(): IdentityCredentialStore {
        return if (PreferencesHelper.isHardwareBacked(applicationContext))
            IdentityCredentialStore.getHardwareInstance(applicationContext)
                ?: createKeystoreBackedStore() else createKeystoreBackedStore()
    }

    private fun createKeystoreBackedStore(): IdentityCredentialStore {
        val keystoreBackedStorageLocation = PreferencesHelper
            .getKeystoreBackedStorageLocation(applicationContext)
        return IdentityCredentialStore
            .getKeystoreInstance(applicationContext, keystoreBackedStorageLocation)
    }

    private fun getConnectionMethods(): List<ConnectionMethod> {
        val connectionMethods = ArrayList<ConnectionMethod>()
        if (PreferencesHelper.isBleDataRetrievalEnabled(applicationContext)) {
            connectionMethods.add(ConnectionMethodBle(false, true, null, UUID.randomUUID()))
        }
        if (PreferencesHelper.isBleDataRetrievalPeripheralModeEnabled(applicationContext)) {
            connectionMethods.add(ConnectionMethodBle(true, false, UUID.randomUUID(), null))
        }
        if (PreferencesHelper.isWifiDataRetrievalEnabled(applicationContext)) {
            val empty = OptionalLong.empty()
            connectionMethods.add(ConnectionMethodWifiAware(null, empty, empty, null))
        }
        if (PreferencesHelper.isNfcDataRetrievalEnabled(applicationContext)) {
            connectionMethods.add(ConnectionMethodNfc(0xffff, 0x10000))
        }
        return connectionMethods
    }

    private fun getConnectionOptions(): DataTransportOptions {
        val builder = DataTransportOptions.Builder()
            .setBleUseL2CAP(PreferencesHelper.isBleL2capEnabled(applicationContext))
            .setBleClearCache(PreferencesHelper.isBleClearCacheEnabled(applicationContext))
        return builder.build()
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
