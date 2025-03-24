package javacard.framework;

public class TransactionException extends CardRuntimeException{
  public static final short IN_PROGRESS = 1;
  public static final short NOT_IN_PROGRESS = 2;
  public static final short BUFFER_FULL = 3;
  public static final short INTERNAL_FAILURE = 4;
  public static final short ILLEGAL_USE = 5;
  public TransactionException(short var1) {
    super(var1);
  }

  public static void throwIt(short var0) {
  }
}
