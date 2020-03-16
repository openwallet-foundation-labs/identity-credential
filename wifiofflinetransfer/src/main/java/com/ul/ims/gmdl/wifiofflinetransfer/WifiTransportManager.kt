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

package com.ul.ims.gmdl.wifiofflinetransfer

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.util.Log
import com.ul.ims.gmdl.offlinetransfer.config.AppMode
import com.ul.ims.gmdl.offlinetransfer.transportLayer.EventType
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportEventListener
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransportManager
import com.ul.ims.gmdl.wifiofflinetransfer.publisher.WifiAwareServicePublisher
import com.ul.ims.gmdl.wifiofflinetransfer.subscriber.WifiAwareServiceSubscriber
import com.ul.ims.gmdl.wifiofflinetransfer.utils.WifiUtils

/**
 * Wi-fi Aware Specific Transport Manager
 */
@TargetApi(29)
class WifiTransportManager(
    private val context: Context,
    private val appMode: AppMode,
    publicKey: ByteArray, // required to generate the service name and passphrase
    private val wifiPassphrase: String? // Informed on NFC engagement passphrase
) : TransportManager {

    private var transportEventListener: ITransportEventListener? = null

    private var connectivityManager: ConnectivityManager? = null

    private var session: WifiAwareSession? = null

    private var callback: ConnectivityManager.NetworkCallback? = null

    private val serviceName = WifiUtils.getServiceName(publicKey)

    private val passphrase = WifiUtils.getPassphrase(publicKey)

    private var wifiAwareService: ITransportLayer? = null

    init {
        setupTransportLayer()
    }

    companion object {
        const val LOG_TAG = "WifiTransportManager"
    }

    override fun setTransportProgressListener(transportEventListener: ITransportEventListener) {
        this.transportEventListener = transportEventListener
    }

    private fun setupTransportLayer() {

        connectivityManager = context.
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            throw IllegalStateException("WiFi Aware not supported as a system feature")
        }

        val wifiAwareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE)
                as? WifiAwareManager ?: throw IllegalStateException("Could not get WiFi Aware Manager")

        if (!wifiAwareManager.isAvailable) {
            throw IllegalStateException("WiFi Aware Manager is not available")
        }

        Log.d(LOG_TAG, "Attach listener to session callback")
        wifiAwareManager.attach(WifiAwareAttachListener(this), null)

        connectivityManager?.let { cm ->
            when (appMode) {
                AppMode.HOLDER -> {

                    wifiAwareService = WifiAwareServicePublisher(
                        serviceName,
                        passphrase,
                        cm,
                        this
                    )
                }
                AppMode.VERIFIER -> {

                    wifiAwareService = WifiAwareServiceSubscriber(
                        serviceName,
                        wifiPassphrase ?: passphrase,
                        cm,
                        this
                    )
                }
                else -> throw UnsupportedOperationException("Unknown AppMode")
            }
        }

    }

    // Sets up wifi aware session and starts the service
    private fun setupWifiAwareSession(wifiAwareSession: WifiAwareSession) {
        this.session = wifiAwareSession

        Log.d(LOG_TAG, "createWifiAwareService")

        when (appMode) {
            AppMode.HOLDER -> {
                val wifiAwareServicePublisher = wifiAwareService as? WifiAwareServicePublisher
                wifiAwareServicePublisher?.publish(wifiAwareSession)
            }
            AppMode.VERIFIER -> {
                val wifiAwareServiceSubscriber = wifiAwareService as? WifiAwareServiceSubscriber
                wifiAwareServiceSubscriber?.subscribe(wifiAwareSession)
            }
            else -> throw UnsupportedOperationException("Unknown AppMode")
        }

    }

    override fun getTransportLayer(): ITransportLayer {
        return when (appMode) {
            AppMode.HOLDER -> {
                wifiAwareService ?: throw UnsupportedOperationException("Publish service is null")
            }
            AppMode.VERIFIER -> {
                wifiAwareService ?: throw UnsupportedOperationException("Subscribe service is null")
            }
        }
    }

    fun close() {
        Log.d(LOG_TAG, "WifiTransportManager closing")
        callback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
        session?.close()
        callback = null
    }

    override fun setReadyForNextFile(boolean: Boolean) {
        Log.i(LOG_TAG, "Ready to read next file.")
        // TODO(JS): ?
        throw UnsupportedOperationException("What does this mean for Wi-Fi Aware?")
    }

    fun getTransportProgressDelegate(): ITransportEventListener {
        return transportEventListener ?: throw IllegalStateException("TransportProgressDelegate is null")
    }

    private class WifiAwareAttachListener(private val wifiTransportManager: WifiTransportManager) :
        AttachCallback() {

        companion object {
            private const val LOG_TAG = "WifiAwareAttachListener"
        }

        override fun onAttached(session: WifiAwareSession?) {
            Log.d(LOG_TAG, "onAttached: $session")
            session?.let {
                wifiTransportManager.setupWifiAwareSession(it)
            }
        }

        override fun onAttachFailed() {
            Log.d(LOG_TAG, "onAttachFailed")
            wifiTransportManager.transportEventListener?.onEvent(
                EventType.ERROR,
                "Failed to attach wifi aware"
            )
        }
    }
}