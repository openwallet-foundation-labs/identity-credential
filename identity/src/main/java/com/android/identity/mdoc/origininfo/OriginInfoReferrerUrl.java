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

package com.android.identity.mdoc.origininfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.internal.Util;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;

public class OriginInfoReferrerUrl extends OriginInfo {
    private static final String TAG = "OriginInfoReferrerUrl";

    static final int TYPE = 1;
    private final String mUrl;

    public OriginInfoReferrerUrl(String url) {
        mUrl = url;
    }

    public String getUrl() {
        return mUrl;
    }

    @NonNull
    @Override
    public DataItem encode() {
        return new CborBuilder()
                .addMap()
                .put("cat", CAT)
                .put("type", TYPE)
                .put("details", mUrl)
                .end()
                .build().get(0);
    }

    @Nullable
    public static OriginInfoReferrerUrl decode(@NonNull DataItem oiDataItem) {
        if (!(oiDataItem instanceof Map)) {
            throw new IllegalArgumentException("Top-level CBOR is not an map");
        }
        long cat = Util.cborMapExtractNumber(oiDataItem, "cat");
        int type = (int) Util.cborMapExtractNumber(oiDataItem, "type");
        if (!(cat == 1 && type == 1)) {
            throw new IllegalArgumentException(String.format("This CBOR object has the wrong " +
                    "category or type. Expected cat = 1, type = 1 for baseURL type but got " +
                    "cat = %d, type = %d", cat, type));
        }
        String url = Util.cborMapExtractString(oiDataItem, "details");
        return new OriginInfoReferrerUrl(url);
    }
}
