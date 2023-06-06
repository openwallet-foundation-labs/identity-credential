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

package com.android.identity.credential;

import androidx.annotation.NonNull;

import com.android.identity.keystore.KeystoreEngine;
import com.android.identity.util.Timestamp;

/**
 * A set of utilities and helpers for working with credentials.
 */
public class CredentialUtil {
    /**
     * A helper for managing a set of authentication keys.
     *
     * <p>This helper provides a high-level way to manage authentication keys on a
     * {@link Credential}. Its goal is to always have a fixed number of authentication
     * keys available within the following constraints
     * <ul>
     *     <li>If a key is used more than {@code maxUsesPerKey} times, a replacement is generated.</li>
     *     <li>If a key expires within {@code minValidTimeMillis} milliseconds, a replacement is generated.</li>
     * </ul>
     * <p>This is all implemented on top of {@link Credential#createPendingAuthenticationKey(KeystoreEngine.CreateKeySettings, Credential.AuthenticationKey)}
     * and {@link Credential.PendingAuthenticationKey#certify(byte[], Timestamp, Timestamp)}.
     * The application should examine the return value and if positive, collect the
     * pending authentication keys via {@link Credential#getPendingAuthenticationKeys()},
     * send them to the issuer for certification, and then call
     * {@link Credential.PendingAuthenticationKey#certify(byte[], Timestamp, Timestamp)}
     * when receiving the certification from the issuer.
     *
     * <p>Authentication keys created using this helper will have application data set for
     * the with the key given by the {@code managedKeyDomain} and the helper will
     * only touch keys with this key set. This allows the application to manage multiple
     * sets of authentication keys for different purposes and with different strategies.
     *
     * @param credential the credential to manage authentication keys for.
     * @param createKeySettings the settings used to create new pending authentication keys.
     * @param managedKeyDomain the identifier to use for created authentication keys.
     * @param now the time right now, used for figuring out when existing should be replaced.
     * @param numAuthenticationKeys the number of authentication keys that should be kept.
     * @param maxUsesPerKey the maximum number of uses per key.
     * @param minValidTimeMillis requests a replacement for a key if it expires within this window.
     * @return the number of pending authentication keys created.
     */
    public static int managedAuthenticationKeyHelper(
            @NonNull Credential credential,
            @NonNull KeystoreEngine.CreateKeySettings createKeySettings,
            @NonNull String managedKeyDomain,
            @NonNull Timestamp now,
            int numAuthenticationKeys,
            int maxUsesPerKey,
            long minValidTimeMillis) {
        // First determine which of the existing keys need a replacement...
        int numKeysNotNeedingReplacement = 0;
        int numReplacementsGenerated = 0;
        for (Credential.AuthenticationKey authKey : credential.getAuthenticationKeys()) {
            boolean keyExceededUseCount = false;
            boolean keyBeyondExpirationDate = false;

            if (authKey.getApplicationData(managedKeyDomain) == null) {
                // not one of ours...
                continue;
            }

            if (authKey.getUsageCount() >= maxUsesPerKey) {
                keyExceededUseCount = true;
            }

            Timestamp expirationDate = Timestamp.ofEpochMilli(
                    authKey.getValidUntil().toEpochMilli() - minValidTimeMillis);
            if (now.toEpochMilli() > expirationDate.toEpochMilli()) {
                keyBeyondExpirationDate = true;
            }

            if (keyExceededUseCount || keyBeyondExpirationDate) {
                if (authKey.getReplacement() == null) {
                    Credential.PendingAuthenticationKey pendingKey =
                            credential.createPendingAuthenticationKey(createKeySettings, authKey);
                    pendingKey.setApplicationData(managedKeyDomain, new byte[0]);
                    numReplacementsGenerated++;
                    continue;
                }
            }
            numKeysNotNeedingReplacement++;
        }

        // It's possible we need to generate pending keys that aren't replacements
        int numNonReplacementsToGenerate = numAuthenticationKeys
                - numKeysNotNeedingReplacement
                - numReplacementsGenerated;
        if (numNonReplacementsToGenerate > 0) {
            for (int n = 0; n < numNonReplacementsToGenerate; n++) {
                Credential.PendingAuthenticationKey pendingKey =
                        credential.createPendingAuthenticationKey(createKeySettings, null);
                pendingKey.setApplicationData(managedKeyDomain, new byte[0]);
            }
        }
        return numReplacementsGenerated + numNonReplacementsToGenerate;
    }
}
