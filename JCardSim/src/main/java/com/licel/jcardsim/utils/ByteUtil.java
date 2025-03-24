/*
 * Copyright 2014 Robert Bachmann
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
package com.licel.jcardsim.utils;

import org.bouncycastle.util.encoders.Hex;

/**
 * Utility methods for dealing with byte arrays.
 */
public final class ByteUtil {
    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    /**
     * Create byte array from hex string
     * @param hexString hex string
     * @return new byte array
     * @throws java.lang.NullPointerException if <code>hexString</code> is null
     */
    public static byte[] byteArray(String hexString) {
        if (hexString == null) {
            throw new NullPointerException("hexArray");
        }
        return Hex.decode(hexString);
    }

    /**
     * Convert byte array into hex string
     * @param bytes hex string
     * @return hexString
     * @throws java.lang.NullPointerException if <code>bytes</code> is null
     */
    public static String hexString(byte[] bytes) {
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        return hexString(bytes, 0, bytes.length);
    }

    /**
     * Convert byte array into hex string
     * @param bytes hex string
     * @param offset offset
     * @param length length
     * @return hexString
     * @throws java.lang.NullPointerException if <code>bytes</code> is null
     */
    public static String hexString(byte[] bytes, int offset, int length) {
        // http://stackoverflow.com/questions/9655181/convert-from-byte-array-to-hex-string-in-java
        if (bytes == null) {
            throw new NullPointerException("bytes");
        }
        char[] hexChars = new char[length * 2];
        for ( int j = offset, i=0; j < (offset + length); j++, i++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * Extract status word from APDU
     * @param apduBuffer APDU bytes
     * @return status word
     * @throws java.lang.NullPointerException if <code>apduBuffer</code> is null
     * @throws java.lang.IllegalArgumentException if <code>apduBuffer.length</code>  is &lt; 2
     */
    public static short getSW(byte[] apduBuffer) {
        if (apduBuffer == null) {
            throw new NullPointerException("bytes");
        }
        if (apduBuffer.length < 2) {
            throw new IllegalArgumentException("bytes.length must be at least 2");
        }
        return getShort(apduBuffer, apduBuffer.length - 2);
    }

    /**
     * Check status word from APDU
     * @param apduBuffer APDU bytes
     * @param expected expected status word
     * @throws java.lang.NullPointerException if <code>apduBuffer</code> is null
     * @throws java.lang.IllegalArgumentException if <code>apduBuffer.length</code>  is &lt; 2
     * @throws java.lang.AssertionError if <code>expected</code> does not match the status word from <code>apduBuffer</code>
     */
    public static void requireSW(byte[] apduBuffer, int expected) {
        int sw = getSW(apduBuffer) & 0xFFFF;
        if (sw != expected) {
            throw new AssertionError(String.format("Expected status word %x but got %x", expected, sw));
        }
    }

    /**
     * Check status word from APDU
     * @param apduBuffer APDU bytes
     * @param expected expected status word
     * @throws java.lang.NullPointerException if <code>apduBuffer</code> is null
     * @throws java.lang.IllegalArgumentException if <code>apduBuffer.length</code>  is &lt; 2
     * @throws java.lang.AssertionError if <code>expected</code> does not match the status word from <code>apduBuffer</code>
     */
    public static void requireSW(byte[] apduBuffer, short expected) {
        requireSW(apduBuffer, expected & 0xFFFF);
    }

    /**
     * Read short from array
     * @see javacard.framework.Util#getShort(byte[], short)
     * @param bArray byte array
     * @param offset offset
     * @return short value
     */
    public static short getShort(byte[] bArray, int offset) {
        return (short) (((short) bArray[offset] << 8) + ((short) bArray[offset + 1] & 0xff));
    }

    private ByteUtil() {}
}
