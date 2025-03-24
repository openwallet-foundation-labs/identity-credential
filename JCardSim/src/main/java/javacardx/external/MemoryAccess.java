package javacardx.external;

public interface MemoryAccess {
  boolean writeData(byte[] var1, short var2, short var3, byte[] var4, short var5, short var6, short var7, short var8) throws ExternalException;

  short readData(byte[] var1, short var2, byte[] var3, short var4, short var5, short var6, short var7, short var8) throws ExternalException;
}
