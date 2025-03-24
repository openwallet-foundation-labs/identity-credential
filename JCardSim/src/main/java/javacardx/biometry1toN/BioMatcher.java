package javacardx.biometry1toN;

public interface BioMatcher {
  short MINIMUM_SUCCESSFUL_MATCH_SCORE = 16384;
  short MATCH_NEEDS_MORE_DATA = -1;

  boolean isInitialized();

  boolean isValidated();

  void reset();

  byte getTriesRemaining();

  byte getBioType();

  short getVersion(byte[] var1, short var2);

  short initMatch(byte[] var1, short var2, short var3) throws Bio1toNException;

  short match(byte[] var1, short var2, short var3) throws Bio1toNException;

  short getMaxNbOfBioTemplateData();

  short getIndexOfLastMatchingBioTemplateData();

  BioTemplateData getBioTemplateData(short var1);
}
