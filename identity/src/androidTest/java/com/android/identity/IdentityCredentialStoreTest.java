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

import static com.android.identity.IdentityCredentialStore.getDefaultInstance;
import static com.android.identity.IdentityCredentialStoreCapabilities.FEATURE_VERSION_202009;
import static com.android.identity.IdentityCredentialStoreCapabilities.FEATURE_VERSION_202101;
import static com.android.identity.IdentityCredentialStoreCapabilities.FEATURE_VERSION_202201;
import static com.android.identity.IdentityCredentialStoreCapabilities.FEATURE_VERSION_BASE;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class IdentityCredentialStoreTest {

  private static final List<Integer> FEATURE_VERSIONS =
          Collections.unmodifiableList(Arrays.asList(
                  FEATURE_VERSION_BASE,
                  FEATURE_VERSION_202009,
                  FEATURE_VERSION_202101,
                  FEATURE_VERSION_202201));

  private Context appContext;

  @Before
  public void setUp() {
    appContext = androidx.test.InstrumentationRegistry.getTargetContext();
  }

  @Test
  public void getDefaultInstance_defaultsToBaseVersion() {
    IdentityCredentialStore defaultStore = getDefaultInstance(appContext);
    IdentityCredentialStore baseStore = getDefaultInstance(appContext, FEATURE_VERSION_BASE);
    assertEquals(baseStore.getClass(), defaultStore.getClass());
    assertEquals(baseStore.getCapabilities().getFeatureVersion(),
            defaultStore.getCapabilities().getFeatureVersion());
  }

  @Test
  public void getDefaultInstance_isHardwareOrSoftwareBacked() {
    for (int featureVersion : FEATURE_VERSIONS) {
      IdentityCredentialStore store = getDefaultInstance(appContext, featureVersion);
      List<Class<? extends IdentityCredentialStore>> expectedClasses = Arrays.asList(
              SoftwareIdentityCredentialStore.class, HardwareIdentityCredentialStore.class);
      assertTrue("Unexpected store impl: " + store.getClass(),
              expectedClasses.contains(store.getClass()));
    }
  }

  @Test
  public void getDefaultInstanceWithUnsupportedFeatureVersion() {
    try {
      // Ask for a featureVersion larger than any supported one.
      getDefaultInstance(appContext, Integer.MAX_VALUE);
      fail();
    } catch (IllegalArgumentException expected) {
      // pass
    }
  }

  @Test
  public void getDefaultInstanceWithSupportedFeatureVersion() {
    // Assertions below rely on ascending order
    List<Integer> sortedVersions = FEATURE_VERSIONS.stream().sorted().collect(toList());
    Integer lowestSoftwareVersion = null;
    int previousVersionSeen = Integer.MIN_VALUE;
    for (int requestedVersion : sortedVersions) {
      IdentityCredentialStore store = getDefaultInstance(
              appContext, requestedVersion);
      int actualVersion = store.getCapabilities().getFeatureVersion();

      // Requested featureVersion should be supported.
      assertTrue("Expected value >= " + requestedVersion + ", got " + actualVersion,
              actualVersion >= requestedVersion);

      // If version X falls back to software, versions Y >= X should, too.
      if (store.getCapabilities().isHardwareBacked()) {
        assertNull("Version " + lowestSoftwareVersion + " falls back to software, but later " +
                        "version " + requestedVersion + " unexpectedly supported in hardware.",
                lowestSoftwareVersion);
      } else {
        lowestSoftwareVersion = requestedVersion;
      }

      // Supported features should not decrease as requested features increase.
      assertTrue("Capabilities version decreased at requested version " + requestedVersion,
               actualVersion >= previousVersionSeen);
    }
  }

}
