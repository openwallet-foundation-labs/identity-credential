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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import android.view.View
import androidx.biometric.BiometricPrompt
import androidx.databinding.ObservableField
import androidx.databinding.ObservableInt
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.viewModelScope
import androidx.security.identity.IdentityCredentialException
import com.ul.ims.gmdl.R
import com.ul.ims.gmdl.bleofflinetransfer.utils.BleUtils
import com.ul.ims.gmdl.cbordata.deviceEngagement.DeviceEngagement
import com.ul.ims.gmdl.cbordata.deviceEngagement.security.Security
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.BleTransferMethod
import com.ul.ims.gmdl.cbordata.deviceEngagement.transferMethods.WiFiAwareTransferMethod
import com.ul.ims.gmdl.cbordata.model.UserCredential.Companion.CREDENTIAL_NAME
import com.ul.ims.gmdl.issuerauthority.MockIssuerAuthority
import com.ul.ims.gmdl.nfcengagement.NfcHandler
import com.ul.ims.gmdl.offlineTransfer.OfflineTransferManager
import com.ul.ims.gmdl.offlinetransfer.appLayer.IofflineTransfer
import com.ul.ims.gmdl.offlinetransfer.config.AppMode
import com.ul.ims.gmdl.offlinetransfer.config.BleServiceMode
import com.ul.ims.gmdl.offlinetransfer.data.DataTypes
import com.ul.ims.gmdl.offlinetransfer.transportLayer.TransferChannels
import com.ul.ims.gmdl.offlinetransfer.utils.Resource
import com.ul.ims.gmdl.qrcode.MdlQrCode
import com.ul.ims.gmdl.security.sessionencryption.holder.HolderSessionManager
import com.ul.ims.gmdl.util.NfcTransferApduService
import com.ul.ims.gmdl.util.SharedPreferenceUtils
import com.ul.ims.gmdl.wifiofflinetransfer.utils.WifiUtils
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.uiThread


class ShareCredentialsViewModel(val app: Application) : AndroidViewModel(app) {

    companion object {
        val LOG_TAG = ShareCredentialsViewModel::class.java.simpleName
        const val BLE_VERSION = 1
        const val WIFI_AWARE_VERSION = 1
        const val NFC_VERSION = 1
        const val DE_VERSION = "1.0"
        const val CHIPER_SUITE_IDENT = 1
        //const val COSE_KEY_KTY = "tbd"
    }

    var deviceEngagementQr = ObservableField<Bitmap>()
    var qrcodeVisibility = ObservableInt()
    var permissionRequestVisibility = ObservableInt()
    var permissionRequestText = ObservableField<String>()
    var btnEnableBtVisibility = ObservableInt()
    var btnReqPermissionVisibility = ObservableInt()
    var loadingVisibility = ObservableInt()
    private var offlineTransferHolder: IofflineTransfer? = null
    private var liveDataMerger = MediatorLiveData<Resource<Any>>()
    private var deviceEngagement: DeviceEngagement? = null


    fun getOfflineTransferStatusLd(): LiveData<Resource<Any>> {
        return liveDataMerger
    }

    fun setUp() {
        permissionRequestVisibility.set(View.GONE)
        qrcodeVisibility.set(View.GONE)
        btnReqPermissionVisibility.set(View.GONE)
        btnEnableBtVisibility.set(View.GONE)
    }

    fun createHolderDe(transferMethod: TransferChannels) {
        createHolderDeAsync(transferMethod)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(QrcodeConsumer())
    }

    fun onUserConsent(userConsentMap: Map<String, Boolean>?) {
        viewModelScope.launch {
            offlineTransferHolder?.onUserConsent(userConsentMap)
        }
    }

    /**
     * This function is only called when used NFC Transfer, the user consent is needed before
     * to avoid interrupting NFC connection with user interaction
     *
     * @param userConsentMap User consent items
     */
    fun onUserPreConsent(userConsentMap: Map<String, Boolean>) {
        // Initiate Nfc Service Tag
        Log.d(LOG_TAG, "start NFC transport service")
        Intent(app.applicationContext, NfcTransferApduService::class.java).also { intent ->
            intent.putExtra(
                NfcTransferApduService.EXTRA_NFC_TRANSFER_DEVICE_ENGAGEMENT,
                deviceEngagement?.encode()
            )
            intent.putExtra(
                NfcTransferApduService.EXTRA_NFC_TRANSFER_USER_CONSENT,
                HashMap(userConsentMap)
            )
            app.applicationContext.startService(intent)
        }
    }

    fun onUserConsentCancel() {
        viewModelScope.launch {
            // null as a parameter here means that the user cancelled the consent dialog
            // and will trigger the mDL Holder to send the response as error 19
            offlineTransferHolder?.onUserConsent(null)
        }
    }

    fun getCryptoObject(): BiometricPrompt.CryptoObject? {
        return offlineTransferHolder?.getCryptoObject()
    }

