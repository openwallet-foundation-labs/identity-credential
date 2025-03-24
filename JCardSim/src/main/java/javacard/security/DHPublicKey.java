package javacard.security;
public interface DHPublicKey extends PublicKey, DHKey {
  void setY(byte[] var1, short var2, short var3) throws CryptoException;

  short getY(byte[] var1, short var2);
}
