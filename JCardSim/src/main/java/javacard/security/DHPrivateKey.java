package javacard.security;

public interface DHPrivateKey extends PrivateKey, DHKey {
  void setX(byte[] var1, short var2, short var3) throws CryptoException;

  short getX(byte[] var1, short var2);
}
