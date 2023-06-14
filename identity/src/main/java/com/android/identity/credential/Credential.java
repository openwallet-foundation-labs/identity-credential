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

import com.android.identity.internal.Util;
import com.android.identity.keystore.KeystoreEngine;
import com.android.identity.keystore.KeystoreEngineRepository;
import com.android.identity.storage.StorageEngine;
import com.android.identity.util.Logger;
import com.android.identity.util.Timestamp;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnicodeString;

/**
 * This class represents a credential created in {@link CredentialStore}.
 *
 * <p>Credentials in this store are identified by a name which must be unique
 * per credential. For each credential an asymmetric key-pair called <em>CredentialKey</em>
 * is generated and this key-pair is intended to be the primary identifier for the credential
 * and used for communication with the credential issuing authority (IA). In particular, the
 * IA can examine the attestation on <em>CredentialKey</em> to verify that the device is
 * in a known good state (e.g. verified boot is enabled, the OS is at a sufficiently recent
 * patch level, it's communicating with the expected Android application, etc.) before
 * deciding to issue a credential to the device.
 *
 * <p>Data can be stored in credentials using the {@link NameSpacedData} abstraction
 * which contains a set of key/value pairs, organized by namespace. This can be
 * set and get using {@link #setNameSpacedData(NameSpacedData)} and
 * {@link #getNameSpacedData()}. Typically this is sent by the issuer at
 * credential creation and when data in the credential has changed. Additionally,
 * the application can set/get any data of data it wants using
 * {@link #setApplicationData(String, byte[])} and {@link #getApplicationData(String)}.
 * Both kinds of data is persisted for the life-time of the application.
 *
 * <p>Each credential may have a number of <em>Authentication Keys</em>
 * associated with it. These keys are intended to be used in ways specified by the
 * underlying credential shape but the general idea is that they are created on
 * the device and then sent to the issuer for certification. The issuer then returns
 * some shape-specific data related to the key. For Mobile Driving License and MDOCs according
 * to <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5:2021</a>
 * the authentication key plays the role of <em>DeviceKey</em> and the issuer-signed
 * data includes the <em>Mobile Security Object</em> which includes the authentication
 * key and is signed by the issuer. This is used for anti-cloning and to return data signed
 * by the device. The way it works in this API is that the application can use
 * {@link #createPendingAuthenticationKey(KeystoreEngine.CreateKeySettings, AuthenticationKey)}
 * to get a {@link PendingAuthenticationKey}. With this in hand, the application can use
 * {@link PendingAuthenticationKey#getAttestation()} and send the attestation
 * to the issuer for certification. The issuer will then craft credential-shape
 * specific data (for ISO/IEC 18013-5:2021 it will be a signed MSO which references
 * the public part of the newly created authentication key) and send it back
 * to the app. The application can then call
 * {@link PendingAuthenticationKey#certify(byte[], Timestamp, Timestamp)} to
 * upgrade the {@link PendingAuthenticationKey} to a {@link AuthenticationKey}.
 *
 * <p>At credential presentation time the application first receives the request
 * from a remote reader using a specific credential presentation protocol, such
 * as ISO/IEC 18013-5:2021. The details of the credential-specific request includes
 * enough information (for example, the <em>DocType</em> if using ISO/IEC 18013-5:2021)
 * for the application to locate a suitable {@link Credential} from a {@link CredentialStore}.
 * See {@link CredentialRequest} for more information about how to generate the response for
 * the remote reader given a {@link Credential} instance.
 *
 * <p>There is nothing mDL/MDOC specific about this type, it can be used for any kind
 * of credential regardless of shape, presentation, or issuance protocol used.
 */
public class Credential {
    private static final String TAG = "Credential";
    static final String CREDENTIAL_PREFIX = "IC_Credential_";

    static final String CREDENTIAL_KEY_ALIAS_PREFIX = "IC_CredentialKey_";

    static final String AUTHENTICATION_KEY_ALIAS_PREFIX = "IC_AuthenticationKey_";

    private final StorageEngine mStorageEngine;
    private final KeystoreEngineRepository mKeystoreEngineRepository;
    private String mName;
    private String mCredentialKeyAlias;

    private NameSpacedData mNameSpacedData = new NameSpacedData.Builder().build();
    private KeystoreEngine mKeystoreEngine;
    private LinkedHashMap<String, byte[]> mApplicationData = new LinkedHashMap<>();

    private List<PendingAuthenticationKey> mPendingAuthenticationKeys = new ArrayList<>();
    private List<AuthenticationKey> mAuthenticationKeys = new ArrayList<>();

    private long mAuthenticationKeyCounter;

    private Credential(@NonNull StorageEngine storageEngine,
                       @NonNull KeystoreEngineRepository keystoreEngineRepository) {
        mStorageEngine = storageEngine;
        mKeystoreEngineRepository = keystoreEngineRepository;
    }

