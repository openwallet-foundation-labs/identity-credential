package javacard.security;

import com.licel.jcardsim.crypto.KeyAgreementImpl;

public abstract class KeyAgreement {
  public static final byte ALG_EC_SVDP_DH = 1;
  public static final byte ALG_EC_SVDP_DH_KDF = 1;
  public static final byte ALG_EC_SVDP_DHC = 2;
  public static final byte ALG_EC_SVDP_DHC_KDF = 2;
  public static final byte ALG_EC_SVDP_DH_PLAIN = 3;
  public static final byte ALG_EC_SVDP_DHC_PLAIN = 4;
  public static final byte ALG_EC_PACE_GM = 5;
  public static final byte ALG_EC_SVDP_DH_PLAIN_XY = 6;
  public static final byte ALG_DH_PLAIN = 7;
  /**
   * Creates a <CODE>KeyAgreement</CODE> object instance of the selected algorithm.
   * @param algorithm the desired key agreement algorithm
   * Valid codes listed in ALG_ .. constants above, for example, <CODE>ALG_EC_SVDP_DH</CODE>
   * @param externalAccess  if <code>true</code> indicates that the instance will be shared among
   * multiple applet instances and that the <code>KeyAgreement</code> instance will also be accessed (via a <code>Shareable</code>
   * interface) when the owner of the <code>KeyAgreement</code> instance is not the currently selected applet.
   * If <code>true</code> the implementation must not
   * allocate <code>CLEAR_ON_DESELECT</code> transient space for internal data.
   * @return the KeyAgreement object instance of the requested algorithm
   * @throws CryptoException with the following reason codes:
   * <ul>
   * <li><code>CryptoException.NO_SUCH_ALGORITHM</code> if the requested
   * algorithm or shared access mode is not supported.
   * </ul>
   */
  public static final KeyAgreement getInstance(byte algorithm, boolean externalAccess)
      throws CryptoException {
    return new KeyAgreementImpl(algorithm);
  }



  protected KeyAgreement() {
  }


  public abstract void init(PrivateKey var1) throws CryptoException;

  public abstract byte getAlgorithm();

  public abstract short generateSecret(byte[] var1, short var2, short var3, byte[] var4, short var5) throws CryptoException;
}
