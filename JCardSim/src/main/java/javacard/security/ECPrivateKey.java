package javacard.security;

public interface ECPrivateKey extends PrivateKey, ECKey {
  void setS(byte[] var1, short var2, short var3) throws CryptoException;

  short getS(byte[] var1, short var2) throws CryptoException;
}
