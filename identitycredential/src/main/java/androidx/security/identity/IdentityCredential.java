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

package androidx.security.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricPrompt.CryptoObject;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedList;

/**
 * Class used to read data from a previously provisioned credential.
 *
 * Use {@link IdentityCredentialStore#getCredentialByName(String, int)} to get a
 * {@link IdentityCredential} instance.
 */
public abstract class IdentityCredential {

    /**
     * Create an ephemeral key pair to use to establish a secure channel with a reader.
     *
     * <p>Most applications will use only the public key, and only to send it to the reader,
     * allowing the private key to be used internally for {@link #encryptMessageToReader(byte[])}
     * and {@link #decryptMessageFromReader(byte[])}. The private key is also provided for
     * applications that wish to use a cipher suite that is not supported by
     * {@link IdentityCredentialStore}.
     *
     * @return ephemeral key pair to use to establish a secure channel with a reader.
     */
    public @NonNull abstract KeyPair createEphemeralKeyPair();

    /**
     * Set the ephemeral public key provided by the reader. This must be called before
     * {@link #encryptMessageToReader} or {@link #decryptMessageFromReader} can be called.
     *
     * @param readerEphemeralPublicKey The ephemeral public key provided by the reader to
     *                                 establish a secure session.
     * @throws InvalidKeyException if the given key is invalid.
     */
    public abstract void setReaderEphemeralPublicKey(@NonNull PublicKey readerEphemeralPublicKey)
            throws InvalidKeyException;

    /**
     * Encrypt a message for transmission to the reader.
     *
     * @param messagePlaintext unencrypted message to encrypt.
     * @return encrypted message.
     */
    public @NonNull abstract byte[] encryptMessageToReader(@NonNull byte[] messagePlaintext);

    /**
     * Decrypt a message received from the reader.
     *
     * @param messageCiphertext encrypted message to decrypt.
     * @return decrypted message.
     * @throws MessageDecryptionException if the ciphertext couldn't be decrypted.
     */
    public @NonNull abstract byte[] decryptMessageFromReader(@NonNull byte[] messageCiphertext)
            throws MessageDecryptionException;

    /**
     * Gets the X.509 certificate chain for the CredentialKey which identifies this
     * credential to the issuing authority. This is the same certificate chain that
     * was returned by {@link WritableIdentityCredential#getCredentialKeyCertificateChain(byte[])}
     * when the credential was first created and its Android Keystore extension will
     * contain the <code>challenge</code> data set at that time. See the documentation
     * for that method for important information about this certificate chain.
     *
     * @return the certificate chain for this credential's CredentialKey.
     */
    public @NonNull abstract Collection<X509Certificate> getCredentialKeyCertificateChain();

    /**
     * Sets whether to allow using an authentication key which use count has been exceeded if no
     * other key is available. This must be called prior to calling
     * {@link #getEntries(byte[], Collection, byte[], byte[])} or {@link #getCryptoObject()}.
     *
     * By default this is set to true.
     *
     * @param allowUsingExhaustedKeys whether to allow using an authentication key which use count
     *                                has been exceeded if no other key is available.
     */
    public abstract void setAllowUsingExhaustedKeys(boolean allowUsingExhaustedKeys);

    /**
     * Gets the {@link CryptoObject} that can be used with
     * {@link android.hardware.biometrics.BiometricPrompt}
     * for authenticating the user.
     *
     * @return A {@link CryptoObject}.
     * @throws NoAuthenticationKeyAvailableException  if authentication keys were never
     *                                                provisioned, the method
     *                                             {@link #setAvailableAuthenticationKeys(int, int)}
     *                                                was called with {@code keyCount} set to 0,
     *                                                the method
     *                                                {@link #setAllowUsingExhaustedKeys(boolean)}
     *                                                was called with {@code false} and all
     *                                                available authentication keys have been
     *                                                exhausted.
     */
    public abstract @NonNull CryptoObject getCryptoObject()
            throws NoAuthenticationKeyAvailableException;

