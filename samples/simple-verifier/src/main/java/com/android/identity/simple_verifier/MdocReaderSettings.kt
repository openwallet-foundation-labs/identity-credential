/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.identity.simple_verifier

class MdocReaderSettings private constructor(
    private val mRequestedAge: AgeVerificationType,
) {

    internal fun getAgeDisplayString(): String {
        return when (mRequestedAge) {
            AgeVerificationType.Over18 -> "Age over 18"
            AgeVerificationType.Over21 -> "Age over 21"
        }
    }

    internal fun getAgeRequested(): AgeVerificationType {
        return mRequestedAge
    }

    class Builder {

        private var mRequestedAge: AgeVerificationType = AgeVerificationType.Over18

        /**
         * Sets the requested age for age verification. If not set, the default is age verification
         * over 18.
         *
         * @param requestedAge either [AgeVerificationType.Over18] or [AgeVerificationType.Over21]
         * @return the modified builder
         */
        fun setAgeVerificationType(requestedAge: AgeVerificationType): Builder {
            mRequestedAge = requestedAge
            return this
        }

        fun build(): MdocReaderSettings {
            return MdocReaderSettings(mRequestedAge)
        }
    }
}

enum class AgeVerificationType() {
    Over18,
    Over21
}