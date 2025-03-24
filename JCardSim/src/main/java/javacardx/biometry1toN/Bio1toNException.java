package javacardx.biometry1toN;

import javacard.framework.CardRuntimeException;

public class Bio1toNException extends CardRuntimeException{
    public static final short ILLEGAL_VALUE = 1;
    public static final short INVALID_DATA = 2;
    public static final short UNSUPPORTED_BIO_TYPE = 3;
    public static final short NO_BIO_TEMPLATE_ENROLLED = 4;
    public static final short ILLEGAL_USE = 5;
    public static final short BIO_TEMPLATE_DATA_CAPACITY_EXCEEDED = 6;
    public static final short MISMATCHED_BIO_TYPE = 7;

    public Bio1toNException(short var1) {
      super(var1);
    }

    public static void throwIt(short var0) throws Bio1toNException {

    }
}
