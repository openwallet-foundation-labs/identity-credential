package javacard.security;

public interface SignatureMessageRecovery {
  void init(Key var1, byte var2) throws CryptoException;

  short beginVerify(byte[] var1, short var2, short var3) throws CryptoException;

  short sign(byte[] var1, short var2, short var3, byte[] var4, short var5, short[] var6, short var7) throws CryptoException;

  boolean verify(byte[] var1, short var2, short var3) throws CryptoException;

  byte getAlgorithm();

  short getLength() throws CryptoException;

  void update(byte[] var1, short var2, short var3) throws CryptoException;
}