package javacard.security;

public interface Key {
  boolean isInitialized();

  void clearKey();

  byte getType();

  short getSize();
}