    /**
     * Retrieve data entries and associated data from this {@code IdentityCredential}.
     *
     * <p>If an access control check fails for one of the requested entries or if the entry
     * doesn't exist, the entry is simply not returned. The application can detect this
     * by using the {@link ResultNamespace#getStatus(String)} method on each of the requested
     * entries.
     *
     * <p>It is the responsibility of the calling application to know if authentication is needed
     * and use {@link #getCryptoObject()} together with {@link androidx.biometric.BiometricPrompt})
     * to make the user authenticate. If needed, this must be done before calling
     * {@link #getEntries(byte[], Collection, byte[], byte[])}.
     *
     * <p>If this method returns successfully (i.e. without throwing an exception), it must not be
     * called again on this instance.
     *
     * <p>If not {@code null} the {@code requestMessage} parameter must contain data for the request
     * from the verifier. The content can be defined in the way appropriate for the credential, byt
     * there are three requirements that must be met to work with this API:
     * <ul>
     * <li>The content must be a CBOR-encoded structure.</li>
     * <li>The CBOR structure must be a map.</li>
     * <li>The map must contain a tstr key "nameSpaces" whose value contains a map, as described in
     *     the example below.</li>
     * </ul>
     *
     * <p>Here's an example of CBOR which conforms to this requirement:
     * <pre>
     *   ItemsRequest = {
     *     ? "docType" : DocType,
     *     "nameSpaces" : NameSpaces,
     *     ? "RequestInfo" : {* tstr => any} ; Additional info the reader wants to provide
     *   }
     *
     *   NameSpaces = {
     *     + NameSpace => DataElements    ; Requested data elements for each NameSpace
     *   }
     *
     *   NameSpace = tstr
     *
     *   DataElements = {
     *     + DataElement => IntentToRetain
     *   }
     *
     *   DataElement = tstr
     *   IntentToRetain = bool
     * </pre>
     *
     * <p>If the {@code sessionTranscript} parameter is not {@code null}, it must contain CBOR
     * data conforming to the following CDDL schema:
     *
     * <pre>
     *   SessionTranscript = [
     *     DeviceEngagementBytes,
     *     EReaderKeyBytes
     *   ]
     *
     *   DeviceEngagementBytes = #6.24(bstr .cbor DeviceEngagement)
     *   EReaderKeyBytes = #6.24(bstr .cbor EReaderKey.Pub)
     * </pre>
     *
     * <p>If the SessionTranscript is not empty, a COSE_Key structure for the public part
     * of the key-pair previously generated by {@link #createEphemeralKeyPair()} must appear
     * somewhere in {@code DeviceEngagement} and the X and Y coordinates must both be present
     * in uncompressed form.
     *
     * <p>If {@code readerAuth} is not {@code null} it must be the bytes of a COSE_Sign1
     * structure as defined in RFC 8152. For the payload nil shall be used and the
     * detached payload is the ReaderAuthentication CBOR described below.
     * <pre>
     *     ReaderAuthentication = [
     *       "ReaderAuthentication",
     *       SessionTranscript,
     *       ItemsRequestBytes
     *     ]
     *
     *     ItemsRequestBytes = #6.24(bstr .cbor ItemsRequest)   ; Bytes of ItemsRequest
     * </pre>
     *
     * <p>The public key corresponding to the key used to made signature, can be
     * found in the {@code x5chain} unprotected header element of the COSE_Sign1
     * structure (as as described in 'draft-ietf-cose-x509-04'). There will be at
     * least one certificate in said element and there may be more (and if so,
     * each certificate must be signed by its successor).
     *
     * <p>Data elements protected by reader authentication is returned if, and only if, they are
     * mentioned in {@code requestMessage}, {@code requestMessage} is signed by the top-most
     * certificate in {@code readerCertificateChain}, and the data element is configured
     * with an {@link AccessControlProfile} with a {@link X509Certificate} in
     * {@code readerCertificateChain}.
     *
     * <p>Note that only items referenced in {@code entriesToRequest} are returned - the
     * {@code requestMessage} parameter is only used to for enforcing reader authentication.
     *
     * @param requestMessage         If not {@code null}, must contain CBOR data conforming to
     *                               the schema mentioned above.
     * @param entriesToRequest       A collection of {@link RequestNamespace} objects
     *                               specifying all the data elements to request.
     * @param readerSignature        COSE_Sign1 structure as described above or {@code null}
     *                               if reader authentication is not being used.
     * @return A {@link GetEntryResult} object containing entry data organized by namespace and a
     *         cryptographically authenticated representation of the same data.
     * @throws AlreadyCalledException                 if already called on this instance and that
     *                                                call was successful.
     * @throws NoAuthenticationKeyAvailableException  if authentication keys were never
     *                                                provisioned, the method
     *                                             {@link #setAvailableAuthenticationKeys(int, int)}
     *                                                was called with {@code keyCount} set to 0,
     *                                                the method
     *                                                {@link #setAllowUsingExhaustedKeys(boolean)}
     *                                                was called with {@code false} and all
     *                                                available authentication keys have been
     *                                                exhausted.
     * @throws InvalidReaderSignatureException        if the reader signature is invalid, or it
     *                                                doesn't contain a certificate chain, or if
     *                                                the signature failed to validate.
     * @throws InvalidRequestMessageException         if the requestMessage is malformed.
     * @throws EphemeralPublicKeyNotFoundException    if the ephemeral public key was not found in
     *                                                the session transcript.
     */
    public abstract @NonNull GetEntryResult getEntries(
            @Nullable byte[] requestMessage,
            @NonNull Collection<RequestNamespace> entriesToRequest,
            @Nullable byte[] sessionTranscript,
            @Nullable byte[] readerSignature)
            throws AlreadyCalledException, NoAuthenticationKeyAvailableException,
            InvalidReaderSignatureException, EphemeralPublicKeyNotFoundException,
            InvalidRequestMessageException;

