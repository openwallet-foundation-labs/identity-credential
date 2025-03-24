package javacardx.framework.string;

import javacard.framework.CardRuntimeException;

public class StringException extends CardRuntimeException {
  public static final short UNSUPPORTED_ENCODING = 1;
  public static final short ILLEGAL_NUMBER_FORMAT = 2;
  public static final short INVALID_BYTE_SEQUENCE = 3;

  public StringException(short var1) {
    super(var1);
  }

  public static void throwIt(short var0) {
  }
}
