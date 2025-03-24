package javacard.framework;

public interface OwnerPINx extends PIN {
  void update(byte[] var1, short var2, byte var3) throws PINException;

  byte getTryLimit();

  void setTryLimit(byte var1);

  void setTriesRemaining(byte var1);
}