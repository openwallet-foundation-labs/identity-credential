package javacard.security;

public abstract class InitializedMessageDigest extends MessageDigest {
  public abstract void setInitialDigest(byte[] var1, short var2, short var3, byte[] var4, short var5, short var6) throws CryptoException;

  protected InitializedMessageDigest() {
  }

  public static final class OneShot extends InitializedMessageDigest {
    private OneShot() {
    }

    public static final InitializedMessageDigest.OneShot open(byte var0) throws CryptoException {
      return null;
    }

    public void close() {
    }

    public void setInitialDigest(byte[] var1, short var2, short var3, byte[] var4, short var5, short var6) throws CryptoException {
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

    public void reset() {
    }

    public void update(byte[] var1, short var2, short var3) throws CryptoException {
    }
  }
}
