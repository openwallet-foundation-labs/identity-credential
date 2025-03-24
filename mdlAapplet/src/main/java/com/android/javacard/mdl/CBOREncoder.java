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

/** This class implements CBOR encoder. */
public class CBOREncoder extends CBORBase {

  /**
   * Start a new array at the current buffer location with the given array size.
   *
   * @return The offset in the buffer where the first array entry is supposed to be copied into.
   */
  public short startArray(short arraySize) {
    encodeValue((byte) (TYPE_ARRAY << 5), arraySize);
    return getCurrentOffset();
  }

  /**
   * Start a new map at the current buffer location with the given map size.
   *
   * @return The offset in the buffer where the first key is supposed to be copied into.
   */
  public short startMap(short mapSize) {
    encodeValue((byte) (TYPE_MAP << 5), mapSize);
    return getCurrentOffset();
  }

  /**
   * Encodes the start of a byte string with the given length at the current buffer location. The
   * actual byte string is not copied into the buffer (offset will be set to the location after the
   * byte string)
   *
   * @return The offset in the buffer where the byte string is supposed to be copied into.
   */
  public short startByteString(short length) {
    encodeValue((byte) (TYPE_BYTE_STRING << 5), length);
    return getCurrentOffset();
  }

  /**
   * Encodes the start of a text string with the given length at the current location. The actual
   * text string is not copied into the buffer (offset will be set to the location after the byte
   * string)
   *
   * @return The offset in the buffer where the text string is supposed to be copied into.
   */
  public short startTextString(short length) {
    encodeValue((byte) (TYPE_TEXT_STRING << 5), length);
    return getCurrentOffset();
  }

  /**
   * Encodes the given byte string at the current buffer location.
   *
   * @return The number of bytes written to buffer
   */
  public short encodeByteString(byte[] byteString, short offset, short length) {
    short len = encodeValue((byte) (TYPE_BYTE_STRING << 5), length);
    len += writeRawByteArray(byteString, offset, length);
    return len;
  }

  /**
   * Encodes the text string at the current buffer location.
   *
   * @return The number of bytes written to buffer
   */
  public short encodeTextString(byte[] byteString, short offset, short length) {
    short len = encodeValue((byte) (TYPE_TEXT_STRING << 5), length);
    len += writeRawByteArray(byteString, offset, length);
    return len;
  }

  /**
   * Encode the given integer value at the current buffer location.
   *
   * @param value Value to encode in the byte array. Note: as there are no unsigned shorts in Java
   *     card, a negative number will be interpreted as positive value.
   * @return The number of bytes written to buffer
   */
  public short encodeUInt8(byte value) {
    return encodeValue(TYPE_UNSIGNED_INTEGER, (short) (value & 0x00FF));
  }

  /**
   * Encode the given integer value at the current buffer location.
   *
   * @param value Value to encode in the byte array. Note: as there are no unsigned shorts in Java
   *     card, a negative number will be interpreted as positive value.
   * @return The number of bytes written to buffer
   */
  public short encodeUInt16(short value) {
    return encodeValue(TYPE_UNSIGNED_INTEGER, value);
  }

  /**
   * Encodes the given byte array as 4 byte Integer
   *
   * @return The number of bytes written to buffer
   */
  public short encodeUInt32(byte[] valueBuf, short valueOffset) {
    writeRawByte((byte) (TYPE_UNSIGNED_INTEGER | ENCODED_FOUR_BYTES));
    return (short) (writeRawByteArray(valueBuf, valueOffset, (short) 4) + 1);
  }

  /**
   * Encodes the given byte array as 8 byte Integer
   *
   * @return The number of bytes written to buffer
   */
  public short encodeUInt64(byte[] valueBuf, short valueOffset) {
    writeRawByte((byte) (TYPE_UNSIGNED_INTEGER | ENCODED_EIGHT_BYTES));
    return (short) (writeRawByteArray(valueBuf, valueOffset, (short) 8) + 1);
  }

  /**
   * Encodes the given boolean
   *
   * @return The number of bytes written to buffer
   */
  public short encodeBoolean(boolean value) {
    if (value) {
      return writeRawByte(ENCODED_TRUE);
    } else {
      return writeRawByte(ENCODED_FALSE);
    }
  }

  public short encodeTag(byte value) {
    encodeValue((byte) (TYPE_TAG << 5), value);
    return getCurrentOffset();
  }

  public short encodeRawData(byte[] value, short offset, short length) {
    return writeRawByteArray(value, offset, length);
  }

  private final short encodeValue(byte majorType, short value) {
    if (isLessThanAsUnsignedShort(value, ENCODED_ONE_BYTE)) {
      return writeRawByte((byte) (majorType | value));
    } else if (isLessThanAsUnsignedShort(value, (short) 0x100)) {
      return writeUInt8(majorType, (byte) value);
    } else {
      return writeUInt16(majorType, value);
    }
  }

  private final short writeUInt8(byte type, byte value) {
    writeRawByte((byte) (type | ENCODED_ONE_BYTE));
    writeRawByte(value);
    return (short) 2;
  }

  private final short writeUInt16(byte type, short value) {
    writeRawByte((byte) (type | ENCODED_TWO_BYTES));
    writeRawShort(value);
    return (short) 3;
  }

  /** Write the given byte at the current buffer location and increase the offset by one. */
  public short writeRawByte(byte val) {
    checkIncrement((short) 1);
    getBuffer()[getCurrentOffset()] = val;
    increaseOffset((short) 1);
    return (short) 1;
  }

  /** Write the given short value at the current buffer location and increase the offset by two. */
  short writeRawShort(short val) {
    checkIncrement((short) 2);
    Util.setShort(getBuffer(), getCurrentOffset(), val);
    increaseOffset((short) 2);
    return (short) 2;
  }

  /**
   * Write the byte array at the current buffer location and increase the offset by its size.
   *
   * @param value Buffer array with the content
   * @param offset Offset in input buffer
   * @param length Length of data that should be encoded
   * @return The current offset in the buffer
   */
  short writeRawByteArray(byte[] value, short offset, short length) {
    checkIncrement(length);
    if (((short) (value.length + offset) >= 0 && length > (short) (value.length + offset))
        || (short) (length + getCurrentOffset()) > getBufferLength()) {
      ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
    }
    short currentOff = getCurrentOffset();
    length =
        (short)
            (Util.arrayCopyNonAtomic(value, offset, getBuffer(), currentOff, length) - currentOff);
    increaseOffset(length);

    return length;
  }

  /**
   * Check whether there is sufficient space for the increment. The reason to do this is to avoid
   * writing data if there isn't sufficient space. Also exception is cauht by the caller to take
   * appropriate action.
   *
   * @param inc value of increment
   */
  void checkIncrement(short inc) {
    if ((short) (getCurrentOffset() + inc) > getBufferLength() || inc < 0) {
      ISOException.throwIt(ISO7816.SW_UNKNOWN);
    }
  }

  boolean isLessThanAsUnsignedShort(short n1, short n2) {
    return (n1 < n2) ^ ((n1 < 0) != (n2 < 0));
  }
}
