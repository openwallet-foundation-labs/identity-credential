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

package com.android.identity.wwwreader;

//import android.util.Log;

//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;

import java.util.List;

import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Number;

/**
 * A class representing the OriginInfo structure exchanged by the mdoc and the mdoc reader.
 */
public abstract class OriginInfo {
    private static final String TAG = "OriginInfo";

    /**
     * The constant used to specify how the current engagement structure is delivered.
     */
    public static final long CAT_DELIVERY = 0;

    /**
     * The constant used to specify how the other party engagement structure has been received.
     */
    public static final long CAT_RECEIVE = 1;

    /**
     * Specifies whether the OriginInfoOptions are about this engagement or the one
     * received previously
     *
     * @return one of {@link #CAT_DELIVERY} or {@link #CAT_RECEIVE}.
     */
    public abstract long getCat();

    abstract
    DataItem encode();

    static
    OriginInfo decode(DataItem oiDataItem) {
        if (!(oiDataItem instanceof co.nstant.in.cbor.model.Map)) {
            throw new IllegalArgumentException("Top-level CBOR is not a Map");
        }
        long type = Util.cborMapExtractNumber(oiDataItem, "type");
        switch ((int) type) {
            /*
            case OriginInfoQr.TYPE:
                return OriginInfoQr.decode(oiDataItem);
            case OriginInfoNfc.TYPE:
                return OriginInfoNfc.decode(oiDataItem);
            */
            case OriginInfoWebsite.TYPE:
                return OriginInfoWebsite.decode(oiDataItem);
        }
        //Log.w(TAG, "Unsupported type " + type);
        return null;
    }
}