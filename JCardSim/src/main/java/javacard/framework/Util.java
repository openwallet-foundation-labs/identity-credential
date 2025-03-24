package javacard.framework;

public class Util {
  /**
   * Copies an array from the specified source array,
   * beginning at the specified position,
   * to the specified position of the destination array.
   * <p>
   * Note:
   * <ul>
   * <li><em>!!! CHECK <b>Current implementation use <code>System.arraycopy</code> method and no supported transactions !</b> !!!</em></li>
   * <li><em>If </em><code>srcOff</code><em> or </em><code>destOff</code><em> or </em><code>length</code><em> parameter
   *    is negative an </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown.</em></li>
   * <li><em>If </em><code>srcOff+length</code><em> is greater than </em><code>src.length</code><em>, the length
   *     of the </em><code>src</code><em> array a </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown
   *     and no copy is performed.</em></li>
   * <li><em>If </em><code>destOff+length</code><em> is greater than </em><code>dest.length</code><em>, the length
   *     of the </em><code>dest</code><em> array an </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown
   *     and no copy is performed.</em></li>
   * <li><em>If </em><code>src</code><em> or </em><code>dest</code><em> parameter is </em><code>null</code><em>
   *     a </em><code>NullPointerException</code><em> exception is thrown.</em></li>
   * <li><em>If the <code>src</code> and <code>dest</code> arguments refer to the same array object,
   *     then the copying is performed as if the components at positions </em><code>srcOff</code><em>
   *     through </em><code>srcOff+length-1</code><em> were first copied to a temporary array with length components
   *     and then the contents of the temporary array were copied into
   *     positions </em><code>destOff</code><em> through </em><code>destOff+length-1</code><em> of the argument array.</em></li>
   * <li><em>If the destination array is persistent, the entire copy is performed atomically.</em></li>
   * <li><em>The copy operation is subject to atomic commit capacity limitations.
   *     If the commit capacity is exceeded, no copy is performed and a </em><code>TransactionException</code><em>
   *     exception is thrown.</em></li>
   * </ul>
   * @param src source byte array
   * @param srcOff offset within source byte array to start copy from
   * @param dest destination byte array
   * @param destOff offset within destination byte array to start copy into
   * @param length byte length to be copied
   * @return destOff+length
   * @throws ArrayIndexOutOfBoundsException if copying would cause access of data outside array bounds
   * @throws NullPointerException if either <code>src</code> or <code>dest</code> is <code>null</code>
   * @throws TransactionException if copying would cause the commit capacity to be exceeded
   * @see javacard.framework.JCSystem#getUnusedCommitCapacity()
   */
  public static final short arrayCopy(byte src[], short srcOff, byte dest[], short destOff, short length)
      throws ArrayIndexOutOfBoundsException, NullPointerException, TransactionException {
    System.arraycopy(src, srcOff, dest, destOff, length);
    return (short) (destOff + length);
  }

