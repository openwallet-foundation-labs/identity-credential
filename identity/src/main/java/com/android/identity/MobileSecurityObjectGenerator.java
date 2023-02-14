package com.android.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.UnicodeString;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MobileSecurityObjectGenerator {

    private final String mDigestAlgorithm;
    private final String mDocType;

    private final CborBuilder mValueDigestsBuilder = new CborBuilder();
    private final MapBuilder<CborBuilder> mValueDigestsOuter = mValueDigestsBuilder.addMap();
    private Boolean digestEmpty = true;

    private final List<String> mAuthorizedNameSpaces = new ArrayList<>();
    private final Map<String, List<String>> mAuthorizedDataElements = new HashMap<>();
    private final Map<Integer, byte[]> mKeyInfo = new HashMap<>();

    private PublicKey mDeviceKey;
    private Timestamp mSigned, mValidFrom, mValidUntil, mExpectedUpdate;

    /**
     * Constructs a new {@link MobileSecurityObjectGenerator}.
     *
     * @param digestAlgorithm The digest algorithm identifier. Must be one of {"SHA-256", "SHA-384", "SHA-512"}.
     * @param docType The document type.
     * @exception IllegalArgumentException if the <code>digestAlgorithm</code> is not one of
     *                                     {"SHA-256", "SHA-384", "SHA-512"}.
     */
    public MobileSecurityObjectGenerator(@NonNull String digestAlgorithm, @NonNull String docType) {
        final List<String> allowableDigestAlgorithms = List.of("SHA-256", "SHA-384", "SHA-512");
        if (!allowableDigestAlgorithms.contains(digestAlgorithm)) {
            throw new IllegalArgumentException("digestAlgorithm must be one of " +
                    allowableDigestAlgorithms);
        }

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
     * @exception IllegalArgumentException if the <code>digestIDs</code> is empty.
     */
    @NonNull
    public MobileSecurityObjectGenerator addDigestIDs(@NonNull String nameSpace,
            @NonNull Map<Long, String> digestIDs) {

        if (digestIDs.isEmpty()) {
            throw new IllegalArgumentException("digestIDs must not be empty");
        }
        digestEmpty = false;

        MapBuilder<MapBuilder<CborBuilder>> valueDigestsInner = mValueDigestsOuter.putMap(nameSpace);
        for (Long digestID : digestIDs.keySet()) {
            valueDigestsInner.put(digestID, digestIDs.get(digestID));
        }
        valueDigestsInner.end();
        return this;
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
        mDeviceKey = deviceKey;
        return this;
    }

    /**
     * Populates the <code>AuthorizedNameSpaces</code> portion of the <code>keyAuthorizations</code>
     * within <code>DeviceKeyInfo</code>. This gives authorizations to full namespaces
     * included in the <code>authorizedNameSpaces</code> array. If authorization is given for a full
     * namespace, that namespace shall not be included in 
     * {@link #setDeviceKeyAuthorizedDataElements(Map)}.
     *
     * @param authorizedNameSpaces A list of namespaces which should be given authorization.
     * @return The <code>MobileSecurityObjectGenerator</code>.
     */
    @NonNull
    public MobileSecurityObjectGenerator setDeviceKeyAuthorizedNameSpaces(
            @NonNull List<String> authorizedNameSpaces) {

        mAuthorizedNameSpaces.clear();
        mAuthorizedNameSpaces.addAll(authorizedNameSpaces);
        return this;
    }

    /**
     * Populates the <code>AuthorizedDataElements</code> portion of the <code>keyAuthorizations</code>
     * within <code>DeviceKeyInfo</code>. This gives authorizations to data elements
     * included in the <code>authorizedDataElements</code> mapping. If a namespace is included here,
     * then it should not be included in {@link #setDeviceKeyAuthorizedNameSpaces(List)}
     *
     * @param authorizedDataElements A mapping from namespaces to a list of
     *                               <code>DataElementIdentifier</code>
     * @return The <code>MobileSecurityObjectGenerator</code>.
     */
    @NonNull
    public MobileSecurityObjectGenerator setDeviceKeyAuthorizedDataElements(
            @NonNull Map<String, List<String>> authorizedDataElements) {

        mAuthorizedDataElements.clear();
        mAuthorizedDataElements.putAll(authorizedDataElements);
        return this;
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
        mKeyInfo.clear();
        mKeyInfo.putAll(keyInfo);
        return this;
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

        mSigned = signed;
        mValidFrom = validFrom;
        mValidUntil = validUntil;
        mExpectedUpdate = expectedUpdate;
        return this;
    }

    @NonNull
    private CborBuilder generateDeviceKeyBuilder() {
        CborBuilder deviceKeyBuilder = new CborBuilder();
        MapBuilder<CborBuilder> deviceKeyMapBuilder = deviceKeyBuilder.addMap();
        deviceKeyMapBuilder.put("deviceKey", mDeviceKey.getEncoded());

        if (!mAuthorizedNameSpaces.isEmpty() | !mAuthorizedDataElements.isEmpty()) {
            MapBuilder<MapBuilder<CborBuilder>> keyAuthMapBuilder = deviceKeyMapBuilder.putMap("keyAuthorizations");
            if (!mAuthorizedNameSpaces.isEmpty()) {
                ArrayBuilder<MapBuilder<MapBuilder<CborBuilder>>> authNameSpacesArrayBuilder =
                        keyAuthMapBuilder.putArray("nameSpaces");
                for (String namespace : mAuthorizedNameSpaces) {
                    authNameSpacesArrayBuilder.add(namespace);
                }
                authNameSpacesArrayBuilder.end();
            }

            if (!mAuthorizedDataElements.isEmpty()) {
                MapBuilder<MapBuilder<MapBuilder<CborBuilder>>> authDataElemOuter =
                        keyAuthMapBuilder.putMap("dataElements");
                for (String namespace : mAuthorizedDataElements.keySet()) {
                    ArrayBuilder<MapBuilder<MapBuilder<MapBuilder<CborBuilder>>>> authDataElemInner =
                            authDataElemOuter.putArray(namespace);
                    for (String dataElemIdentifier : mAuthorizedDataElements.get(namespace)) {
                        authDataElemInner.add(dataElemIdentifier);
                    }
                    authDataElemInner.end();
                }
                authDataElemOuter.end();
            }
            keyAuthMapBuilder.end();
        }

        if (!mKeyInfo.isEmpty()) {
            MapBuilder<MapBuilder<CborBuilder>> keyInfoMapBuilder = deviceKeyMapBuilder.putMap("keyInfo");
            for (Integer label : mKeyInfo.keySet()) {
                keyInfoMapBuilder.put(label, mKeyInfo.get(label));
            }
            keyInfoMapBuilder.end();
        }

        deviceKeyMapBuilder.end();
        return deviceKeyBuilder;
    }

    @NonNull
    private CborBuilder generateValidityInfoBuilder() {
        CborBuilder validityInfoBuilder = new CborBuilder();
        MapBuilder<CborBuilder> validityMapBuilder = validityInfoBuilder.addMap();
        validityMapBuilder.put("signed", mSigned.toEpochMilli());
        validityMapBuilder.put("validFrom", mValidFrom.toEpochMilli());
        validityMapBuilder.put("validUntil", mValidUntil.toEpochMilli());
        if (mExpectedUpdate != null) {
            validityMapBuilder.put("expectedUpdate", mExpectedUpdate.toEpochMilli());
        }
        validityMapBuilder.end();
        return validityInfoBuilder;
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
        if (digestEmpty) {
            throw new IllegalStateException("Must call addDigestIDs before generating");
        } else if (mDeviceKey == null) {
            throw new IllegalStateException("Must call setDeviceKey before generating");
        } else if (mSigned == null) {
            throw new IllegalStateException("Must call setValidityInfo before generating");
        }

        CborBuilder msoBuilder = new CborBuilder();
        MapBuilder<CborBuilder> msoMapBuilder = msoBuilder.addMap();
        msoMapBuilder.put("version", "1.0");
        msoMapBuilder.put("digestAlgorithm", mDigestAlgorithm);
        msoMapBuilder.put("docType", mDocType);
        msoMapBuilder.put(new UnicodeString("valueDigests"), mValueDigestsBuilder.build().get(0));
        msoMapBuilder.put(new UnicodeString("deviceKeyInfo"), generateDeviceKeyBuilder().build().get(0));
        msoMapBuilder.put(new UnicodeString("validityInfo"), generateValidityInfoBuilder().build().get(0));
        msoMapBuilder.end();

        return Util.cborEncodeWithoutCanonicalizing(msoBuilder.build().get(0));
    }

}
