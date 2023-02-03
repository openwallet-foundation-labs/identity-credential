package com.android.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.PublicKey;
import java.security.Timestamp;
import java.util.List;
import java.util.Map;

public class MobileSecurityObjectGenerator {

    private final String mDigestAlgorithm;
    private final String mDocType;

    /**
     * Constructs a new {@link MobileSecurityObjectGenerator}.
     *
     * @param digestAlgorithm The digest algorithm identifier. Must be one of {"SHA-256", "SHA-384", "SHA-512"}.
     * @param docType The document type.
     */
    public MobileSecurityObjectGenerator(@NonNull String digestAlgorithm, @NonNull String docType) {
        mDigestAlgorithm = digestAlgorithm;
        mDocType = docType;
    }

    /**
     * Populates the <code>ValueDigests</code> mapping. This must be called at least once before
     * generating since <code>ValueDigests</code> must be non-empty.
     *
     * @param nameSpace The namespace.
     * @param digestIDs A non-empty mapping between a <code>DigestID</code> and a
     *                  <code>Digest</code>.
     * @return The <code>MobileSecurityObjectGenerator</code>.
     */
    @NonNull
    public MobileSecurityObjectGenerator addDigestIDs(@NonNull String nameSpace,
            @NonNull Map<Long, String> digestIDs) {
        return null;
    }

    /**
     * Sets the mdoc authentication public key as part of <code>DeviceKeyInfo</code>. This must be
     * called before generating since this a required component of the
     * <code>MobileSecurityObject</code>.
     *
     * @param deviceKey The public part of the key pair used for mdoc authentication.
     * @return The <code>MobileSecurityObjectGenerator</code>.
     */
    @NonNull
    public MobileSecurityObjectGenerator setDeviceKey(@NonNull PublicKey deviceKey) {

        return null;
    }

    /**
     * Populates the <code>AuthorizedNameSpaces</code> portion of the <code>keyAuthorizations</code>
     * within <code>DeviceKeyInfo</code>. This gives authorizations to full namespaces
     * included in the <code>authorizedNameSpaces</code> array. If authorization is given for a full
     * namespace, that namespace shall not be included in 
     * {@link #setDeviceKeyAuthorizedDataElements(Map)}.
     *
     * @param authorizedNameSpaces A non-empty list of namespaces which should be given authorization.
     * @return The <code>MobileSecurityObjectGenerator</code>.
     */
    @NonNull
    public MobileSecurityObjectGenerator setDeviceKeyAuthorizedNameSpaces(
            @NonNull List<String> authorizedNameSpaces) {

        return null;
    }

    /**
     * Populates the <code>AuthorizedDataElements</code> portion of the <code>keyAuthorizations</code>
     * within <code>DeviceKeyInfo</code>. This gives authorizations to data elements
     * included in the <code>authorizedDataElements</code> mapping. If a namespace is included here,
     * then it should not be included in {@link #setDeviceKeyAuthorizedNameSpaces(List)}
     *
     * @param authorizedDataElements A non-empty mapping from namespaces to a non-empty list of
     *                               <code>DataElementIdentifier</code>
     * @return The <code>MobileSecurityObjectGenerator</code>.
     */
    @NonNull
    public MobileSecurityObjectGenerator setDeviceKeyAuthorizedDataElements(
            @NonNull Map<String, List<String>> authorizedDataElements) {

        return null;
    }

    /**
     * Provides extra info for the mdoc authentication public key as part of the
     * <code>KeyInfo</code> portion of the <code>DeviceKeyInfo</code>.
     *
     * @param keyInfo A mapping to represent additional key information.
     * @return The <code>MobileSecurityObjectGenerator</code>.
     */
    @NonNull
    public MobileSecurityObjectGenerator setDeviceKeyInfo(@NonNull Map<Integer, byte[]> keyInfo) {
        return null;
    }

    /**
     * Sets the <code>ValidityInfo</code> structure which contains information related to the
     * validity of the MSO and its signature. This must be called before generating since this a
     * required component of the <code>MobileSecurityObject</code>.
     *
     * @param signed         The timestamp at which the MSO signature was created.
     * @param validFrom      The timestamp before which the MSO is not yet valid. This shall be
     *                       equal or later than the signed element.
     * @param validUntil     The timestamp after which the MSO is no longer valid. This shall be
     *                       later than the validFrom element.
     * @param expectedUpdate Optional: if provided, represents the timestamp at which the issuing
     *                       authority infrastructure expects to re-sign the MSO, else, null
     * @return
     */
    @NonNull
    public MobileSecurityObjectGenerator setValidityInfo(@NonNull Timestamp signed,
            @NonNull Timestamp validFrom, @NonNull Timestamp validUntil,
            @Nullable Timestamp expectedUpdate) {

        return null;
    }

    /**
     * Builds the <code>MobileSecurityObject</code> CBOR.
     *
     * <p>It's mandatory to call {@link #addDigestIDs(String, Map)}, {@link #setDeviceKey(PublicKey)},
     * {@link #setValidityInfo(Timestamp, Timestamp, Timestamp, Timestamp)} before this call.
     *
     * @return the bytes of <code>MobileSecurityObject</code> CBOR.
     * @exception IllegalStateException if required data hasn't been set using the setter
     * methods on this class.
     */
    @NonNull
    public byte[] generate() {

        return new byte[0];
    }

}
