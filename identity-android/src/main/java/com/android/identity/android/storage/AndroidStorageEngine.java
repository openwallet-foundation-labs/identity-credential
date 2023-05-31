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
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.identity.internal.Util;
import com.android.identity.storage.StorageEngine;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.builder.ArrayBuilder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;

/**
 * A storage engine on Android.
 *
 * <p>Values in the key/value store is stored encrypted on disk, using a hardware-backed
 * symmetric encryption key and AES-128 GCM.
 *
 * <p>Each file name in the given directory will be prefixed with {@code IC_AndroidStorageEngine_}.
 */
public class AndroidStorageEngine implements StorageEngine {

    private static final String PREFIX = "IC_AndroidStorageEngine_";

    private final Context mContext;
    private final File mStorageDirectory;
    private final boolean mUseEncryption;

    AndroidStorageEngine(@NonNull Context context,
                         @NonNull File storageDirectory,
                         boolean useEncryption) {
        mContext = context;
        mStorageDirectory = storageDirectory;
        mUseEncryption = useEncryption;
    }

    private @NonNull File getTargetFile(@NonNull String key) {
        try {
            String fileName = PREFIX + URLEncoder.encode(key, "UTF-8");
            return new File(mStorageDirectory, fileName);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unexpected UnsupportedEncodingException", e);
        }
    }

    private @NonNull SecretKey ensureSecretKey() {
        String keyAlias = PREFIX + "_KeyFor_" + mStorageDirectory;
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            KeyStore.Entry entry = ks.getEntry(keyAlias, null);
            if (entry != null) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }

            KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(128);
            kg.init(builder.build());
            return kg.generateKey();
        } catch (NoSuchProviderException
                 | InvalidAlgorithmParameterException
                 | NoSuchAlgorithmException
                 | CertificateException
                 | IOException
                 | UnrecoverableEntryException
                 | KeyStoreException e) {
            throw new IllegalStateException("Error loading secret key", e);
        }
    }

    // We prefix the data stored with a magic marker so we know whether it needs to be
    // decryption or not.
    //
    private static final int MAGIC_SIZE = 4;
    private static final byte[] MAGIC_ENCRYPTED = "Ienc".getBytes(StandardCharsets.UTF_8);
    private static final byte[] MAGIC_NOT_ENCRYPTED = "Iraw".getBytes(StandardCharsets.UTF_8);

    @Nullable
    @Override
    public byte[] get(@NonNull String key) {
        AtomicFile file = new AtomicFile(getTargetFile(key));
        try {
            byte[] data = file.readFully();
            if (data.length < MAGIC_SIZE) {
                throw new IllegalStateException("File too short for magic");
            }
            byte[] magic = Arrays.copyOfRange(data, 0, MAGIC_SIZE);
            if (Arrays.equals(magic, MAGIC_ENCRYPTED)) {
                return decrypt(ensureSecretKey(), Arrays.copyOfRange(data, MAGIC_SIZE, data.length));
            } else if (Arrays.equals(magic, MAGIC_NOT_ENCRYPTED)) {
                return Arrays.copyOfRange(data, MAGIC_SIZE, data.length);
            } else {
                throw new IllegalStateException("Unexpected magic " + Util.toHex(magic));
            }
        } catch(FileNotFoundException e) {
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    @Override
    public void put(@NonNull String key, @NonNull byte[] data) {
        AtomicFile file = new AtomicFile(getTargetFile(key));
        FileOutputStream outputStream = null;
        try {
            outputStream = file.startWrite();
            if (mUseEncryption) {
                outputStream.write(MAGIC_ENCRYPTED);
                outputStream.write(encrypt(ensureSecretKey(), data));
            } else {
                outputStream.write(MAGIC_NOT_ENCRYPTED);
                outputStream.write(data);
            }
            file.finishWrite(outputStream);
        } catch (IOException e) {
            if (outputStream != null) {
                file.failWrite(outputStream);
            }
            throw new IllegalStateException("Error writing data", e);
        }
    }

    @Override
    public void delete(@NonNull String key) {
        AtomicFile file = new AtomicFile(getTargetFile(key));
        file.delete();
    }

    @Override
    public void deleteAll() {
        for (File file : mStorageDirectory.listFiles()) {
            String name = file.getName();
            if (!name.startsWith(PREFIX)) {
                continue;
            }
            file.delete();
        }
    }

    @Override
    @NonNull
    public Collection<String> enumerate() {
        ArrayList<String> ret = new ArrayList<>();
        for (File file : mStorageDirectory.listFiles()) {
            String name = file.getName();
            if (!name.startsWith(PREFIX)) {
                continue;
            }
            try {
                String decodedName = URLDecoder.decode(name.substring(PREFIX.length()), "UTF-8");
                ret.add(decodedName);
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        }
        return ret;
    }

    /**
     * A builder for {@link AndroidStorageEngine}.
     */
    public static class Builder {

        private final Context mContext;
        private final File mStorageDirectory;
        private boolean mUseEncryption;

        /**
         * Creates a new builder.
         *
         * @param context application context.
         * @param storageDirectory the directory to store data files in.
         */
        public Builder(@NonNull Context context,
                       @NonNull File storageDirectory) {
            mContext = context;
            mStorageDirectory = storageDirectory;
            mUseEncryption = true;
        }

        /**
         * Sets whether to encrypt the values stored on disk.
         *
         * <p>Note that keys are not encrypted, only values.
         *
         * <p>By default this is set to {@code true}.
         *
         * @param useEncryption whether to encrypt values.
         * @return the builder.
         */
        public @NonNull Builder setUseEncryption(boolean useEncryption) {
            mUseEncryption = useEncryption;
            return this;
        }

        /**
         * Builds the {@link AndroidStorageEngine}.
         *
         * @return a {@link AndroidStorageEngine}.
         */
        public @NonNull AndroidStorageEngine build() {
            return new AndroidStorageEngine(mContext, mStorageDirectory, mUseEncryption);
        }
    }


    // Because some older Android versions have a buggy Android Keystore where encryption
    // only works with small amounts of data (b/234563696) chop the cleartext into smaller
    // chunks and encrypt them separately. We store the data using the following CDDL
    //
    //  StoredData = [+ bstr]
    //

    static final int CHUNKED_ENCRYPTED_MAX_CHUNK_SIZE = 16384;

    static private
    @NonNull byte[] decrypt(@NonNull SecretKey secretKey,
                            @NonNull byte[] encryptedData) {
        ByteArrayInputStream bais = new ByteArrayInputStream(encryptedData);
        List<DataItem> dataItems;
        try {
            dataItems = new CborDecoder(bais).decode();
        } catch (CborException e) {
            throw new IllegalStateException("Error decoding CBOR");
        }
        if (dataItems.size() != 1) {
            throw new IllegalStateException("Expected one item, found " + dataItems.size());
        }
        if (!(dataItems.get(0) instanceof Array)) {
            throw new IllegalStateException("Item is not a array");
        }
        Array array = (co.nstant.in.cbor.model.Array) dataItems.get(0);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (DataItem item : array.getDataItems()) {
                if (!(item instanceof ByteString)) {
                    throw new IllegalStateException("Item in inner array is not a bstr");
                }
                byte[] encryptedChunk = ((ByteString) item).getBytes();

                ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedChunk);
                byte[] iv = new byte[12];
                byteBuffer.get(iv);
                byte[] cipherText = new byte[encryptedChunk.length - 12];
                byteBuffer.get(cipherText);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
                byte[] decryptedChunk = cipher.doFinal(cipherText);
                baos.write(decryptedChunk);
            }
            return baos.toByteArray();

        } catch (InvalidAlgorithmParameterException
                 | NoSuchPaddingException
                 | BadPaddingException
                 | NoSuchAlgorithmException
                 | InvalidKeyException
                 | IOException
                 | IllegalBlockSizeException e) {
            throw new IllegalStateException("Error decrypting chunk", e);
        }
    }

    static private
    @NonNull byte[] encrypt(@NonNull SecretKey secretKey,
                            @NonNull byte[] data) {
        CborBuilder builder = new CborBuilder();
        ArrayBuilder<CborBuilder> arrayBuilder = builder.addArray();

        try {
            int offset = 0;
            boolean lastChunk = false;
            do {
                int chunkSize = data.length - offset;
                if (chunkSize <= CHUNKED_ENCRYPTED_MAX_CHUNK_SIZE) {
                    lastChunk = true;
                } else {
                    chunkSize = CHUNKED_ENCRYPTED_MAX_CHUNK_SIZE;
                }

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                // Produced cipherText includes auth tag
                byte[] cipherTextForChunk = cipher.doFinal(
                        data,
                        offset,
                        chunkSize);

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write(cipher.getIV());
                baos.write(cipherTextForChunk);
                byte[] cipherTextForChunkWithIV = baos.toByteArray();

                arrayBuilder.add(cipherTextForChunkWithIV);
                offset += chunkSize;
            } while (!lastChunk);
            return Util.cborEncode(builder.build().get(0));
        } catch (NoSuchPaddingException
                 | BadPaddingException
                 | NoSuchAlgorithmException
                 | InvalidKeyException
                 | IOException
                 | IllegalBlockSizeException e) {
            throw new IllegalStateException("Error encrypting data", e);
        }
    }

}
