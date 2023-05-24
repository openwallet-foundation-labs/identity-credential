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

package com.android.identity.keystore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A repository of {@link KeystoreEngine} implementations.
 *
 * <p>This is used by to provide fine-grained control for which {@link KeystoreEngine}
 * implementation to use when loading keys using different implementations.
 */
public class KeystoreEngineRepository {

    List<KeystoreEngine> mImplementations = new ArrayList<>();

    /**
     * Constructs a new object.
     */
    public KeystoreEngineRepository() {
    }

    /**
     * Gets all implementations in the repository.
     *
     * @return A list of {@link KeystoreEngine} implementations in the repository.
     */
    public @NonNull List<KeystoreEngine> getImplementations() {
        return Collections.unmodifiableList(mImplementations);
    }

    /**
     * Gets a {@link KeystoreEngine} for a specific classname.
     *
     * @param className the classname for an implementation.
     * @return the implementation or {@code null} if no implementation has been registered.
     */
    public @Nullable KeystoreEngine getImplementation(@NonNull String className) {
        for (KeystoreEngine implementation : mImplementations) {
            if (implementation.getClass().getName().equals(className)) {
                return implementation;
            }
        }
        return null;
    }

    /**
     * Adds a {@link KeystoreEngine} to the repository.
     *
     * @param keystoreEngine an instance of a type implementing the {@link KeystoreEngine} interface.
     */
    public void addImplementation(@NonNull KeystoreEngine keystoreEngine) {
        mImplementations.add(keystoreEngine);
    }
}
