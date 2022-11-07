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
import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.security.PublicKey;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.builder.MapBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.UnsignedInteger;

/**
 * Helper to generate <code>DeviceEngagement</code> or <code>ReaderEngagement</code> CBOR.
 */
public final class EngagementGenerator {
    private static final String TAG = "EngagementGenerator";
    private final String mVersion;
    private PublicKey mESenderKey;
    private ArrayBuilder<CborBuilder> mConnectionMethodsArrayBuilder;
    private ArrayBuilder<CborBuilder> mOriginInfoArrayBuilder;
    private int mNumConnectionMethods = 0;
    private int mNumOriginInfos = 0;

    public static final String ENGAGEMENT_VERSION_1_0 = "1.0";
    public static final String ENGAGEMENT_VERSION_1_1 = "1.1";

    /** @hidden */
    @Retention(RetentionPolicy.SOURCE)
    @StringDef(value = {ENGAGEMENT_VERSION_1_0, ENGAGEMENT_VERSION_1_1})
    public @interface EngagementVersion {
    }

    /**
     * Helper class for building Engagement structures.
     *
     * @param ESenderKey The ephemeral key used by the device (when generating
     *                   <code>DeviceEngagement</code>) or the reader (when generating
     *                   <code>ReaderEngagement</code>).
     * @param version the version to use.
     */
    public EngagementGenerator(@NonNull PublicKey ESenderKey,
                               @EngagementVersion @NonNull String version) {
        mESenderKey = ESenderKey;
        mVersion = version;

        mConnectionMethodsArrayBuilder = new CborBuilder().addArray();
        mOriginInfoArrayBuilder = new CborBuilder().addArray();
    }

    /**
     * Adds a connection method to the engagement.
     *
     * @param connectionMethod An instance of a type derived from {@link ConnectionMethod}.
     * @return the generator.
     */
    public @NonNull
    EngagementGenerator addConnectionMethod(@NonNull ConnectionMethod connectionMethod) {
        mConnectionMethodsArrayBuilder.add(connectionMethod.toDeviceEngagement());
        mNumConnectionMethods++;
        return this;
    }

    /**
     * Adds origin info to the engagement.
     *
     * @param originInfo An instance of a type derived from {@link OriginInfo}.
     * @return the generator.
     */
    public @NonNull
    EngagementGenerator addOriginInfo(@NonNull OriginInfo originInfo) {
        mOriginInfoArrayBuilder.add(originInfo.encode());
        mNumOriginInfos++;
        return this;
    }

    /**
     * Generates the binary Engagement structure.
     *
     * @return the bytes of the <code>Engagement</code> structure.
     */
    public @NonNull
    byte[] generate() {
        DataItem eDeviceKeyBytes = Util.cborBuildTaggedByteString(
                Util.cborEncode(Util.cborBuildCoseKey(mESenderKey)));

        DataItem securityDataItem = new CborBuilder()
                .addArray()
                .add(1) // cipher suite
                .add(eDeviceKeyBytes)
                .end()
                .build().get(0);

        CborBuilder builder = new CborBuilder();
        MapBuilder<CborBuilder> map = builder.addMap();
        // TODO: support other versions...
        map.put(0, mVersion);
        map.put(new UnsignedInteger(1), securityDataItem);
        if (mNumConnectionMethods > 0) {
            map.put(new UnsignedInteger(2), mConnectionMethodsArrayBuilder.end().build().get(0));
        }
        if (mNumOriginInfos > 0) {
            map.put(new UnsignedInteger(5), mOriginInfoArrayBuilder.end().build().get(0));
        }
        map.end();
        return Util.cborEncode(builder.build().get(0));
    }

}
