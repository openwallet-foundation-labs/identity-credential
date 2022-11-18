/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.identity;


import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

class KeystoreIdentityCredentialStore extends IdentityCredentialStore {

    private static final String TAG = "KSICStore"; // limit to <= 23 chars

    private Context mContext;
    private final File mStorageDirectory;

    private KeystoreIdentityCredentialStore(@NonNull Context context,
                                            @NonNull File storageDirectory) {
        mContext = context;
        mStorageDirectory = storageDirectory;
    }

    @SuppressWarnings("deprecation")
    public static @NonNull IdentityCredentialStore getInstance(@NonNull Context context,
                                                               @NonNull File storageDirectory) {
        return new KeystoreIdentityCredentialStore(context, storageDirectory);
    }

    @SuppressWarnings("deprecation")
    public static @NonNull IdentityCredentialStore getDirectAccessInstance(@NonNull
            Context context) {
        throw new RuntimeException("Direct-access IdentityCredential is not supported");
    }

    @SuppressWarnings("deprecation")
    public static boolean isDirectAccessSupported(@NonNull Context context) {
        return false;
    }

    @Override
    public int getFeatureVersion() {
        // We implement the latest feature version.
        return FEATURE_VERSION_202301;
    }

    @Override
    public @NonNull @ImplementationType String getImplementationType () {
        return IMPLEMENTATION_TYPE_KEYSTORE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public @NonNull String[] getSupportedDocTypes() {
        // We'll support any doc type.
        return new String[] {};
    }

    @Override
    public @NonNull WritableIdentityCredential createCredential(
            @NonNull String credentialName,
            @NonNull String docType) throws AlreadyPersonalizedException,
            DocTypeNotSupportedException {
        return new KeystoreWritableIdentityCredential(mContext, mStorageDirectory, credentialName, docType);
    }

    @Override
    public @Nullable IdentityCredential getCredentialByName(
            @NonNull String credentialName,
            @Ciphersuite int cipherSuite) throws CipherSuiteNotSupportedException {
        KeystoreIdentityCredential credential =
                new KeystoreIdentityCredential(mContext, mStorageDirectory, credentialName, cipherSuite, null);
        if (credential.loadData()) {
            return credential;
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    @Override
    public @Nullable byte[] deleteCredentialByName(@NonNull String credentialName) {
        return KeystoreIdentityCredential.delete(mContext, mStorageDirectory, credentialName);
    }

    @Override
    public @NonNull PresentationSession createPresentationSession(@Ciphersuite int cipherSuite)
            throws CipherSuiteNotSupportedException {
        return new KeystorePresentationSession(mContext, mStorageDirectory, cipherSuite);
    }
}
