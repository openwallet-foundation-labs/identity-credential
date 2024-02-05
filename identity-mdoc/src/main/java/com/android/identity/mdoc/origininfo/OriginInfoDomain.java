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

package com.android.identity.mdoc.origininfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.cbor.CborMap;
import com.android.identity.cbor.DataItem;

public class OriginInfoDomain extends OriginInfo {
    private static final String TAG = "OriginInfoDomain";

    static final int TYPE = 1;
    private final String mUrl;

    public OriginInfoDomain(String url) {
        mUrl = url;
    }

    public String getUrl() {
        return mUrl;
    }

    @NonNull
    @Override
    public DataItem encode() {
        return CborMap.Companion.builder()
                .put("cat", CAT)
                .put("type", TYPE)
                .putMap("details").put("domain", mUrl).end()
                .end().build();
    }

    @Nullable
    public static OriginInfoDomain decode(@NonNull DataItem oiDataItem) {
        long cat = oiDataItem.get("cat").getAsNumber();
        long type = oiDataItem.get("type").getAsNumber();
        if (!(cat == 1 && type == 1)) {
            throw new IllegalArgumentException(String.format("This CBOR object has the wrong " +
                    "category or type. Expected cat = 1, type = 1 for baseURL type but got " +
                    "cat = %d, type = %d", cat, type));
        }
        DataItem details = oiDataItem.get("details");
        return new OriginInfoDomain(details.get("domain").getAsTstr());
    }
}
