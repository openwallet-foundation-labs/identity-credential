/*
 * Copyright 2023 The Android Open Source Project
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
package com.android.identity.wallet.trustmanagement

import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import javax.security.auth.x500.X500Principal

/**
 * Extract the common name of a X500Principal (subject or issuer)
 */
fun X500Principal.getCommonName(defaultValue: String): String {
    return readRdn(this.name, BCStyle.CN, defaultValue)
}

/**
 * Extract the organisation of a X500Principal (subject or issuer)
 */
fun X500Principal.getOrganisation(defaultValue: String): String {
    return readRdn(this.name, BCStyle.O, defaultValue)
}

/**
 * Extract the organisational unit of a X500Principal (subject or issuer)
 */
fun X500Principal.organisationalUnit(defaultValue: String): String {
    return readRdn(this.name, BCStyle.OU, defaultValue)
}

/**
 * Extract the country code of a X500Principal (subject or issuer)
 */
fun X500Principal.countryCode(defaultValue: String): String {
    return readRdn(this.name, BCStyle.C, defaultValue)
}

/**
 * Read a relative distinguished name from a distinguished name
 */
private fun readRdn(name: String, field: ASN1ObjectIdentifier, defaultValue: String): String {
    val x500name = X500Name(name)
    for (rdn in x500name.getRDNs(field)) {
        val attributes = rdn.typesAndValues
        for (attribute in attributes) {
            return attribute.value.toString()
        }
    }
    return defaultValue
}