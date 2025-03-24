package javacardx.crypto;

import com.licel.jcardsim.crypto.AsymmetricCipherImpl;
import com.licel.jcardsim.crypto.AuthenticatedSymmetricCipherImpl;
import com.licel.jcardsim.crypto.SymmetricCipherImpl;
import javacard.security.CryptoException;
import javacard.security.Key;

public abstract class Cipher {
  public static final byte ALG_DES_CBC_NOPAD = 1;
  public static final byte ALG_DES_CBC_ISO9797_M1 = 2;
  public static final byte ALG_DES_CBC_ISO9797_M2 = 3;
  public static final byte ALG_DES_CBC_PKCS5 = 4;
  public static final byte ALG_DES_ECB_NOPAD = 5;
  public static final byte ALG_DES_ECB_ISO9797_M1 = 6;
  public static final byte ALG_DES_ECB_ISO9797_M2 = 7;
  public static final byte ALG_DES_ECB_PKCS5 = 8;
  /** @deprecated */
  public static final byte ALG_RSA_ISO14888 = 9;
  public static final byte ALG_RSA_PKCS1 = 10;
  /** @deprecated */
  public static final byte ALG_RSA_ISO9796 = 11;
  public static final byte ALG_RSA_NOPAD = 12;
  public static final byte ALG_AES_BLOCK_128_CBC_NOPAD = 13;
  public static final byte ALG_AES_BLOCK_128_ECB_NOPAD = 14;
  public static final byte ALG_RSA_PKCS1_OAEP = 15;
  public static final byte ALG_KOREAN_SEED_ECB_NOPAD = 16;
  public static final byte ALG_KOREAN_SEED_CBC_NOPAD = 17;
  /** @deprecated */
  public static final byte ALG_AES_BLOCK_192_CBC_NOPAD = 18;
  /** @deprecated */
  public static final byte ALG_AES_BLOCK_192_ECB_NOPAD = 19;
  /** @deprecated */
  public static final byte ALG_AES_BLOCK_256_CBC_NOPAD = 20;
  /** @deprecated */
  public static final byte ALG_AES_BLOCK_256_ECB_NOPAD = 21;
  public static final byte ALG_AES_CBC_ISO9797_M1 = 22;
  public static final byte ALG_AES_CBC_ISO9797_M2 = 23;
  public static final byte ALG_AES_CBC_PKCS5 = 24;
  public static final byte ALG_AES_ECB_ISO9797_M1 = 25;
  public static final byte ALG_AES_ECB_ISO9797_M2 = 26;
  public static final byte ALG_AES_ECB_PKCS5 = 27;
  public static final byte CIPHER_AES_CBC = 1;
  public static final byte CIPHER_AES_ECB = 2;
  public static final byte CIPHER_DES_CBC = 3;
  public static final byte CIPHER_DES_ECB = 4;
  public static final byte CIPHER_KOREAN_SEED_CBC = 5;
  public static final byte CIPHER_KOREAN_SEED_ECB = 6;
  public static final byte CIPHER_RSA = 7;
  public static final byte PAD_NULL = 0;
  public static final byte PAD_NOPAD = 1;
  public static final byte PAD_ISO9797_M1 = 2;
  public static final byte PAD_ISO9797_M2 = 3;
  public static final byte PAD_ISO9797_1_M1_ALG3 = 4;
  public static final byte PAD_ISO9797_1_M2_ALG3 = 5;
  public static final byte PAD_PKCS5 = 6;
  public static final byte PAD_PKCS1 = 7;
  public static final byte PAD_PKCS1_PSS = 8;
  public static final byte PAD_PKCS1_OAEP = 9;
  public static final byte PAD_PKCS1_OAEP_SHA224 = 13;
  public static final byte PAD_PKCS1_OAEP_SHA256 = 14;
  public static final byte PAD_PKCS1_OAEP_SHA384 = 15;
  public static final byte PAD_PKCS1_OAEP_SHA512 = 16;
  public static final byte PAD_PKCS1_OAEP_SHA3_224 = 17;
  public static final byte PAD_PKCS1_OAEP_SHA3_256 = 18;
  public static final byte PAD_PKCS1_OAEP_SHA3_384 = 19;
  public static final byte PAD_PKCS1_OAEP_SHA3_512 = 20;
  public static final byte PAD_ISO9796 = 10;
  public static final byte PAD_ISO9796_MR = 11;
  public static final byte PAD_RFC2409 = 12;
  public static final byte MODE_DECRYPT = 1;
  public static final byte MODE_ENCRYPT = 2;
  public static final byte ALG_AES_CTR = -16;

