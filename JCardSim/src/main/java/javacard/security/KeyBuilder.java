package javacard.security;

import com.licel.jcardsim.crypto.DHPrivateKeyImpl;
import com.licel.jcardsim.crypto.DHPublicKeyImpl;
import com.licel.jcardsim.crypto.DSAPrivateKeyImpl;
import com.licel.jcardsim.crypto.DSAPublicKeyImpl;
import com.licel.jcardsim.crypto.ECPrivateKeyImpl;
import com.licel.jcardsim.crypto.ECPublicKeyImpl;
import com.licel.jcardsim.crypto.RSAKeyImpl;
import com.licel.jcardsim.crypto.RSAPrivateCrtKeyImpl;
import com.licel.jcardsim.crypto.SymmetricKeyImpl;

public class KeyBuilder {
  public static final byte TYPE_DES_TRANSIENT_RESET = 1;
  public static final byte TYPE_DES_TRANSIENT_DESELECT = 2;
  public static final byte TYPE_DES = 3;
  public static final byte TYPE_RSA_PUBLIC = 4;
  public static final byte TYPE_RSA_PRIVATE = 5;
  public static final byte TYPE_RSA_CRT_PRIVATE = 6;
  public static final byte TYPE_DSA_PUBLIC = 7;
  public static final byte TYPE_DSA_PRIVATE = 8;
  public static final byte TYPE_EC_F2M_PUBLIC = 9;
  public static final byte TYPE_EC_F2M_PRIVATE = 10;
  public static final byte TYPE_EC_FP_PUBLIC = 11;
  public static final byte TYPE_EC_FP_PRIVATE = 12;
  public static final byte TYPE_AES_TRANSIENT_RESET = 13;
  public static final byte TYPE_AES_TRANSIENT_DESELECT = 14;
  public static final byte TYPE_AES = 15;
  public static final byte TYPE_KOREAN_SEED_TRANSIENT_RESET = 16;
  public static final byte TYPE_KOREAN_SEED_TRANSIENT_DESELECT = 17;
  public static final byte TYPE_KOREAN_SEED = 18;
  public static final byte TYPE_HMAC_TRANSIENT_RESET = 19;
  public static final byte TYPE_HMAC_TRANSIENT_DESELECT = 20;
  public static final byte TYPE_HMAC = 21;
  public static final byte TYPE_RSA_PRIVATE_TRANSIENT_RESET = 22;
  public static final byte TYPE_RSA_PRIVATE_TRANSIENT_DESELECT = 23;
  public static final byte TYPE_RSA_CRT_PRIVATE_TRANSIENT_RESET = 24;
  public static final byte TYPE_RSA_CRT_PRIVATE_TRANSIENT_DESELECT = 25;
  public static final byte TYPE_DSA_PRIVATE_TRANSIENT_RESET = 26;
  public static final byte TYPE_DSA_PRIVATE_TRANSIENT_DESELECT = 27;
  public static final byte TYPE_EC_F2M_PRIVATE_TRANSIENT_RESET = 28;
  public static final byte TYPE_EC_F2M_PRIVATE_TRANSIENT_DESELECT = 29;
  public static final byte TYPE_EC_FP_PRIVATE_TRANSIENT_RESET = 30;
  public static final byte TYPE_EC_FP_PRIVATE_TRANSIENT_DESELECT = 31;
  public static final byte TYPE_DH_PUBLIC = 32;
  public static final byte TYPE_DH_PUBLIC_TRANSIENT_DESELECT = 33;
  public static final byte TYPE_DH_PUBLIC_TRANSIENT_RESET = 34;
  public static final byte TYPE_DH_PRIVATE = 35;
  public static final byte TYPE_DH_PRIVATE_TRANSIENT_DESELECT = 36;
  public static final byte TYPE_DH_PRIVATE_TRANSIENT_RESET = 37;
  public static final byte ALG_TYPE_DES = 1;
  public static final byte ALG_TYPE_AES = 2;
  public static final byte ALG_TYPE_DSA_PUBLIC = 3;
  public static final byte ALG_TYPE_DSA_PRIVATE = 4;
  public static final byte ALG_TYPE_EC_F2M_PUBLIC = 5;
  public static final byte ALG_TYPE_EC_F2M_PRIVATE = 6;
  public static final byte ALG_TYPE_EC_FP_PUBLIC = 7;
  public static final byte ALG_TYPE_EC_FP_PRIVATE = 8;
  public static final byte ALG_TYPE_HMAC = 9;
  public static final byte ALG_TYPE_KOREAN_SEED = 10;
  public static final byte ALG_TYPE_RSA_PUBLIC = 11;
  public static final byte ALG_TYPE_RSA_PRIVATE = 12;
  public static final byte ALG_TYPE_RSA_CRT_PRIVATE = 13;
  public static final byte ALG_TYPE_DH_PUBLIC = 14;
  public static final byte ALG_TYPE_DH_PRIVATE = 15;
  public static final byte ALG_TYPE_EC_F2M_PARAMETERS = 16;
  public static final byte ALG_TYPE_EC_FP_PARAMETERS = 17;
  public static final byte ALG_TYPE_DSA_PARAMETERS = 18;
  public static final byte ALG_TYPE_DH_PARAMETERS = 19;
  public static final short LENGTH_DES = 64;
  public static final short LENGTH_DES3_2KEY = 128;
  public static final short LENGTH_DES3_3KEY = 192;
  public static final short LENGTH_RSA_512 = 512;
  public static final short LENGTH_RSA_736 = 736;
  public static final short LENGTH_RSA_768 = 768;
  public static final short LENGTH_RSA_896 = 896;
  public static final short LENGTH_RSA_1024 = 1024;
  public static final short LENGTH_RSA_1280 = 1280;
  public static final short LENGTH_RSA_1536 = 1536;
  public static final short LENGTH_RSA_1984 = 1984;
  public static final short LENGTH_RSA_2048 = 2048;
  public static final short LENGTH_RSA_3072 = 3072;
  public static final short LENGTH_RSA_4096 = 4096;
  public static final short LENGTH_DSA_512 = 512;
  public static final short LENGTH_DSA_768 = 768;
  public static final short LENGTH_DSA_1024 = 1024;
  public static final short LENGTH_EC_FP_112 = 112;
  public static final short LENGTH_EC_F2M_113 = 113;
  public static final short LENGTH_EC_FP_128 = 128;
  public static final short LENGTH_EC_F2M_131 = 131;
  public static final short LENGTH_EC_FP_160 = 160;
  public static final short LENGTH_EC_F2M_163 = 163;
  public static final short LENGTH_EC_FP_192 = 192;
  public static final short LENGTH_EC_F2M_193 = 193;
  public static final short LENGTH_EC_FP_224 = 224;
  public static final short LENGTH_EC_FP_256 = 256;
  public static final short LENGTH_EC_FP_384 = 384;
  public static final short LENGTH_EC_FP_521 = 521;
  public static final short LENGTH_AES_128 = 128;
  public static final short LENGTH_AES_192 = 192;
  public static final short LENGTH_AES_256 = 256;
  public static final short LENGTH_KOREAN_SEED_128 = 128;
  public static final short LENGTH_HMAC_SHA_1_BLOCK_64 = 64;
  public static final short LENGTH_HMAC_SHA_256_BLOCK_64 = 64;
  public static final short LENGTH_HMAC_SHA_384_BLOCK_128 = 128;
  public static final short LENGTH_HMAC_SHA_512_BLOCK_128 = 128;
  public static final short LENGTH_DH_1024 = 1024;
  public static final short LENGTH_DH_2048 = 2048;

