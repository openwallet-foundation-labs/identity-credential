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

import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;

/** This class implements CBOR decoder. */
public class CBORDecoder extends CBORBase {

  /**
   * Return the current major type (does not increase buffer location)
   *
   * @return Major type at the current buffer location
   */
  public byte getMajorType() {
    return (byte) ((getRawByte() >>> 5) & MAJOR_TYPE_MASK);
  }

  /**
   * Returns the size of the integer at the current location.
   *
   * @return Size of the integer in bytes
   */
  public byte getIntegerSize() {
    final byte eventlength = (byte) (getRawByte() & ADDINFO_MASK);
    if (eventlength <= ENCODED_ONE_BYTE) {
      return 1;
    } else if (eventlength == ENCODED_TWO_BYTES) {
      return 2;
    } else if (eventlength == ENCODED_FOUR_BYTES) {
      return 4;
    } else if (eventlength == ENCODED_EIGHT_BYTES) {
      return 8;
    }
    return INVALID_INPUT;
  }

  /**
   * Skips the current entry (offset will be increased by the size of the entry)
   *
   * @return The offset value after the skipped entry
   */
  public short skipEntry() {
    short mapentries = 1;
    byte mType = getMajorType();
    short len = getIntegerSize();
    switch (mType) {
      case TYPE_UNSIGNED_INTEGER:
      case TYPE_NEGATIVE_INTEGER:
        short size = getIntegerSize();
        if (size == 1) { // Make sure one byte integers are handled correctly
          readInt8(); // Increases by 1 (one byte encoded int) or 2 bytes
        } else {
          increaseOffset((short) (1 + size));
        }
        break;
      case TYPE_TEXT_STRING:
      case TYPE_BYTE_STRING:
        increaseOffset(readLength());
        break;
      case TYPE_MAP:
        mapentries = 2; // Number of entries are doubled for maps (keys + values)
      case TYPE_ARRAY:
        mapentries = (short) (mapentries * readLength());
        for (short i = 0; i < mapentries; i++) {
          skipEntry();
        }
        break;
      case TYPE_TAG:
        {
          // We currently onlu check for 0x18 tag.
          // TODO In future put full tag related support
          increaseOffset((short) 1);
          increaseOffset(len);
          skipEntry();
        }
        break;
      case TYPE_FLOAT:
        // TODO in future add more simple types.
        short n = (byte) (getRawByte() & ADDINFO_MASK);
        if (n == 22 || n == 21 || n == 20) { // nil
          increaseOffset((short) 1);
        }
        break;
    }
    return getCurrentOffset();
  }

  /**
   * Read the major type and verifies if it matches the given type. Returns the length information
   * of the additional information field (increases offset by the number of length bytes). Throws an
   * ISOExeption if the major type is not correct.
   *
   * @param majorType The expected major type
   * @return The length in the addition information field
   */
  public short readMajorType(byte majorType) {
    byte b = getRawByte();
    if (majorType != ((b >>> 5) & MAJOR_TYPE_MASK)) {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    return readLength();
  }

  /**
   * Read the 8bit integer at the current location (offset will be increased). Note: this function
   * works for positive and negative integers. Sign interpretation needs to be done by the caller.
   *
   * @return The current 8bit Integer
   */
  public byte readInt8() {
    final byte eventlength = (byte) (readRawByte() & ADDINFO_MASK);
    if (eventlength < ENCODED_ONE_BYTE) {
      return eventlength;
    } else if (eventlength == ENCODED_ONE_BYTE) {
      return (byte) (readRawByte() & 0xff);
    } else {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    return 0; // Never reached
  }

  /**
   * Read the 16bit integer at the current location (offset will be increased) Note: this function
   * works for positive and negative integers. Sign interpretation needs to be done by the caller.
   *
   * @return The current 16bit Integer
   */
  public short readInt16() {
    final byte addInfo = (byte) (readRawByte() & ADDINFO_MASK);
    if (addInfo == ENCODED_TWO_BYTES) {
      return Util.getShort(getBuffer(), getCurrentOffsetAndIncrease((short) 2));
    } else {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    return 0; // Never reached
  }

  public void readInt32(byte[] output, short offset) {
    final byte addInfo = (byte) (readRawByte() & ADDINFO_MASK);
    if (addInfo == ENCODED_FOUR_BYTES) {
      Util.arrayCopyNonAtomic(
          getBuffer(), getCurrentOffsetAndIncrease((short) 4), output, offset, (short) 4);
    } else {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
  }

  public void readInt64(byte[] output, short offset) {
    final byte addInfo = (byte) (readRawByte() & ADDINFO_MASK);
    if (addInfo == ENCODED_EIGHT_BYTES) {
      Util.arrayCopyNonAtomic(
          getBuffer(), getCurrentOffsetAndIncrease((short) 8), output, offset, (short) 8);
    } else {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
  }

  public short readEncodedInteger(byte[] output, short offset) {
    final byte size = getIntegerSize();
    if (size == 1) { // Check for special case (integer could be encoded in first type)
      output[offset] = readInt8();
    } else {
      Util.arrayCopyNonAtomic(
          getBuffer(), getCurrentOffsetAndIncrease((short) (1 + size)), output, offset, size);
    }
    return (short) (size & 0xFF);
  }

  public short readLength() {
    final byte size = getIntegerSize(); // Read length information
    short length = 0;
    if (size == 1) {
      length = (short) (readInt8() & 0xFF);
    } else if (size == 2) {
      length = readInt16();
    } else { // length information above 4 bytes not supported
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    return length;
  }

  /** Reads a boolean at the current location (offset will be increased). */
  public boolean readBoolean() {
    byte b = readRawByte();
    if (b == ENCODED_TRUE) {
      return true;
    } else if (b == ENCODED_FALSE) {
      return false;
    } else {
      ISOException.throwIt(ISO7816.SW_DATA_INVALID);
    }
    // Never happens
    return true;
  }

  /**
   * Read a byte string at the current location and copy it into the given buffer (offset will be
   * increased).
   *
   * @param outBuffer Buffer where the array should be copied to
   * @param outOffset Offset location within the buffer
   * @return Number of bytes copied into the buffer
   */
  public short readByteString(byte[] outBuffer, short outOffset) {
    short length = readLength();
    return readRawByteArray(outBuffer, outOffset, length);
  }

  /**
   * Read the byte array at the current location and copy it into the given buffer (offset will be
   * increased).
   *
   * @param outBuffer Buffer where the array should be copied to
   * @param outOffset Offset location within the buffer
   * @param length Number of bytes that should be read from the buffer
   * @return Number of bytes copied into the buffer
   */
  public short readRawByteArray(byte[] outBuffer, short outOffset, short length) {
    if (length > (short) outBuffer.length
        || (short) (length + getCurrentOffset()) > getBufferLength()) {
      ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
    }

    length =
        (short)
            (Util.arrayCopyNonAtomic(getBuffer(), getCurrentOffset(), outBuffer, outOffset, length)
                - outOffset);
    increaseOffset(length);

    return length;
  }

  /**
   * Read the raw byte at the current buffer location and increase the offset by one.
   *
   * @return Current raw byte
   */
  private byte readRawByte() {
    return getBuffer()[mStatusWords[0]++];
  }
}
