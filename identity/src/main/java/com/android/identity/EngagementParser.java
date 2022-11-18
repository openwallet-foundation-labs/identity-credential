/*
 * Copyright 2022 The Android Open Source Project
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

import androidx.annotation.NonNull;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

/**
 * Helper for parsing <code>DeviceEngagement</code> or <code>ReaderEngagement</code> CBOR.
 */
public class EngagementParser {
    private static final String TAG = "EngagementParser";

    private final byte[] mEncodedEngagement;

    /**
     * Constructs a new parser.
     *
     * @param encodedEngagement the bytes of the <code>Engagement</code> structure.
     */
    public EngagementParser(@NonNull byte[] encodedEngagement) {
        mEncodedEngagement = encodedEngagement;
    }

    /**
     * Parses the given <code>Engagement</code> structure.
     *
     * @return A {@link Engagement} object with the parsed data.
     */
    @NonNull public Engagement parse() {
        EngagementParser.Engagement engagement = new EngagementParser.Engagement();
        engagement.parse(mEncodedEngagement);
        return engagement;
    }

    /**
     * An object used to represent data extract from an <code>Engagement</code> structure.
     */
    public class Engagement {
        private static final String TAG = "Engagement";
        private String mVersion;
        private PublicKey mESenderKey;
        private byte[] mESenderKeyBytes;
        private List<ConnectionMethod> mConnectionMethods = new ArrayList<>();
        private List<OriginInfo> mOriginInfos = new ArrayList<>();

        Engagement() {
        }

        /**
         * Gets the version string set in the <code>Engagement</code> CBOR.
         *
         * @return the version string in the engagement e.g. "1.0" or "1.1".
         */
        @NonNull
        public String getVersion() {
            return mVersion;
        }

        /**
         * Gets the ephemeral key used by the other side.
         *
         * @return The ephemeral key used by the device (when parsing <code>DeviceEngagement</code>)
         *     or the reader (when generating <code>ReaderEngagement</code>).
         */
        @NonNull
        public PublicKey getESenderKey() {
            return mESenderKey;
        }

        /**
         * Gets the encoding of the key that was sent from the other side.
         *
         * <p>The returned data are the bytes of <code>ESenderKeyBytes</code> which is defined
         * as <code>#6.24(bstr .cbor ESenderKey)</code> where <code>ESenderKey</code> is a
         * <code>COSE_Key</code>.
         *
         * @return The encoding of the ephemeral reader key that was sent from the mdoc
         *     (when parsing <code>DeviceEngagement</code>) or the mdoc reader (when
         *     generating <code>ReaderEngagement</code>).
         */
        @NonNull
        public byte[] getESenderKeyBytes() {
            return mESenderKeyBytes;
        }

        /**
         * Gets the connection methods listed in the engagement.
         *
         * @return a list of {@link ConnectionMethod}-derived instances.
         */
        @NonNull
        public List<ConnectionMethod> getConnectionMethods() {
            return mConnectionMethods;
        }

        /**
         * Gets the origin infos listed in the engagement.
         *
         * @return A list of {@link OriginInfo}-derived instances.
         */
        @NonNull
        public List<OriginInfo> getOriginInfos() {
            return mOriginInfos;
        }

        void parse(@NonNull byte[] encodedEngagement) {
            DataItem map = Util.cborDecode(encodedEngagement);
            if (!(map instanceof co.nstant.in.cbor.model.Map)) {
                throw new IllegalArgumentException("Top-level Engagement CBOR is not a map");
            }

            mVersion = Util.cborMapExtractString(map, 0);

            List<DataItem> securityItems = Util.cborMapExtractArray(map, 1);
            if (securityItems.size() < 2) {
                throw new IllegalArgumentException("Expected at least two items in Security array");
            }
            if (!(securityItems.get(0) instanceof co.nstant.in.cbor.model.UnsignedInteger)) {
                throw new IllegalArgumentException("First item in Security array is not a number");
            }
            int cipherSuite = ((co.nstant.in.cbor.model.UnsignedInteger) securityItems.get(0)).getValue().intValue();
            if (cipherSuite != 1) {
                throw new IllegalArgumentException("Expected cipher suite 1, got " + cipherSuite);
            }
            if (!(securityItems.get(1) instanceof co.nstant.in.cbor.model.ByteString) ||
                    securityItems.get(1).getTag().getValue() != 24) {
                throw new IllegalArgumentException("Second item in Security array is not a tagged bstr");
            }
            ByteString eSenderKeyBytes = ((ByteString) securityItems.get(1));
            DataItem coseKey = Util.cborDecode(eSenderKeyBytes.getBytes());
            mESenderKey = Util.coseKeyDecode(coseKey);
            mESenderKeyBytes = Util.cborEncode(Util.cborBuildTaggedByteString(eSenderKeyBytes.getBytes()));

            if (Util.cborMapHasKey(map, 2)) {
                List<DataItem> connectionMethodItems = Util.cborMapExtractArray(map, 2);
                for (DataItem cmDataItem : connectionMethodItems) {
                    ConnectionMethod connectionMethod = ConnectionMethod.fromDeviceEngagement(cmDataItem);
                    if (connectionMethod != null) {
                        mConnectionMethods.add(connectionMethod);
                    }
                }
            }

            if (Util.mdocVersionCompare(mVersion, "1.1") >= 0) {
                // 18013-7 defines key 5 as having origin info
                if (Util.cborMapHasKey(map, 5)) {
                    List<DataItem> originInfoItems = Util.cborMapExtractArray(map, 5);
                    for (DataItem oiDataItem : originInfoItems) {
                        OriginInfo originInfo = OriginInfo.decode(oiDataItem);
                        if (originInfo != null) {
                            mOriginInfos.add(originInfo);
                        }
                    }
                }
            } else {
                if (Util.cborMapHasKey(map, 5)) {
                    Logger.w(TAG,
                            String.format("Ignoring key 5 in Engagement as version is set to %s. "
                                    + "The mdoc application producing this DeviceEngagement is "
                                    + "not compliant to ISO/IEC 18013-5:2021.",
                                    mVersion));
                }
            }
        }
    }
}
