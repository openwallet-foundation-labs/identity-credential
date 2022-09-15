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

import static com.android.identity.IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.icu.util.Calendar;

import androidx.core.util.Preconditions;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Locale;
import java.util.Random;

import org.junit.Test;

/**
 * Tests that the implementation works when the application is using many and large static
 * authentication data blobs.
 */
public class StoreStaticAuthenticationDataTest {

  private static final String CREDENTIAL_NAME = "fake credential for test";
  private static final int CIPHER_SUITE = CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256;

  private IdentityCredentialStore mStore;

  public StoreStaticAuthenticationDataTest() {
    mStore = Util.getIdentityCredentialStore(androidx.test.InstrumentationRegistry.getTargetContext());
  }

  /**
   * Tests storing static authentication data of varying size for varying numbers of auth keys.
   */
  @MediumTest
  @Test
  public void storeStaticAuthenticationData() throws IdentityCredentialException {
    checkStaticAuthData(1, 100); // 1 key x 100 bytes of static authentication data = 100 Bytes
    checkStaticAuthData(3, 100);
    checkStaticAuthData(100, 100); // 100 keys x 100 Bytes = 10 KBytes
    checkStaticAuthData(1, 100000); // 1 key x 100 KBytes
  }

  @LargeTest
  @Test
  public void storeStaticAuthenticationDataLarge() throws IdentityCredentialException {
    // Some of these tests used to fail with older Android Keystore implementations (API
    // 28 or earlier), see b/234563696 for details.
    checkStaticAuthData(10, 10000);
    checkStaticAuthData(100, 1000);
    checkStaticAuthData(100, 10000);
  }

  private void checkStaticAuthData(int numAuthKeys, int staticAuthDataSizeBytes)
          throws IdentityCredentialException {
    final int usesPerKey = 3;
    mStore.deleteCredentialByName(CREDENTIAL_NAME);
    ProvisioningTest.createCredential(mStore, CREDENTIAL_NAME);
    try {
      IdentityCredential credential = Preconditions.checkNotNull(
              mStore.getCredentialByName(CREDENTIAL_NAME, CIPHER_SUITE));
      storeFakeStaticAuthData(credential, usesPerKey, numAuthKeys, staticAuthDataSizeBytes);

      // Check that this doesn't throw (it did throw in internal b/234563696).
      try {
        mStore.getCredentialByName(CREDENTIAL_NAME, CIPHER_SUITE);
      } catch (RuntimeException e) {
        e.printStackTrace();
        fail(String.format(Locale.US,
                "Failed to load credential for %d authKeys with %d bytes static auth data each",
                numAuthKeys, staticAuthDataSizeBytes));
      }
    } finally {
      // deleteCredentialByName() is deprecated, but its suggested replacement
      // (credential.delete()) is unreliable because it requires loading the credential
      // first, which can fail (e.g. b/234563696).
      mStore.deleteCredentialByName(CREDENTIAL_NAME);
    }
  }

  private static void storeFakeStaticAuthData(IdentityCredential credential,
          int maxUsesPerKey, int numKeys, int staticAuthDataSizeBytes)
          throws UnknownAuthenticationKeyException {
    // Arbitrary but deterministic fake staticAuthData for testing
    Random random = new Random(31337 + numKeys);
    credential.setAvailableAuthenticationKeys(numKeys, maxUsesPerKey);
    Collection<X509Certificate> authKeys = credential.getAuthKeysNeedingCertification();
    assertEquals(numKeys, authKeys.size());

    for (X509Certificate authKey : authKeys) {
      byte[] staticAuthData = new byte[staticAuthDataSizeBytes];
      random.nextBytes(staticAuthData);
      Calendar expirationDate = Calendar.getInstance();
      expirationDate.add(Calendar.YEAR, 1);
      credential.storeStaticAuthenticationData(authKey, expirationDate, staticAuthData);
    }
  }
}
