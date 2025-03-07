package org.multipaz.securearea.cloud

import org.multipaz.securearea.KeyUnlockData

/**
 * A class to provide information used for unlocking a Cloud Secure Area key.
 *
 * @param alias the alias of the key to unlock.
 */
class CloudKeyUnlockData(
    private val cloudSecureArea: CloudSecureArea,
    private val alias: String
) : KeyUnlockData {

    /**
     * The passphrase used to unlock the key or `null` if a passphrase isn't required.
     */
    var passphrase: String? = null
}