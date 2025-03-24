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
package com.android.javacard.mdl;

import javacard.framework.Shareable;

/**
 * This interface declares the methods required to implement presentation package store. The
 * presentation packages can be of any doc type. It stores one package per slot. Number of slots are
 * specified during install time. This interface can be implemented as shareable interface.
 * Presentation Applet and Provisioning applet both uses this to share the data,
 */
public interface MdlPresentationPkgStore extends Shareable {

  byte SERVICE_ID = 2;

  void write(short slotId, byte[] buf, short start, short len);

  short getUsageCount(short slotId);

  void createPackage(short slotId, short size, byte[] docStr,
                     short docStrStart, short docStrLen);

  void deletePackage(short slotId);

  void startProvisioning(short slotId);

  void commitProvisioning(short slotId);
}
