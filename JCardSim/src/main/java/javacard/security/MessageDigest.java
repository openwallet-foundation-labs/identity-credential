package javacard.security;

import com.licel.jcardsim.crypto.MessageDigestImpl;

public abstract class MessageDigest {
  public static final byte ALG_NULL = 0;
  public static final byte ALG_SHA = 1;
  public static final byte ALG_MD5 = 2;
  public static final byte ALG_RIPEMD160 = 3;
  public static final byte ALG_SHA_256 = 4;
  public static final byte ALG_SHA_384 = 5;
  public static final byte ALG_SHA_512 = 6;
  public static final byte ALG_SHA_224 = 7;
  public static final byte ALG_SHA3_224 = 8;
  public static final byte ALG_SHA3_256 = 9;
  public static final byte ALG_SHA3_384 = 10;
  public static final byte ALG_SHA3_512 = 11;
  public static final byte LENGTH_MD5 = 16;
  public static final byte LENGTH_RIPEMD160 = 20;
  public static final byte LENGTH_SHA = 20;
  public static final byte LENGTH_SHA_224 = 28;
  public static final byte LENGTH_SHA_256 = 32;
  public static final byte LENGTH_SHA_384 = 48;
  public static final byte LENGTH_SHA_512 = 64;
  public static final byte LENGTH_SHA3_224 = 28;
  public static final byte LENGTH_SHA3_256 = 32;
  public static final byte LENGTH_SHA3_384 = 48;
  public static final byte LENGTH_SHA3_512 = 64;

  protected MessageDigest() {
  }
  /**
   * Creates a <code>MessageDigest</code> object instance of the selected algorithm.
   * @param algorithm the desired message digest algorithm.
   * Valid codes listed in ALG_ .. constants above, for example, <A HREF="../../javacard/security/MessageDigest.html#ALG_SHA"><CODE>ALG_SHA</CODE></A>.
   * @param externalAccess <code>true</code> indicates that the instance will be shared among
   * multiple applet instances and that the <code>MessageDigest</code> instance will also be accessed (via a <code>Shareable</code>.
   * interface) when the owner of the <code>MessageDigest</code> instance is not the currently selected applet.
   * If <code>true</code> the implementation must not allocate CLEAR_ON_DESELECT transient space for internal data.
   * @return the <code>MessageDigest</code> object instance of the requested algorithm
   * @throws CryptoException with the following reason codes:<ul>
   * <li><code>CryptoException.NO_SUCH_ALGORITHM</code> if the requested algorithm
   * or shared access mode is not supported.</ul>
   */
  public static final MessageDigest getInstance(byte algorithm, boolean externalAccess)
      throws CryptoException {
    if (externalAccess) {
      CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
    }
    MessageDigest instance = new MessageDigestImpl(algorithm);
    return instance;
  }

  /**
   * Creates a
   * <code>InitializedMessageDigest</code> object instance of the selected algorithm.
   * <p>
   *
   * @param algorithm the desired message digest algorithm. Valid codes listed in ALG_* constants above,
   * for example, {@link MessageDigest#ALG_SHA}.
   * @param externalAccess true indicates that the instance will be shared among multiple applet
   * instances and that the <code>InitializedMessageDigest</code> instance will also be accessed (via a <code>Shareable</code>. interface)
   * when the owner of the <code>InitializedMessageDigest</code> instance is not the currently selected applet.
   * If true the implementation must not allocate CLEAR_ON_DESELECT transient space for internal data.
   * @return the <code>InitializedMessageDigest</code> object instance of the requested algorithm
   * @throws CryptoException with the following reason codes: <code>CryptoException.NO_SUCH_ALGORITHM</code>
   * if the requested algorithm or shared access mode is not supported.
   * @since 2.2.2
   */
  public static final InitializedMessageDigest getInitializedMessageDigestInstance(byte algorithm,
      boolean externalAccess) throws CryptoException {
    if (externalAccess) {
      CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
    }
    InitializedMessageDigest instance = new MessageDigestImpl(algorithm);
    return instance;
  }
  public abstract byte getAlgorithm();

  public abstract byte getLength();

  public abstract short doFinal(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException;

  public abstract void update(byte[] var1, short var2, short var3) throws CryptoException;

  public abstract void reset();

  public static final class OneShot extends MessageDigest {
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

    public byte getLength() {
      return 0;
    }

    public short doFinal(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException {
      return 0;
    }

    public void update(byte[] var1, short var2, short var3) throws CryptoException {
    }

    public void reset() {
    }
  }
}
