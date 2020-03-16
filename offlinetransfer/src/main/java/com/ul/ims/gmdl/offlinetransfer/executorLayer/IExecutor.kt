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

package com.ul.ims.gmdl.offlinetransfer.executorLayer

import com.ul.ims.gmdl.cbordata.interpreter.IDataInterpreter
import com.ul.ims.gmdl.offlinetransfer.transportLayer.ITransportLayer

/**
 *
 * Interface with the basic structure to execute a command sent by the VerifierExecutor or HolderExecutor.
 * A Command can be either a Request or a Response objects.
 *
 * **/

interface IExecutor {

    /**
     * Interpreter instance to be used by a executor to interpret the data received
     * **/
    var interpreter: IDataInterpreter?

    /**
     * Transport instance to be used by a executor to send data
     * **/
    var transportLayer : ITransportLayer?

    /**
     * Function called by the ITransportEventListener inside onReceive(data : ByteArray) or
     * onEvent(string : String, i : Int). It must use a Interpreter in order to translate
     * a byteArray into a Request or Response obj and then call either onRequest() or onResponse()
     *
     * @param data : ByteArray
     *
     * **/
    fun onCommand(data : ByteArray)

    /**
     * Function used to write data to the transport layer. As the transport variable is a conditional
     * variable, this is a function used for convenience, we must check if transportLayer is not null
     * before write to it.
     *
     * @param bytes : ByteArray
     *
     * **/
    fun sendData(bytes : ByteArray)

    // Helper function to display a cbor structure in HEX
    fun encodeToString(bytes: ByteArray) : String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    // Helper function used to generate formatted data for junit tests
    fun encodeToStringDebug(encoded: ByteArray): String {
        val sb = StringBuilder(encoded.size * 2)

        val iterator = encoded.iterator().withIndex()
        var newLineCounter = 0
        iterator.forEach { b ->
            sb.append("0x")
            sb.append(String.format("%02x", b.value))
            sb.append(".toByte()")

            if (iterator.hasNext()) {
                newLineCounter++
                sb.append(", ")

                if (newLineCounter == 5) {
                    sb.append("\n")
                    newLineCounter = 0
                }
            }
        }

        return sb.toString()
    }
}