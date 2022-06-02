package com.android.identity;

import static com.android.identity.IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256;
import static org.junit.Assert.assertEquals;

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
  public void storeStaticAuthenticationData()
          throws IdentityCredentialException {
    checkStaticAuthData(1, 100); // 1 key x 100 bytes of static authentication data = 100 Bytes
    checkStaticAuthData(3, 100);
    checkStaticAuthData(100, 100); // 100 keys x 100 Bytes = 10 KBytes
    checkStaticAuthData(1, 100000); // 1 key x 100 KBytes
  }

  @LargeTest
  @Test
  public void storeStaticAuthenticationDataLarge()
          throws IdentityCredentialException {
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
      IdentityCredential credential = Preconditions.checkNotNull(getCredentialIfPresent());

      storeFakeStaticAuthData(credential, usesPerKey, numAuthKeys, staticAuthDataSizeBytes);

      // Check that this doesn't throw (it did throw in internal b/234563696).
      try {
        getCredentialIfPresent();
      } catch (RuntimeException e) {
        String msg = String.format(Locale.US,
                "Failed to load credential for %d authKeys with %d bytes static auth data each",
                numAuthKeys, staticAuthDataSizeBytes);
        throw new IdentityCredentialException(msg, e);
      }
    } finally {
      deleteCredentialFromStoreIfPresent();
    }
  }

  @Before
  public void setUp() {
    deleteCredentialFromStoreIfPresent(); // clean up in case a previous test didn't
  }

  @After
  public void tearDown() {
    deleteCredentialFromStoreIfPresent();
    store = null;
  }

  private static void storeFakeStaticAuthData(IdentityCredential credential,
          int maxUsesPerKey, int numKeys, int staticAuthDataSizeBytes) {
    Random random = new Random(31337 + numKeys);
    credential.setAvailableAuthenticationKeys(numKeys, maxUsesPerKey);
    Collection<X509Certificate> authKeys = credential.getAuthKeysNeedingCertification();
    assertEquals(numKeys, authKeys.size());

    for (X509Certificate authKey : authKeys) {
      byte[] staticAuthData = new byte[staticAuthDataSizeBytes];
      random.nextBytes(staticAuthData);
      Calendar expirationDate = Calendar.getInstance();
      expirationDate.add(Calendar.YEAR, 1);
      try {
        credential.storeStaticAuthenticationData(authKey, expirationDate, staticAuthData);
      } catch (UnknownAuthenticationKeyException e) {
        throw new AssertionFailedError("Auth authKey not recognized: " + e);
      }
    }
  }

  private @Nullable IdentityCredential getCredentialIfPresent() {
    try {
      return store.getCredentialByName(CREDENTIAL_NAME, CIPHER_SUITE);
    } catch (CipherSuiteNotSupportedException e) {
      throw new AssertionFailedError("Unexpected exception: " + e);
    }
  }

  private void deleteCredentialFromStoreIfPresent() {
    store.deleteCredentialByName(CREDENTIAL_NAME);
    // The above method is deprecated, but works even if the credential is corrupted, whereas the
    // following can throw RuntimeException in that case (e.g. b/234563696):
    // IdentityCredential credential = getCredentialIfPresent();
    // if (credential != null) {
    //   credential.delete("fake challenge for deletion".getBytes(UTF_8));
    // }
  }

  // parameters: { store, storeName }
  @Parameters(name = "{1}")
  public static Collection<Object[]> stores() {
    List<IdentityCredentialStore> stores = new ArrayList<>();
    Context appContext = androidx.test.InstrumentationRegistry.getTargetContext();
    IdentityCredentialStore defaultStore = IdentityCredentialStore.getInstance(appContext);
    // Usually, the default instance's implementation will be one of these; on the odd chance
    // that it isn't, add it to make sure it is covered.
    if (!Arrays.asList(SoftwareIdentityCredentialStore.class, HardwareIdentityCredentialStore.class)
            .contains(defaultStore.getClass())) {
      stores.add(defaultStore);
    }
    stores.add(IdentityCredentialStore.getSoftwareInstance(appContext));
    IdentityCredentialStore hwStore = IdentityCredentialStore.getHardwareInstance(appContext);
    if (hwStore != null) {
      stores.add(hwStore);
    }
    if (IdentityCredentialStore.isDirectAccessSupported(appContext)) {
      stores.add(IdentityCredentialStore.getDirectAccessInstance(appContext));
    }

    List<Object[]> result = stores
            .stream()
            .map(store -> new Object[]{store, store.getClass().getSimpleName()})
            .collect(Collectors.toList());
    return result;
  }
}
