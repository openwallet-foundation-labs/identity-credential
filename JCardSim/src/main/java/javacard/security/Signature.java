package javacard.security;

import com.licel.jcardsim.crypto.AsymmetricSignatureImpl;
import com.licel.jcardsim.crypto.SymmetricSignatureImpl;

public abstract class Signature {
  public static final byte ALG_DES_MAC4_NOPAD = 1;
  public static final byte ALG_DES_MAC8_NOPAD = 2;
  public static final byte ALG_DES_MAC4_ISO9797_M1 = 3;
  public static final byte ALG_DES_MAC8_ISO9797_M1 = 4;
  public static final byte ALG_DES_MAC4_ISO9797_M2 = 5;
  public static final byte ALG_DES_MAC8_ISO9797_M2 = 6;
  public static final byte ALG_DES_MAC4_PKCS5 = 7;
  public static final byte ALG_DES_MAC8_PKCS5 = 8;
  public static final byte ALG_RSA_SHA_ISO9796 = 9;
  public static final byte ALG_RSA_SHA_PKCS1 = 10;
  public static final byte ALG_RSA_MD5_PKCS1 = 11;
  public static final byte ALG_RSA_RIPEMD160_ISO9796 = 12;
  public static final byte ALG_RSA_RIPEMD160_PKCS1 = 13;
  public static final byte ALG_DSA_SHA = 14;
  public static final byte ALG_RSA_SHA_RFC2409 = 15;
  public static final byte ALG_RSA_MD5_RFC2409 = 16;
  public static final byte ALG_ECDSA_SHA = 17;
  public static final byte ALG_AES_MAC_128_NOPAD = 18;
  public static final byte ALG_AES_CMAC_128 = 49;
  public static final byte ALG_DES_MAC4_ISO9797_1_M2_ALG3 = 19;
  public static final byte ALG_DES_MAC8_ISO9797_1_M2_ALG3 = 20;
  public static final byte ALG_RSA_SHA_PKCS1_PSS = 21;
  public static final byte ALG_RSA_MD5_PKCS1_PSS = 22;
  public static final byte ALG_RSA_RIPEMD160_PKCS1_PSS = 23;
  public static final byte ALG_HMAC_SHA1 = 24;
  public static final byte ALG_HMAC_SHA_256 = 25;
  public static final byte ALG_HMAC_SHA_384 = 26;
  public static final byte ALG_HMAC_SHA_512 = 27;
  public static final byte ALG_HMAC_MD5 = 28;
  public static final byte ALG_HMAC_RIPEMD160 = 29;
  public static final byte ALG_RSA_SHA_ISO9796_MR = 30;
  public static final byte ALG_RSA_RIPEMD160_ISO9796_MR = 31;
  public static final byte ALG_KOREAN_SEED_MAC_NOPAD = 32;
  public static final byte ALG_ECDSA_SHA_256 = 33;
  public static final byte ALG_ECDSA_SHA_384 = 34;
  /** @deprecated */
  public static final byte ALG_AES_MAC_192_NOPAD = 35;
  /** @deprecated */
  public static final byte ALG_AES_MAC_256_NOPAD = 36;
  public static final byte ALG_ECDSA_SHA_224 = 37;
  public static final byte ALG_ECDSA_SHA_512 = 38;
  public static final byte ALG_RSA_SHA_224_PKCS1 = 39;
  public static final byte ALG_RSA_SHA_256_PKCS1 = 40;
  public static final byte ALG_RSA_SHA_384_PKCS1 = 41;
  public static final byte ALG_RSA_SHA_512_PKCS1 = 42;
  public static final byte ALG_RSA_SHA_224_PKCS1_PSS = 43;
  public static final byte ALG_RSA_SHA_256_PKCS1_PSS = 44;
  public static final byte ALG_RSA_SHA_384_PKCS1_PSS = 45;
  public static final byte ALG_RSA_SHA_512_PKCS1_PSS = 46;
  public static final byte ALG_DES_MAC4_ISO9797_1_M1_ALG3 = 47;
  public static final byte ALG_DES_MAC8_ISO9797_1_M1_ALG3 = 48;
  public static final byte SIG_CIPHER_DES_MAC4 = 1;
  public static final byte SIG_CIPHER_DES_MAC8 = 2;
  public static final byte SIG_CIPHER_RSA = 3;
  public static final byte SIG_CIPHER_DSA = 4;
  public static final byte SIG_CIPHER_ECDSA = 5;
  public static final byte SIG_CIPHER_ECDSA_PLAIN = 9;
  public static final byte SIG_CIPHER_AES_MAC128 = 6;
  public static final byte SIG_CIPHER_AES_CMAC128 = 10;
  public static final byte SIG_CIPHER_HMAC = 7;
  public static final byte SIG_CIPHER_KOREAN_SEED_MAC = 8;
  public static final byte MODE_SIGN = 1;
  public static final byte MODE_VERIFY = 2;

