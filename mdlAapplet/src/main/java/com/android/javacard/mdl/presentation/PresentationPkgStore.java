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
package com.android.javacard.mdl.presentation;

import com.android.javacard.mdl.MdlPresentationPkgStore;
import com.android.javacard.mdl.MdlSpecifications;
import com.android.javacard.mdl.SEProvider;

/**
 * Thi is the implementation of presentation package store shareable interface and it is
 * instantiated by the presentation applet. The provisioning applet uses this shareable interface to
 * provision the data.
 */
public class PresentationPkgStore implements MdlPresentationPkgStore {

  private static PresentationPkgStore mInstance;
  private MdocPresentationPkg[] mPackages;

  private PresentationPkgStore() {
    mInstance = this;
  }

  public static PresentationPkgStore instance() {
    if (mInstance == null) {
      mInstance = new PresentationPkgStore();
    }
    return mInstance;
  }

  public void configure(
      SEProvider se,
      MdlSpecifications mdlSpecs,
      short maxSlots,
      byte preAllocatedDocCount,
      short maxDocumentSize) {
    mPackages = new MdocPresentationPkg[maxSlots];
    for (byte i = 0; i < maxSlots; i++) {
      mPackages[i] = new MdocPresentationPkg(se, mdlSpecs);
      if (preAllocatedDocCount > 0) {
        mPackages[i].allocMem(maxDocumentSize);
        mPackages[i].setPreAllocated();
        preAllocatedDocCount--;
      }
    }
  }

  public MdocPresentationPkg findPackage(byte[] id, short start, short len) {
    for (byte i = 0; i < mPackages.length; i++) {
      if (mPackages[i].isMatching(id, start, len)) {
        return mPackages[i];
      }
    }
    return null;
  }

  @Override
  public void write(short slotId, byte[] buf, short start, short len) {
    mPackages[slotId].write(buf, start, len);
  }

  @Override
  public short getUsageCount(short slotId) {
    return mPackages[slotId].getUsageCount();
  }

  @Override
  public void createPackage(short slotId, short size, byte[] docStr, short docStrStart, short docStrLen) {
    mPackages[slotId].create(size, docStr, docStrStart, docStrLen);
  }

  @Override
  public void deletePackage(short slotId) {
    mPackages[slotId].delete();
  }

  @Override
  public void startProvisioning(short slotId) {
    mPackages[slotId].startProvisioning();
  }

  @Override
  public void commitProvisioning(short slotId) {
    mPackages[slotId].commitProvisioning();
  }
}
