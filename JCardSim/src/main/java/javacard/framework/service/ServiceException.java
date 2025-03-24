package javacard.framework.service;

import javacard.framework.CardRuntimeException;

public class ServiceException extends CardRuntimeException {
  public static final short ILLEGAL_PARAM = 1;
  public static final short DISPATCH_TABLE_FULL = 2;
  public static final short COMMAND_DATA_TOO_LONG = 3;
  public static final short CANNOT_ACCESS_IN_COMMAND = 4;
  public static final short CANNOT_ACCESS_OUT_COMMAND = 5;
  public static final short COMMAND_IS_FINISHED = 6;
  public static final short REMOTE_OBJECT_NOT_EXPORTED = 7;

  public ServiceException(short var1) {
    super(var1);
  }

  public static void throwIt(short var0) throws ServiceException {
  }
}
