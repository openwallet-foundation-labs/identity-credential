package javacard.framework;

public class OwnerPIN implements PIN {

  private static final byte VALIDATED = 0;
  private static final byte NUMFLAGS = 1;
  private byte tryLimit;
  private byte maxPINSize;
  private byte pinValue[];
  private byte pinSize;
  // CHECK !!! Use transient array with size 1 element for store validation state
  private boolean flags[];
  // CHECK !!! Use transient array with size 1 element for store tries counter
  private byte triesLeft[];


  /**
   * Constructor. Allocates a new <code>PIN</code> instance with validated flag
   * set to <code>false</code>
   * @param tryLimit the maximum number of times an incorrect PIN can be presented. <code>tryLimit</code> must be &gt;=1
   * @param maxPINSize the maximum allowed PIN size. <code>maxPINSize</code> must be &gt;=1
   * @throws PINException with the following reason codes:
   * <ul>
   *  <li><code>PINException.ILLEGAL_VALUE</code> if <code>tryLimit</code> parameter is less than 1.
   *  <li><code>PINException.ILLEGAL_VALUE</code> if <code>maxPINSize</code> parameter is less than 1.
   * </ul>
   */
  public OwnerPIN(byte tryLimit, byte maxPINSize)
      throws PINException {
    if (tryLimit < 1 || maxPINSize < 1) {
      PINException.throwIt(PINException.ILLEGAL_VALUE);
    }
    pinValue = new byte[maxPINSize];
    pinSize = maxPINSize;
    this.maxPINSize = maxPINSize;
    this.tryLimit = tryLimit;
    triesLeft = new byte[1];
    resetTriesRemaining();
    flags = JCSystem.makeTransientBooleanArray((short) 1, JCSystem.CLEAR_ON_RESET);
    setValidatedFlag(false);
  }

  /**
   * This protected method returns the validated flag.
   * This method is intended for subclass of this <code>OwnerPIN</code> to access or
   * override the internal PIN state of the <code>OwnerPIN</code>.
   * @return the boolean state of the PIN validated flag
   */
  protected boolean getValidatedFlag() {
    return flags[0];
  }

  /**
   * This protected method sets the value of the validated flag.
   * This method is intended for subclass of this <code>OwnerPIN</code> to control or
   * override the internal PIN state of the <code>OwnerPIN</code>.
   * @param value the new value for the validated flag
   */
  protected void setValidatedFlag(boolean value) {
    flags[0] = value;
  }

  /**
   * !!! CHECK
   * This internal method resets tries counter
   */
  private void resetTriesRemaining() {
    Util.arrayFillNonAtomic(triesLeft, (short) 0, (short) 1, tryLimit);
  }

  /**
   * !!! CHECK
   * This internal method decrement tries counter
   */
  private void decrementTriesRemaining() {
    Util.arrayFillNonAtomic(triesLeft, (short) 0, (short) 1, (byte) (triesLeft[0] - 1));
  }

  /**
   * Returns the number of times remaining that an incorrect PIN can
   * be presented before the <code>PIN</code> is blocked.
   * @return the number of times remaining
   */
  @Override
  public byte getTriesRemaining() {
    return triesLeft[0];
  }

