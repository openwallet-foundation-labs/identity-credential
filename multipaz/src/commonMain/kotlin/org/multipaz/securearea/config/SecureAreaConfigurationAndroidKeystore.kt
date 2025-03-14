package org.multipaz.securearea.config

/** Secure area configuration for [AndroidKeystoreSecureArea] */
class SecureAreaConfigurationAndroidKeystore(
    algorithm: String,
    /** true to use StrongBox, false otherwise */
    val useStrongBox: Boolean,
    /** whether to require user authentication */
    val userAuthenticationRequired: Boolean,
    /** User authentication timeout in milliseconds or 0 to require authentication on every use. */
    val userAuthenticationTimeoutMillis: Long,
    /** number like in [UserAuthenticationType.encodeSet] */
    val userAuthenticationTypes: Long,
) : SecureAreaConfiguration(algorithm)
