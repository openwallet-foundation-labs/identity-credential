package javacard.framework;

public interface PIN {
  byte getTriesRemaining();

  boolean check(byte[] var1, short var2, byte var3) throws ArrayIndexOutOfBoundsException, NullPointerException;

  boolean isValidated();

  void reset();
}
