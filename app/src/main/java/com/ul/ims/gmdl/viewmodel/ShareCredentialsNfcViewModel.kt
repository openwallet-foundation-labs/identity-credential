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

package com.ul.ims.gmdl.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import android.view.View
import androidx.biometric.BiometricPrompt
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.*
import androidx.security.identity.IdentityCredentialException
import com.ul.ims.gmdl.R
import com.ul.ims.gmdl.bleofflinetransfer.utils.BleUtils
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.deviceEngagement.security.Security
import com.ul.ims.gmdl.cbordata.model.UserCredential
import com.ul.ims.gmdl.issuerauthority.MockIssuerAuthority
import com.ul.ims.gmdl.nfcengagement.NfcHandler
import com.ul.ims.gmdl.offlineTransfer.OfflineTransferManager
import com.ul.ims.gmdl.offlinetransfer.appLayer.IofflineTransfer
import com.ul.ims.gmdl.offlinetransfer.config.AppMode
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.data.DataTypes
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.offlinetransfer.utils.Resource
import com.ul.ims.gmdl.provisioning.ProvisioningManager
import com.ul.ims.gmdl.security.sessionencryption.holder.HolderSessionManager
import com.ul.ims.gmdl.util.SharedPreferenceUtils
import com.ul.ims.gmdl.wifiofflinetransfer.utils.WifiUtils
import kotlinx.coroutines.launch
import org.jetbrains.anko.AnkoAsyncContext
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread

class ShareCredentialsNfcViewModel(val app: Application) : AndroidViewModel(app)  {
    val LOG_TAG = ShareCredentialsNfcViewModel::class.java.simpleName

    companion object {
        const val BLE_VERSION = 1
        const val DE_VERSION = "1.0"
        const val CIPHER_SUIT_IDENT = 1
        const val COSE_KEY_KTY = "tbd"
    }

    var nfcIconVisibility = ObservableInt()
    var permissionRequestVisibility = ObservableInt()
    var permissionRequestText = ObservableField<String>()
    var btnEnableBtVisibility = ObservableInt()
    var btnReqPermissionVisibility = ObservableInt()
    var loadingVisibility = ObservableInt()
    private var offlineTransferStatusLd = MutableLiveData<Resource<Any>>()
    private var iofflineTransferHolder : IofflineTransfer? = null
    private var liveDataMerger = MediatorLiveData<Resource<Any>>()

    fun getOfflineTransferStatusLd() : LiveData<Resource<Any>> {
        return liveDataMerger
    }


    fun setUp() {
        permissionRequestVisibility.set(View.GONE)
        nfcIconVisibility.set(View.GONE)
        btnReqPermissionVisibility.set(View.GONE)
        btnEnableBtVisibility.set(View.GONE)
    }

    fun launchNfcService(transferMethod: TransferChannels, isBleEnabled: Boolean) {
        launchNfcServiceAsync(transferMethod, isBleEnabled)
    }

    fun onUserConsent(userConsentMap: Map<String, Boolean>?) {
        viewModelScope.launch {
            iofflineTransferHolder?.onUserConsent(userConsentMap)
        }
    }

    fun onUserConsentCancel() {
        viewModelScope.launch {
            // null as a parameter here means that the user cancelled the consent dialog
            // and will trigger the mDL Holder to send the response as error 19
            iofflineTransferHolder?.onUserConsent(null)
        }
    }

    fun getCryptoObject(): BiometricPrompt.CryptoObject? {
        return iofflineTransferHolder?.getCryptoObject()
    }

