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
import com.android.identity.securearea.SecureArea;
import com.android.identity.securearea.SecureAreaRepository;
import com.android.identity.storage.StorageEngine;
import com.android.identity.util.ApplicationData;
import com.android.identity.util.Logger;
import com.android.identity.util.SimpleApplicationData;
import com.android.identity.util.Timestamp;

import java.io.ByteArrayInputStream;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
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
 * <p>Data can be stored in credentials using the {@link ApplicationData} returned by
 * {@link #getApplicationData()} which supports key/value pairs with typed values
 * including raw blobs, strings, booleans, numbers, and {@link NameSpacedData}.
 * This data is persisted for the life-time of the credential.
 *
 * <p>Each credential may have a number of <em>Authentication Keys</em>
 * associated with it. These keys are intended to be used in ways specified by the
 * underlying credential format but the general idea is that they are created on
 * the device and then sent to the issuer for certification. The issuer then returns
 * some format-specific data related to the key. For Mobile Driving License and MDOCs according
 * to <a href="https://www.iso.org/standard/69084.html">ISO/IEC 18013-5:2021</a>
 * the authentication key plays the role of <em>DeviceKey</em> and the issuer-signed
 * data includes the <em>Mobile Security Object</em> which includes the authentication
 * key and is signed by the issuer. This is used for anti-cloning and to return data signed
 * by the device. The way it works in this API is that the application can use
 * {@link #createPendingAuthenticationKey(String, SecureArea, SecureArea.CreateKeySettings, AuthenticationKey)}
 * to get a {@link PendingAuthenticationKey}. With this in hand, the application can use
 * {@link PendingAuthenticationKey#getAttestation()} and send the attestation
 * to the issuer for certification. The issuer will then craft credential-format
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
 * of credential regardless of format, presentation, or issuance protocol used.
 */
public class Credential {
    private static final String TAG = "Credential";
    static final String CREDENTIAL_PREFIX = "IC_Credential_";

    static final String CREDENTIAL_KEY_ALIAS_PREFIX = "IC_CredentialKey_";

    static final String AUTHENTICATION_KEY_ALIAS_PREFIX = "IC_AuthenticationKey_";

    private final StorageEngine mStorageEngine;
    private final SecureAreaRepository mSecureAreaRepository;
    private String mName;
    private String mCredentialKeyAlias;

    private SecureArea mSecureArea;
    private SimpleApplicationData mApplicationData = new SimpleApplicationData(this::saveCredential);

    private List<PendingAuthenticationKey> mPendingAuthenticationKeys = new ArrayList<>();
    private List<AuthenticationKey> mAuthenticationKeys = new ArrayList<>();

    private long mAuthenticationKeyCounter;

    private Credential(@NonNull StorageEngine storageEngine,
                       @NonNull SecureAreaRepository secureAreaRepository) {
        mStorageEngine = storageEngine;
        mSecureAreaRepository = secureAreaRepository;
    }

    // Called by CredentialStore.createCredential().
    static Credential create(@NonNull StorageEngine storageEngine,
                             @NonNull SecureAreaRepository secureAreaRepository,
                             @NonNull String name,
                             @NonNull SecureArea secureArea,
                             @NonNull SecureArea.CreateKeySettings credentialKeySettings) {

        Credential credential = new Credential(storageEngine, secureAreaRepository);
        credential.mName = name;
        credential.mSecureArea = secureArea;
        credential.mCredentialKeyAlias = CREDENTIAL_KEY_ALIAS_PREFIX + name;

        credential.mSecureArea.createKey(credential.mCredentialKeyAlias, credentialKeySettings);

        credential.saveCredential();

        return credential;
    }

    // Called by CredentialStore.createCredentialWithExistingKey().
    static @NonNull Credential createWithExistingKey(
            @NonNull StorageEngine storageEngine,
            @NonNull SecureAreaRepository secureAreaRepository,
            @NonNull String name,
            @NonNull SecureArea secureArea,
            @NonNull SecureArea.CreateKeySettings credentialKeySettings,
            @NonNull String existingKeyAlias) {

        Credential credential = new Credential(storageEngine, secureAreaRepository);
        credential.mName = name;
        credential.mSecureArea = secureArea;
        credential.mCredentialKeyAlias = existingKeyAlias;

        credential.mSecureArea.createKey(credential.mCredentialKeyAlias, credentialKeySettings);

        return credential;
    }

    private void saveCredential() {
        Timestamp t0 = Timestamp.now();
        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> map = builder.addMap();
        map.put("secureAreaIdentifier", mSecureArea.getIdentifier());
        map.put("credentialKeyAlias", mCredentialKeyAlias);

        map.put("applicationData", mApplicationData.encodeAsCbor());

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
        Timestamp t1 = Timestamp.now();

        // Saving a credential is a costly affair (often more than 100ms) so log when we're doing
        // this so application developers are aware. This is to deter applications from storing
        // ephemeral data in the ApplicationData instances of the credential and our associated
        // authentication keys.
        Logger.d(TAG, String.format(Locale.US, "Saved credential '%s' to disk in %d msec",
                mName, t1.toEpochMilli() - t0.toEpochMilli()));
    }

    // Called by CredentialStore.lookupCredential().
    static Credential lookup(@NonNull StorageEngine storageEngine,
                             @NonNull SecureAreaRepository secureAreaRepository,
                             @NonNull String name) {
        Credential credential = new Credential(storageEngine, secureAreaRepository);
        credential.mName = name;
        if (!credential.loadCredential(secureAreaRepository)) {
            return null;
        }
        return credential;
    }

    private boolean loadCredential(@NonNull SecureAreaRepository secureAreaRepository) {
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

        String secureAreaIdentifier = Util.cborMapExtractString(map, "secureAreaIdentifier");
        mSecureArea = secureAreaRepository.getImplementation(secureAreaIdentifier);

        mCredentialKeyAlias = Util.cborMapExtractString(map, "credentialKeyAlias");

        DataItem applicationDataDataItem = map.get(new UnicodeString("applicationData"));
        if (!(applicationDataDataItem instanceof co.nstant.in.cbor.model.ByteString)) {
            throw new IllegalStateException("applicationData not found or not byte[]");
        }
        mApplicationData = SimpleApplicationData.decodeFromCbor(
                ((ByteString) applicationDataDataItem).getBytes(), this::saveCredential);

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
        mSecureArea.deleteKey(mCredentialKeyAlias);
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
        SecureArea.KeyInfo keyInfo = mSecureArea.getKeyInfo(mCredentialKeyAlias);
        return keyInfo.getAttestation();
    }

    /**
     * Gets the alias for <em>CredentialKey</em>.
     *
     * <p>This can be used together with the {@link SecureArea} returned by
     * {@link #getCredentialSecureArea()}.
     *
     * @return The alias for <em>CredentialKey</em>.
     */
    public @NonNull String getCredentialKeyAlias() {
        return mCredentialKeyAlias;
    }

    /**
     * Gets the secure area for <em>CredentialKey</em>.
     *
     * <p>This can be used together with the alias returned by
     * {@link #getCredentialKeyAlias()}.
     *
     * @return The {@link SecureArea} used for <em>CredentialKey</em>.
     */
    public @NonNull SecureArea getCredentialSecureArea() {
        return mSecureArea;
    }

    /**
     * Gets application specific data.
     *
     * <p>Use this object to store additional data an application may want to associate with the
     * credential, for example the document type (e.g. DocType for 18013-5 credentials),
     * user-visible name, logos/background, state, and so on. Setters and associated getters are
     * enumerated in the {@link ApplicationData} interface.
     *
     * @return application specific data.
     */
    public @NonNull ApplicationData getApplicationData() {
        return mApplicationData;
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
     * Gets the authentication key counter.
     *
     * <p>This is a number which starts at 0 and is increased by one for every call
     * to {@link #createPendingAuthenticationKey(String, SecureArea, SecureArea.CreateKeySettings, AuthenticationKey)}.
     *
     * @return the authentication key counter.
     */
    public long getAuthenticationKeyCounter() {
        return mAuthenticationKeyCounter;
    }

    /**
     * A certified authentication key.
     *
     * <p>To create an instance of this type, an application must first use
     * {@link Credential#createPendingAuthenticationKey(String, SecureArea, SecureArea.CreateKeySettings, AuthenticationKey)}
     * to create a {@link PendingAuthenticationKey} and after issuer certification
     * has been received it can be upgraded to a {@link AuthenticationKey}.
     */
    public static class AuthenticationKey {
        private String mAlias;
        private String mDomain;
        private int mUsageCount;
        private byte[] mData;
        private Timestamp mValidFrom;
        private Timestamp mValidUntil;
        private Credential mCredential;
        private SecureArea mSecureArea;
        private String mReplacementAlias;
        private SimpleApplicationData mApplicationData;
        private long mAuthenticationKeyCounter;

        static AuthenticationKey create(
                @NonNull PendingAuthenticationKey pendingAuthenticationKey,
                @NonNull byte[] issuerProvidedAuthenticationData,
                @NonNull Timestamp validFrom,
                @NonNull Timestamp validUntil,
                @NonNull Credential credential) {
            AuthenticationKey ret = new AuthenticationKey();
            ret.mAlias = pendingAuthenticationKey.mAlias;
            ret.mDomain = pendingAuthenticationKey.mDomain;
            ret.mData = issuerProvidedAuthenticationData;
            ret.mValidFrom = validFrom;
            ret.mValidUntil = validUntil;
            ret.mCredential = credential;
            ret.mSecureArea = pendingAuthenticationKey.mSecureArea;
            ret.mApplicationData = pendingAuthenticationKey.mApplicationData;
            ret.mAuthenticationKeyCounter = pendingAuthenticationKey.mAuthenticationKeyCounter;
            return ret;
        }

        /**
         * Gets the domain of the authentication key.
         *
         * <p>This returns the domain set when the pending authentication key was created.
         *
         * @return the domain.
         */
        public @NonNull String getDomain() {
            return mDomain;
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
         * Gets the authentication key counter.
         *
         * <p>This is the value of the Credential's Authentication Key Counter
         * at the time the pending authentication key for this authentication key
         * was created.
         *
         * @return the authentication key counter.
         */
        public long getAuthenticationKeyCounter() {
            return mAuthenticationKeyCounter;
        }

        /**
         * Deletes the authentication key.
         *
         * <p>After deletion, this object should no longer be used.
         */
        public void delete() {
            mSecureArea.deleteKey(mAlias);
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
            SecureArea.KeyInfo keyInfo = mSecureArea.getKeyInfo(mAlias);
            return keyInfo.getAttestation();
        }

        /**
         * Gets the alias for the authentication key.
         *
         * <p>This can be used together with the {@link SecureArea} returned by
         * {@link #getSecureArea()} ()}.
         *
         * @return The alias for the authentication key.
         */
        public @NonNull String getAlias() {
            return mAlias;
        }

        /**
         * Gets the secure area for the authentication key.
         *
         * <p>This can be used together with the alias returned by
         * {@link #getAlias()}.
         *
         * @return The {@link SecureArea} used for <em>CredentialKey</em>.
         */
        public @NonNull SecureArea getSecureArea() {
            return mSecureArea;
        }

        DataItem toCbor() {
            CborBuilder builder = new CborBuilder();
            MapBuilder<CborBuilder> mapBuilder = builder.addMap();
            mapBuilder.put("alias", mAlias)
                    .put("domain", mDomain)
                    .put("secureAreaIdentifier", mSecureArea.getIdentifier())
                    .put("usageCount", mUsageCount)
                    .put("data", mData)
                    .put("validFrom", mValidFrom.toEpochMilli())
                    .put("validUntil", mValidUntil.toEpochMilli())
                    .put("applicationData", mApplicationData.encodeAsCbor())
                    .put("authenticationKeyCounter", mAuthenticationKeyCounter);
            if (mReplacementAlias != null) {
                mapBuilder.put("replacementAlias", mReplacementAlias);
            }
            return builder.build().get(0);
        }

        static AuthenticationKey fromCbor(@NonNull DataItem dataItem,
                                          @NonNull Credential credential) {
            AuthenticationKey ret = new AuthenticationKey();
            ret.mAlias = Util.cborMapExtractString(dataItem, "alias");
            if (Util.cborMapHasKey(dataItem, "domain")) {
                ret.mDomain = Util.cborMapExtractString(dataItem, "domain");
            } else {
                ret.mDomain = "";
            }
            String secureAreaIdentifier = Util.cborMapExtractString(dataItem, "secureAreaIdentifier");
            ret.mSecureArea = credential.mSecureAreaRepository.getImplementation(secureAreaIdentifier);
            if (ret.mSecureArea == null) {
                throw new IllegalArgumentException("Unknown Secure Area " + secureAreaIdentifier);
            }
            ret.mUsageCount = (int) Util.cborMapExtractNumber(dataItem, "usageCount");
            ret.mData = Util.cborMapExtractByteString(dataItem, "data");
            ret.mValidFrom = Timestamp.ofEpochMilli(Util.cborMapExtractNumber(dataItem, "validFrom"));
            ret.mValidUntil = Timestamp.ofEpochMilli(Util.cborMapExtractNumber(dataItem, "validUntil"));
            if (Util.cborMapHasKey(dataItem, "replacementAlias")) {
                ret.mReplacementAlias = Util.cborMapExtractString(dataItem, "replacementAlias");
            }
            DataItem applicationDataDataItem = Util.cborMapExtract(dataItem, "applicationData");
            if (!(applicationDataDataItem instanceof co.nstant.in.cbor.model.ByteString)) {
                throw new IllegalStateException("applicationData not found or not byte[]");
            }
            ret.mCredential = credential;
            ret.mApplicationData = SimpleApplicationData.decodeFromCbor(
                    ((ByteString) applicationDataDataItem).getBytes(),
                    () -> ret.mCredential.saveCredential());
            if (Util.cborMapHasKey(dataItem, "authenticationKeyCounter")) {
                ret.mAuthenticationKeyCounter = Util.cborMapExtractNumber(dataItem, "authenticationKeyCounter");
            }
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
         * Gets application specific data.
         *
         * <p>Use this object to store additional data an application may want to associate
         * with the authentication key. Setters and associated getters are
         * enumerated in the {@link ApplicationData} interface.
         *
         * @return application specific data.
         */
        public @NonNull ApplicationData getApplicationData() {
            return mApplicationData;
        }
    }

    /**
     * An authentication key pending certification.
     *
     * <p>To create a pending authentication key, use
     * {@link Credential#createPendingAuthenticationKey(String, SecureArea, SecureArea.CreateKeySettings, AuthenticationKey)}.
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
        SecureArea mSecureArea;

        String mAlias;
        String mDomain;
        Credential mCredential;
        private String mReplacementForAlias;
        private SimpleApplicationData mApplicationData;
        private long mAuthenticationKeyCounter;

        static @NonNull PendingAuthenticationKey create(
                @NonNull String alias,
                @NonNull String domain,
                @NonNull SecureArea secureArea,
                @NonNull SecureArea.CreateKeySettings createKeySettings,
                @Nullable AuthenticationKey asReplacementFor,
                @NonNull Credential credential) {
            PendingAuthenticationKey ret = new PendingAuthenticationKey();
            ret.mAlias = alias;
            ret.mDomain = domain;
            ret.mSecureArea = secureArea;
            ret.mSecureArea.createKey(alias, createKeySettings);
            if (asReplacementFor != null) {
                ret.mReplacementForAlias = asReplacementFor.getAlias();
            }
            ret.mCredential = credential;
            ret.mApplicationData = new SimpleApplicationData(() -> ret.mCredential.saveCredential());
            ret.mAuthenticationKeyCounter = credential.mAuthenticationKeyCounter;
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
            SecureArea.KeyInfo keyInfo = mSecureArea.getKeyInfo(mAlias);
            return keyInfo.getAttestation();
        }

        /**
         * Gets the domain of the pending authentication key.
         *
         * <p>This returns the domain set when the pending authentication key was created.
         *
         * @return the domain.
         */
        public @NonNull String getDomain() {
            return mDomain;
        }

        /**
         * Gets the alias for the pending authentication key.
         *
         * <p>This can be used together with the {@link SecureArea} returned by
         * {@link #getSecureArea()} ()}.
         *
         * @return The alias for the authentication key.
         */
        public @NonNull String getAlias() {
            return mAlias;
        }

        /**
         * Gets the secure area for the pending authentication key.
         *
         * <p>This can be used together with the alias returned by
         * {@link #getAlias()}.
         *
         * @return The {@link SecureArea} used for <em>CredentialKey</em>.
         */
        public @NonNull SecureArea getSecureArea() {
            return mSecureArea;
        }

        /**
         * Gets the authentication key counter.
         *
         * <p>This is the value of the Credential's Authentication Key Counter
         * at the time this pending authentication key was created.
         *
         * @return the authentication key counter.
         */
        public long getAuthenticationKeyCounter() {
            return mAuthenticationKeyCounter;
        }

        /**
         * Deletes the pending authentication key.
         */
        public void delete() {
            mSecureArea.deleteKey(mAlias);
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
                    .put("domain", mDomain)
                    .put("secureAreaIdentifier", mSecureArea.getIdentifier());
            if (mReplacementForAlias != null) {
                mapBuilder.put("replacementForAlias", mReplacementForAlias);
            }
            mapBuilder.put("applicationData", mApplicationData.encodeAsCbor())
                    .put("authenticationKeyCounter", mAuthenticationKeyCounter);
            return builder.build().get(0);
        }

        static PendingAuthenticationKey fromCbor(@NonNull DataItem dataItem,
                                                 @NonNull Credential credential) {
            PendingAuthenticationKey ret = new PendingAuthenticationKey();
            ret.mAlias = Util.cborMapExtractString(dataItem, "alias");
            if (Util.cborMapHasKey(dataItem, "domain")) {
                ret.mDomain = Util.cborMapExtractString(dataItem, "domain");
            } else {
                ret.mDomain = "";
            }
            ret.mCredential = credential;
            String secureAreaIdentifier = Util.cborMapExtractString(dataItem, "secureAreaIdentifier");
            ret.mSecureArea = credential.mSecureAreaRepository.getImplementation(secureAreaIdentifier);
            if (ret.mSecureArea == null) {
                throw new IllegalArgumentException("Unknown Secure Area " + secureAreaIdentifier);
            }
            if (Util.cborMapHasKey(dataItem, "replacementForAlias")) {
                ret.mReplacementForAlias = Util.cborMapExtractString(dataItem, "replacementForAlias");
            }
            DataItem applicationDataDataItem = Util.cborMapExtract(dataItem, "applicationData");
            if (!(applicationDataDataItem instanceof co.nstant.in.cbor.model.ByteString)) {
                throw new IllegalStateException("applicationData not found or not byte[]");
            }
            ret.mApplicationData = SimpleApplicationData.decodeFromCbor(
                    ((ByteString) applicationDataDataItem).getBytes(),
                    () -> ret.mCredential.saveCredential());
            if (Util.cborMapHasKey(dataItem, "authenticationKeyCounter")) {
                ret.mAuthenticationKeyCounter = Util.cborMapExtractNumber(dataItem, "authenticationKeyCounter");
            }
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
         * Gets application specific data.
         *
         * <p>Use this object to store additional data an application may want to associate
         * with the pending authentication key. Setters and associated getters are
         * enumerated in the {@link ApplicationData} interface.
         *
         * @return application specific data.
         */
        public @NonNull ApplicationData getApplicationData() {
            return mApplicationData;
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
     * {@link CredentialUtil#managedAuthenticationKeyHelper(Credential, SecureArea, SecureArea.CreateKeySettings, String, Timestamp, int, int, long)}.
     *
     * @param domain a string used to group authentications keys together.
     * @param secureArea the secure area to use for the authentication key.
     * @param createKeySettings settings for the authentication key.
     * @param asReplacementFor if not {@code null}, replace the given authentication key
     *                         with this one, once it has been certified.
     * @return a {@link PendingAuthenticationKey}.
     * @throws IllegalArgumentException if {@code asReplacementFor} is not null and the given
     *   key already has a pending key intending to replace it.
     */
    public @NonNull PendingAuthenticationKey createPendingAuthenticationKey(
            @NonNull String domain,
            @NonNull SecureArea secureArea,
            @NonNull SecureArea.CreateKeySettings createKeySettings,
            @Nullable AuthenticationKey asReplacementFor) {
        if (asReplacementFor != null && asReplacementFor.getReplacement() != null) {
            throw new IllegalStateException(
                    "The given key already has an existing pending key intending to replace it");
        }
        String alias = AUTHENTICATION_KEY_ALIAS_PREFIX + mName + "_authKey_" + mAuthenticationKeyCounter++;
        PendingAuthenticationKey pendingAuthenticationKey =
                PendingAuthenticationKey.create(
                        alias,
                        domain,
                        secureArea,
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
