package com.android.identity.testapp

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A model for settings for samples/testapp.
 *
 * TODO: Use [Storage] to back this model, for persistence.
 *
 * TODO: Port [CloudSecureAreaScreen] and [ProvisioningTestScreen] to use this.
 */
class TestAppSettingsModel {
    companion object {
        private const val TAG = "TestAppSettingsModel"
    }

    val presentmentBleCentralClientModeEnabled = MutableStateFlow<Boolean>(true)
    val presentmentBlePeripheralServerModeEnabled = MutableStateFlow<Boolean>(false)
    val presentmentNfcDataTransferEnabled = MutableStateFlow<Boolean>(false)
    val presentmentBleL2CapEnabled = MutableStateFlow<Boolean>(false)
    val presentmentUseNegotiatedHandover = MutableStateFlow<Boolean>(true)
    val presentmentAllowMultipleRequests = MutableStateFlow<Boolean>(false)
    val presentmentNegotiatedHandoverPreferredOrder = MutableStateFlow<List<String>>(listOf(
        "ble:central_client_mode:",
        "ble:peripheral_server_mode:",
        "nfc:"
    ))
    val presentmentShowConsentPrompt = MutableStateFlow<Boolean>(true)

    fun resetPresentmentSettings() {
        presentmentBleCentralClientModeEnabled.value = true
        presentmentBlePeripheralServerModeEnabled.value = false
        presentmentNfcDataTransferEnabled.value = false
        presentmentBleL2CapEnabled.value = true
        presentmentUseNegotiatedHandover.value = true
        presentmentAllowMultipleRequests.value = false
        presentmentNegotiatedHandoverPreferredOrder.value = listOf(
            "ble:central_client_mode:",
            "ble:peripheral_server_mode:",
            "nfc:"
        )
        presentmentShowConsentPrompt.value = true
    }

    val readerBleCentralClientModeEnabled = MutableStateFlow<Boolean>(true)
    val readerBlePeripheralServerModeEnabled = MutableStateFlow<Boolean>(true)
    val readerNfcDataTransferEnabled = MutableStateFlow<Boolean>(true)
    val readerBleL2CapEnabled = MutableStateFlow<Boolean>(true)
    val readerAutomaticallySelectTransport = MutableStateFlow<Boolean>(false)
    val readerAllowMultipleRequests = MutableStateFlow<Boolean>(false)

    fun resetReaderSettings() {
        readerBleCentralClientModeEnabled.value = true
        readerBlePeripheralServerModeEnabled.value = true
        readerNfcDataTransferEnabled.value = true
        readerBleL2CapEnabled.value = true
        readerAutomaticallySelectTransport.value = false
        readerAllowMultipleRequests.value = false
    }

}