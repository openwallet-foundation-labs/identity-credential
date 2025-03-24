package javacardx.framework.util.intx;

import javacard.framework.SystemException;
import javacard.framework.TransactionException;

public final class JCint {
  JCint() {
  }

  public static final int makeInt(byte var0, byte var1, byte var2, byte var3) {
    return 0;
  }

  public static final int makeInt(short var0, short var1) {
    return 0;
  }

  public static final int getInt(byte[] var0, short var1) throws NullPointerException, ArrayIndexOutOfBoundsException {
    return 0;
  }

  public static final native short setInt(byte[] var0, short var1, int var2) throws TransactionException, NullPointerException, ArrayIndexOutOfBoundsException;

  public static native int[] makeTransientIntArray(short var0, byte var1) throws NegativeArraySizeException, SystemException;
}

