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

package com.android.identity.securearea;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A repository of {@link SecureArea} implementations.
 *
 * <p>This is used by to provide fine-grained control for which {@link SecureArea}
 * implementation to use when loading keys and objects using different implementations.
 */
public class SecureAreaRepository {

    List<SecureArea> mImplementations = new ArrayList<>();

    /**
     * Constructs a new object.
     */
    public SecureAreaRepository() {
    }

    /**
     * Gets all implementations in the repository.
     *
     * @return A list of {@link SecureArea} implementations in the repository.
     */
    public @NonNull List<SecureArea> getImplementations() {
        return Collections.unmodifiableList(mImplementations);
    }

    /**
     * Gets a {@link SecureArea} for a specific classname.
     *
     * @param className the classname for an implementation.
     * @return the implementation or {@code null} if no implementation has been registered.
     */
    public @Nullable SecureArea getImplementation(@NonNull String className) {
        for (SecureArea implementation : mImplementations) {
            if (implementation.getClass().getName().equals(className)) {
                return implementation;
            }
        }
        return null;
    }

    /**
     * Adds a {@link SecureArea} to the repository.
     *
     * @param secureArea an instance of a type implementing the {@link SecureArea} interface.
     */
    public void addImplementation(@NonNull SecureArea secureArea) {
        mImplementations.add(secureArea);
    }
}
