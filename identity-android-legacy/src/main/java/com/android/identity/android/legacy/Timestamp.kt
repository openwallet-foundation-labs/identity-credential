/*
 * Copyright 2022 The Android Open Source Project
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
package com.android.identity.android.legacy

import androidx.annotation.Keep

/**
 * Represents a single instant in time. Ideally, we'd use `java.time.Instant`, but we cannot
 * do so until we move to API level 26.
 *
 * TODO: using Kotlin's Instant type instead.
 */
class Timestamp private constructor(private val epochMillis: Long) {
    /**
     * @return this represented as the number of milliseconds since midnight, January 1, 1970 UTC.
     */
    fun toEpochMilli(): Long = epochMillis

    override fun toString(): String = "Timestamp{epochMillis=$epochMillis}"

    override fun equals(other: Any?): Boolean =
        other is Timestamp && other.epochMillis == epochMillis

    override fun hashCode(): Int = java.lang.Long.hashCode(epochMillis)

    companion object {
        /**
         * @return a `Timestamp` representing the current time
         */
        @JvmStatic
        fun now(): Timestamp = Timestamp(System.currentTimeMillis())

        /**
         * @param epochMillis A time represented as the number of milliseconds since midnight,
         * January 1, 1970 UTC
         * @return a `Timestamp` representing the given time
         */
        @JvmStatic
        fun ofEpochMilli(epochMillis: Long): Timestamp = Timestamp(epochMillis)
    }
}