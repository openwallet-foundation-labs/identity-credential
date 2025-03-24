package javacard.framework;


public class AID {
  public byte aid[];


  /**
   * The Java Card runtime environment uses this constructor to create a new <code>AID</code> instance
   * encapsulating the specified AID bytes.
   * @param bArray the byte array containing the AID bytes
   * @param offset the start of AID bytes in bArray
   * @param length the length of the AID bytes in bArray
   * @throws SystemException with the following reason code:
   * <ul>
   *       <li><code>SystemException.ILLEGAL_VALUE</code> if the <code>length</code> parameter is
   *       less than <code>5</code> or greater than <code>16</code>
   * </ul>
   * @throws NullPointerException  if the bArray parameter is null
   * @throws ArrayIndexOutOfBoundsException if the offset parameter or length parameter is negative or if offset+length is greater than the length of the bArray parameter
   * @throws SecurityException if the <code>bArray</code> array is not accessible in the caller's context
   */
  public AID(byte bArray[], short offset, byte length)
      throws SystemException, NullPointerException, ArrayIndexOutOfBoundsException, SecurityException {
    if (length < 5 || length > 16) {
      SystemException.throwIt(SystemException.ILLEGAL_VALUE);
    }
    aid = new byte[length];
    Util.arrayCopy(bArray, offset, aid, (short) 0, length);
  }

  /**
   * Called to get all the AID bytes encapsulated within AID object.
   * @param dest byte array to copy the AID bytes
   * @param offset within dest where the AID bytes begin
   * @return the length of the AID bytes
   * @throws NullPointerException if the <code>dest</code> parameter is null
   * @throws ArrayIndexOutOfBoundsException  if the <code>offset</code> parameter is negative or <code>offset</code>+length of AID bytes is greater than the length of the dest array
   * @throws SecurityException if the <code>dest</code> array is not accessible in the caller's context
   */

  public final byte getBytes(byte dest[], short offset)
      throws NullPointerException, ArrayIndexOutOfBoundsException, SecurityException {
    Util.arrayCopy(aid, (short) 0, dest, offset, (short) aid.length);
    return (byte) aid.length;
  }

  /**
   * Compares the AID bytes in <code>this</code> <code>AID</code> instance to the AID bytes in the
   * specified object.
   *
   * The result is <code>true</code> if and only if the argument is not <code>null</code>
   * and is an <code>AID</code> object that encapsulates the same AID bytes as <code>this</code>
   * object.
   *
   * <p>
   * This method does not throw <code>NullPointerException</code>.
   * @param anObject the object to compare <code>this</code> <code>AID</code> against
   * @return <code>true</code> if the AID byte values are equal, <code>false</code> otherwise
   * @throws SecurityException if <code>anObject</code> object is not accessible in the caller's context
   */
  public final boolean equals(Object anObject)
      throws SecurityException {
    if (anObject == null) {
      return false;
    }
    if (!(anObject instanceof AID) || ((AID) anObject).aid.length != aid.length) {
      return false;
    } else {
      return Util.arrayCompare(((AID) anObject).aid, (short) 0, aid, (short) 0, (short) aid.length) == 0;
    }
  }

  /**
   * Checks if the specified AID bytes in <code>bArray</code> are the same as those encapsulated
   * in <code>this</code> <code>AID</code> object.
   *
   * The result is <code>true</code> if and only if the <code>bArray</code> argument is not <code>null</code>
   * and the AID bytes encapsulated in <code>this</code> <code>AID</code> object are equal to
   * the specified AID bytes in <code>bArray</code>.
   *
   * <p>
   * This method does not throw <code>NullPointerException</code>.
   * @param bArray containing the AID bytes
   * @param offset within bArray to begin
   * @param length of AID bytes in bArray
   * @return <code>true</code> if equal, <code>false</code> otherwise
   * @throws ArrayIndexOutOfBoundsException if the <code>offset</code> parameter or <code>length</code> parameter is negative or
   * if <code>offset+length</code> is greater than the length of the <code>bArray</code> parameter
   * @throws SecurityException if the <code>bArray</code> array is not accessible in the caller's context
   */
  public final boolean equals(byte bArray[], short offset, byte length)
      throws ArrayIndexOutOfBoundsException, SecurityException {
    return length == aid.length && Util.arrayCompare(bArray, offset, aid, (short) 0, length) == 0;
  }

