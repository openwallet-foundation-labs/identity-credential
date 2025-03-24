package javacard.framework;

public class SystemException extends CardRuntimeException{
  public static final short ILLEGAL_VALUE = 1;
  public static final short NO_TRANSIENT_SPACE = 2;
  public static final short ILLEGAL_TRANSIENT = 3;
  public static final short ILLEGAL_AID = 4;
  public static final short NO_RESOURCE = 5;
  public static final short ILLEGAL_USE = 6;

  public SystemException(short var1) {
    super(var1);
  }

  public static void throwIt(short var0) throws SystemException {
  }
}
