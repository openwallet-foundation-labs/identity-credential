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

/**
 * This class extends encoder and it is used to calculate the total size required for encoded
 * stream. This is used for cases where size of encoded stream is required before the actual
 * encoding is done in the future.
 */
public class CBOREncoderCalc extends CBOREncoder {

  /** Increase the offset by one. */
  public short writeRawByte(byte val) {
    increaseOffset((short) 1);
    return (short) 1;
  }

  /** Increase the offset by two. */
  short writeRawShort(short val) {
    increaseOffset((short) 2);
    return (short) 2;
  }

  /**
   * Increase the offset by its size.
   *
   * @param value Buffer array with the content
   * @param offset Offset in input buffer
   * @param length Length of data that should be encoded
   * @return The current offset in the buffer
   */
  short writeRawByteArray(byte[] value, short offset, short length) {
    increaseOffset(length);
    return length;
  }
}
