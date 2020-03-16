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

package com.ul.ims.gmdl.offlinetransfer.utils

class Resource<T> {
    val status: Status
    val data: T?
    val message: String?

    constructor(status: Status, data: T, message: String) {
        this.status = status
        this.data = data
        this.message = message
    }

    constructor(status: Status) {
        this.status = status
        this.data = null
        this.message = null
    }

    constructor(status: Status, message: String) {
        this.status = status
        this.data = null
        this.message = message
    }

    companion object {

        fun <T> scanning(): Resource<T> {
            return Resource(Status.SCANNING)
        }


        fun <T> connecting(): Resource<T> {
            return Resource(Status.CONNECTING)
        }

        fun <T> transferring(): Resource<T> {
            return Resource(Status.TRANSFERRING)
        }

        fun <T> success(data: T): Resource<T> {
            return Resource(
                Status.TRANSFER_SUCCESSFUL,
                data,
                Status.TRANSFER_SUCCESSFUL.toString()
            )
        }

        fun <T> success(): Resource<T> {
            return Resource(Status.TRANSFER_SUCCESSFUL)
        }

        fun <T> error(msg: String): Resource<T> {
            return Resource(
                Status.TRANSFER_ERROR,
                msg
            )
        }

        fun <T> noDeviceFound(msg: String): Resource<T> {
            return Resource(
                Status.NO_DEVICE_FOUND,
                msg
            )
        }

        fun <T> askUserConsent(data: T): Resource<T> {
            return Resource(
                Status.ASK_USER_CONSENT,
                data,
                Status.ASK_USER_CONSENT.toString()
            )
        }

    }

    enum class Status {
        SCANNING,
        NO_DEVICE_FOUND,
        ASK_USER_CONSENT,
        CONNECTING,
        TRANSFERRING,
        TRANSFER_SUCCESSFUL,
        TRANSFER_ERROR
    }
}