  private KeyBuilder() {
  }


  public static Key buildKey(byte var0, byte var1, short var2, boolean var3) throws CryptoException {
    return null;
  }

  public static Key buildKeyWithSharedDomain(byte var0, byte var1, Key var2, boolean var3) throws CryptoException {
    return null;
  }
  /**
   * Creates uninitialized cryptographic keys for signature and cipher algorithms. Only instances created
   * by this method may be the key objects used to initialize instances of
   * <code>Signature</code>, <code>Cipher</code> and <code>KeyPair</code>.
   * Note that the object returned must be cast to their appropriate key type interface.
   * @param keyType the type of key to be generated. Valid codes listed in TYPE.. constants.
   * See {@link KeyBuilder#TYPE_DES_TRANSIENT_RESET}.
   * @param keyLength the key size in bits. The valid key bit lengths are key type dependent. Some common
   * key lengths are listed above above in the LENGTH_.. constants.
   * See {@link KeyBuilder#LENGTH_DES}.
   * @param keyEncryption if <code>true</code> this boolean requests a key implementation
   * which implements the <code>javacardx.crypto.KeyEncryption</code> interface.
   * The key implementation returned may implement the <code>javacardx.crypto.KeyEncryption</code>
   * interface even when this parameter is <code>false</code>.
   * @return the key object instance of the requested key type, length and encrypted access
   * @throws CryptoException with the following reason codes:<ul>
   * <li><code>CryptoException.NO_SUCH_ALGORITHM</code> if the requested algorithm
   * associated with the specified type, size of key and key encryption interface is not supported.</ul>
   */
  public static Key buildKey(byte keyType, short keyLength, boolean keyEncryption)
      throws CryptoException {
    Key key = null;
    switch (keyType) {
      // des
      case KeyBuilder.TYPE_DES_TRANSIENT_RESET:
      case KeyBuilder.TYPE_DES_TRANSIENT_DESELECT:
      case KeyBuilder.TYPE_DES:
        if (keyLength != 64 && keyLength != 128 && keyLength != 192) {
          CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }
        key = new SymmetricKeyImpl(keyType, keyLength);
        break;

      // rsa
      case KeyBuilder.TYPE_RSA_PUBLIC:
        key = new RSAKeyImpl(false, keyLength);
        break;

      case KeyBuilder.TYPE_RSA_PRIVATE:
        key = new RSAKeyImpl(true, keyLength);
        break;

      case KeyBuilder.TYPE_RSA_CRT_PRIVATE:
        key = new RSAPrivateCrtKeyImpl(keyLength);
        break;

      // dsa
      case KeyBuilder.TYPE_DSA_PUBLIC:
        key = new DSAPublicKeyImpl(keyLength);
        break;

      case KeyBuilder.TYPE_DSA_PRIVATE:
        key = new DSAPrivateKeyImpl(keyLength);
        break;

      // ecc
      case KeyBuilder.TYPE_EC_F2M_PUBLIC:
        key = new ECPublicKeyImpl(keyType, keyLength);
        break;
      case KeyBuilder.TYPE_EC_F2M_PRIVATE:
        key = new ECPrivateKeyImpl(keyType, keyLength);
        break;

      case KeyBuilder.TYPE_EC_FP_PUBLIC:
        key = new ECPublicKeyImpl(keyType, keyLength);
        break;
      case KeyBuilder.TYPE_EC_FP_PRIVATE:
        key = new ECPrivateKeyImpl(keyType, keyLength);
        break;

      // aes
      case KeyBuilder.TYPE_AES_TRANSIENT_RESET:
      case KeyBuilder.TYPE_AES_TRANSIENT_DESELECT:
      case KeyBuilder.TYPE_AES:
        if (keyLength != 128 && keyLength != 192 && keyLength != 256) {
          CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }
        key = new SymmetricKeyImpl(keyType, keyLength);
        break;

      // hmac
      case KeyBuilder.TYPE_HMAC_TRANSIENT_RESET:
      case KeyBuilder.TYPE_HMAC_TRANSIENT_DESELECT:
      case KeyBuilder.TYPE_HMAC:
        key = new SymmetricKeyImpl(keyType, keyLength);
        break;

      // dh
      case KeyBuilder.TYPE_DH_PUBLIC_TRANSIENT_RESET:
      case KeyBuilder.TYPE_DH_PUBLIC_TRANSIENT_DESELECT:
      case KeyBuilder.TYPE_DH_PUBLIC:
        key = new DHPublicKeyImpl(keyLength);
        break;

      case KeyBuilder.TYPE_DH_PRIVATE_TRANSIENT_RESET:
      case KeyBuilder.TYPE_DH_PRIVATE_TRANSIENT_DESELECT:
      case KeyBuilder.TYPE_DH_PRIVATE:
        key = new DHPrivateKeyImpl(keyLength);
        break;

      case KeyBuilder.TYPE_KOREAN_SEED_TRANSIENT_RESET:
      case KeyBuilder.TYPE_KOREAN_SEED_TRANSIENT_DESELECT:
      case KeyBuilder.TYPE_KOREAN_SEED:
        if (keyLength != KeyBuilder.LENGTH_KOREAN_SEED_128) {
          CryptoException.throwIt(CryptoException.ILLEGAL_VALUE);
        }
        key = new SymmetricKeyImpl(keyType, keyLength);

        break;
      default:
        CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
        break;
    }
    return key;
  }
}