    // Called by CredentialStore.createCredential().
    static Credential create(@NonNull StorageEngine storageEngine,
                             @NonNull KeystoreEngineRepository keystoreEngineRepository,
                             @NonNull String name,
                             @NonNull KeystoreEngine.CreateKeySettings credentialKeySettings) {

        Credential credential = new Credential(storageEngine, keystoreEngineRepository);
        credential.mName = name;
        String keystoreEngineClassName = credentialKeySettings.getKeystoreEngineClass().getName();
        credential.mKeystoreEngine = keystoreEngineRepository.getImplementation(keystoreEngineClassName);
        if (credential.mKeystoreEngine == null) {
            throw new IllegalStateException("No KeystoreEngine with name " + keystoreEngineClassName);
        }
        credential.mCredentialKeyAlias = CREDENTIAL_KEY_ALIAS_PREFIX + name;

        credential.mKeystoreEngine.createKey(credential.mCredentialKeyAlias, credentialKeySettings);

        credential.saveCredential();

        return credential;
    }

    private void saveCredential() {
        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> map = builder.addMap();
        map.put("keystoreImplementationClassName", mKeystoreEngine.getClass().getName());
        map.put("credentialKeyAlias", mCredentialKeyAlias);
        map.put(new UnicodeString("nameSpacedData"), mNameSpacedData.toCbor());

        MapBuilder<MapBuilder<CborBuilder>> applicationDataBuilder =
                map.putMap("applicationData");
        for (String key : mApplicationData.keySet()) {
            byte[] value = mApplicationData.get(key);
            applicationDataBuilder.put(key, value);
        }

        ArrayBuilder<MapBuilder<CborBuilder>> pendingAuthenticationKeysArrayBuilder =
                map.putArray("pendingAuthenticationKeys");
        for (PendingAuthenticationKey pendingAuthenticationKey : mPendingAuthenticationKeys) {
            pendingAuthenticationKeysArrayBuilder.add(pendingAuthenticationKey.toCbor());
        }

        ArrayBuilder<MapBuilder<CborBuilder>> authenticationKeysArrayBuilder =
                map.putArray("authenticationKeys");
        for (AuthenticationKey authenticationKey : mAuthenticationKeys) {
            authenticationKeysArrayBuilder.add(authenticationKey.toCbor());
        }

        map.put("authenticationKeyCounter", mAuthenticationKeyCounter);

        mStorageEngine.put(CREDENTIAL_PREFIX + mName, Util.cborEncode(builder.build().get(0)));
    }

    // Called by CredentialStore.lookupCredential().
    static Credential lookup(@NonNull StorageEngine storageEngine,
                             @NonNull KeystoreEngineRepository keystoreEngineRepository,
                             @NonNull String name) {
        Credential credential = new Credential(storageEngine, keystoreEngineRepository);
        credential.mName = name;
        if (!credential.loadCredential(keystoreEngineRepository)) {
            return null;
        }
        return credential;
    }

