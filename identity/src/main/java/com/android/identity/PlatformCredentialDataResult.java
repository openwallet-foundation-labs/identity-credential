package com.android.identity;

import android.annotation.SuppressLint;
import android.icu.util.Calendar;
import android.icu.util.GregorianCalendar;
import android.icu.util.TimeZone;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.Collection;

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class PlatformCredentialDataResult extends CredentialDataResult {

    private final android.security.identity.CredentialDataResult mResult;
    private final CredstoreEntries mDeviceSignedEntries;
    private final CredstoreEntries mIssuerSignedEntries;

    public PlatformCredentialDataResult(android.security.identity.CredentialDataResult platformResult) {
        mResult = platformResult;
        mDeviceSignedEntries = new CredstoreEntries(platformResult.getDeviceSignedEntries());
        mIssuerSignedEntries = new CredstoreEntries(platformResult.getIssuerSignedEntries());
    }

    @NonNull
    @Override
    public byte[] getDeviceNameSpaces() {
        return mResult.getDeviceNameSpaces();
    }

    @Nullable
    @Override
    public byte[] getDeviceMac() {
        return mResult.getDeviceMac();
    }

    @Nullable
    @Override
    public byte[] getDeviceSignature() {
        return null;
    }

    @NonNull
    @Override
    public byte[] getStaticAuthenticationData() {
        return mResult.getStaticAuthenticationData();
    }

    @NonNull
    @Override
    public Entries getDeviceSignedEntries() {
        return mDeviceSignedEntries;
    }

    @NonNull
    @Override
    public Entries getIssuerSignedEntries() {
        return mIssuerSignedEntries;
    }

    static class CredstoreEntries implements CredentialDataResult.Entries {
        android.security.identity.CredentialDataResult.Entries mEntries;

        @SuppressWarnings("deprecation")
        CredstoreEntries(android.security.identity.CredentialDataResult.Entries entries) {
            mEntries = entries;
        }

        @Override
        public @NonNull
        Collection<String> getNamespaces() {
            return mEntries.getNamespaces();
        }

        @Override
        public @NonNull Collection<String> getEntryNames(@NonNull String namespaceName) {
            return mEntries.getEntryNames(namespaceName);
        }

        @Override
        public @NonNull Collection<String> getRetrievedEntryNames(@NonNull String namespaceName) {
            return mEntries.getRetrievedEntryNames(namespaceName);
        }

        @Override
        @Status
        @SuppressLint("WrongConstant")
        public int getStatus(@NonNull String namespaceName, @NonNull String name) {
            return mEntries.getStatus(namespaceName, name);
        }

        @Override
        public @Nullable byte[] getEntry(@NonNull String namespaceName, @NonNull String name) {
            return mEntries.getEntry(namespaceName, name);
        }

        @Nullable
        @Override
        public String getEntryString(@NonNull String namespaceName, @NonNull String name) {
            byte[] value = getEntry(namespaceName, name);
            if (value == null) {
                return null;
            }
            return Util.cborDecodeString(value);
        }

        @Nullable
        @Override
        public byte[] getEntryBytestring(@NonNull String namespaceName, @NonNull String name) {
            byte[] value = getEntry(namespaceName, name);
            if (value == null) {
                return null;
            }
            return Util.cborDecodeByteString(value);
        }

        @Override
        public long getEntryInteger(@NonNull String namespaceName, @NonNull String name) {
            byte[] value = getEntry(namespaceName, name);
            if (value == null) {
                return 0;
            }
            return Util.cborDecodeLong(value);
        }

        @Override
        public boolean getEntryBoolean(@NonNull String namespaceName, @NonNull String name) {
            byte[] value = getEntry(namespaceName, name);
            if (value == null) {
                return false;
            }
            return Util.cborDecodeBoolean(value);
        }

        @Nullable
        @Override
        public Calendar getEntryCalendar(@NonNull String namespaceName, @NonNull String name) {
            byte[] value = getEntry(namespaceName, name);
            if (value == null) {
                return null;
            }
            Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
            calendar.setTimeInMillis(Util.cborDecodeDateTime(value).toEpochMilli());
            return calendar;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean isUserAuthenticationNeeded() {
            for (String namespaceName : mEntries.getNamespaces()) {
                for (String entryName : mEntries.getEntryNames(namespaceName)) {
                    if (mEntries.getStatus(namespaceName, entryName)
                            == android.security.identity.CredentialDataResult.Entries.STATUS_USER_AUTHENTICATION_FAILED) {
                        return true;
                    }
                }
            }
            return false;
        }

    }

}
