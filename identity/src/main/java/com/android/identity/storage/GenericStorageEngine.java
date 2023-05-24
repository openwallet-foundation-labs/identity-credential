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

package com.android.identity.storage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * An storage engine implemented by storing data in a directory.
 *
 * <p>Each file name in the given directory will the key name prefixed with
 * {@code IC_GenericStorageEngine_}.
 */
public class GenericStorageEngine implements StorageEngine {

    private final File mStorageDirectory;

    private static final String PREFIX = "IC_GenericStorageEngine_";

    /**
     * Creates a new {@link GenericStorageEngine}.
     *
     * @param storageDirectory the directory to store data files in.
     */
    public GenericStorageEngine(@NonNull File storageDirectory) {
        mStorageDirectory = storageDirectory;
    }

    private File getTargetFile(@NonNull String name) {
        try {
            String fileName = PREFIX + URLEncoder.encode(name, "UTF-8");
            return new File(mStorageDirectory, fileName);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unexpected UnsupportedEncodingException", e);
        }
    }

    @Nullable
    @Override
    public byte[] get(@NonNull String key) {
        File file = getTargetFile(key);
        try {
            if (!Files.exists(file.toPath())) {
                return null;
            }
            return Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    @Override
    public void put(@NonNull String key, @NonNull byte[] data) {
        File file = getTargetFile(key);
        try {
            // TODO: do this atomically
            Files.deleteIfExists(file.toPath());
            Files.write(file.toPath(), data, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new IllegalStateException("Error writing data", e);
        }
    }

    @Override
    public void delete(@NonNull String name) {
        File file = getTargetFile(name);
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            throw new IllegalStateException("Error deleting file", e);
        }
    }

    @Override
    public void deleteAll() {
        try {
            for (File file : Files.list(mStorageDirectory.toPath())
                    .map(Path::toFile)
                    .filter(File::isFile)
                    .collect(Collectors.toList())) {
                String name = file.getName();
                if (!name.startsWith(PREFIX)) {
                    continue;
                }
                Files.delete(file.toPath());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error deleting files", e);
        }
    }

    @NonNull
    @Override
    public Collection<String> enumerate() {
        ArrayList<String> ret = new ArrayList<>();
        try {
            for (File file : Files.list(mStorageDirectory.toPath())
                    .map(Path::toFile)
                    .filter(File::isFile)
                    .collect(Collectors.toList())) {
                String name = file.getName();
                if (!name.startsWith(PREFIX)) {
                    continue;
                }
                try {
                    String decodedName = URLDecoder.decode(name.substring(PREFIX.length()), "UTF-8");
                    ret.add(decodedName);
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error deleting files", e);
        }
        return ret;
    }
}
