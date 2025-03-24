package javacard.security;

public interface RSAPrivateCrtKey extends PrivateKey {
  void setP(byte[] var1, short var2, short var3) throws CryptoException;

  void setQ(byte[] var1, short var2, short var3) throws CryptoException;

  void setDP1(byte[] var1, short var2, short var3) throws CryptoException;

  void setDQ1(byte[] var1, short var2, short var3) throws CryptoException;

  void setPQ(byte[] var1, short var2, short var3) throws CryptoException;

  short getP(byte[] var1, short var2);

  short getQ(byte[] var1, short var2);

  short getDP1(byte[] var1, short var2);

  short getDQ1(byte[] var1, short var2);

  short getPQ(byte[] var1, short var2);
}
