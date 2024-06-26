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
package com.android.identity.mdoc.engagement

import com.android.identity.cbor.ArrayBuilder
import com.android.identity.cbor.Cbor
import com.android.identity.cbor.CborArray
import com.android.identity.cbor.CborBuilder
import com.android.identity.cbor.CborMap
import com.android.identity.crypto.EcPublicKey
import com.android.identity.mdoc.connectionmethod.ConnectionMethod
import com.android.identity.mdoc.origininfo.OriginInfo

/**
 * Helper to generate `DeviceEngagement` or `ReaderEngagement` CBOR.
 *
 * @param eSenderKey The ephemeral key used by the device (when generating
 * `DeviceEngagement`) or the reader (when generating `ReaderEngagement`).
 * @param version the version to use.
 */
class EngagementGenerator(
    private val eSenderKey: EcPublicKey,
    private val version: String
) {
    private var deviceRetrievalMethodsArrayBuilder: ArrayBuilder<CborBuilder> = CborArray.builder()
    private var originInfoArrayBuilder: ArrayBuilder<CborBuilder> = CborArray.builder()

    /**
     * Adds connection methods to the engagement.
     *
     * @param connectionMethods A list with instances derived from [ConnectionMethod].
     * @return the generator.
     */
    fun addConnectionMethods(connectionMethods: List<ConnectionMethod>): EngagementGenerator {
        for (connectionMethod in connectionMethods) {
            deviceRetrievalMethodsArrayBuilder.add(
                Cbor.decode(connectionMethod.toDeviceEngagement()))
        }
        return this
    }

    /**
     * Adds origin infos to the engagement.
     *
     * @param originInfos A list with instances derived from [OriginInfo].
     * @return the generator.
     */
    fun addOriginInfos(originInfos: List<OriginInfo>): EngagementGenerator {
        for (originInfo in originInfos) {
            originInfoArrayBuilder.add(originInfo.encode())
        }
        return this
    }

    /**
     * Generates the binary Engagement structure.
     *
     * @return the bytes of the `Engagement` structure.
     */
    fun generate(): ByteArray {
        val encodedCoseKey = Cbor.encode(eSenderKey.toCoseKey(emptyMap()).toDataItem())
        val security = CborArray.builder()
            .add(1) // cipher suite
            .addTaggedEncodedCbor(encodedCoseKey)
            .end().build()
        val mapBuilder = CborMap.builder()
        mapBuilder
            .put(0, version)
            .put(1, security)
        val deviceRetrievalMethodsArray = deviceRetrievalMethodsArrayBuilder.end().build()
        if (!deviceRetrievalMethodsArray.asArray.isEmpty()) {
            mapBuilder.put(2, deviceRetrievalMethodsArray)
        }
        val originInfoArray = originInfoArrayBuilder.end().build()
        if (!originInfoArray.asArray.isEmpty()) {
            mapBuilder.put(5, originInfoArray)
        }
        return Cbor.encode(mapBuilder.end().build())
    }

    companion object {
        const val ENGAGEMENT_VERSION_1_0 = "1.0"
        const val ENGAGEMENT_VERSION_1_1 = "1.1"
    }
}
