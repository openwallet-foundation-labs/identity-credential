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

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * BLE data transport
 */
abstract class DataTransportBle extends DataTransport {
    private static final String TAG = "DataTransportBle";

    // The size of buffer for read() calls when using L2CAP sockets.
    static final int L2CAP_BUF_SIZE = 4096;
    protected UUID mServiceUuid;
    protected ConnectionMethodBle mConnectionMethod;

    public DataTransportBle(@NonNull Context context,
                            @Role int role,
                            @NonNull ConnectionMethodBle connectionMethod,
                            @NonNull DataTransportOptions options) {
        super(context, role, options);
        mConnectionMethod = connectionMethod;
    }

    void setServiceUuid(@NonNull UUID serviceUuid) {
        mServiceUuid = serviceUuid;
    }

    @Override
    public @NonNull ConnectionMethod getConnectionMethod() {
        return mConnectionMethod;
    }

}
