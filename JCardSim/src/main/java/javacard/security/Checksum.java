package javacard.security;

import com.licel.jcardsim.crypto.CRC16;
import com.licel.jcardsim.crypto.CRC32;

public abstract class Checksum {
  public static final byte ALG_ISO3309_CRC16 = 1;
  public static final byte ALG_ISO3309_CRC32 = 2;
  /**
   * Creates a <code>Checksum</code> object instance of the selected algorithm.
   * @param algorithm the desired checksum algorithm.
   * @param externalAccess <code>true</code> indicates that the instance will be shared among
   * multiple applet instances and that the <code>Checksum</code> instance will also be accessed (via a <code>Shareable</code>.
   * interface) when the owner of the <code>Checksum</code> instance is not the currently selected applet.
   * If <code>true</code> the implementation must not allocate CLEAR_ON_DESELECT transient space for internal data.
   * @return the <code>Checksum</code> object instance of the requested algorithm.
   * @throws CryptoException  with the following reason codes:
   * <ul>
   * <li><code>CryptoException.NO_SUCH_ALGORITHM</code> if the requested algorithm
   * or shared access mode is not supported.
   * </ul>
   */
  public static final Checksum getInstance(byte algorithm, boolean externalAccess)
      throws CryptoException {
    if (externalAccess) {
      CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
    }
    Checksum instance = null;
    switch (algorithm) {
      case Checksum.ALG_ISO3309_CRC16:
        instance = new CRC16();
        break;

      case Checksum.ALG_ISO3309_CRC32:
        instance = new CRC32();
        break;

      default:
        CryptoException.throwIt(CryptoException.NO_SUCH_ALGORITHM);
        break;
    }
    return instance;
  }

  protected Checksum() {
  }

  public abstract void init(byte[] var1, short var2, short var3) throws CryptoException;

  public abstract byte getAlgorithm();

  public abstract short doFinal(byte[] var1, short var2, short var3, byte[] var4, short var5);

  public abstract void update(byte[] var1, short var2, short var3);
}