    private fun createHolderDeAsync(transferMethod: TransferChannels): Single<Bitmap> {
        return Single.create { emitter ->
            viewModelScope.launch {
                try {
                    // Session Manager is used to Encrypt/Decrypt Messages
                    val sessionManager =
                        HolderSessionManager.getInstance(app.applicationContext, CREDENTIAL_NAME)

                    // Set up a new holder session so the Device Engagement COSE_Key is ephemeral to this engagement
                    sessionManager.initializeHolderSession()

                    // Check if there are device keys needing certification
                    sessionManager.checkDeviceKeysNeedingCertification(
                        MockIssuerAuthority.getInstance(app.applicationContext)
                    )

                    // Generate a CoseKey with an Ephemeral Key
                    val coseKey = sessionManager
                        .generateHolderCoseKey()
                        ?: throw IdentityCredentialException("Error generating Holder CoseKey")

                    val security = Security.Builder()
                        .setCoseKey(coseKey)
                        .setCipherSuiteIdent(CHIPER_SUITE_IDENT)
                        .build()

                    // Device engagement for QR Code
                    val deBuilderQR = DeviceEngagement.Builder()

                    deBuilderQR.version(DE_VERSION)
                    deBuilderQR.security(security)

                    // Device engagement for NFC
                    val deBuilderNFC = DeviceEngagement.Builder()

                    deBuilderNFC.version(DE_VERSION)
                    deBuilderNFC.security(security)

                    var hideQrCode = false

                    var bleServiceMode: BleServiceMode? = null
                    var blePeripheralMode = false
                    var bleCentralMode = false

                    var wifiPassphrase: String? = null
                    var wifi5GHzBandSupported = false

                    when (transferMethod) {
                        TransferChannels.BLE -> {
                            blePeripheralMode =
                                BleUtils.isPeripheralSupported(app.applicationContext)
                            bleCentralMode = BleUtils.isCentralModeSupported(app.applicationContext)

                            bleServiceMode = getBleServiceMode()
                            deBuilderQR.transferMethods(
                                BleTransferMethod(
                                    DeviceEngagement.TRANSFER_TYPE_BLE, BLE_VERSION,
                                    BleTransferMethod.BleIdentification(
                                        blePeripheralMode, bleCentralMode, null, null
                                    )
                                )
                            )
                        }
                        TransferChannels.WiFiAware -> {
                            wifiPassphrase = WifiUtils.getPassphrase(coseKey.getPublicKey().encoded)
                            wifi5GHzBandSupported =
                                WifiUtils.getWifiManager(app.applicationContext)?.is5GHzBandSupported == true

                            deBuilderQR.transferMethods(
                                WiFiAwareTransferMethod(
                                    DeviceEngagement.TRANSFER_TYPE_WIFI_AWARE,
                                    WIFI_AWARE_VERSION
                                )
                            )
                        }
                        TransferChannels.NFC -> {
                            deBuilderQR.transferMethods(
                                WiFiAwareTransferMethod(
                                    DeviceEngagement.TRANSFER_TYPE_NFC,
                                    NFC_VERSION
                                )
                            )
                            hideQrCode = true
                        }
                        else -> throw UnsupportedOperationException("Unsupported transfer method")
                    }

                    val deQr = deBuilderQR.build()
                    // Build QR Code
                    val mdlQrCode = MdlQrCode.Builder()
                        .setDeviceEngagement(deQr)
                        .build()

                    val deNfc = deBuilderNFC.build()

                    deviceEngagement = deNfc
                    // Build NFC service

                    val intent = Intent(app.applicationContext, NfcHandler::class.java)
                    intent.putExtra(
                        NfcHandler.EXTRA_DEVICE_ENGAGEMENT_PAYLOAD, deNfc.encode()
                    )
                    intent.putExtra(
                        NfcHandler.EXTRA_TRANSFER_METHOD, transferMethod
                    )
                    intent.putExtra(
                        NfcHandler.EXTRA_BLE_PERIPHERAL_SERVER_MODE, blePeripheralMode
                    )
                    intent.putExtra(
                        NfcHandler.EXTRA_BLE_CENTRAL_CLIENT_MODE, bleCentralMode
                    )
                    intent.putExtra(
                        NfcHandler.EXTRA_WIFI_PASSPHRASE, wifiPassphrase
                    )
                    intent.putExtra(
                        NfcHandler.EXTRA_WIFI_5GHZ_BAND_SUPPORTED, wifi5GHzBandSupported
                    )

                    // Setup Holder works the same for both engagement method
                    setupHolder(deQr, bleServiceMode)

                    // Start NFC service
                    app.applicationContext.startService(intent)

                    if (hideQrCode) {
                        app.getDrawable(R.drawable.ic_nfc)?.let { drawable ->
                            val bitmap = Bitmap.createBitmap(
                                app.resources.getDimension(R.dimen.qr_width).toInt(),
                                app.resources.getDimension(R.dimen.qr_height).toInt(),
                                Bitmap.Config.ARGB_8888
                            )
                            val canvas = Canvas(bitmap)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                            emitter.onSuccess(bitmap)

                        }
                    } else {
                        // Show QR Code
                        mdlQrCode.getQrCode(
                            app.resources.getDimension(R.dimen.qr_width).toInt(),
                            app.resources.getDimension(R.dimen.qr_height).toInt()
                        )?.let {
                            emitter.onSuccess(it)
                        }
                    }
                } catch (ex: IdentityCredentialException) {
                    Log.e(LOG_TAG, ex.message, ex)

                    emitter.onError(ex)
                }
            }
        }
    }

