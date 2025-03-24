package javacardx.biometry;

import javacard.framework.CardRuntimeException;

public class BioException extends CardRuntimeException {
  public static final short ILLEGAL_VALUE = 1;
  public static final short INVALID_DATA = 2;
  public static final short NO_SUCH_BIO_TEMPLATE = 3;
  public static final short NO_TEMPLATES_ENROLLED = 4;
  public static final short ILLEGAL_USE = 5;

  public BioException(short var1) {
    super(var1);
  }

  public static void throwIt(short var0) throws BioException {
  }
}