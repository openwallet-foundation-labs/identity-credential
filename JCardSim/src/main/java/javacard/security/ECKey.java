package javacard.security;

public interface ECKey {
  void setFieldFP(byte[] var1, short var2, short var3) throws CryptoException;

  void setFieldF2M(short var1) throws CryptoException;

  void setFieldF2M(short var1, short var2, short var3) throws CryptoException;

  void setA(byte[] var1, short var2, short var3) throws CryptoException;

  void setB(byte[] var1, short var2, short var3) throws CryptoException;

  void setG(byte[] var1, short var2, short var3) throws CryptoException;

  void setR(byte[] var1, short var2, short var3) throws CryptoException;

  void setK(short var1);

  short getField(byte[] var1, short var2) throws CryptoException;

  short getA(byte[] var1, short var2) throws CryptoException;

  short getB(byte[] var1, short var2) throws CryptoException;

  short getG(byte[] var1, short var2) throws CryptoException;

  short getR(byte[] var1, short var2) throws CryptoException;

  short getK() throws CryptoException;

  void copyDomainParametersFrom(ECKey var1) throws CryptoException;
}