    /**
     * Sets the number of dynamic authentication keys the {@code IdentityCredential} will maintain,
     * and the number of times each should be used.
     *
     * <p>{@code IdentityCredential}s will select the least-used dynamic authentication key each
     * time {@link #getEntries getEntries} is called. {@code IdentityCredential}s for which this
     * method has not been called behave as though it had been called with {@code keyCount} 0 and
     * {@code maxUsesPerKey} 1.
     *
     * @param keyCount      The number of active, certified dynamic authentication keys the
     *                      {@code IdentityCredential} will try to keep available. This value
     *                      must be non-negative.
     * @param maxUsesPerKey The maximum number of times each of the keys will be used before it's
     *                      eligible for replacement. This value must be greater than zero.
     */
    public abstract void setAvailableAuthenticationKeys(int keyCount, int maxUsesPerKey);

    /**
     * Gets a collection of dynamic authentication keys that need certification.
     *
     * <p>When there aren't enough certified dynamic authentication keys, either because the key
     * count has been increased or because one or more keys have reached their usage count, this
     * method will generate replacement keys and certificates and return them for issuer
     * certification. The issuer certificates and associated static authentication data must then
     * be provided back to the {@code IdentityCredential} using
     * {@link #storeStaticAuthenticationData(X509Certificate, byte[])}.
     *
     * <p>Each X.509 certificate is signed by CredentialKey. The certificate chain for CredentialKey
     * can be obtained using the {@link #getCredentialKeyCertificateChain()} method.
     *
     * @return A collection of X.509 certificates for dynamic authentication keys that need issuer
     * certification.
     */
    public @NonNull abstract Collection<X509Certificate> getAuthKeysNeedingCertification();

    /**
     * Store authentication data associated with a dynamic authentication key.
     *
     * This should only be called for an authenticated key returned by
     * {@link #getAuthKeysNeedingCertification()}.
     *
     * @param authenticationKey The dynamic authentication key for which certification and
     *                          associated static
     *                          authentication data is being provided.
     * @param staticAuthData    Static authentication data provided by the issuer that validates
     *                          the authenticity
     *                          and integrity of the credential data fields.
     * @throws UnknownAuthenticationKeyException If the given authentication key is not recognized.
     */
    public abstract void storeStaticAuthenticationData(
            @NonNull X509Certificate authenticationKey,
            @NonNull byte[] staticAuthData)
            throws UnknownAuthenticationKeyException;

    /**
     * Get the number of times the dynamic authentication keys have been used.
     *
     * @return int array of dynamic authentication key usage counts.
     */
    public @NonNull abstract int[] getAuthenticationDataUsageCount();

    /**
     * The result of retrieving data entries from a credential.
     */
    public static class GetEntryResult {
        byte[] mStaticAuthenticationData;
        Collection<ResultNamespace> mEntryNamespaces;
        byte[] mAuthenticatedData;
        byte[] mMessageAuthenticationCode;
        byte[] mEcdsaSignature;

