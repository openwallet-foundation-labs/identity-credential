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
 * This interface provides method for Ndef Tag Applet to get the handover select message from the
 * mdl service i.e. PresentationApplet. This is required because part of the handover select message
 * is ephemeral and thus dynamically generated at the runtime.
 */
public interface MdlService extends Shareable {

  byte SERVICE_ID = 1;

  short getHandoverSelectMessage(byte[] buf, short start);
}