  /**
   * Copies an array from the specified source array,
   * beginning at the specified position,
   * to the specified position of the destination array (non-atomically).
   * <p>This method does not use the transaction facility during the copy operation even if
   * a transaction is in progress. Thus, this
   * method is suitable for use only when the contents of the destination array can be left in
   * a partially modified state in the event of a power loss in the middle of the copy operation.
   * <p>
   * Note:<ul>
   * <li><em>!!! CHECK <b>Current implementation use <code>System.arraycopy</code> method and no supported transactions !</b> !!!</em></li>
   * <li><em>If </em><code>srcOff</code><em> or </em><code>destOff</code><em> or </em><code>length</code><em> parameter
   * is negative an </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown.</em>
   * <li><em>If </em><code>srcOff+length</code><em> is greater than </em><code>src.length</code><em>, the length
   * of the </em><code>src</code><em> array a </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown
   * and no copy is performed.</em>
   * <li><em>If </em><code>destOff+length</code><em> is greater than </em><code>dest.length</code><em>, the length
   * of the </em><code>dest</code><em> array an </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown
   * and no copy is performed.</em>
   * <li><em>If </em><code>src</code><em> or </em><code>dest</code><em> parameter is </em><code>null</code><em>
   * a </em><code>NullPointerException</code><em> exception is thrown.</em>
   * <li><em>If the <code>src</code> and <code>dest</code> arguments refer to the same array object,
   * then the copying is performed as if the components at positions </em><code>srcOff</code><em>
   * through </em><code>srcOff+length-1</code><em> were first copied to a temporary array with length components
   * and then the contents of the temporary array were copied into
   * positions </em><code>destOff</code><em> through </em><code>destOff+length-1</code><em> of the argument array.</em>
   * <li><em>If power is lost during the copy operation and the destination array is persistent,
   * a partially changed destination array could result.</em>
   * <li><em>The copy </em><code>length</code><em> parameter is not constrained by the atomic commit capacity limitations.</em></ul>
   * @param src source byte array
   * @param srcOff offset within source byte array to start copy from
   * @param dest destination byte array
   * @param destOff offset within destination byte array to start copy into
   * @param length byte length to be copied
   * @return destOff+length
   * @throws ArrayIndexOutOfBoundsException if copying would cause access of data outside array bounds
   * @throws NullPointerException if either <code>src</code> or <code>dest</code> is <code>null</code>
   * @throws TransactionException if copying would cause the commit capacity to be exceeded
   * @see javacard.framework.JCSystem#getUnusedCommitCapacity()
   */
  public static final short arrayCopyNonAtomic(byte src[], short srcOff, byte dest[], short destOff, short length)
      throws ArrayIndexOutOfBoundsException, NullPointerException {
    System.arraycopy(src, srcOff, dest, destOff, length);
    return (short) (destOff + length);
  }

  /**
   * Fills the byte array (non-atomically) beginning at the specified position,
   * for the specified length with the specified byte value.
   *  <p>This method does not use the transaction facility during the fill operation even if
   * a transaction is in progress. Thus, this
   * method is suitable for use only when the contents of the byte array can be left in
   * a partially filled state in the event of a power loss in the middle of the fill operation.
   *  <p>
   * Note:<ul>
   * <li><em>If </em><code>bOff</code><em> or </em><code>bLen</code><em> parameter
   * is negative an </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown.</em>
   * <li><em>If </em><code>bOff+bLen</code><em> is greater than </em><code>bArray.length</code><em>, the length
   * of the </em><code>bArray</code><em> array an </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown.</em>
   * <li><em>If </em><code>bArray</code><em> parameter is </em><code>null</code><em>
   * a </em><code>NullPointerException</code><em> exception is thrown.</em>
   * <li><em>If power is lost during the copy operation and the byte array is persistent,
   * a partially changed byte array could result.</em>
   * <li><em>The </em><code>bLen</code><em> parameter is not constrained by the atomic commit capacity limitations.</em></ul>
   * @param bArray the byte array
   * @param bOff offset within byte array to start filling bValue into
   * @param bLen byte length to be filled
   * @param bValue the value to fill the byte array with
   * @return bOff+bLen
   * @throws ArrayIndexOutOfBoundsException if the fill operation would cause access of data outside array bounds
   * @throws NullPointerException if bArray is <code>null</code>
   * @see javacard.framework.JCSystem#getUnusedCommitCapacity()
   */
  public static final short arrayFillNonAtomic(byte bArray[], short bOff, short bLen, byte bValue)
      throws ArrayIndexOutOfBoundsException, NullPointerException {
    byte tester = bArray[bOff];
    if (bLen < 0) {
      throw new ArrayIndexOutOfBoundsException();
    }
    while (bLen > 0) {
      bArray[bOff++] = bValue;
      bLen--;
    }
    // after loop bOff = bOff + bLen
    return bOff;
  }

