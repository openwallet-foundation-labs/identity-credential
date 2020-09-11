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

package com.ul.ims.gmdl.cbordata.namespace

object MdlNamespace : INamespace {
    override val namespace = "org.iso.18013.5.1"

    override val items = linkedMapOf(
        Pair("family_name", false),
        Pair("given_name", false),
        Pair("birth_date", false),
        Pair("issue_date", false),
        Pair("expiry_date", false),
        Pair("issuing_country", false),
        Pair("issuing_authority", false),
        Pair("driving_privileges", false),
        Pair("portrait", false),
        Pair("document_number", false),
        Pair("age_over_18", false),
        Pair("age_over_21", false)
    )
}