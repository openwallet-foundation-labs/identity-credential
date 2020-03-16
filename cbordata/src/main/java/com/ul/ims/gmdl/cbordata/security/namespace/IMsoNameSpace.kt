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

package com.ul.ims.gmdl.cbordata.security.namespace

/**
 * Interface representing a listOfNameSpaces. Each listOfNameSpaces object must have a defined a unique
 * identifier and supported data items.
 * For now, we'll have only one listOfNameSpaces supported 'nl.mvr.rdw'.
 * **/
interface IMsoNameSpace {
    /**
     * Namespace unique identifier
     * **/
    val namespace : String

    /**
     * Namespace items.
     * **/
    val items : Map<Int, ByteArray>
}