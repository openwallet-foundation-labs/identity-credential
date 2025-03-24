package javacard.security;

public interface DSAPrivateKey extends PrivateKey, DSAKey {
  void setX(byte[] var1, short var2, short var3) throws CryptoException;

  short getX(byte[] var1, short var2);
}
