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

package com.android.identity.android.storage;

import android.content.Context;
import android.util.AtomicFile;

import com.android.identity.internal.Util;
import com.android.identity.storage.StorageEngine;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class AndroidStorageTest {

    @Test
    public void testStorageImplementation() {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storage = new AndroidStorageEngine.Builder(context, storageDir).build();

        storage.deleteAll();

        Assert.assertEquals(0, storage.enumerate().size());

        Assert.assertNull(storage.get("foo"));
        byte[] data = new byte[] {1, 2, 3};
        storage.put("foo", data);
        Assert.assertArrayEquals(storage.get("foo"), data);

        Assert.assertEquals(1, storage.enumerate().size());
        Assert.assertEquals("foo", storage.enumerate().iterator().next());

        Assert.assertNull(storage.get("bar"));
        byte[] data2 = new byte[] {4, 5, 6};
        storage.put("bar", data2);
        Assert.assertArrayEquals(storage.get("bar"), data2);

        Assert.assertEquals(2, storage.enumerate().size());

        storage.delete("foo");
        Assert.assertNull(storage.get("foo"));
        Assert.assertNotNull(storage.get("bar"));

        Assert.assertEquals(1, storage.enumerate().size());

        storage.delete("bar");
        Assert.assertNull(storage.get("bar"));

        Assert.assertEquals(0, storage.enumerate().size());
    }

    @Test
    public void testPersistence() {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");
        StorageEngine storage = new AndroidStorageEngine.Builder(context, storageDir).build();

        storage.deleteAll();

        Assert.assertEquals(0, storage.enumerate().size());

        Assert.assertNull(storage.get("foo"));
        byte[] data = new byte[]{1, 2, 3};
        storage.put("foo", data);
        Assert.assertArrayEquals(storage.get("foo"), data);

        // Create a new StorageEngine instance and check that data is still there...
        storage = new AndroidStorageEngine.Builder(context, storageDir).build();
        Assert.assertArrayEquals(storage.get("foo"), data);
    }

    private static final String PREFIX = "IC_AndroidStorageEngine_";

    @Test
    public void testEncryption() throws IOException {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");

        final byte[] data = "xyz123".getBytes(StandardCharsets.UTF_8);

        // First try with a storage engine using encryption... we should not be able
        // to find the data in what is saved to disk.
        StorageEngine storage = new AndroidStorageEngine.Builder(context, storageDir)
                .setUseEncryption(true)
                .build();
        storage.deleteAll();
        storage.put("foo", data);
        File targetFile = new File(storageDir, PREFIX + URLEncoder.encode("foo", "UTF-8"));
        byte[] fileContents = new AtomicFile(targetFile).readFully();
        Assert.assertEquals(-1, Util.toHex(fileContents).indexOf(Util.toHex(data)));

        // Try again without encryption. The data should start at offset 4.
        storage = new AndroidStorageEngine.Builder(context, storageDir)
                .setUseEncryption(false)
                .build();
        storage.deleteAll();
        storage.put("foo", data);
        targetFile = new File(storageDir, PREFIX + URLEncoder.encode("foo", "UTF-8"));
        fileContents = new AtomicFile(targetFile).readFully();
        Assert.assertArrayEquals(data, Arrays.copyOfRange(fileContents, 4, fileContents.length));
    }

    @Test
    public void testEncryptionLargeValue() throws IOException {
        Context context = androidx.test.InstrumentationRegistry.getTargetContext();
        File storageDir = new File(context.getDataDir(), "ic-testing");

        // Store 2 MiB of data...
        final byte[] data = new byte[2*1024*1024];
        for (int n = 0; n < data.length; n++) {
            int value = n*3 + n + n*n;
            data[n] = (byte) (value & 0xff);
        }

        StorageEngine storage = new AndroidStorageEngine.Builder(context, storageDir)
                .setUseEncryption(true)
                .build();
        storage.deleteAll();
        storage.put("foo", data);

        byte[] retrievedData = storage.get("foo");

        Assert.assertArrayEquals(retrievedData, data);
    }

}