  /**
   * Checks if the specified partial AID byte sequence matches the first <code>length</code> bytes
   * of the encapsulated AID bytes within <code>this</code> <code>AID</code> object.
   * The result is <code>true</code> if and only if the <code>bArray</code> argument is not <code>null</code>
   * and the input <code>length</code> is less than or equal to the length of the encapsulated AID
   * bytes within <code>this</code> <code>AID</code> object and the specified bytes match.
   *
   * <p>
   * This method does not throw <code>NullPointerException</code>.
   *
   * @param bArray containing the partial AID byte sequence
   * @param offset within bArray to begin
   * @param length of partial AID bytes in bArray
   * @return <code>true</code> if equal, <code>false</code> otherwise
   * @throws ArrayIndexOutOfBoundsException if the <code>offset</code> parameter or <code>length</code> parameter is negative or
   * if <code>offset+length</code> is greater than the length of the <code>bArray</code> parameter
   * @throws SecurityException if the <code>bArray</code> array is not accessible in the caller's context
   */
  public final boolean partialEquals(byte bArray[], short offset, byte length)
      throws ArrayIndexOutOfBoundsException, SecurityException {
    if (length > aid.length) {
      return false;
    } else {
      return Util.arrayCompare(bArray, offset, aid, (short) 0, length) == 0;
    }
  }

  /**
   * Checks if the RID (National Registered Application provider identifier) portion of the encapsulated
   * AID bytes within the <code>otherAID</code> object matches
   * that of <code>this</code> <code>AID</code> object.
   * The first 5 bytes of an AID byte sequence is the RID. See ISO 7816-5 for details.
   * The result is <code>true</code> if and only if the argument is not <code>null</code>
   * and is an <code>AID</code> object that encapsulates the same RID bytes as <code>this</code>
   * object.
   *
   *<p>
   * This method does not throw <code>NullPointerException</code>.
   * @param otherAID the <code>AID</code> to compare against
   * @return <code>true</code> if the RID bytes match, <code>false</code> otherwise
   * @throws SecurityException if the <code>otherAID</code> object is not accessible in the caller's context
   */
  public final boolean RIDEquals(AID otherAID)
      throws SecurityException {
    if (otherAID == null) {
      return false;
    }
    return Util.arrayCompare(aid, (short) 0, otherAID.aid, (short) 0, (short) 5) == 0;
  }

  /**
   * Called to get part of the AID bytes encapsulated within the <code>AID</code> object starting
   * at the specified offset for the specified length.
   * @param aidOffset offset within AID array to begin copying bytes
   * @param dest the destination byte array to copy the AID bytes into
   * @param oOffset offset within dest where the output bytes begin
   * @param oLength the length of bytes requested in <code>dest</code>. <code>0</code>
   * implies a request to copy all remaining AID bytes.
   * @return the actual length of the bytes returned in <code>dest</code>
   * @throws NullPointerException if the <code>dest</code> parameter is <code>null</code>
   * @throws ArrayIndexOutOfBoundsException if the <code>aidOffset</code> parameter is
   * negative or greater than the length of the encapsulated AID bytes or the
   * <code>oOffset</code> parameter is negative
   * or <code>oOffset+length</code> of bytes requested is greater than the length of the
   * <code>dest</code> array
   * @throws SecurityException if the <code>dest</code> array is not accessible in the caller's context
   */
  public final byte getPartialBytes(short aidOffset, byte dest[], short oOffset, byte oLength)
      throws NullPointerException, ArrayIndexOutOfBoundsException, SecurityException {
    short copyLen = oLength;
    if (oLength == 0) {
      copyLen = (short) (aid.length - aidOffset);
    }
    Util.arrayCopy(aid, aidOffset, dest, oOffset, copyLen);
    return (byte) copyLen;
  }
}
