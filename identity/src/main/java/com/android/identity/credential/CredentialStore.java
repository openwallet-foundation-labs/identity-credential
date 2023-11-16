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
import androidx.annotation.Nullable;

import com.android.identity.securearea.SecureArea;
import com.android.identity.securearea.SecureAreaRepository;
import com.android.identity.storage.StorageEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Class for storing real-world identity credentials.
 *
 * <p>This class is designed for storing real-world identity credentials such as
 * Mobile Driving Licenses (mDL) as specified in ISO/IEC 18013-5:2021. It is however
 * not tied to that specific credential shape and is designed to hold any kind of
 * credential, regardless of shape, presentation-, or issuance-protocol used.
 *
 * <p>This code relies on a Secure Area for keys and this dependency is abstracted
 * by the {@link SecureArea} interface and allows the use of different {@link SecureArea}
 * implementations for <em>Credential Key</em> and <em>Authentication Keys</em>) used
 * in the credentials stored in the Credential Store.
 *
 * <p>It is guaranteed that calls to {@link #createCredential(String, SecureArea, SecureArea.CreateKeySettings)},
 * {@link #createCredentialWithExistingKey(String, SecureArea, SecureArea.CreateKeySettings, String)},
 * and {@link #lookupCredential(String)} will return the same {@link Credential} instance
 * if passed the same {@code name}.
 *
 * <p>For more details about credentials stored in a {@link CredentialStore} see the
 * {@link Credential} class.
 */
public class CredentialStore {
    private final StorageEngine mStorageEngine;
    private final SecureAreaRepository mSecureAreaRepository;

    // Use a cache so the same instance is returned by multiple lookupCredential() calls.
    private final HashMap<String, Credential> mCredentialCache = new LinkedHashMap<>();

    /**
     * Creates a new credential store.
     *
     * @param storageEngine the {@link StorageEngine} to use for storing/retrieving credentials.
     * @param secureAreaRepository the repository of configured {@link SecureArea} that can
     *                                 be used.
     */
    public CredentialStore(@NonNull StorageEngine storageEngine,
                           @NonNull SecureAreaRepository secureAreaRepository) {
        mStorageEngine = storageEngine;
        mSecureAreaRepository = secureAreaRepository;
    }

    /**
     * Creates a new credential.
     *
     * <p>If a credential with the given name already exists, it will be overwritten by the
     * newly created credential.
     *
     * @param name name of the credential.
     * @param secureArea the secure area to use for <em>CredentialKey</em>.
     * @param credentialKeySettings the settings to use for creating <em>CredentialKey</em>.
     * @return A newly created credential.
     */
    public @NonNull Credential createCredential(@NonNull String name,
                                                @NonNull SecureArea secureArea,
                                                @NonNull SecureArea.CreateKeySettings credentialKeySettings) {
        Credential result = Credential.create(mStorageEngine,
                mSecureAreaRepository,
                name,
                secureArea,
                credentialKeySettings);
        mCredentialCache.put(name, result);
        return result;
    }

    /**
     * Creates a new credential using a key which already exists in some keystore.
     *
     * <p>If a credential with the given name already exists, it will be overwritten by the
     * newly created credential.
     *
     * @param name name of the credential.
     * @param secureArea the secure area to use for CredentialKey.
     * @param credentialKeySettings the settings to use for creating CredentialKey.
     * @param existingKeyAlias the alias of the existing key.
     * @return A newly created credential.
     */
    public @NonNull Credential createCredentialWithExistingKey(
            @NonNull String name,
            @NonNull SecureArea secureArea,
            @NonNull SecureArea.CreateKeySettings credentialKeySettings,
            @NonNull String existingKeyAlias) {
        Credential result = Credential.createWithExistingKey(mStorageEngine,
                mSecureAreaRepository,
                name,
                secureArea,
                credentialKeySettings,
                existingKeyAlias);
        mCredentialCache.put(name, result);
        return result;
    }

    /**
     * Looks up a previously created credential.
     *
     * @param name the name of the credential.
     * @return the credential or {@code null} if not found.
     */
    public @Nullable Credential lookupCredential(@NonNull String name) {
        Credential result = mCredentialCache.get(name);
        if (result != null) {
            return result;
        }
        result = Credential.lookup(mStorageEngine, mSecureAreaRepository, name);
        mCredentialCache.put(name, result);
        return result;
    }

    /**
     * Lists all credentials in the store.
     *
     * @return list of all credential names in the store.
     */
    public @NonNull List<String> listCredentials() {
        ArrayList<String> ret = new ArrayList<>();
        for (String name : mStorageEngine.enumerate()) {
            if (name.startsWith(Credential.CREDENTIAL_PREFIX)) {
                ret.add(name.substring(Credential.CREDENTIAL_PREFIX.length()));
            }
        }
        return ret;
    }

    /**
     * Deletes a credential.
     *
     * <p>If the credential doesn't exist this does nothing.
     *
     * @param name the name of the credential.
     */
    public void deleteCredential(@NonNull String name) {
        Credential credential = lookupCredential(name);
        if (credential == null) {
            return;
        }
        mCredentialCache.remove(name);
        credential.deleteCredential();
    }
}
