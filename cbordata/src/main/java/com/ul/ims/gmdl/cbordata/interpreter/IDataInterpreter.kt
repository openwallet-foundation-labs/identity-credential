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

package com.ul.ims.gmdl.cbordata.interpreter

import com.ul.ims.gmdl.cbordata.command.ICommand

/**
 * Interface with the foundation to implement a class that can interpret a byte array sent
 * by the transport layer and deserialize it as a Request or Response object.
 * Initially we'll have only one interpreter (CBOR) as it's the only data type
 * used on the request/response for MVR
 *
 * **/
interface IDataInterpreter {

    /**
     * Function responsible to interpret bytes, create a Request or Response object
     *
     * @param data: ByteArray
     * @return Either a Request or Response object
     *
     * **/
    fun interpret(data : ByteArray?) : ICommand?
}