    private fun launchNfcServiceAsync(transferMethod: TransferChannels, isBleEnabled: Boolean) {
        doAsync {
            try {
                // Session Manager is used to Encrypt/Decrypt Messages
                val sessionManager = HolderSessionManager.getInstance(
                    app.applicationContext,
                    UserCredential.CREDENTIAL_NAME
                )

                // Set up a new holder session so the Device Engagement COSE_Key is ephemeral to this engagement
                sessionManager.initializeHolderSession()

                val coseKey = sessionManager
                    .generateHolderCoseKey()
                    ?: throw IdentityCredentialException("Error generating Holder CoseKey")

                val security = Security.Builder()
                    .setCoseKey(coseKey)
                    .setCipherSuiteIdent(CIPHER_SUIT_IDENT)
                    .build()

                val builder = DeviceEngagement.Builder()

                builder.version(DE_VERSION)
                builder.security(security)

                var bleServiceMode: BleServiceMode? = null
                var blePeripheralMode = false
                var bleCentralMode = false

                if (transferMethod == TransferChannels.BLE) {
                    if (!isBleEnabled && transferMethod == TransferChannels.BLE) {
                        throw IdentityCredentialException("Bluetooth LE is not enabled")
                    }

                    blePeripheralMode = BleUtils.isPeripheralSupported(app.applicationContext)
                    bleCentralMode = BleUtils.isCentralModeSupported(app.applicationContext)

                    bleServiceMode =
                            // both modes are supported
                        if (blePeripheralMode && bleCentralMode) {
                            // When the mDL supports both modes, the mDL reader should act as BLE central mode.
                            BleServiceMode.PERIPHERAL_SERVER_MODE
                        } else {
                            if (bleCentralMode) {
                                // only central client mode supported
                                BleServiceMode.CENTRAL_CLIENT_MODE
                            } else {
                                // only peripheral server mode supported
                                BleServiceMode.PERIPHERAL_SERVER_MODE
                            }
                        }
                }

                var wifiPassphrase: String? = null
                var wifi5GHzBandSupported = false

                if (transferMethod == TransferChannels.WiFiAware) {
                    wifiPassphrase = WifiUtils.getPassphrase(coseKey.getPublicKey().encoded)
                    wifi5GHzBandSupported =
                        WifiUtils.getWifiManager(app.applicationContext)?.is5GHzBandSupported == true
                }

                val deviceEngagement = builder.build()

                val intent = Intent(app.applicationContext, NfcHandler::class.java)
                intent.putExtra(
                    NfcHandler.EXTRA_DEVICE_ENGAGEMENT_PAYLOAD,
                    deviceEngagement.encode()
                )
                intent.putExtra(
                    NfcHandler.EXTRA_TRANSFER_METHOD,
                    transferMethod
                )

                intent.putExtra(
                    NfcHandler.EXTRA_BLE_PERIPHERAL_SERVER_MODE,
                    blePeripheralMode
                )

                intent.putExtra(
                    NfcHandler.EXTRA_BLE_CENTRAL_CLIENT_MODE,
                    bleCentralMode
                )

                intent.putExtra(
                    NfcHandler.EXTRA_WIFI_PASSPHRASE,
                    wifiPassphrase
                )

                intent.putExtra(
                    NfcHandler.EXTRA_WIFI_5GHZ_BAND_SUPPORTED,
                    wifi5GHzBandSupported
                )

                app.applicationContext.startService(intent)

                when (transferMethod) {
                    TransferChannels.BLE -> setupBleHolder(deviceEngagement, bleServiceMode)
                    TransferChannels.WiFiAware -> setupWiFiHolder(deviceEngagement)
                    else -> throw IdentityCredentialException("Unsupported transfer method: $transferMethod")
                }

                onSuccess()
            } catch (ex: IdentityCredentialException) {
                Log.e(LOG_TAG, ex.message, ex)

                onError(ex)
            }
        }
    }

    private fun onSuccess() {
        nfcIconVisibility.set(View.VISIBLE)
        loadingVisibility.set(View.GONE)
    }

    private fun onError(e: Throwable) {
        Log.e(LOG_TAG, e.message ?: "", e)
        nfcIconVisibility.set(View.GONE)
        loadingVisibility.set(View.GONE)
    }

