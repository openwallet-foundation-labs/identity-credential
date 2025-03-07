package org.multipaz_credential.wallet

/**
 * Configuration for the Wallet Application.
 *
 * This class contains configuration/settings intended to be overridden by downstream
 * consumers of this application through the flavor-system.
 *
 * This is the file with customized configuration/settings.
 */
object WalletApplicationConfiguration {
    /**
     * If `true`, the Settings screen will enable functionality to enable/disable developer mode.
     */
    const val DEVELOPER_MODE_TOGGLE_AVAILABLE = false

    /**
     * If `true`, the Settings screen will allow the user to configure the Wallet Server URL.
     */
    const val WALLET_SERVER_SETTING_AVAILABLE = false

    /**
     * The default Wallet Server URL.
     */
    const val WALLET_SERVER_DEFAULT_URL = "https://ws.example.com/server"

    /**
     * See the description in the "upstream version" of this class.
     * (upstream/com/android/identity_credential/wallet/WalletApplicationConfiguration.kt).
     *
     * In customized configuration, this is not used, unless [WALLET_SERVER_DEFAULT_URL] is set to
     * "dev:".
     */
    const val MIN_SERVER_DEFAULT_URL = "https://ws.example.com/server"

    /**
     * If `true`, the Settings screen will allow the user to configure the Cloud Secure Area URL.
     */
    const val CLOUD_SECURE_AREA_SETTING_AVAILABLE = false

    /**
     * The default Cloud Secure Area URL.
     */
    const val CLOUD_SECURE_AREA_DEFAULT_URL = "https://ws.example.com/server"
}