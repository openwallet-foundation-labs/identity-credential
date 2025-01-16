/*
 * Copyright 2025 The Android Open Source Project
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
package com.android.identity.android.mdoc.deviceretrieval;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import java.io.IOException;

public class IsoDepWrapperImpl implements IsoDepWrapper {
  private IsoDep mIsoDep;

  public IsoDepWrapperImpl(Tag tag) {
    mIsoDep = IsoDep.get(tag);
  }

  @Override
  public Tag getTag() {
    return mIsoDep.getTag();
  }

  @Override
  public boolean isConnected() {
    return mIsoDep.isConnected();
  }

  @Override
  public void connect() throws IOException {
    mIsoDep.connect();
  }

  @Override
  public void close() throws IOException {
    mIsoDep.close();
  }

  @Override
  public void getIsoDep(Tag tag) {
    mIsoDep = IsoDep.get(tag);
  }

  @Override
  public boolean isTagSupported() {
    return mIsoDep != null;
  }

  @Override
  public void setTimeout(int timeout) {
    mIsoDep.setTimeout(timeout);
  }

  @Override
  public int getTimeout() {
    return mIsoDep.getTimeout();
  }

  @Override
  public byte[] getHistoricalBytes() {
    return mIsoDep.getHistoricalBytes();
  }

  @Override
  public byte[] getHiLayerResponse() {
    return mIsoDep.getHiLayerResponse();
  }

  @Override
  public int getMaxTransceiveLength() {
    // This value is set based on the Pixel's eSE APDU Buffer size
    return 261;
  }

  @Override
  public byte[] transceive(byte[] data) throws IOException {
    return mIsoDep.transceive(data);
  }

  @Override
  public boolean isExtendedLengthApduSupported() {
    return mIsoDep.isExtendedLengthApduSupported();
  }
}
