package javacard.security;

import com.licel.jcardsim.crypto.RandomDataImpl;

public abstract class RandomData {
  /** @deprecated */
  public static final byte ALG_PSEUDO_RANDOM = 1;
  /** @deprecated */
  public static final byte ALG_SECURE_RANDOM = 2;
  public static final byte ALG_TRNG = 3;
  public static final byte ALG_PRESEEDED_DRBG = 4;
  public static final byte ALG_FAST = 5;
  public static final byte ALG_KEYGENERATION = 6;

  protected RandomData() {
  }

  /** @deprecated */
  public abstract void generateData(byte[] var1, short var2, short var3) throws CryptoException;

  public abstract short nextBytes(byte[] var1, short var2, short var3) throws CryptoException;

  public abstract void setSeed(byte[] var1, short var2, short var3);

  public abstract byte getAlgorithm();

  public static final class OneShot extends RandomData {
    private OneShot() {
    }

    public static final OneShot open(byte var0) throws CryptoException {
      return null;
    }

    public void close() {
    }

    public byte getAlgorithm() {
      return 0;
    }

    /** @deprecated */
    public void generateData(byte[] var1, short var2, short var3) throws CryptoException {
    }

    public short nextBytes(byte[] var1, short var2, short var3) throws CryptoException {
      return 0;
    }

    public void setSeed(byte[] var1, short var2, short var3) {
    }
  }
  /**
   * Creates a <code>RandomData</code> instance of the selected algorithm.
   * The pseudo random <code>RandomData</code> instance's seed is initialized to a internal default value.
   * @param algorithm the desired random number algorithm. Valid codes listed in ALG_ .. constants above. See <A HREF="../../javacard/security/RandomData.html#ALG_PSEUDO_RANDOM"><CODE>ALG_PSEUDO_RANDOM</CODE></A>.
   * @return the <code>RandomData</code> object instance of the requested algorithm
   * @throws CryptoException with the following reason codes:<ul>
   * <li><code>CryptoException.NO_SUCH_ALGORITHM</code> if the requested algorithm is not supported.</ul>
   */
  public static final RandomData getInstance(byte algorithm)
      throws CryptoException {
    RandomData instance = null;
    switch (algorithm) {
      case ALG_PSEUDO_RANDOM:
      case ALG_SECURE_RANDOM:
      case ALG_TRNG:
      case ALG_FAST:
      case ALG_KEYGENERATION:
        instance = new RandomDataImpl(algorithm);
        break;
      default:
        CryptoException.throwIt((short) 3);
        break;
    }
    return instance;
  }
}
