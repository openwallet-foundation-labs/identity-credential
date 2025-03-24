package javacardx.framework.util;

import javacard.framework.SystemException;
import javacard.framework.TransactionException;

public final class ArrayLogic {
  ArrayLogic() {
  }

  public static final native short arrayCopyRepack(Object var0, short var1, short var2, Object var3, short var4) throws ArrayIndexOutOfBoundsException, NullPointerException, TransactionException, UtilException;

  public static final native short arrayCopyRepackNonAtomic(Object var0, short var1, short var2, Object var3, short var4) throws ArrayIndexOutOfBoundsException, NullPointerException, UtilException, SystemException;

  public static final native short arrayFillGeneric(Object var0, short var1, short var2, Object var3, short var4) throws ArrayIndexOutOfBoundsException, NullPointerException, UtilException, TransactionException;

  public static final native short arrayFillGenericNonAtomic(Object var0, short var1, short var2, Object var3, short var4) throws ArrayIndexOutOfBoundsException, NullPointerException, UtilException, SystemException;

  public static final native byte arrayCompareGeneric(Object var0, short var1, Object var2, short var3, short var4) throws ArrayIndexOutOfBoundsException, NullPointerException, UtilException;

  public static final native short arrayFindGeneric(Object var0, short var1, byte[] var2, short var3) throws ArrayIndexOutOfBoundsException, NullPointerException, UtilException;
}
