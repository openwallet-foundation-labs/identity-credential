package javacardx.biometry1toN;

public interface OwnerBioMatcher extends BioMatcher {
  void putBioTemplateData(short var1, BioTemplateData var2) throws Bio1toNException, SecurityException;

  void resetUnblockAndSetTryLimit(byte var1) throws Bio1toNException;

  short getIndexOfLastMatchingBioTemplateData();

  OwnerBioTemplateData getBioTemplateData(short var1);
}
