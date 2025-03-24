package javacardx.biometry1toN;

public interface OwnerBioTemplateData extends BioTemplateData {
  void init(byte[] var1, short var2, short var3) throws Bio1toNException;

  void update(byte[] var1, short var2, short var3) throws Bio1toNException;

  void doFinal() throws Bio1toNException;
}