package org.multipaz.securearea.software

import org.multipaz.securearea.KeyUnlockData

/**
 * A class that can be used to provide information used for unlocking a key.
 *
 * Currently only passphrases are supported.
 *
 * @param passphrase the passphrase.
 */
class SoftwareKeyUnlockData(val passphrase: String) : KeyUnlockData