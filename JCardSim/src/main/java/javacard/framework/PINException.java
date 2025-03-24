package javacard.framework;

public class PINException extends CardRuntimeException {
  public static final short ILLEGAL_VALUE = 1;
  public static final short ILLEGAL_STATE = 2;

  public PINException(short var1) {
    super(var1);
  }

  public static void throwIt(short var0) {
  }
}