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

import co.nstant.in.cbor.model.DataItem;
import com.android.identity.util.Logger;

/**
 * A class representing the OriginInfo structure exchanged by the mdoc and the mdoc reader.
 */
public abstract class OriginInfo {
    private static final String TAG = "OriginInfo";

    protected static final long CAT = 1;

    public abstract @NonNull DataItem encode();

    public static @Nullable OriginInfo decode(@NonNull DataItem oiDataItem) {
        if (!(oiDataItem instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalArgumentException("Top-level CBOR is not a Map");
        }
        long type = Util.cborMapExtractNumber(oiDataItem, "type");
        switch ((int) type) {
            case OriginInfoReferrerUrl.TYPE:
                return OriginInfoReferrerUrl.decode(oiDataItem);
            case OriginInfoBaseUrl.TYPE:
                return OriginInfoBaseUrl.decode(oiDataItem);
        }
        Logger.w(TAG, "Unsupported type " + type);
        return null;
    }
}
