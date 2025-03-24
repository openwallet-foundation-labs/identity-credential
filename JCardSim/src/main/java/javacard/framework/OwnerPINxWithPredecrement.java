package javacard.framework;

public interface OwnerPINxWithPredecrement extends OwnerPINx {
  byte decrementTriesRemaining();

  boolean check(byte[] var1, short var2, byte var3) throws PINException, ArrayIndexOutOfBoundsException, NullPointerException;
}
