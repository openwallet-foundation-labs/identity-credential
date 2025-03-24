package javacard.security;

import javacard.framework.CardRuntimeException;

  public class CryptoException extends CardRuntimeException {
    public static final short ILLEGAL_VALUE = 1;
    public static final short UNINITIALIZED_KEY = 2;
    public static final short NO_SUCH_ALGORITHM = 3;
    public static final short INVALID_INIT = 4;
    public static final short ILLEGAL_USE = 5;

    public CryptoException(short var1) {
      super(var1);
    }

    public static void throwIt(short var0) {
    }
  }
