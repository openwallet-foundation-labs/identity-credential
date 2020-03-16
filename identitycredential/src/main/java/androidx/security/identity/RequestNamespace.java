/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.security.identity;

import androidx.annotation.NonNull;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * An object that contains a set of requests for data entries in one namespace. This is used to
 * request data from a {@link IdentityCredential}.
 *
 * @see IdentityCredential#getEntries
 */
public class RequestNamespace {

    private String mNamespace;
    private Collection<Pair<String, Boolean>> mEntries = new ArrayList<>();

    private RequestNamespace(@NonNull String namespace) {
        this.mNamespace = namespace;
    }

    /**
     * Gets the requested namespace name.
     *
     * @returns the requested namespace name.
     */
    public String getNamespaceName() {
        return mNamespace;
    }

    /**
     * Gets the requested names.
     *
     * @returns the request names.
     */
    public Collection<String> getEntryNames() {
        ArrayList<String> entries = new ArrayList<>();
        for (Pair<String, Boolean> pair : mEntries) {
            entries.add(pair.first);
        }
        return entries;
    }

    /**
     * Gets whether authenticated has been requested for the given entry.
     *
     * If the entry hasn't been requested, IllegalArgumentException will be thrown.
     *
     * @returns true if authentication has been requested, false otherwise
     */
    public boolean getEntryAuthentication(String entryName) {
        // TODO: could use a HashMap to speed this up.
        for (Pair<String, Boolean> pair : mEntries) {
            if (pair.first.equals(entryName)) {
                return pair.second;
            }
        }
        throw new IllegalArgumentException("No entry with the given name");
    }

    Collection<Pair<String, Boolean>> getEntries() {
        return Collections.unmodifiableCollection(mEntries);
    }

    boolean hasEntry(String requestedEntryName) {
        // TODO: could use a HashMap to speed this up.
        for (Pair<String, Boolean> pair : mEntries) {
            if (pair.first.equals(requestedEntryName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A builder for {@link RequestNamespace}.
     */
    public static final class Builder {
        private RequestNamespace mEntryNamespace;

        /**
         * Creates a new builder for a given namespace.
         *
         * @param namespace The namespace to use, e.g. {@code org.iso.18013-5.2019}.
         */
        public Builder(@NonNull String namespace) {
            this.mEntryNamespace = new RequestNamespace(namespace);
        }

        /**
         * Adds a new entry to the builder.
         *
         * @param name The name of the entry, e.g. {@code height}.
         * @return The builder.
         */
        public @NonNull Builder addEntryName(@NonNull String name) {
            mEntryNamespace.mEntries.add(new Pair<>(name, true));
            return this;
        }

        /**
         * Adds a new entry to the builder.
         *
         * <p>This is like {@link #addEntryName(String)} but the resulting data element won't be in
         * the CBOR returned by {@link IdentityCredential.GetEntryResult#getAuthenticatedData()}
         * during a credential presentation.</p>
         *
         * <p>This might be useful if the integrity of the data element is proved by e.g. an issuer
         * signature.</p>
         *
         * @param name The name of the entry, e.g. {@code height}.
         * @return The builder.
         */
        public @NonNull Builder addEntryNameNoAuthentication(@NonNull String name) {
            mEntryNamespace.mEntries.add(new Pair<>(name, false));
            return this;
        }

        /**
         * Creates a new {@link RequestNamespace} with all the entries added to the builder.
         *
         * @return A new {@link RequestNamespace} instance.
         */
        public @NonNull RequestNamespace build() {
            return mEntryNamespace;
        }
    }
}