  /**
   * Compares <code>pin</code> against the PIN value. If they match and the
   * <code>PIN</code> is not blocked, it sets the validated flag
   * and resets the try counter to its maximum. If it does not match,
   * it decrements the try counter and, if the counter has reached
   * zero, blocks the <code>PIN</code>. Even if a transaction is in progress, update of
   * internal state - the try counter, the validated flag, and the blocking state,
   * shall not participate in the transaction.
   * <p>
   * Note:<ul>
   * <li><em>If </em><code>NullPointerException</code><em> or </em><code>ArrayIndexOutOfBoundsException</code><em> is
   * thrown, the validated flag must be set to false, the try counter must be decremented
   * and, the <code>PIN</code> blocked if the counter reaches zero.</em>
   * <li><em>If </em><code>offset</code><em> or </em><code>length</code><em> parameter
   * is negative an </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown.</em>
   * <li><em>If </em><code>offset+length</code><em> is greater than </em><code>pin.length</code><em>, the length
   * of the </em><code>pin</code><em> array, an </em><code>ArrayIndexOutOfBoundsException</code><em> exception is thrown.</em>
   * <li><em>If </em><code>pin</code><em> parameter is </em><code>null</code><em>
   * a </em><code>NullPointerException</code><em> exception is thrown.</em></ul>
   * @param pin the byte array containing the PIN value being checked
   * @param offset the starting offset in the <code>pin</code> array
   * @param length the length of <code>pin</code>
   * @return <code>true</code> if the PIN value matches; <code>false</code> otherwise
   * @throws ArrayIndexOutOfBoundsException if the check operation would cause access of data outside array bounds.
   * @throws NullPointerException if <code>pin</code> is <code>null</code>
   */
  @Override
  public boolean check(byte pin[], short offset, byte length)
      throws ArrayIndexOutOfBoundsException, NullPointerException {
    boolean noMoreTries = false;
    setValidatedFlag(false);
    if (getTriesRemaining() == 0) {
      noMoreTries = true;
    } else {
      decrementTriesRemaining();
    }
    if (length > 0) {
      byte tester = pin[(short) ((offset + length) - 1)];
      if (length != pinSize || noMoreTries) {
        return false;
      }
    }
    if (Util.arrayCompare(pin, offset, pinValue, (short) 0, length) == 0 && length == pinSize) {
      setValidatedFlag(true);
      resetTriesRemaining();
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns <code>true</code> if a valid PIN has been presented since the last
   * card reset or last call to <code>reset()</code>.
   * @return <code>true</code> if validated; <code>false</code> otherwise
   */
  @Override
  public boolean isValidated() {
    return getValidatedFlag();
  }

  /**
   * If the validated flag is set, this method resets the validated flag and
   * resets the <code>PIN</code> try counter to the value of the <code>PIN</code> try limit.
   * Even if a transaction is in progress, update of
   * internal state - the try counter, the validated flag, and the blocking state,
   * shall not participate in the transaction.
   * If the validated flag is not set, this method does nothing.
   */
  @Override
  public void reset() {
    if (isValidated()) {
      resetAndUnblock();
    }
  }

  /**
   * This method sets a new value for the PIN and resets the <code>PIN</code> try
   * counter to the value of the <code>PIN</code> try limit. It also resets the validated flag.<p>
   * This method copies the input pin parameter into an internal representation. If a transaction is
   * in progress, the new pin and try counter update must be conditional i.e
   * the copy operation must use the transaction facility.
   * @param pin the byte array containing the new PIN value
   * @param offset the starting offset in the pin array
   * @param length he length of the new PIN
   * @throws PINException with the following reason codes:
   * <ul>
   *   <li><code>PINException.ILLEGAL_VALUE</code> if length is greater than configured maximum PIN size.
   * </ul>
   */
  public void update(byte pin[], short offset, byte length)
      throws PINException {
    if (length > maxPINSize) {
      PINException.throwIt(PINException.ILLEGAL_VALUE);
    }
    Util.arrayCopy(pin, offset, pinValue, (short) 0, length);
    pinSize = length;
    triesLeft[0] = tryLimit;
    setValidatedFlag(false);
  }

  /**
   * This method resets the validated flag and
   * resets the <code>PIN</code> try counter to the value of the <code>PIN</code> try limit.
   * Even if a transaction is in progress, update of
   * internal state - the try counter, the validated flag, and the blocking state,
   * shall not participate in the transaction.
   * This method is used by the owner to re-enable the blocked <code>PIN</code>.
   */
  public void resetAndUnblock() {
    resetTriesRemaining();
    setValidatedFlag(false);
  }

}