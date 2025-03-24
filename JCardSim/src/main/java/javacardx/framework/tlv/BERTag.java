package javacardx.framework.tlv;

public abstract class BERTag {
  public static final byte BER_TAG_CLASS_MASK_UNIVERSAL = 0;
  public static final byte BER_TAG_CLASS_MASK_APPLICATION = 1;
  public static final byte BER_TAG_CLASS_MASK_CONTEXT_SPECIFIC = 2;
  public static final byte BER_TAG_CLASS_MASK_PRIVATE = 3;
  public static final boolean BER_TAG_TYPE_CONSTRUCTED = true;
  public static final boolean BER_TAG_TYPE_PRIMITIVE = false;

  protected BERTag() {
  }

  public abstract void init(byte[] var1, short var2) throws TLVException;

  public static BERTag getInstance(byte[] var0, short var1) throws TLVException {
    return null;
  }

  public byte size() throws TLVException {
    return 0;
  }

  public short toBytes(byte[] var1, short var2) throws TLVException {
    return 0;
  }

  public short tagNumber() throws TLVException {
    return 0;
  }

  public boolean isConstructed() {
    return false;
  }

  public byte tagClass() {
    return 0;
  }

  public boolean equals(BERTag var1) {
    return false;
  }

  public boolean equals(Object var1) {
    return false;
  }

  public static short toBytes(short var0, boolean var1, short var2, byte[] var3, short var4) {
    return 0;
  }

  public static byte size(byte[] var0, short var1) throws TLVException {
    return 0;
  }

  public static short tagNumber(byte[] var0, short var1) throws TLVException {
    return 0;
  }

  public static boolean isConstructed(byte[] var0, short var1) {
    return false;
  }

  public static byte tagClass(byte[] var0, short var1) {
    return 0;
  }

  public static boolean verifyFormat(byte[] var0, short var1) {
    return false;
  }
}