    private boolean loadCredential(@NonNull KeystoreEngineRepository keystoreEngineRepository) {
        byte[] data = mStorageEngine.get(CREDENTIAL_PREFIX + mName);
        if (data == null) {
            return false;
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        List<DataItem> dataItems;
        try {
            dataItems = new CborDecoder(bais).decode();
        } catch (CborException e) {
            throw new IllegalStateException("Error decoded CBOR", e);
        }
        if (dataItems.size() != 1) {
            throw new IllegalStateException("Expected 1 item, found " + dataItems.size());
        }
        if (!(dataItems.get(0) instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalStateException("Item is not a map");
        }
        co.nstant.in.cbor.model.Map map = (co.nstant.in.cbor.model.Map) dataItems.get(0);

        String keystoreImplementationClassName =
                Util.cborMapExtractString(map, "keystoreImplementationClassName");
        mKeystoreEngine = keystoreEngineRepository.getImplementation(keystoreImplementationClassName);

        mCredentialKeyAlias = Util.cborMapExtractString(map, "credentialKeyAlias");

        DataItem nameSpacedDataItem = map.get(new UnicodeString("nameSpacedData"));
        if (nameSpacedDataItem == null) {
            throw new IllegalStateException("nameSpacedData not found");
        }
        mNameSpacedData = NameSpacedData.fromCbor(nameSpacedDataItem);

        mApplicationData = new LinkedHashMap<>();
        DataItem applicationDataDataItem = map.get(new UnicodeString("applicationData"));
        if (!(applicationDataDataItem instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalStateException("applicationData not found or not map");
        }
        for (DataItem keyItem : ((co.nstant.in.cbor.model.Map) applicationDataDataItem).getKeys()) {
            String key = ((UnicodeString) keyItem).getString();
            byte[] value = Util.cborMapExtractByteString(applicationDataDataItem, key);
            mApplicationData.put(key, value);
        }

        mPendingAuthenticationKeys = new ArrayList<>();
        DataItem pendingAuthenticationKeysDataItem = map.get(new UnicodeString("pendingAuthenticationKeys"));
        if (!(pendingAuthenticationKeysDataItem instanceof Array)) {
            throw new IllegalStateException("pendingAuthenticationKeys not found or not array");
        }
        for (DataItem item : ((Array) pendingAuthenticationKeysDataItem).getDataItems()) {
            mPendingAuthenticationKeys.add(PendingAuthenticationKey.fromCbor(item, this));
        }

        mAuthenticationKeys = new ArrayList<>();
        DataItem authenticationKeysDataItem = map.get(new UnicodeString("authenticationKeys"));
        if (!(authenticationKeysDataItem instanceof Array)) {
            throw new IllegalStateException("authenticationKeys not found or not array");
        }
        for (DataItem item : ((Array) authenticationKeysDataItem).getDataItems()) {
            mAuthenticationKeys.add(AuthenticationKey.fromCbor(item, this));
        }

        mAuthenticationKeyCounter = Util.cborMapExtractNumber(map, "authenticationKeyCounter");

        return true;
    }

    void deleteCredential() {
        // Need to use shallow copies because delete() modifies the list.
        for (PendingAuthenticationKey key : new ArrayList<>(mPendingAuthenticationKeys)) {
            key.delete();
        }
        for (AuthenticationKey key : new ArrayList<>(mAuthenticationKeys)) {
            key.delete();
        }
        mKeystoreEngine.deleteKey(mCredentialKeyAlias);
        mStorageEngine.delete(CREDENTIAL_PREFIX + mName);
    }

    /**
     * Gets the name of the credential.
     *
     * @return The name chosen at credential creation time.
     */
    public @NonNull String getName() {
        return mName;
    }

    /**
     * Gets the X.509 certificate chain for <em>CredentialKey</em>.
     *
     * <p>The application can use this to prove properties about the device at provisioning time.
     *
     * @return An X.509 certificate chain for <em>CredentialKey</em>.
     */
    public @NonNull List<X509Certificate> getAttestation() {
        KeystoreEngine.KeyInfo keyInfo = mKeystoreEngine.getKeyInfo(mCredentialKeyAlias);
        return keyInfo.getAttestation();
    }

    /**
     * Gets the alias for <em>CredentialKey</em>.
     *
     * <p>This can be used together with the {@link KeystoreEngine} returned by
     * {@link #getCredentialKeystoreEngine()}.
     *
     * @return The alias for <em>CredentialKey</em>.
     */
    public @NonNull String getCredentialKeyAlias() {
        return mCredentialKeyAlias;
    }

    /**
     * Gets the keystore engine for <em>CredentialKey</em>.
     *
     * <p>This can be used together with the alias returned by
     * {@link #getCredentialKeyAlias()}.
     *
     * @return The {@link KeystoreEngine} used for <em>CredentialKey</em>.
     */
    public @NonNull KeystoreEngine getCredentialKeystoreEngine() {
        return mKeystoreEngine;
    }

    /**
     * Gets the name-space-organized key/value pairs for the credential.
     *
     * <p>This returns the same data set with {@link #setNameSpacedData(NameSpacedData)}.
     *
     * @return A {@link NameSpacedData}.
     */
    public @NonNull NameSpacedData getNameSpacedData() {
        return mNameSpacedData;
    }

    /**
     * Sets name-space-organized key/value pairs for the credential.
     *
     * @param nameSpacedData A {@link NameSpacedData}.
     */
    public void setNameSpacedData(@NonNull NameSpacedData nameSpacedData) {
        mNameSpacedData = nameSpacedData;
        saveCredential();
    }

    /**
     * Sets application specific data.
     *
     * <p>This allows applications to store additional data it wants to associate with the
     * credential, for example the document type (e.g. DocType for 18013-5 credentials),
     * user-visible name, logos/background, state, and so on.
     *
     * <p>Use {@link #getApplicationData(String)} to read the data back.
     *
     * @param key the key for the data.
     * @param value the value or {@code null} to remove.
     */
    public void setApplicationData(@NonNull String key, @Nullable byte[] value) {
        if (value == null) {
            mApplicationData.remove(key);
        } else {
            mApplicationData.put(key, value);
        }
        saveCredential();
    }

    /**
     * Sets application specific data as a string.
     *
     * <p>Like {@link #setApplicationData(String, byte[])} but encodes the given value
     * as an UTF-8 string before storing it.
     *
     * @param key the key for the data.
     * @param value the value or {@code null} to remove.
     */
    public void setApplicationDataString(@NonNull String key, @Nullable String value) {
        if (value == null) {
            mApplicationData.remove(key);
        } else {
            mApplicationData.put(key, value.getBytes(StandardCharsets.UTF_8));
        }
        saveCredential();
    }

    /**
     * Gets application specific data.
     *
     * <p>Gets data previously stored with {@link #getApplicationData(String)}.
     *
     * @param key the key for the data.
     * @return the value or {@code null} if there is no value for the given key.
     */
    public @Nullable byte[] getApplicationData(@NonNull String key) {
        return mApplicationData.get(key);
    }

    /**
     * Gets application specific data as a string.
     *
     * <p>Like {@link #getApplicationData(String)} but decodes the value as
     * an UTF-8 string before returning it.
     *
     * @param key the key for the data.
     * @return the value or {@code null} if there is no value for the given key.
     */
    public @Nullable String getApplicationDataString(@NonNull String key) {
        byte[] value = mApplicationData.get(key);
        if (value == null) {
            return null;
        }
        return new String(value, StandardCharsets.UTF_8);
    }

    /**
     * Finds a suitable authentication key to use.
     *
     * @param now Pass current time to ensure that the selected slot's validity period or
     *   {@code null} to not consider validity times.
     * @return An authentication key which can be used for signing or {@code null} if none was found.
     */
    public @Nullable AuthenticationKey findAuthenticationKey(@Nullable Timestamp now) {

        AuthenticationKey candidate = null;
        for (AuthenticationKey authenticationKey : mAuthenticationKeys) {
            // If current time is passed...
            if (now != null) {
                // ... ignore slots that aren't yet valid
                if (now.toEpochMilli() < authenticationKey.getValidFrom().toEpochMilli()) {
                    continue;
                }
                // .. ignore slots that aren't valid anymore
                if (now.toEpochMilli() > authenticationKey.getValidUntil().toEpochMilli()) {
                    continue;
                }
            }
            // If we already have a candidate, prefer this one if its usage count is lower
            if (candidate != null) {
                if (authenticationKey.getUsageCount() < candidate.getUsageCount()) {
                    candidate = authenticationKey;
                }
            } else {
                candidate = authenticationKey;
            }
        }
        return candidate;
    }

    /**
     * A certified authentication key.
     *
     * <p>To create an instance of this type, an application must first use
     * {@link Credential#createPendingAuthenticationKey(KeystoreEngine.CreateKeySettings, AuthenticationKey)}
     * to create a {@link PendingAuthenticationKey} and after issuer certification
     * has been received it can be upgraded to a {@link AuthenticationKey}.
     */
    public static class AuthenticationKey {
        private String mAlias;
        private int mUsageCount;
        private byte[] mData;
        private Timestamp mValidFrom;
        private Timestamp mValidUntil;
        private Credential mCredential;
        private String mKeystoreEngineName;
        private String mReplacementAlias;
        private LinkedHashMap<String, byte[]> mApplicationData = new LinkedHashMap<>();

        static AuthenticationKey create(
                @NonNull PendingAuthenticationKey pendingAuthenticationKey,
                @NonNull byte[] issuerProvidedAuthenticationData,
                @NonNull Timestamp validFrom,
                @NonNull Timestamp validUntil,
                @NonNull Credential credential) {
            AuthenticationKey ret = new AuthenticationKey();
            ret.mAlias = pendingAuthenticationKey.mAlias;
            ret.mData = issuerProvidedAuthenticationData;
            ret.mValidFrom = validFrom;
            ret.mValidUntil = validUntil;
            ret.mCredential = credential;
            ret.mKeystoreEngineName = pendingAuthenticationKey.mKeystoreEngineName;
            ret.mApplicationData = pendingAuthenticationKey.mApplicationData;
            return ret;
        }

        /**
         * Returns how many time the key in the slot has been used.
         *
         * @return The number of times the key in slot has been used.
         */
        public int getUsageCount() {
            return mUsageCount;
        }

        /**
         * Gets the issuer-provided data associated with the key.
         *
         * @return The issuer-provided data.
         */
        public @NonNull byte[] getIssuerProvidedData() {
            return mData;
        }

        /**
         * Gets the point in time the issuer-provided data is valid from.
         *
         * @return The valid-from time.
         */
        public @NonNull Timestamp getValidFrom() {
            return mValidFrom;
        }

        /**
         * Gets the point in time the issuer-provided data is valid until.
         *
         * @return The valid-until time.
         */
        public @NonNull Timestamp getValidUntil() {
            return mValidUntil;
        }

        /**
         * Deletes the authentication key.
         *
         * <p>After deletion, this object should no longer be used.
         */
        public void delete() {
            KeystoreEngine keystoreEngine = mCredential.mKeystoreEngineRepository
                    .getImplementation(mKeystoreEngineName);
            if (keystoreEngine == null) {
                throw new IllegalArgumentException("Unknown engine " + mKeystoreEngineName);
            }
            keystoreEngine.deleteKey(mAlias);
            mCredential.removeAuthenticationKey(this);
        }

        /**
         * Increases usage count of the authentication key.
         */
        public void increaseUsageCount() {
            mUsageCount += 1;
            mCredential.saveCredential();
        }

        /**
         * Gets the X.509 certificate chain for the authentication key
         *
         * @return An X.509 certificate chain for the authentication key.
         */
        public @NonNull List<X509Certificate> getAttestation() {
            KeystoreEngine keystoreEngine = mCredential.mKeystoreEngineRepository
                    .getImplementation(mKeystoreEngineName);
            if (keystoreEngine == null) {
                throw new IllegalArgumentException("Unknown engine " + mKeystoreEngineName);
            }
            KeystoreEngine.KeyInfo keyInfo = keystoreEngine.getKeyInfo(mAlias);
            return keyInfo.getAttestation();
        }

        /**
         * Gets the alias for the authentication key.
         *
         * <p>This can be used together with the {@link KeystoreEngine} returned by
         * {@link #getKeystoreEngine()} ()}.
         *
         * @return The alias for the authentication key.
         */
        public @NonNull String getAlias() {
            return mAlias;
        }

        /**
         * Gets the keystore engine for the authentication key.
         *
         * <p>This can be used together with the alias returned by
         * {@link #getAlias()}.
         *
         * @return The {@link KeystoreEngine} used for <em>CredentialKey</em>.
         */
        public @NonNull KeystoreEngine getKeystoreEngine() {
            KeystoreEngine keystoreEngine = mCredential.mKeystoreEngineRepository
                    .getImplementation(mKeystoreEngineName);
            if (keystoreEngine == null) {
                throw new IllegalArgumentException("Unknown engine " + mKeystoreEngineName);
            }
            return keystoreEngine;
        }

        DataItem toCbor() {
            CborBuilder builder = new CborBuilder();
            MapBuilder<CborBuilder> mapBuilder = builder.addMap();
            mapBuilder.put("alias", mAlias)
                    .put("keystoreEngineName", mKeystoreEngineName)
                    .put("usageCount", mUsageCount)
                    .put("data", mData)
                    .put("validFrom", mValidFrom.toEpochMilli())
                    .put("validUntil", mValidUntil.toEpochMilli());
            MapBuilder<MapBuilder<CborBuilder>> applicationDataBuilder =
                    mapBuilder.putMap("applicationData");
            for (String key : mApplicationData.keySet()) {
                byte[] value = mApplicationData.get(key);
                applicationDataBuilder.put(key, value);
            }
            if (mReplacementAlias != null) {
                mapBuilder.put("replacementAlias", mReplacementAlias);
            }
            return builder.build().get(0);
        }

        static AuthenticationKey fromCbor(@NonNull DataItem dataItem,
                                          @NonNull Credential credential) {
            AuthenticationKey ret = new AuthenticationKey();
            ret.mAlias = Util.cborMapExtractString(dataItem, "alias");
            ret.mKeystoreEngineName = Util.cborMapExtractString(dataItem, "keystoreEngineName");
            ret.mUsageCount = (int) Util.cborMapExtractNumber(dataItem, "usageCount");
            ret.mData = Util.cborMapExtractByteString(dataItem, "data");
            ret.mValidFrom = Timestamp.ofEpochMilli(Util.cborMapExtractNumber(dataItem, "validFrom"));
            ret.mValidUntil = Timestamp.ofEpochMilli(Util.cborMapExtractNumber(dataItem, "validUntil"));
            if (Util.cborMapHasKey(dataItem, "replacementAlias")) {
                ret.mReplacementAlias = Util.cborMapExtractString(dataItem, "replacementAlias");
            }
            ret.mApplicationData = new LinkedHashMap<>();
            DataItem applicationDataDataItem = Util.cborMapExtractMap(dataItem, "applicationData");
            if (!(applicationDataDataItem instanceof co.nstant.in.cbor.model.Map)) {
                throw new IllegalStateException("applicationData not found or not map");
            }
            for (DataItem keyItem : ((co.nstant.in.cbor.model.Map) applicationDataDataItem).getKeys()) {
                String key = ((UnicodeString) keyItem).getString();
                byte[] value = Util.cborMapExtractByteString(applicationDataDataItem, key);
                ret.mApplicationData.put(key, value);
            }
            ret.mCredential = credential;
            return ret;
        }

        /**
         * Gets the pending auth key that will replace this key once certified.
         *
         * @return An {@link PendingAuthenticationKey} or {@code null} if no key was designated
         *   to replace this key.
         */
        public @Nullable PendingAuthenticationKey getReplacement() {
            if (mReplacementAlias == null) {
                return null;
            }
            for (PendingAuthenticationKey pendingAuthKey : mCredential.getPendingAuthenticationKeys()) {
                if (pendingAuthKey.getAlias().equals(mReplacementAlias)) {
                    return pendingAuthKey;
                }
            }
            Logger.w(TAG, "Pending key with alias " + mReplacementAlias + " which "
                    + "is intended to replace this key does not exist");
            return null;
        }


        void setReplacementAlias(@NonNull String alias) {
            mReplacementAlias = alias;
            mCredential.saveCredential();
        }

        /**
         * Sets application specific data.
         *
         * <p>This allows applications to store additional data it wants to associate with the
         * pending authentication key.
         *
         * <p>Use {@link #getApplicationData(String)} to read the data back.
         *
         * @param key the key for the data.
         * @param value the value or {@code null} to remove.
         */
        public void setApplicationData(@NonNull String key, @Nullable byte[] value) {
            if (value == null) {
                mApplicationData.remove(key);
            } else {
                mApplicationData.put(key, value);
            }
            mCredential.saveCredential();
        }

        /**
         * Sets application specific data as a string.
         *
         * <p>Like {@link #setApplicationData(String, byte[])} but encodes the given value
         * as an UTF-8 string before storing it.
         *
         * @param key the key for the data.
         * @param value the value or {@code null} to remove.
         */
        public void setApplicationDataString(@NonNull String key, @Nullable String value) {
            if (value == null) {
                mApplicationData.remove(key);
            } else {
                mApplicationData.put(key, value.getBytes(StandardCharsets.UTF_8));
            }
            mCredential.saveCredential();
        }

        /**
         * Gets application specific data.
         *
         * <p>Gets data previously stored with {@link #getApplicationData(String)}.
         *
         * @param key the key for the data.
         * @return the value or {@code null} if there is no value for the given key.
         */
        public @Nullable byte[] getApplicationData(@NonNull String key) {
            return mApplicationData.get(key);
        }

        /**
         * Gets application specific data as a string.
         *
         * <p>Like {@link #getApplicationData(String)} but decodes the value as
         * an UTF-8 string before returning it.
         *
         * @param key the key for the data.
         * @return the value or {@code null} if there is no value for the given key.
         */
        public @Nullable String getApplicationDataString(@NonNull String key) {
            byte[] value = mApplicationData.get(key);
            if (value == null) {
                return null;
            }
            return new String(value, StandardCharsets.UTF_8);
        }
    }

    /**
     * An authentication key pending certification.
     *
     * <p>To create a pending authentication key, use
     * {@link Credential#createPendingAuthenticationKey(KeystoreEngine.CreateKeySettings, AuthenticationKey)}.
     *
     * <p>Because issuer certification of authentication could take a long time, pending
     * authentication keys are persisted and {@link Credential#getPendingAuthenticationKeys()}
     * can be used to get a list of instances. For example this can be used to re-ping
     * the issuing server for outstanding certification requests.
     *
     * <p>Once certification is complete, use {@link #certify(byte[], Timestamp, Timestamp)}
     * to upgrade to a {@link AuthenticationKey}.
     */
    public static class PendingAuthenticationKey {
        String mKeystoreEngineName;

        String mAlias;
        Credential mCredential;
        private String mReplacementForAlias;
        private LinkedHashMap<String, byte[]> mApplicationData = new LinkedHashMap<>();

        static @NonNull PendingAuthenticationKey create(
                @NonNull String alias,
                @NonNull KeystoreEngine.CreateKeySettings createKeySettings,
                @Nullable AuthenticationKey asReplacementFor,
                @NonNull Credential credential) {
            PendingAuthenticationKey ret = new PendingAuthenticationKey();
            ret.mAlias = alias;
            ret.mKeystoreEngineName = createKeySettings.getKeystoreEngineClass().getName();
            KeystoreEngine keystoreEngine = credential.mKeystoreEngineRepository
                    .getImplementation(ret.mKeystoreEngineName);
            if (keystoreEngine == null) {
                throw new IllegalArgumentException("Unknown engine " + ret.mKeystoreEngineName);
            }
            keystoreEngine.createKey(alias, createKeySettings);
            if (asReplacementFor != null) {
                ret.mReplacementForAlias = asReplacementFor.getAlias();
            }
            ret.mCredential = credential;
            return ret;
        }

        /**
         * Gets the X.509 certificate chain for the authentication key pending certification.
         *
         * <p>The application should send this key to the issuer which should create issuer-provided
         * data (e.g. an MSO if using ISO/IEC 18013-5:2021) using the key as the {@code DeviceKey}.
         *
         * @return An X.509 certificate chain for the pending authentication key.
         */
        public @NonNull List<X509Certificate> getAttestation() {
            KeystoreEngine keystoreEngine = mCredential.mKeystoreEngineRepository
                    .getImplementation(mKeystoreEngineName);
            if (keystoreEngine == null) {
                throw new IllegalArgumentException("Unknown engine " + mKeystoreEngineName);
            }
            KeystoreEngine.KeyInfo keyInfo = keystoreEngine.getKeyInfo(mAlias);
            return keyInfo.getAttestation();
        }

        /**
         * Gets the alias for the pending authentication key.
         *
         * <p>This can be used together with the {@link KeystoreEngine} returned by
         * {@link #getKeystoreEngine()} ()}.
         *
         * @return The alias for the authentication key.
         */
        public @NonNull String getAlias() {
            return mAlias;
        }

        /**
         * Gets the keystore engine for the pending authentication key.
         *
         * <p>This can be used together with the alias returned by
         * {@link #getAlias()}.
         *
         * @return The {@link KeystoreEngine} used for <em>CredentialKey</em>.
         */
        public @NonNull KeystoreEngine getKeystoreEngine() {
            KeystoreEngine keystoreEngine = mCredential.mKeystoreEngineRepository
                    .getImplementation(mKeystoreEngineName);
            if (keystoreEngine == null) {
                throw new IllegalArgumentException("Unknown engine " + mKeystoreEngineName);
            }
            return keystoreEngine;
        }

        /**
         * Deletes the pending authentication key.
         */
        public void delete() {
            KeystoreEngine keystoreEngine = mCredential.mKeystoreEngineRepository
                    .getImplementation(mKeystoreEngineName);
            if (keystoreEngine == null) {
                throw new IllegalArgumentException("Unknown engine " + mKeystoreEngineName);
            }
            keystoreEngine.deleteKey(mAlias);
            mCredential.removePendingAuthenticationKey(this);
        }

        /**
         * Certifies the pending authentication key.
         *
         * <p>This will convert this {@link PendingAuthenticationKey} into a
         * {@link AuthenticationKey} including preserving the application-data
         * set.
         *
         * <p>The {@link PendingAuthenticationKey} object should no longer be used after calling this.
         *
         * @param issuerProvidedAuthenticationData the issuer-provided static authentication data.
         * @param validFrom the point in time before which the data is not valid.
         * @param validUntil the point in time after which the data is not valid.
         * @return a {@link AuthenticationKey}.
         */
        public @NonNull AuthenticationKey certify(@NonNull byte[] issuerProvidedAuthenticationData,
                                                  @NonNull Timestamp validFrom,
                                                  @NonNull Timestamp validUntil) {
            return mCredential.certifyPendingAuthenticationKey(this,
                    issuerProvidedAuthenticationData,
                    validFrom,
                    validUntil);
        }

        @NonNull DataItem toCbor() {
            CborBuilder builder = new CborBuilder();
            MapBuilder<CborBuilder> mapBuilder = builder.addMap();
            mapBuilder.put("alias", mAlias)
                    .put("keystoreEngineName", mKeystoreEngineName);
            if (mReplacementForAlias != null) {
                mapBuilder.put("replacementForAlias", mReplacementForAlias);
            }
            MapBuilder<MapBuilder<CborBuilder>> applicationDataBuilder =
                    mapBuilder.putMap("applicationData");
            for (String key : mApplicationData.keySet()) {
                byte[] value = mApplicationData.get(key);
                applicationDataBuilder.put(key, value);
            }
            return builder.build().get(0);
        }

        static PendingAuthenticationKey fromCbor(@NonNull DataItem dataItem,
                                                 @NonNull Credential credential) {
            PendingAuthenticationKey ret = new PendingAuthenticationKey();
            ret.mAlias = Util.cborMapExtractString(dataItem, "alias");
            ret.mKeystoreEngineName = Util.cborMapExtractString(dataItem, "keystoreEngineName");
            if (Util.cborMapHasKey(dataItem, "replacementForAlias")) {
                ret.mReplacementForAlias = Util.cborMapExtractString(dataItem, "replacementForAlias");
            }
            ret.mApplicationData = new LinkedHashMap<>();
            DataItem applicationDataDataItem = Util.cborMapExtractMap(dataItem, "applicationData");
            if (!(applicationDataDataItem instanceof co.nstant.in.cbor.model.Map)) {
                throw new IllegalStateException("applicationData not found or not map");
            }
            for (DataItem keyItem : ((co.nstant.in.cbor.model.Map) applicationDataDataItem).getKeys()) {
                String key = ((UnicodeString) keyItem).getString();
                byte[] value = Util.cborMapExtractByteString(applicationDataDataItem, key);
                ret.mApplicationData.put(key, value);
            }
            ret.mCredential = credential;
            return ret;
        }

        /**
         * Gets the auth key that will be replaced by this key once it's been certified.
         *
         * @return An {@link AuthenticationKey} or {@code null} if no key was designated
         *   when this pending key was created.
         */
        public @Nullable AuthenticationKey getReplacementFor() {
            if (mReplacementForAlias == null) {
                return null;
            }
            for (AuthenticationKey authKey : mCredential.getAuthenticationKeys()) {
                if (authKey.getAlias().equals(mReplacementForAlias)) {
                    return authKey;
                }
            }
            Logger.w(TAG, "Key with alias " + mReplacementForAlias + " which "
                    + "is intended to be replaced does not exist");
            return null;
        }

        /**
         * Sets application specific data.
         *
         * <p>This allows applications to store additional data it wants to associate with the
         * authentication key.
         *
         * <p>Use {@link #getApplicationData(String)} to read the data back.
         *
         * @param key the key for the data.
         * @param value the value or {@code null} to remove.
         */
        public void setApplicationData(@NonNull String key, @Nullable byte[] value) {
            if (value == null) {
                mApplicationData.remove(key);
            } else {
                mApplicationData.put(key, value);
            }
            mCredential.saveCredential();
        }

        /**
         * Sets application specific data as a string.
         *
         * <p>Like {@link #setApplicationData(String, byte[])} but encodes the given value
         * as an UTF-8 string before storing it.
         *
         * @param key the key for the data.
         * @param value the value or {@code null} to remove.
         */
        public void setApplicationDataString(@NonNull String key, @Nullable String value) {
            if (value == null) {
                mApplicationData.remove(key);
            } else {
                mApplicationData.put(key, value.getBytes(StandardCharsets.UTF_8));
            }
            mCredential.saveCredential();
        }

        /**
         * Gets application specific data.
         *
         * <p>Gets data previously stored with {@link #getApplicationData(String)}.
         *
         * @param key the key for the data.
         * @return the value or {@code null} if there is no value for the given key.
         */
        public @Nullable byte[] getApplicationData(@NonNull String key) {
            return mApplicationData.get(key);
        }

        /**
         * Gets application specific data as a string.
         *
         * <p>Like {@link #getApplicationData(String)} but decodes the value as
         * an UTF-8 string before returning it.
         *
         * @param key the key for the data.
         * @return the value or {@code null} if there is no value for the given key.
         */
        public @Nullable String getApplicationDataString(@NonNull String key) {
            byte[] value = mApplicationData.get(key);
            if (value == null) {
                return null;
            }
            return new String(value, StandardCharsets.UTF_8);
        }
    }

    /**
     * Creates a new authentication key.
     *
     * <p>This returns a {@link PendingAuthenticationKey} which should be sent to the
     * credential issuer for certification. Use
     * {@link PendingAuthenticationKey#certify(byte[], Timestamp, Timestamp)} when certification
     * has been obtained.
     *
     * <p>For a higher-level way of managing authentication keys, see
     * {@link CredentialUtil#managedAuthenticationKeyHelper(Credential, KeystoreEngine.CreateKeySettings, String, Timestamp, int, int, long)}.
     *
     * @param createKeySettings settings for the authentication key.
     * @param asReplacementFor if not {@code null}, replace the given authentication key
     *                         with this one, once it has been certified.
     * @return a {@link PendingAuthenticationKey}.
     * @throws IllegalArgumentException if {@code asReplacementFor} is not null and the given
     *   key already has a pending key intending to replace it.
     */
    public @NonNull PendingAuthenticationKey createPendingAuthenticationKey(
            @NonNull KeystoreEngine.CreateKeySettings createKeySettings,
            @Nullable AuthenticationKey asReplacementFor) {
        if (asReplacementFor != null && asReplacementFor.getReplacement() != null) {
            throw new IllegalStateException(
                    "The given key already has an existing pending key intending to replace it");
        }
        String alias = AUTHENTICATION_KEY_ALIAS_PREFIX + mName + "_authKey_" + mAuthenticationKeyCounter++;
        PendingAuthenticationKey pendingAuthenticationKey =
                PendingAuthenticationKey.create(
                        alias,
                        createKeySettings,
                        asReplacementFor,
                        this);
        mPendingAuthenticationKeys.add(pendingAuthenticationKey);
        if (asReplacementFor != null) {
            asReplacementFor.setReplacementAlias(pendingAuthenticationKey.getAlias());
        }
        saveCredential();
        return pendingAuthenticationKey;
    }

    void removePendingAuthenticationKey(@NonNull PendingAuthenticationKey pendingAuthenticationKey) {
        if (!mPendingAuthenticationKeys.remove(pendingAuthenticationKey)) {
            throw new IllegalStateException("Error removing pending authentication key");
        }
        if (pendingAuthenticationKey.mReplacementForAlias != null) {
            for (AuthenticationKey authKey : mAuthenticationKeys) {
                if (authKey.mAlias.equals(pendingAuthenticationKey.mReplacementForAlias)) {
                    authKey.mReplacementAlias = null;
                    break;
                }
            }
        }
        saveCredential();
    }

    void removeAuthenticationKey(@NonNull AuthenticationKey authenticationKey) {
        if (!mAuthenticationKeys.remove(authenticationKey)) {
            throw new IllegalStateException("Error removing authentication key");
        }
        if (authenticationKey.mReplacementAlias != null) {
            for (PendingAuthenticationKey pendingAuthKey : mPendingAuthenticationKeys) {
                if (pendingAuthKey.mAlias.equals(authenticationKey.mReplacementAlias)) {
                    pendingAuthKey.mReplacementForAlias = null;
                    break;
                }
            }
        }
        saveCredential();
    }

    @NonNull AuthenticationKey certifyPendingAuthenticationKey(
            @NonNull PendingAuthenticationKey pendingAuthenticationKey,
            @NonNull byte[] issuerProvidedAuthenticationData,
            @NonNull Timestamp validFrom,
            @NonNull Timestamp validUntil) {
        if (!mPendingAuthenticationKeys.remove(pendingAuthenticationKey)) {
            throw new IllegalStateException("Error removing pending authentication key");
        }
        AuthenticationKey authenticationKey =
                AuthenticationKey.create(pendingAuthenticationKey,
                        issuerProvidedAuthenticationData,
                        validFrom,
                        validUntil,
                        this);
        mAuthenticationKeys.add(authenticationKey);

        AuthenticationKey authKeyToDelete = pendingAuthenticationKey.getReplacementFor();
        if (authKeyToDelete != null) {
            authKeyToDelete.delete();
        }

        saveCredential();
        return authenticationKey;
    }

    /**
     * Gets all pending authentication keys.
     *
     * @return a list of all pending authentication keys.
     */
    public @NonNull List<PendingAuthenticationKey> getPendingAuthenticationKeys() {
        // Return a shallow copy b/c caller might call PendingAuthenticationKey.certify()
        // or PendingAuthenticationKey.delete() while iterating.
        return new ArrayList<>(mPendingAuthenticationKeys);
    }

    /**
     * Gets all certified authentication keys.
     *
     * @return a list of all certified authentication keys.
     */
    public @NonNull List<AuthenticationKey> getAuthenticationKeys() {
        // Return a shallow copy b/c caller might call AuthenticationKey.delete()
        // while iterating.
        return new ArrayList<>(mAuthenticationKeys);
    }
}
