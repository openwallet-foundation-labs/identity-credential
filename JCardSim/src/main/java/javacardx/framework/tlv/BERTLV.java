package javacardx.framework.tlv;

public abstract class BERTLV {
  protected BERTLV() {
  }

  public abstract short init(byte[] var1, short var2, short var3) throws TLVException;

  public static BERTLV getInstance(byte[] var0, short var1, short var2) throws TLVException {
    return null;
  }

  public short toBytes(byte[] var1, short var2) {
    return 0;
  }

  public BERTag getTag() throws TLVException {
    return null;
  }

  public short getLength() throws TLVException {
    return 0;
  }

  public short size() {
    return 0;
  }

  public static boolean verifyFormat(byte[] var0, short var1, short var2) {
    return false;
  }

  public static short getTag(byte[] var0, short var1, byte[] var2, short var3) throws TLVException {
    return 0;
  }

  public static short getLength(byte[] var0, short var1) throws TLVException {
    return 0;
  }
}
