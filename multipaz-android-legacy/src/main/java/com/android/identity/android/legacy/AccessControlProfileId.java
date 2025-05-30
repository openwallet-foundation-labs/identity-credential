/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.identity.android.legacy;

import androidx.annotation.Nullable;
import java.util.Objects;

/**
 * A class used to wrap an access control profile identifiers.
 */
public class AccessControlProfileId {
    private int mId = 0;

    /**
     * Constructs a new object holding a numerical identifier.
     *
     * @param id the identifier.
     */
    public AccessControlProfileId(int id) {
        this.mId = id;
    }

    /**
     * Gets the numerical identifier wrapped by this object.
     *
     * @return the identifier.
     */
    public int getId() {
        return this.mId;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccessControlProfileId)) {
            return false;
        }
        AccessControlProfileId that = (AccessControlProfileId) o;
        return mId == that.mId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mId);
    }
}
