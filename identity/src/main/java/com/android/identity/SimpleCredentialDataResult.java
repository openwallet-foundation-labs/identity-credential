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

package com.android.identity;

import android.annotation.SuppressLint;
import android.icu.util.Calendar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collection;

class SimpleCredentialDataResult extends CredentialDataResult {

    @SuppressWarnings("deprecation")
    ResultData mDeviceSignedResult;

    @SuppressWarnings("deprecation")
    ResultData mIssuerSignedResult;

    CredstoreEntries mDeviceSignedEntries;
    CredstoreEntries mIssuerSignedEntries;

    @SuppressWarnings("deprecation")
    SimpleCredentialDataResult(ResultData deviceSignedResult, ResultData issuerSignedResult) {
        mDeviceSignedResult = deviceSignedResult;
        mIssuerSignedResult = issuerSignedResult;
        mDeviceSignedEntries = new CredstoreEntries(deviceSignedResult);
        mIssuerSignedEntries = new CredstoreEntries(issuerSignedResult);
    }

    @Override
    public @NonNull byte[] getDeviceNameSpaces() {
        return mDeviceSignedResult.getAuthenticatedData();
    }

    @Override
    public @Nullable byte[] getDeviceMac() {
        return mDeviceSignedResult.getMessageAuthenticationCode();
    }

    @Override
    public @Nullable byte[] getDeviceSignature() {
        return mDeviceSignedResult.getEcdsaSignature();
    }

    @Override
    public @NonNull byte[] getStaticAuthenticationData() {
        return mDeviceSignedResult.getStaticAuthenticationData();
    }

    @Override
    public @NonNull CredentialDataResult.Entries getDeviceSignedEntries() {
        return mDeviceSignedEntries;
    }

    @Override
    public @NonNull CredentialDataResult.Entries getIssuerSignedEntries() {
        return mIssuerSignedEntries;
    }

    static class CredstoreEntries implements CredentialDataResult.Entries {
        @SuppressWarnings("deprecation")
        ResultData mResultData;

        @SuppressWarnings("deprecation")
        CredstoreEntries(ResultData resultData) {
            mResultData = resultData;
        }

        @Override
        public @NonNull Collection<String> getNamespaces() {
            return mResultData.getNamespaces();
        }

        @Override
        public @NonNull Collection<String> getEntryNames(@NonNull String namespaceName) {
            return mResultData.getEntryNames(namespaceName);
        }

        @Override
        public @NonNull Collection<String> getRetrievedEntryNames(@NonNull String namespaceName) {
            Collection<String> ret = mResultData.getRetrievedEntryNames(namespaceName);
            if (ret != null) {
                return ret;
            }
            throw new IllegalArgumentException("No entries for name space " + namespaceName);
        }

        @Override
        @Status
        @SuppressWarnings("deprecation")
        @SuppressLint("WrongConstant")
        public int getStatus(@NonNull String namespaceName, @NonNull String name) {
            return mResultData.getStatus(namespaceName, name);
        }

        @Override
        public @Nullable byte[] getEntry(@NonNull String namespaceName, @NonNull String name) {
            return mResultData.getEntry(namespaceName, name);
        }

        @Nullable
        @Override
        public String getEntryString(@NonNull String namespaceName, @NonNull String name) {
            return mResultData.getEntryString(namespaceName, name);
        }

        @Nullable
        @Override
        public byte[] getEntryBytestring(@NonNull String namespaceName, @NonNull String name) {
            return mResultData.getEntryBytestring(namespaceName, name);
        }

        @Override
        public long getEntryInteger(@NonNull String namespaceName, @NonNull String name) {
            return mResultData.getEntryInteger(namespaceName, name);
        }

        @Override
        public boolean getEntryBoolean(@NonNull String namespaceName, @NonNull String name) {
            return mResultData.getEntryBoolean(namespaceName, name);
        }

        @Nullable
        @Override
        public Calendar getEntryCalendar(@NonNull String namespaceName, @NonNull String name) {
            return mResultData.getEntryCalendar(namespaceName, name);
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isUserAuthenticationNeeded() {
            for (String namespaceName : mResultData.getNamespaces()) {
                for (String entryName : mResultData.getEntryNames(namespaceName)) {
                    if (mResultData.getStatus(namespaceName, entryName)
                            == ResultData.STATUS_USER_AUTHENTICATION_FAILED) {
                        return true;
                    }
                }
            }
            return false;
        }

    }

}
