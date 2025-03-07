package org.multipaz.securearea.config

class SecureAreaConfigurationCloud(
    purposes: Long,
    curve: Int,
    /** Cloud secure area id */
    val cloudSecureAreaId: String,
    /** whether to require user authentication */
    val userAuthenticationRequired: Boolean,
    val useStrongBox: Boolean,
    /** User authentication timeout in milliseconds or 0 to require authentication on every use. */
    val userAuthenticationTimeoutMillis: Long,
    /** a number like in [UserAuthenticationType.encodeSet] */
    val userAuthenticationTypes: Long,
    /** whether to require passphrase authentication */
    val passphraseRequired: Boolean
): SecureAreaConfiguration(purposes, curve)