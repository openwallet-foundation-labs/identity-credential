package javacard.framework;

public class ISOException extends CardRuntimeException{
  public ISOException(short var1) {
    super(var1);
  }

  public static void throwIt(short var0) {
    throw new ISOException(var0);
  }
}
