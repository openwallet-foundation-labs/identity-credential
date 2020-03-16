/*
 * Copyright (C) 2019 Google LLC
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

package com.ul.ims.gmdl.offlinetransfer.transportLayer;

import com.ul.ims.gmdl.offlinetransfer.executorLayer.IExecutorEventListener;

import java.io.IOException;

/**
 * Basic Interface that all Offline Transport Implementations must implement.
 * **/
public interface ITransportLayer {

    /**
     * Initialization for both mDL Holder and Verifier.
     *
     * **/
    void inititalize(byte[] publicKeyHash);

    /**
     * Method to send data to the other connected device
     * **/
    void write(byte[] data) throws IOException, ParseException;

    /**
     * When the data transfer is finished, we close the connection
     * **/
    void close() throws IOException;


    void setEventListener(IExecutorEventListener eventListener);

    void closeConnection();
}