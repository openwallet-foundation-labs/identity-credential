package org.multipaz_credential.wallet

/**
 * Configuration for the Wallet Application.
 *
 * This class contains configuration/settings intended to be overridden by downstream
 * consumers of this application through the flavor-system.
 */
object WalletApplicationConfiguration {
    /**
     * If `true`, the Settings screen will enable functionality to enable/disable developer mode.
     */
    const val DEVELOPER_MODE_TOGGLE_AVAILABLE = true

    /**
     * If `true`, the Settings screen will allow the user to configure the Wallet Server URL
     * and Minimal Wallet Server URL.
     */
    const val WALLET_SERVER_SETTING_AVAILABLE = true

    /**
     * The default Wallet Server URL.
     */
    const val WALLET_SERVER_DEFAULT_URL = "dev:"

    /**
     * The minimal Wallet Server URL (only important when wallet server URL is set to "dev:").
     */
    const val MIN_SERVER_DEFAULT_URL = "http://localhost:8080/server"

    /**
     * If `true`, the Settings screen will allow the user to configure the Cloud Secure Area URL.
     */
    const val CLOUD_SECURE_AREA_SETTING_AVAILABLE = true

    /**
     * The default Cloud Secure Area URL.
     */
    const val CLOUD_SECURE_AREA_DEFAULT_URL = "http://localhost:8080/server/csa"
}