/*
 * Copyright 2023 The Android Open Source Project
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
package com.android.identity.securearea

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.EcPublicKey

/**
 * An interface to a Secure Area.
 *
 * This interface exists to abstract the underlying secure area used
 * used for creation of key material and other security objects related
 * to credentials.
 */
interface SecureArea {
    /**
     * A stable identifier for the Secure Area.
     *
     * This is typically just the class name but for secure areas allowing
     * multiple instances, this could differ.
     */
    val identifier: String

    /**
     * A string suitable to display to the end user, for identifying the Secure Area instance.
     */
    val displayName: String

    /**
     * Creates an new key.
     *
     * This creates an EC key-pair where the private part of the key never
     * is exposed to the user of this interface.
     *
     * The key is attested to and the generated certificate-chain depends on
     * the specific Secure Area used and the only guarantee is that
     * the leaf certificate contains the public key of the created key. Usually
     * a list of certificates chaining up to a well-known root is returned along
     * with platform specific information in the leaf certificate. The attestation
     * for the created key can be obtained via [KeyInfo.getAttestation].
     *
     * If an existing key with the given alias already exists it will be
     * replaced by the new key.
     *
     * @param alias             A unique string to identify the newly created key.
     * @param createKeySettings A [CreateKeySettings] object.
     * @throws IllegalArgumentException if the underlying Secure Area Implementation
     * does not support the requested creation settings, for example the EC curve to use.
     */
    fun createKey(alias: String, createKeySettings: CreateKeySettings)

    /**
     * Deletes a previously created key.
     *
     * If the key to delete doesn't exist, this is a no-op.
     *
     * @param alias The alias of the EC key to delete.
     */
    fun deleteKey(alias: String)

    /**
     * Signs data with a key.
     *
     * If the key needs unlocking before use (for example user authentication
     * in any shape or form) and `keyUnlockData` isn't set or doesn't contain
     * what's needed, [KeyLockedException] is thrown.
     *
     * The signature is DER encoded except for curve Ed25519 and Ed448 where it's just
     * the raw R and S values.
     *
     * @param alias The alias of the EC key to sign with.
     * @param signatureAlgorithm the signature algorithm to use.
     * @param dataToSign the data to sign.
     * @param keyUnlockData data used to unlock the key or null.
     * @return the signature.
     * @throws IllegalArgumentException if there is no key with the given alias
     * or the key wasn't created with purpose [KeyPurpose.SIGN].
     * @throws IllegalArgumentException if the signature algorithm isn’t compatible with the key.
     * @throws KeyLockedException if the key needs unlocking.
     */
    @Throws(KeyLockedException::class)
    fun sign(
        alias: String,
        signatureAlgorithm: Algorithm,
        dataToSign: ByteArray,
        keyUnlockData: KeyUnlockData?
    ): ByteArray

    /**
     * Performs Key Agreement.
     *
     * If the key needs unlocking before use (for example user authentication
     * in any shape or form) and `keyUnlockData` isn't set or doesn't contain
     * what's needed, [KeyLockedException] is thrown.
     *
     * @param alias the alias of the EC key to use.
     * @param otherKey The public EC key from the other party
     * @param keyUnlockData data used to unlock the key or `null`.
     * @return The shared secret.
     * @throws IllegalArgumentException if the other key isn't the same curve.
     * @throws IllegalArgumentException if there is no key with the given alias
     * or the key wasn't created with purpose [KeyPurpose.AGREE_KEY].
     * @throws KeyLockedException if the key needs unlocking.
     */
    @Throws(KeyLockedException::class)
    fun keyAgreement(
        alias: String,
        otherKey: EcPublicKey,
        keyUnlockData: KeyUnlockData?
    ): ByteArray

    /**
     * Gets information about a key.
     *
     * @param alias the alias of the EC key to use.
     * @return a [KeyInfo] object.
     * @throws IllegalArgumentException if there is no key with the given alias.
     */
    fun getKeyInfo(alias: String): KeyInfo
}