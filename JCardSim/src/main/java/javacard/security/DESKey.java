package javacard.security;

public interface DESKey extends SecretKey {
  void setKey(byte[] var1, short var2) throws CryptoException, NullPointerException, ArrayIndexOutOfBoundsException;

  byte getKey(byte[] var1, short var2);
}
