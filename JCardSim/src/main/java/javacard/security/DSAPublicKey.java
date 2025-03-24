package javacard.security;

public interface DSAPublicKey extends PublicKey, DSAKey {
  void setY(byte[] var1, short var2, short var3) throws CryptoException;

  short getY(byte[] var1, short var2);
}