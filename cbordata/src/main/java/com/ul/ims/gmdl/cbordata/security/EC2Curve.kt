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

package com.ul.ims.gmdl.cbordata.security

import java.io.Serializable

class EC2Curve : Serializable {
    var id: Any? = null
    var xCoordinate: ByteArray? = null
    var yCoordinate: ByteArray? = null
    var privateKey: ByteArray? = null

    override fun equals(other: Any?): Boolean {
        other?.let {
            if (other is EC2Curve) {
                val otherX = other.xCoordinate
                val otherY = other.yCoordinate
                val otherPK = other.privateKey
                if (other.id == id) {
                    val thisX = xCoordinate
                    thisX?.let {
                        otherX?.let {
                            if (otherX.contentEquals(thisX)) {
                                val thisY = yCoordinate
                                thisY?.let {
                                    otherY?.let {
                                        if (otherY.contentEquals(thisY)) {
                                            val thisPK = privateKey
                                            thisPK?.let {
                                                otherPK?.let {
                                                    if (thisPK.contentEquals(otherPK)) {
                                                        return true
                                                    }
                                                }
                                            }
                                            // PK may be null
                                            return otherPK == null
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return false
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + (xCoordinate?.contentHashCode() ?: 0)
        result = 31 * result + (yCoordinate?.contentHashCode() ?: 0)
        result = 31 * result + (privateKey?.contentHashCode() ?: 0)
        return result
    }
}
