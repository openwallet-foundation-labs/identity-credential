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
package com.android.javacard.mdl.provision;

import com.android.javacard.mdl.MdlPresentationPkgStore;
import com.android.javacard.mdl.SEProvider;
import javacard.framework.AID;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.AESKey;
import javacard.security.ECPrivateKey;
import javacard.security.KeyBuilder;
import javacard.security.KeyPair;

/**
 * This class represents the ISO 18013-5 compliant document entry in the direct access store. It
 * stores credential keys and storage key for the document and it is associated with the slot. When
 * provisioning applet reserves the slot for a document, instance of the this class is also
 * reserved. Instance of this class when provisioned will be associated with one or more instances
 * of the presentation packages associated with this document. Provisioning applet will attest the
 * signing key associated with a presentation package using the credential key stored in this class
 * and encrypts the presentation package being provisioned using storage key stored in this class.
 * Further the public credential key itself is attested by the provisioning applet using factory
 * provisioned or remotely provisioned root key. This class uses PresentationPkgStore shareable
 * interface when presentation package is persistently stored in Presentation Applet for the direct
 * access purposes.
 */
public class MDoc {

  public static final byte[] AID_MDL_DIRECT_ACCESS_APPLET = {
    (byte) 0xA0, 0x00, 0x00, 0x02, 0x48, 0x04, 0x00
  };
  static AID mPresentationAppletAid;
  static Object[] mPkgStore;
  private final AESKey mStorageKey;
  private final short mSlotId;
  private final boolean[] mTestCred;
  private final SEProvider mSEProvider;
  private boolean mReserved;
  private boolean mProvisioned;
  private byte[] mDocType;

  public MDoc(short slotId, SEProvider se) {
    mSEProvider = se;
    mTestCred = JCSystem.makeTransientBooleanArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
    mTestCred[0] = false;
    mSlotId = slotId;
    mStorageKey =
        (AESKey) KeyBuilder.buildKey(KeyBuilder.TYPE_AES, KeyBuilder.LENGTH_AES_128, false);
    MDoc.mPresentationAppletAid =
        new AID(
            AID_MDL_DIRECT_ACCESS_APPLET, (short) 0, (byte) AID_MDL_DIRECT_ACCESS_APPLET.length);
    MDoc.mPkgStore = JCSystem.makeTransientObjectArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
  }

  static MdlPresentationPkgStore getPkgStore() {
    if (mPkgStore[0] == null) {
      mPkgStore[0] =
          JCSystem.getAppletShareableInterfaceObject(
              mPresentationAppletAid, MdlPresentationPkgStore.SERVICE_ID);
    }
    return (MdlPresentationPkgStore) mPkgStore[0];
  }

  public void store(byte[] buf, short start, short len) {
    MdlPresentationPkgStore pkgStore = getPkgStore();
    pkgStore.write(mSlotId, buf, start, len);
  }

  public void clearUsageCount() {
    MdlPresentationPkgStore pkgStore = getPkgStore();
    pkgStore.clearUsageCount(mSlotId);
  }

  public short getUsageCount() {
    MdlPresentationPkgStore pkgStore = getPkgStore();
    return pkgStore.getUsageCount(mSlotId);
  }

  public void createPackage(short size, byte[] docStr,
                            short docStrStart, short docStrLen) {
    MdlPresentationPkgStore pkgStore = getPkgStore();
    pkgStore.createPackage(mSlotId, size, docStr, docStrStart, docStrLen);
  }

  public void deletePackage() {
    MdlPresentationPkgStore pkgStore = getPkgStore();
    pkgStore.deletePackage(mSlotId);
  }

  public void startProvisioning() {
    MdlPresentationPkgStore pkgStore = getPkgStore();
    pkgStore.startProvisioning(mSlotId);
    mProvisioned = false;
  }

  public void commitProvisioning() {
    MdlPresentationPkgStore pkgStore = getPkgStore();
    pkgStore.commitProvisioning(mSlotId);
    mProvisioned = true;
  }

  public void reserve() {
    mReserved = true;
  }

  public void release() {
    mReserved = false;
  }

  public boolean isReserved() {
    return mReserved;
  }


  // docStr must be sent in the apdu buffer as this data is passed over Shareable interface.
  public void create(short size, byte[] scratch, short start, short len, byte[] docStr,
                     short docStrStart, short docStrLen) {
    mSEProvider.generateRandomData(scratch, start, (short) (KeyBuilder.LENGTH_AES_256 / 8));
    mStorageKey.setKey(scratch, start);
    createPackage(size, docStr, docStrStart, docStrLen);
  }

  public void delete(byte[] scratch, short start, short len) {
    mTestCred[0] = false;
    // Clear the keys. Here, the clearKey method is not used because it will reset the
    // initialization. Instead keys are set to zero.
    len = mStorageKey.getKey(scratch, start);
    Util.arrayFillNonAtomic(scratch, start, len, (byte) 0);
    mStorageKey.setKey(scratch, start);
    deletePackage();
  }

  public void enableTestCred(byte[] scratch, short start, short len) {
    mTestCred[0] = true;
    // Clear the storage key and set it to zero.
    len = mStorageKey.getKey(scratch, start);
    Util.arrayFillNonAtomic(scratch, start, len, (byte) 0);
    mStorageKey.setKey(scratch, start);
  }

  public AESKey getStorageKey() {
    return mStorageKey;
  }

  public boolean isProvisioned() {
    return mProvisioned;
  }

  public boolean isTestCredential() {
    return mTestCred[0];
  }
}
