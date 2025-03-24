package javacard.security;

public interface HMACKey extends SecretKey {
  void setKey(byte[] var1, short var2, short var3) throws CryptoException, NullPointerException, ArrayIndexOutOfBoundsException;

  byte getKey(byte[] var1, short var2);
}