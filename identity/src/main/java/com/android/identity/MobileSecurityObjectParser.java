package com.android.identity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.security.PublicKey;
import java.util.List;
import java.util.Map;

public class MobileSecurityObjectParser {

    /**
     * Constructs a {@link MobileSecurityObjectParser}.
     */
    public MobileSecurityObjectParser() {}

    /**
     * Sets the bytes of the <code>MobileSecurityObject</code> CBOR.
     *
     * @param encodedMobileSecurityObject The bytes of <code>MobileSecurityObject</code>.
     * @return The <code>MobileSecurityObjectParser</code>.
     */
    @NonNull
    public MobileSecurityObjectParser setMobileSecurityObject(
            @NonNull byte[] encodedMobileSecurityObject) {

        return null;
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

        return null;
    }

    /**
     * An object used to represent data parsed from <code>MobileSecurityObject</code>
     * <a href="http://cbor.io/">CBOR</a>
     * as specified in <em>ISO/IEC 18013-5</em> section 9.1.2 <em>Issuer data authentication</em>
     */
    public static class MobileSecurityObject {
        MobileSecurityObject() {}

        @NonNull
        public String getVersion() {

            return null;
        }

        @NonNull
        public String getDigestAlgorithm() {

            return null;
        }

        @NonNull
        public String getDocType() {

            return null;
        }

        @Nullable
        public List<String> getValueDigestNamespaces() {

            return null;
        }

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

        @NonNull
        public Timestamp getSigned() {

            return null;
        }

        @NonNull
        public Timestamp getValidFrom() {

            return null;
        }

        @NonNull
        public Timestamp getValidUntil() {

            return null;
        }

        @Nullable
        public Timestamp getExpectedUpdate() {

            return null;
        }

        void parse(byte[] encodedMobileSecurityObject) {

        }
    }

}
