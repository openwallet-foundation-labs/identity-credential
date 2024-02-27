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

package com.android.identity.credentialtype

/**
 * Enumeration of the different types of Credential Attributes
 *
 * Attributes in credentials can have different types and this
 * enumeration contains a type-system generic enough to be
 * used across various credential formats. This is useful for
 * wallet and reader user interfaces which wants to provide UI
 * for inputting or displaying credentials attributes.
 */
sealed class CredentialAttributeType {
    object String : CredentialAttributeType()
    object Number : CredentialAttributeType()
    object Date : CredentialAttributeType()
    object DateTime : CredentialAttributeType()
    object Picture : CredentialAttributeType()
    object Boolean : CredentialAttributeType()
    object ComplexType : CredentialAttributeType()
    class StringOptions(val options: List<StringOption>) : CredentialAttributeType()
    class IntegerOptions(val options: List<IntegerOption>) : CredentialAttributeType()
}