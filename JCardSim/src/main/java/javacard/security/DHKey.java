package javacard.security;

public interface DHKey {
  void setP(byte[] var1, short var2, short var3) throws CryptoException;

  void setQ(byte[] var1, short var2, short var3) throws CryptoException;

  void setG(byte[] var1, short var2, short var3) throws CryptoException;

  short getP(byte[] var1, short var2);

  short getQ(byte[] var1, short var2);

  short getG(byte[] var1, short var2);
}