    fun isBleEnabled(isEnabled: Boolean) {
        if (isEnabled) {
            permissionRequestVisibility.set(View.GONE)
            btnEnableBtVisibility.set(View.GONE)
        } else {
            permissionRequestText.set(app.applicationContext.getString(R.string.ble_disabled_txt))
            permissionRequestVisibility.set(View.VISIBLE)
            btnEnableBtVisibility.set(View.VISIBLE)
        }
    }

    fun isWifiEnabled(isEnabled: Boolean) {
        if (isEnabled) {
            permissionRequestVisibility.set(View.GONE)
            btnEnableBtVisibility.set(View.GONE)
        } else {
            permissionRequestText.set(app.applicationContext.getString(R.string.wifi_disabled_txt))
            permissionRequestVisibility.set(View.VISIBLE)
            btnEnableBtVisibility.set(View.GONE)
        }
    }

    fun isPermissionGranted(isGranted: Boolean) {
        if (isGranted) {
            permissionRequestVisibility.set(View.GONE)
            btnReqPermissionVisibility.set(View.GONE)
        } else {
            permissionRequestText.set(app.applicationContext.getString(R.string.location_permission_txt))
            permissionRequestVisibility.set(View.VISIBLE)
            btnReqPermissionVisibility.set(View.VISIBLE)
        }
    }

    private fun setupBleHolder(deviceEngagement: DeviceEngagement, bleServiceMode: BleServiceMode?) {
        doAsync {
            bleServiceMode?.let {
                val coseKey = deviceEngagement.security?.coseKey

                coseKey?.let {
                    val builder = OfflineTransferManager.Builder()
                        .actAs(AppMode.HOLDER)
                        .setContext(app.applicationContext)
                        .setDataType(DataTypes.CBOR)
                        .setTransferChannel(TransferChannels.BLE)
                        .setBleServiceMode(bleServiceMode)
                        .setCoseKey(coseKey)

                    iofflineTransferHolder = builder.build()
                    iofflineTransferHolder?.let { holder ->
                        setupOfflineTransferHolder(holder, deviceEngagement)
                    }
                }
            }
        }
    }

    private fun setupWiFiHolder(deviceEngagement: DeviceEngagement) {
        doAsync {
            val coseKey = deviceEngagement.security?.coseKey

            coseKey?.let {
                val builder = OfflineTransferManager.Builder()
                    .actAs(AppMode.HOLDER)
                    .setContext(app.applicationContext)
                    .setDataType(DataTypes.CBOR)
                    .setTransferChannel(TransferChannels.WiFiAware)
                    .setCoseKey(coseKey)

                iofflineTransferHolder = builder.build()
                iofflineTransferHolder?.let { holder ->
                    setupOfflineTransferHolder(holder, deviceEngagement)
                }
            }
        }
    }

    private fun AnkoAsyncContext<ShareCredentialsNfcViewModel>.setupOfflineTransferHolder(
        holder: IofflineTransfer,
        deviceEngagement: DeviceEngagement
    ): Boolean {
        // Set Data to be sent to the Verifier over BLE
        val issuerAuthority = MockIssuerAuthority.getInstance(app.applicationContext)
        val icAPI = ProvisioningManager.getIdentityCredential(
            app.applicationContext,
            UserCredential.CREDENTIAL_NAME
        )

        icAPI?.let { ic ->
                holder.setupHolder(
                    UserCredential.CREDENTIAL_NAME, deviceEngagement.encode(), ic,
                    SharedPreferenceUtils(app.applicationContext).isBiometricAuthRequired(),
                    issuerAuthority
                )
        }
        return uiThread {
            iofflineTransferHolder?.data?.let { livedata ->
                liveDataMerger.addSource(livedata) {
                    liveDataMerger.value = it
                }
            }
        }
    }

    fun tearDownTransfer() {
        iofflineTransferHolder?.tearDown()
        liveDataMerger.removeSource(offlineTransferStatusLd)
    }
}
