package javacard.framework;

public class SensitiveArrays {
  private SensitiveArrays() {
  }

  public static native void assertIntegrity(Object var0);

  public static native boolean isIntegritySensitive(Object var0);

  public static native boolean isIntegritySensitiveArraysSupported();

  public static native Object makeIntegritySensitiveArray(byte var0, byte var1, short var2);

  public static native short clearArray(Object var0) throws TransactionException;
}