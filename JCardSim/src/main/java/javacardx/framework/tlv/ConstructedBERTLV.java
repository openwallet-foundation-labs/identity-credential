package javacardx.framework.tlv;

public final class ConstructedBERTLV extends BERTLV {
  public ConstructedBERTLV(short var1) {
  }

  public short init(byte[] var1, short var2, short var3) throws TLVException {
    return 0;
  }

  public short init(ConstructedBERTag var1, BERTLV var2) throws TLVException {
    return 0;
  }

  public short init(ConstructedBERTag var1, byte[] var2, short var3, short var4) throws TLVException {
    return 0;
  }

  public short append(BERTLV var1) throws TLVException {
    return 0;
  }

  public short delete(BERTLV var1, short var2) throws TLVException {
    return 0;
  }

  public BERTLV find(BERTag var1) {
    return null;
  }

  public BERTLV findNext(BERTag var1, BERTLV var2, short var3) {
    return null;
  }

  public static short append(byte[] var0, short var1, byte[] var2, short var3) throws TLVException {
    return 0;
  }

  public static short find(byte[] var0, short var1, byte[] var2, short var3) throws TLVException {
    return 0;
  }

  public static short findNext(byte[] var0, short var1, short var2, byte[] var3, short var4) throws TLVException {
    return 0;
  }
}