  /**
   * Compares an array from the specified source array,
   * beginning at the specified position,
   * with the specified position of the destination array from left to right.
   * Returns the ternary result of the comparison : less than(-1), equal(0) or greater than(1).
   * <p>Note:<ul>
   * <li><em>If </em><code>srcOff</code><em> or </em><code>destOff</code><em> or </em><code>length</code><em> parameter
   * is negative an </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown.</em>
   * <li><em>If </em><code>srcOff+length</code><em> is greater than </em><code>src.length</code><em>, the length
   * of the </em><code>src</code><em> array a </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown.</em>
   * <li><em>If </em><code>destOff+length</code><em> is greater than </em><code>dest.length</code><em>, the length
   * of the </em><code>dest</code><em> array an </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown.</em>
   * <li><em>If </em><code>src</code><em> or </em><code>dest</code><em> parameter is </em><code>null</code><em>
   * a </em><code>NullPointerException</code><em> exception is thrown.</em>
   * </ul>
   * @param src source byte array
   * @param srcOff offset within source byte array to start compare
   * @param dest destination byte array
   * @param destOff offset within destination byte array to start compare
   * @param length byte length to be compared
   * @return the result of the comparison as follows:<ul>
   * <li> <code>0</code> if identical</li>
   * <li> <code>-1</code> if the first miscomparing byte in source array is less than that in destination array</li>
   * <li> <code>1</code> if the first miscomparing byte in source array is greater that that in destination array</li>
   * </ul>
   * @throws ArrayIndexOutOfBoundsException if comparing all bytes would cause access of data outside array bounds
   * @throws NullPointerException if either <code>src</code> or <code>dest</code> is <code>null</code>
   */
  public static final byte arrayCompare(byte src[], short srcOff, byte dest[], short destOff, short length)
      throws ArrayIndexOutOfBoundsException, NullPointerException {
    if (length < 0) {
      throw new ArrayIndexOutOfBoundsException();
    }
    if (length != 0) {
      byte tester = src[(srcOff + length) - 1];
      tester = dest[(destOff + length) - 1];
    }
    for (short i = 0; i < length; i++) {
      if (src[srcOff + i] != dest[destOff + i]) {
        short thisSrc = (short) (src[srcOff + i] & 0x00ff);
        short thisDest = (short) (dest[destOff + i] & 0x00ff);
        return (byte) (thisSrc >= thisDest ? 1 : -1);
      }
    }

    return 0;
  }

  /**
   * Concatenates the two parameter bytes to form a short value.
   * @param b1 the first byte ( high order byte )
   * @param b2 the second byte ( low order byte )
   * @return the short value the concatenated result
   */
  public static final short makeShort(byte b1, byte b2) {
    return (short) (((short) b1 << 8) + ((short) b2 & 0xff));
  }

  /**
   * Concatenates two bytes in a byte array to form a short value.
   * @param bArray byte array
   * @param bOff offset within byte array containing first byte (the high order byte)
   * @return the short value the concatenated result
   * @throws ArrayIndexOutOfBoundsException if the <CODE>bOff</CODE> parameter is negative or if <CODE>bOff+2</CODE> is greater than the length of <code>bArray</code>
   * @throws NullPointerException if the <CODE>bArray</CODE> parameter is <CODE>null</CODE>
   */
  public static final short getShort(byte bArray[], short bOff) throws ArrayIndexOutOfBoundsException, NullPointerException {
    return (short) (((short) bArray[bOff] << 8) + ((short) bArray[bOff + 1] & 0xff));
  }

  /**
   * Deposits the short value as two successive bytes at the specified offset in the byte array.
   * @param bArray byte array
   * @param bOff offset within byte array to deposit the first byte (the high order byte)
   * @param sValue the short value to set into array.
   * @return <code>bOff+2</code>
   * <p>Note:<ul>
   * <li><em>If the byte array is persistent, this operation is performed atomically.
   * If the commit capacity is exceeded, no operation is performed and a </em><code>TransactionException</code><em>
   * exception is thrown.</em></li></ul>
   * @throws ArrayIndexOutOfBoundsException if the <CODE>bOff</CODE> parameter is negative or if <CODE>bOff+2</CODE> is greater than the length of <code>bArray</code>
   * of <CODE>bArray</CODE>
   * @throws NullPointerException if the <CODE>bArray</CODE> parameter is <CODE>null</CODE>
   * @throws TransactionException if the operation would cause the commit capacity to be exceeded
   * @see javacard.framework.JCSystem#getUnusedCommitCapacity()
   */
  public static final short setShort(byte bArray[], short bOff, short sValue)
      throws TransactionException, ArrayIndexOutOfBoundsException, NullPointerException {
    bArray[bOff] = (byte) (sValue >> 8);
    bArray[bOff + 1] = (byte) sValue;
    return (short) (bOff + 2);
  }

}
