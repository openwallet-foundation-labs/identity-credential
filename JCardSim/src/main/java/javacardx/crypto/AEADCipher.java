package javacardx.crypto;

import javacard.security.CryptoException;
import javacard.security.Key;

public abstract class AEADCipher extends Cipher {
  public static final byte CIPHER_AES_GCM = -15;
  public static final byte CIPHER_AES_CCM = -14;
  public static final byte ALG_AES_GCM = -13;
  public static final byte ALG_AES_CCM = -12;

  protected AEADCipher() {
  }

  public abstract void init(Key var1, byte var2) throws CryptoException;

  public abstract void init(Key var1, byte var2, byte[] var3, short var4, short var5) throws CryptoException;

  public abstract void init(Key var1, byte var2, byte[] var3, short var4, short var5, short var6, short var7, short var8) throws CryptoException;

  public abstract void updateAAD(byte[] var1, short var2, short var3) throws CryptoException;

  public abstract short update(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException;

  public abstract short doFinal(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException;

  public abstract short retrieveTag(byte[] var1, short var2, short var3) throws CryptoException;

  public abstract boolean verifyTag(byte[] var1, short var2, short var3, short var4) throws CryptoException;
}