  protected Signature() {
  }

  public abstract void init(Key var1, byte var2) throws CryptoException;

  public abstract void init(Key var1, byte var2, byte[] var3, short var4, short var5) throws CryptoException;

  public abstract void setInitialDigest(byte[] var1, short var2, short var3, byte[] var4, short var5, short var6) throws CryptoException;

  public abstract byte getAlgorithm();

  public abstract byte getMessageDigestAlgorithm();

  public abstract byte getCipherAlgorithm();

  public abstract byte getPaddingAlgorithm();

  public abstract short getLength() throws CryptoException;

  public abstract void update(byte[] var1, short var2, short var3) throws CryptoException;

  public abstract short sign(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException;

  public abstract short signPreComputedHash(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException;

  public abstract boolean verify(byte[] var1, short var2, short var3, byte[] var4, short var5, short var6) throws CryptoException;

  public abstract boolean verifyPreComputedHash(byte[] var1, short var2, short var3, byte[] var4, short var5, short var6) throws CryptoException;

  public static final class OneShot extends Signature {
    private OneShot() {
    }

    public static final OneShot open(byte var0, byte var1, byte var2) throws CryptoException {
      return null;
    }

    public void close() {
    }

    public final void update(byte[] var1, short var2, short var3) throws CryptoException {
    }

    public void init(Key var1, byte var2) throws CryptoException {
    }

    public void init(Key var1, byte var2, byte[] var3, short var4, short var5) throws CryptoException {
    }

    public void setInitialDigest(byte[] var1, short var2, short var3, byte[] var4, short var5, short var6) throws CryptoException {
    }

    public byte getAlgorithm() {
      return 0;
    }

    public byte getMessageDigestAlgorithm() {
      return 0;
    }

    public byte getCipherAlgorithm() {
      return 0;
    }

    public byte getPaddingAlgorithm() {
      return 0;
    }

    public short getLength() throws CryptoException {
      return 0;
    }

    public short sign(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException {
      return 0;
    }

    public short signPreComputedHash(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException {
      return 0;
    }

    public boolean verify(byte[] var1, short var2, short var3, byte[] var4, short var5, short var6) throws CryptoException {
      return false;
    }

    public boolean verifyPreComputedHash(byte[] var1, short var2, short var3, byte[] var4, short var5, short var6) throws CryptoException {
      return false;
    }
  }

  /**
   * Creates a <code>Signature</code> object instance of the selected algorithm.
   * @param algorithm the desired Signature algorithm. Valid codes listed in
   * ALG_ .. constants above e.g. <A HREF="../../javacard/security/Signature.html#ALG_DES_MAC4_NOPAD"><CODE>ALG_DES_MAC4_NOPAD</CODE></A>
   * @param externalAccess <code>true</code> indicates that the instance will be shared among
   * multiple applet instances and that the <code>Signature</code> instance will also be accessed (via a <code>Shareable</code>
   * interface) when the owner of the <code>Signature</code> instance is not the currently selected applet.
   * If <code>true</code> the implementation must not allocate CLEAR_ON_DESELECT transient space for internal data.
   * @return the <code>Signature</code> object instance of the requested algorithm
   * @throws CryptoException with the following reason codes:<ul>
   * <li><code>CryptoException.NO_SUCH_ALGORITHM</code> if the requested algorithm
   * or shared access mode is not supported.</ul>
   */
  public static final Signature getInstance(byte algorithm, boolean externalAccess)
      throws CryptoException {
    Signature instance = null;
    //TODO: implement externalAccess logic
//        if (externalAccess) {
//            CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
//        }
    switch (algorithm) {
      case Signature.ALG_RSA_SHA_ISO9796:
      case Signature.ALG_RSA_SHA_PKCS1:
      case Signature.ALG_RSA_SHA_224_PKCS1:
      case Signature.ALG_RSA_SHA_256_PKCS1:
      case Signature.ALG_RSA_SHA_384_PKCS1:
      case Signature.ALG_RSA_SHA_512_PKCS1:
      case Signature.ALG_RSA_SHA_PKCS1_PSS:
      case Signature.ALG_RSA_SHA_224_PKCS1_PSS:
      case Signature.ALG_RSA_SHA_256_PKCS1_PSS:
      case Signature.ALG_RSA_SHA_384_PKCS1_PSS:
      case Signature.ALG_RSA_SHA_512_PKCS1_PSS:
      case Signature.ALG_RSA_MD5_PKCS1:
      case Signature.ALG_RSA_RIPEMD160_ISO9796:
      case Signature.ALG_RSA_RIPEMD160_PKCS1:
      case Signature.ALG_ECDSA_SHA:
      case Signature.ALG_ECDSA_SHA_224:
      case Signature.ALG_ECDSA_SHA_256:
      case Signature.ALG_ECDSA_SHA_384:
      case Signature.ALG_ECDSA_SHA_512:
      case Signature.ALG_RSA_SHA_ISO9796_MR:
      case Signature.ALG_DSA_SHA:
      case Signature.ALG_RSA_SHA_RFC2409:
      case Signature.ALG_RSA_MD5_RFC2409:
      case Signature.ALG_RSA_MD5_PKCS1_PSS:
      case Signature.ALG_RSA_RIPEMD160_PKCS1_PSS:
      case Signature.ALG_RSA_RIPEMD160_ISO9796_MR:
        System.out.println("getInstance of assymetric algo: " + algorithm);
        try {
          instance = new AsymmetricSignatureImpl(algorithm);
          System.out.println("getInstance of assymetric algo: " + algorithm + " is OK!");
        } catch(Exception e) {
          e.printStackTrace();
          e.getCause().printStackTrace();
          CryptoException.throwIt(CryptoException.INVALID_INIT);
        }
        break;
      case Signature.ALG_DES_MAC4_NOPAD:
      case Signature.ALG_DES_MAC8_NOPAD:
      case Signature.ALG_DES_MAC4_ISO9797_M1:
      case Signature.ALG_DES_MAC8_ISO9797_M1:
      case Signature.ALG_DES_MAC4_ISO9797_M2:
      case Signature.ALG_DES_MAC8_ISO9797_M2:
      case Signature.ALG_DES_MAC8_ISO9797_1_M2_ALG3:
      case Signature.ALG_DES_MAC4_PKCS5:
      case Signature.ALG_DES_MAC8_PKCS5:
      case Signature.ALG_AES_MAC_128_NOPAD:
      case Signature.ALG_HMAC_SHA1:
      case Signature.ALG_HMAC_SHA_256:
      case Signature.ALG_HMAC_SHA_384:
      case Signature.ALG_HMAC_SHA_512:
      case Signature.ALG_HMAC_MD5:
      case Signature.ALG_HMAC_RIPEMD160:
      case Signature.ALG_AES_CMAC_128:
        instance = new SymmetricSignatureImpl(algorithm);
        break;

      default:
        CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
        break;


    }
    return instance;
  }

  public static final Signature getInstance(byte messageDigestAlgorithm, byte cipherAlgorithm,
      byte paddingAlgorithm, boolean externalAccess) throws CryptoException {
    return new AsymmetricSignatureImpl(messageDigestAlgorithm, cipherAlgorithm, paddingAlgorithm);
  }
}
