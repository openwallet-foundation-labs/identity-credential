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
 * Enum of the different types of Credential Attributes
 */
sealed class CredentialAttributeType {
    object STRING : CredentialAttributeType()
    object NUMBER : CredentialAttributeType()
    object DATE : CredentialAttributeType()
    object DATE_TIME : CredentialAttributeType()
    object PICTURE : CredentialAttributeType()
    object BOOLEAN : CredentialAttributeType()
    class StringOptions(val options: List<StringOption>): CredentialAttributeType()
    class IntegerOptions(val options: List<IntegerOption>): CredentialAttributeType()
    class ComplexType(val typeName: String, val isArray: Boolean): CredentialAttributeType()
}