  protected Cipher() {
  }


  public static final Cipher getInstance(byte var0, byte var1, boolean var2) throws CryptoException {
    return null;
  }

  public abstract void init(Key var1, byte var2) throws CryptoException;

  public abstract void init(Key var1, byte var2, byte[] var3, short var4, short var5) throws CryptoException;

  public abstract byte getAlgorithm();

  public abstract byte getCipherAlgorithm();

  public abstract byte getPaddingAlgorithm();

  public abstract short doFinal(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException;

  public abstract short update(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException;

  public static final class OneShot extends Cipher {
    private OneShot() {
    }

    public static final OneShot open(byte var0, byte var1) throws CryptoException {
      return null;
    }

    public void close() {
    }

    public short update(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException {
      return 0;
    }

    public void init(Key var1, byte var2) throws CryptoException {
    }

    public void init(Key var1, byte var2, byte[] var3, short var4, short var5) throws CryptoException {
    }

    public byte getAlgorithm() {
      return 0;
    }

    public byte getCipherAlgorithm() {
      return 0;
    }

    public byte getPaddingAlgorithm() {
      return 0;
    }

    public short doFinal(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException {
      return 0;
    }
  }
  /**
   * Creates a <code>Cipher</code> object instance of the selected algorithm.
   * @param algorithm the desired Cipher algorithm. Valid codes listed in
   * ALG_ .. constants above, for example, {@link Cipher#ALG_DES_CBC_NOPAD}
   * @param externalAccess indicates that the instance will be shared among
   * multiple applet instances and that the <code>Cipher</code> instance will also be accessed (via a <code>Shareable</code>
   * interface) when the owner of the <code>Cipher</code> instance is not the currently selected applet.
   * If <code>true</code> the implementation must not allocate CLEAR_ON_DESELECT transient space for internal data.
   * @return the <code>Cipher</code> object instance of the requested algorithm
   * @throws CryptoException with the following reason codes:
   * <ul>
   *  <li><code>CryptoException.NO_SUCH_ALGORITHM</code> if the requested algorithm is not supported
   *  or shared access mode is not supported.
   * </ul>
   */
  public static final Cipher getInstance(byte algorithm, boolean externalAccess)
      throws CryptoException {
    Cipher instance = null;
    if (externalAccess) {
      CryptoException.throwIt((short) 3);
    }

    switch (algorithm) {
      case Cipher.ALG_DES_CBC_NOPAD:
      case Cipher.ALG_DES_CBC_ISO9797_M1:
      case Cipher.ALG_DES_CBC_ISO9797_M2:
      case Cipher.ALG_DES_CBC_PKCS5:
      case Cipher.ALG_DES_ECB_NOPAD:
      case Cipher.ALG_DES_ECB_ISO9797_M1:
      case Cipher.ALG_DES_ECB_ISO9797_M2:
      case Cipher.ALG_DES_ECB_PKCS5:
      case Cipher.ALG_AES_BLOCK_128_CBC_NOPAD:
      case Cipher.ALG_AES_BLOCK_128_ECB_NOPAD:
      case Cipher.ALG_AES_CBC_ISO9797_M2:
      case Cipher.ALG_AES_CTR:
      case Cipher.ALG_KOREAN_SEED_ECB_NOPAD:
      case Cipher.ALG_KOREAN_SEED_CBC_NOPAD:
        instance = new SymmetricCipherImpl(algorithm);
        break;
      case Cipher.ALG_RSA_PKCS1:
      case Cipher.ALG_RSA_NOPAD:
      case Cipher.ALG_RSA_ISO14888:
      case Cipher.ALG_RSA_ISO9796:
      case Cipher.ALG_RSA_PKCS1_OAEP:
        instance = new AsymmetricCipherImpl(algorithm);
        break;
      case AEADCipher.ALG_AES_GCM:
      case AEADCipher.ALG_AES_CCM:
        instance = new AuthenticatedSymmetricCipherImpl(algorithm);
        break;

      default:
        CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
        break;
    }
    return instance;
  }
}
