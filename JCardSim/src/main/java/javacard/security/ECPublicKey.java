package javacard.security;

public interface ECPublicKey extends PublicKey, ECKey {
  void setW(byte[] var1, short var2, short var3) throws CryptoException;

  short getW(byte[] var1, short var2) throws CryptoException;
}
