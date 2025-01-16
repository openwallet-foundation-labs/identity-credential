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

public interface IsoDepWrapper {

  public Tag getTag();

  public void getIsoDep(Tag tag);

  public boolean isConnected();

  public void connect() throws IOException;


  public void close() throws IOException;

  boolean isTagSupported();

  public void setTimeout(int timeout);
  public int getTimeout();

  public byte[] getHistoricalBytes();

  public byte[] getHiLayerResponse();

  public int getMaxTransceiveLength();

  public byte[] transceive(byte[] data) throws IOException;

  public boolean isExtendedLengthApduSupported();

}
