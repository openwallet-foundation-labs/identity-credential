package javacardx.framework.util;

import javacard.framework.CardRuntimeException;

public class UtilException extends CardRuntimeException {
  public static final short ILLEGAL_VALUE = 1;
  public static final short TYPE_MISMATCHED = 2;

  public UtilException(short var1) {
    super(var1);
  }

  public static void throwIt(short var0) {
  }
}
