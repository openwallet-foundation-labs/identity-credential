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

package com.ul.ims.gmdl.issuerauthority.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "credential")
data class Credential(
    @PrimaryKey @ColumnInfo(name = "id") val credentialId: ByteArray,
    @ColumnInfo(name = "value") val credentialValue: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Credential

        if (!credentialId.contentEquals(other.credentialId)) return false
        if (!credentialValue.contentEquals(other.credentialValue)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = credentialId.contentHashCode()
        result = 31 * result + credentialValue.contentHashCode()
        return result
    }
}