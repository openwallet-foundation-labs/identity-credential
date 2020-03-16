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

package com.ul.ims.gmdl.cbordata.deviceEngagement.security

// TODO: Revisit when Implementing Session Encryption
object CurveIdentifiers {
    val curveIds = hashMapOf(
        11 to "curveP256",
        14 to "curveP384",
        15 to "curveP521",
        21 to "brainpoolP256r1",
        22 to "brainpoolP320r1",
        23 to "brainpoolP384r1",
        24 to "brainpoolP512r1"
    )
}