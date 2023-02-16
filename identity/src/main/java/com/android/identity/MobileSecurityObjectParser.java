package com.android.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import co.nstant.in.cbor.model.DataItem;

/**
 * Helper class for parsing the bytes of <code>DeviceResponse</code>
 * <a href="http://cbor.io/">CBOR</a>
 * as specified in <em>ISO/IEC 18013-5</em> section 9.1.2 <em>Issuer data authentication</em>.
 */
public class MobileSecurityObjectParser {
    private byte[] mEncodedMobileSecurityObject;

    /**
     * Constructs a {@link MobileSecurityObjectParser}.
     */
    public MobileSecurityObjectParser() {
    }

    /**
     * Sets the bytes of the <code>MobileSecurityObject</code> CBOR.
     *
     * @param encodedMobileSecurityObject The bytes of <code>MobileSecurityObject</code>.
     * @return The <code>MobileSecurityObjectParser</code>.
     */
    @NonNull
    public MobileSecurityObjectParser setMobileSecurityObject(
            @NonNull byte[] encodedMobileSecurityObject) {
        mEncodedMobileSecurityObject = encodedMobileSecurityObject;
        return this;
    }

    /**
     * Parses the mobile security object.
     *
     * <p>It's mandatory to call {@link #setMobileSecurityObject(byte[])} before this call.
     *
     * @return a {@link MobileSecurityObject} with the parsed data.
     * @exception IllegalArgumentException if the given data isn't valid CBOR or not conforming
     * to the CDDL for its type.
     * @exception IllegalStateException if required data hasn't been set using the setter
     * methods on this class.
     */
    public @NonNull MobileSecurityObject parse() {
        if (mEncodedMobileSecurityObject == null) {
            throw new IllegalStateException("mobileSecurityObject has not been set");
        }
        MobileSecurityObject mso = new MobileSecurityObject();
        mso.parse(mEncodedMobileSecurityObject);
        return mso;
    }

    /**
     * An object used to represent data parsed from <code>MobileSecurityObject</code>
     * <a href="http://cbor.io/">CBOR</a>
     * as specified in <em>ISO/IEC 18013-5</em> section 9.1.2 <em>Issuer data authentication</em>
     */
    public static class MobileSecurityObject {
        private String mVersion;
        private String mDigestAlgorithm;
        private String mDocType;
        private Map<String, Map<Long, byte[]>> mValueDigests;
        private Map<Integer, byte[]> mDeviceKeyInfo;
        private Timestamp mSigned, mValidFrom, mValidUntil, mExpectedUpdate;

        MobileSecurityObject() {}

        /**
         * Gets the version string set in the <code>MobileSecurityObject</code> CBOR.
         *
         * @return the version string e.g. "1.0".
         */
        @NonNull
        public String getVersion() {
            return mVersion;
        }

        /**
         * Gets the digest algorithm set in the <code>MobileSecurityObject</code> CBOR.
         *
         * @return the digest algorithm e.g. "SHA-256".
         */
        @NonNull
        public String getDigestAlgorithm() { return mDigestAlgorithm; }

        /**
         * Gets the document type set in the <code>MobileSecurityObject</code> CBOR.
         *
         * @return the document type e.g. "org.iso.18013.5.1.mDL".
         */
        @NonNull
        public String getDocType() { return mDocType; }

        /**
         * Gets the set of namespaces provided in the ValueDigests map within the
         * <code>MobileSecurityObject</code> CBOR.
         *
         * @return the set of namespaces provided in the ValueDigests map.
         */
        @Nullable
        public Set<String> getValueDigestNamespaces() { return mValueDigests.keySet(); }

        @NonNull
        public Map<Long, byte[]> getDigestIDs(@NonNull String Namespace) {

            return null;
        }

        @NonNull
        public PublicKey getDeviceKey() {

            return null;
        }

        @Nullable
        public List<String> getDeviceKeyAuthorizedNameSpaces() {

            return null;
        }

        @Nullable
        public Map<String, List<String>> getDeviceKeyAuthorizedDataElements() {

            return null;
        }

        @Nullable
        public Map<Integer, byte[]> getDeviceKeyInfo() {

            return null;
        }

        /**
         * Gets the timestamp at which the MSO signature was created, as set in the
         * <code>MobileSecurityObject</code> CBOR.
         *
         * @return the timestamp at which the MSO signature was created.
         */
        @NonNull
        public Timestamp getSigned() { return mSigned; }

        /**
         * Gets the timestamp before which the MSO is not yet valid, as set in the
         * <code>MobileSecurityObject</code> CBOR.
         *
         * @return the timestamp before which the MSO is not yet valid.
         */
        @NonNull
        public Timestamp getValidFrom() { return mValidFrom; }

        /**
         * Gets the timestamp after which the MSO is no longer valid, as set in the
         * <code>MobileSecurityObject</code> CBOR.
         *
         * @return the timestamp after which the MSO is no longer valid.
         */
        @NonNull
        public Timestamp getValidUntil() { return mValidUntil; }

        /**
         * Gets the timestamp at which the issuing authority infrastructure expects to re-sign the
         * MSO, if provided in the <code>MobileSecurityObject</code> CBOR, else null.
         *
         * @return the timestamp at which the issuing authority infrastructure expects to re-sign
         * the MSO.
         */
        @Nullable
        public Timestamp getExpectedUpdate() { return mExpectedUpdate; }

        private void parseValueDigests(DataItem valueDigests) {

        }

        private void parseValidityInfo(DataItem validityInfo) {
            mSigned = Util.cborMapExtractDateTime(validityInfo, "signed");
            mValidFrom = Util.cborMapExtractDateTime(validityInfo, "validFrom");
            mValidUntil = Util.cborMapExtractDateTime(validityInfo, "validUntil");
            if (Util.cborMapHasKey(validityInfo, "expectedUpdate")) {
                mExpectedUpdate = Util.cborMapExtractDateTime(validityInfo, "expectedUpdate");
            } else {
                mExpectedUpdate = null;
            }
            // TODO assert the relative times are as expected
        }

        void parse(byte[] encodedMobileSecurityObject) {
            DataItem mso = Util.cborDecode(encodedMobileSecurityObject);

            mVersion = Util.cborMapExtractString(mso, "version");
            if (mVersion.compareTo("1.0") < 0) {
                throw new IllegalArgumentException("Given version '" + mVersion + "' not >= '1.0'");
            }

            mDigestAlgorithm = Util.cborMapExtractString(mso, "digestAlgorithm");
            final List<String> allowableDigestAlgorithms = List.of("SHA-256", "SHA-384", "SHA-512");
            if (!allowableDigestAlgorithms.contains(mDigestAlgorithm)) {
                throw new IllegalArgumentException("Given digest algorithm '" + mDigestAlgorithm +
                        "' one of " + allowableDigestAlgorithms);
            }

            mDocType = Util.cborMapExtractString(mso, "docType");
            parseValueDigests(Util.cborMapExtract(mso, "valueDigests"));
            parseValidityInfo(Util.cborMapExtract(mso, "validityInfo"));
        }
    }

}