    private fun getBleServiceMode(): BleServiceMode {
        val blePeripheralMode =
            BleUtils.isPeripheralSupported(app.applicationContext)
        val bleCentralMode = BleUtils.isCentralModeSupported(app.applicationContext)

        // both modes are supported
        return if (blePeripheralMode && bleCentralMode) {
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

    private inner class QrcodeConsumer : SingleObserver<Bitmap> {
        override fun onSuccess(t: Bitmap) {
            deviceEngagementQr.set(t)
            qrcodeVisibility.set(View.VISIBLE)
            loadingVisibility.set(View.GONE)
        }

        override fun onSubscribe(d: Disposable) {
            qrcodeVisibility.set(View.GONE)
            loadingVisibility.set(View.VISIBLE)
        }

        override fun onError(e: Throwable) {
            Log.e(LOG_TAG, "${e.message}", e)
            qrcodeVisibility.set(View.GONE)
            loadingVisibility.set(View.GONE)
        }
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

    private fun setupHolder(
        deviceEngagement: DeviceEngagement,
        bleServiceMode: BleServiceMode?
    ) {
        doAsync {
            val coseKey = deviceEngagement.security?.coseKey

            coseKey?.let { cKey ->
                val builder = OfflineTransferManager.Builder()
                    .actAs(AppMode.HOLDER)
                    .setContext(app.applicationContext)
                    .setDataType(DataTypes.CBOR)
                    .setCoseKey(cKey)

                deviceEngagement.getBLETransferMethod()?.let {
                    builder.setTransferChannel(TransferChannels.BLE)
                    bleServiceMode?.let {
                        builder.setBleServiceMode(it)
                    }
                }
                deviceEngagement.getWiFiAwareTransferMethod()?.let {
                    builder.setTransferChannel(TransferChannels.WiFiAware)
                }
                deviceEngagement.getNfcTransferMethod()?.let {
                    builder.setTransferChannel(TransferChannels.NFC)
                }

                val issuerAuthority =
                    MockIssuerAuthority.getInstance(app.applicationContext)

                offlineTransferHolder = builder.build()
                offlineTransferHolder?.let { holder ->
                        holder.setupHolder(
                            CREDENTIAL_NAME, deviceEngagement.encode(),
                            SharedPreferenceUtils(app.applicationContext).isBiometricAuthRequired(),
                            issuerAuthority
                        )
                    uiThread {
                        offlineTransferHolder?.data?.let { livedata ->
                            liveDataMerger.addSource(livedata) {
                                liveDataMerger.value = it
                            }
                        }
                    }
                }
            } ?: kotlin.run {
                Log.e(LOG_TAG, "CoseKey in the Device Engagement is null")
            }
        }
    }

    fun setupHolderNfc(transferMethod: TransferChannels) {
        tearDownTransfer()
        doAsync {
            val coseKey = deviceEngagement?.security?.coseKey

            coseKey?.let { cKey ->
                val builder = OfflineTransferManager.Builder()
                    .actAs(AppMode.HOLDER)
                    .setContext(app.applicationContext)
                    .setDataType(DataTypes.CBOR)
                    .setCoseKey(cKey)
                    .setTransferChannel(transferMethod)

                if (transferMethod == TransferChannels.BLE) {
                    builder.setBleServiceMode(getBleServiceMode())
                }

                val issuerAuthority =
                    MockIssuerAuthority.getInstance(app.applicationContext)

                offlineTransferHolder = builder.build()
                offlineTransferHolder?.let { holder ->
                        deviceEngagement?.let { de ->
                            holder.setupHolder(
                                CREDENTIAL_NAME, de.encode(),
                                SharedPreferenceUtils(app.applicationContext).isBiometricAuthRequired(),
                                issuerAuthority
                            )
                    }
                    uiThread {
                        offlineTransferHolder?.data?.let { livedata ->
                            liveDataMerger.addSource(livedata) {
                                liveDataMerger.value = it
                            }
                        }
                    }
                }
            } ?: kotlin.run {
                Log.e(LOG_TAG, "CoseKey in the Device Engagement is null")
            }
        }
    }

    fun tearDownTransfer() {
        offlineTransferHolder?.data?.let { livedata ->
            liveDataMerger.removeSource(livedata)
        }
        offlineTransferHolder?.tearDown()
    }

}
