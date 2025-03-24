package javacard.framework;
  public class UserException extends CardException {
    public UserException() {
      this((short)0);
    }

    public UserException(short var1) {
      super(var1);
    }

    public static void throwIt(short var0) throws javacard.framework.UserException {
    }
  }
