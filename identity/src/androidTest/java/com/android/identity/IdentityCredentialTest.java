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

import android.content.Context;
import android.icu.util.Calendar;
import android.os.Build.VERSION;
import androidx.annotation.Nullable;
import androidx.core.util.Preconditions;
import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Collectors;
import junit.framework.AssertionFailedError;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests {@link IdentityCredential}.
 */
@RunWith(Parameterized.class)
public class IdentityCredentialTest {

  private static final String CREDENTIAL_NAME = "fake credential for test";
  private static final int CIPHER_SUITE = CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256;

  private IdentityCredentialStore store;

  public IdentityCredentialTest(Object store, Object storeName) {
    this.store = Preconditions.checkNotNull((IdentityCredentialStore) store);
    Preconditions.checkNotNull((String) storeName); // only used for test name
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
    Assume.assumeTrue("Test fails on API <= 28", VERSION.SDK_INT >= 29 /* Android 10+ */);
    // Per b/234563696, I've confirmed these failing on:
    // Pixel 2 API 24
    // Pixel 2 API 26
    // Pixel 3 API 28
    checkStaticAuthData(10, 10000); // fails
    checkStaticAuthData(100, 1000); // flaky
    checkStaticAuthData(100, 10000); // fails
  }

  private void checkStaticAuthData(int numAuthKeys, int staticAuthDataSizeBytes)
          throws IdentityCredentialException {
    final int usesPerKey = 3;
    ProvisioningTest.createCredential(store, CREDENTIAL_NAME);
    try {
      IdentityCredential credential = Preconditions.checkNotNull(
              store.getCredentialByName(CREDENTIAL_NAME, CIPHER_SUITE));
      storeFakeStaticAuthData(credential, usesPerKey, numAuthKeys, staticAuthDataSizeBytes);

      // Check that this doesn't throw (it did throw in internal b/234563696).
      try {
        store.getCredentialByName(CREDENTIAL_NAME, CIPHER_SUITE);
      } catch (RuntimeException e) {
        fail(String.format(Locale.US,
                "Failed to load credential for %d authKeys with %d bytes static auth data each",
                numAuthKeys, staticAuthDataSizeBytes));
      }
    } finally {
      // deleteCredentialByName() is deprecated, but its suggested replacement
      // (credential.delete()) is unreliable because it requires loading the credential
      // first, which can fail (e.g. b/234563696).
      store.deleteCredentialByName(CREDENTIAL_NAME);
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

  // parameters: { store, storeName }
  @Parameters(name = "{1}")
  public static Collection<Object[]> parameters() {
    List<IdentityCredentialStore> resultStores = new ArrayList<>();
    Context appContext = androidx.test.InstrumentationRegistry.getTargetContext();
    IdentityCredentialStore defaultStore = IdentityCredentialStore.getInstance(appContext);
    resultStores.add(IdentityCredentialStore.getSoftwareInstance(appContext));
    IdentityCredentialStore hwStore = IdentityCredentialStore.getHardwareInstance(appContext);
    if (hwStore != null) {
      resultStores.add(hwStore);
    }
    if (IdentityCredentialStore.isDirectAccessSupported(appContext)) {
      resultStores.add(IdentityCredentialStore.getDirectAccessInstance(appContext));
    }
    // Usually, the default instance's implementation will be one of the earlier ones; on the
    // odd chance that it isn't, add it to make sure it is covered.
    if (resultStores.stream().noneMatch(o -> o.getClass().equals(defaultStore.getClass()))) {
      resultStores.add(0, defaultStore);
    }

    List<Object[]> result = resultStores
            .stream()
            .map(store -> new Object[]{store, store.getClass().getSimpleName()})
            .collect(Collectors.toList());
    return result;
  }
}
