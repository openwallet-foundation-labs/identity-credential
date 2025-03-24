package javacard.security;

public interface RSAPrivateKey extends PrivateKey {
  void setModulus(byte[] var1, short var2, short var3) throws CryptoException;

  void setExponent(byte[] var1, short var2, short var3) throws CryptoException;

  short getModulus(byte[] var1, short var2);

  short getExponent(byte[] var1, short var2);
}