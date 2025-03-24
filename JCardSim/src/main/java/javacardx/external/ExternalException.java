package javacardx.external;

import javacard.framework.CardRuntimeException;

public class ExternalException extends CardRuntimeException {
  public static final short NO_SUCH_SUBSYSTEM = 1;
  public static final short INVALID_PARAM = 2;
  public static final short INTERNAL_ERROR = 3;

  public ExternalException(short var1) {
    super(var1);
  }

  public static void throwIt(short var0) {
  }
}