        GetEntryResult() {
            mEntryNamespaces = new LinkedList<ResultNamespace>();
        }

        /**
         * Gets the namespaces with retrieved entries.
         *
         * @return collection of namespaces containing retrieved entries. May be empty if no data
         * was retrieved.
         */
        public @NonNull Collection<ResultNamespace> getEntryNamespaces() {
            return mEntryNamespaces;
        }

        /**
         * Returns a CBOR structure containing the retrieved data.
         *
         * <p>This structure - along with the session transcript - may be cryptographically
         * authenticated to prove to the reader that the data is from a trusted credential
         * and {@link #getMessageAuthenticationCode()} or {@link #getEcdsaSignature()} can
         * be used to get either a MAC or a digital signature. Depending on the implementation
         * only one of these two methods may work.
         *
         * <p>A MAC is preferred to a digital signature because it avoids creating a
         * non-repudiable signature over data provided by the reader, which can create
         * privacy risk. The MAC is computed over data provided by the reader but because
         * the reader can also compute the MAC key, it cannot prove that the MAC was
         * computed by the prover rather than itself. Because only the prover and reader can
         * compute the MAC code, the reader can verify the MAC but can't use it to prove
         * anything to a third party.
         *
         * <p>The CBOR structure which is cryptographically authenticated is the
         * {@code DeviceAuthentication} structure according to the following
         * <a href="https://tools.ietf.org/html/draft-ietf-cbor-cddl-06">CDDL</a> schema:
         *
         * <pre>
         *   DeviceAuthentication = [
         *     "DeviceAuthentication",
         *     SessionTranscript,
         *     DocType,
         *     DeviceNameSpacesBytes
         *   ]
         *
         *   DocType = tstr
         *
         *   SessionTranscript = [
         *     DeviceEngagementBytes,
         *     EReaderKeyBytes
         *   ]
         *
         *   DeviceEngagementBytes = #6.24(bstr .cbor DeviceEngagement)
         *   EReaderKeyBytes = #6.24(bstr .cbor EReaderKey.Pub)
         *
         *   DeviceNameSpacesBytes = #6.24(bstr .cbor DeviceNameSpaces)
         * </pre>
         *
         * where
         *
         * <pre>
         *   DeviceNameSpaces = {
         *     * NameSpace => DeviceSignedItems
         *   }
         *
         *   DeviceSignedItems = {
         *     + DataItemName => DataItemValue
         *   }
         *
         *   NameSpace = tstr
         *   DataItemName = tstr
         *   DataItemValue = any
         * </pre>
         *
         * <p>The returned data is the binary encoding of the {@code DeviceNameSpaces} structure
         * as defined above.
         *
         * @return The bytes of the {@code DeviceNameSpaces} CBOR structure.
         */
        public @NonNull byte[] getAuthenticatedData() {
            return mAuthenticatedData;
        }

        /**
         * Returns a message authentication code over the data returned by
         * {@link #getAuthenticatedData}, to prove to the reader that the data is from a trusted
         * credential.
         *
         * @return null if not implemented, otherwise a message authentication code for the data
         * returned by
         * {@link #getAuthenticatedData}, to prove to the reader that the data is from a
         * trusted credential. This code is produced by using the key agreement and key
         * derivation function from the ciphersuite with the authentication private key and
         * the reader ephemeral public key to compute a shared message authentication code
         * (MAC) key, then using the MAC function from the ciphersuite to compute a MAC of
         * the authenticate data.
         */
        public @Nullable byte[] getMessageAuthenticationCode() {
            return mMessageAuthenticationCode;
        }

        /**
         * Returns a digital signature over the data returned by
         * {@link #getAuthenticatedData}, to prove to the reader that the data is from a trusted
         * credential. The signature will be made with one of the provisioned dynamic authentication
         * keys.
         *
         * @return null if not implemented, otherwise an ECDSA signature.
         */
        public @Nullable byte[] getEcdsaSignature() {
            return mEcdsaSignature;
        }

        /**
         * Returns the static authentication data associated with the dynamic authentication
         * key used to sign or MAC the data returned by {@link #getAuthenticatedData()}.
         *
         * @return The static authentication data associated with with dynamic authentication key
         * used to sign or MAC the data.
         */
        public @NonNull byte[] getStaticAuthenticationData() {
            return mStaticAuthenticationData;
        }
    }
}
