package javacardx.biometry1toN;

public interface BioTemplateData {
  byte getBioType();

  boolean isInitialized();

  short getPublicData(short var1, byte[] var2, short var3, short var4) throws Bio1toNException;
}
