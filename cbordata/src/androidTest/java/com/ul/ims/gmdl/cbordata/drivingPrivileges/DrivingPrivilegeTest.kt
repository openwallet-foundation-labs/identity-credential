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

package com.ul.ims.gmdl.cbordata.drivingPrivileges

import com.ul.ims.gmdl.cbordata.credential.CategoryCodesEnum
import com.ul.ims.gmdl.cbordata.utils.CborUtils
import com.ul.ims.gmdl.cbordata.utils.DateUtils
import org.junit.Assert
import org.junit.Test

class DrivingPrivilegeTest {

    @Test
    fun toCborDataItemTest() {
        val drivingPriv = DrivingPrivilege.Builder()
            .setVehicleCategory(CategoryCodesEnum.A)
            .setIssueDate(DateUtils.genIssueDate())
            .setExpiryDate(DateUtils.genExpiryDate())
            .build()

        val cborDataItem = drivingPriv.toDataItem()
        Assert.assertNotNull(cborDataItem)

        val cborEncoded = drivingPriv.encode()
        Assert.assertNotNull(cborEncoded)
        cborEncoded?.let {
            println(CborUtils.encodeToStringDebug(cborEncoded))
        }

        val drivingPriv2 = DrivingPrivilege.Builder()
            .fromCborBytes(cborEncoded)
            .build()

        Assert.assertNotNull(drivingPriv2)
        Assert.assertNotNull(drivingPriv2.vehicleCategory)
        Assert.assertNotNull(drivingPriv2.issueDate)
        Assert.assertNotNull(drivingPriv2.expiryData)

        val cborEncoded2 = drivingPriv2.encode()
        cborEncoded2?.let {
            println(CborUtils.encodeToStringDebug(cborEncoded2))
        }
    }
}