package javacardx.biometry;

public interface BioTemplate {
  short MINIMUM_SUCCESSFUL_MATCH_SCORE = 16384;
  short MATCH_NEEDS_MORE_DATA = -1;

  boolean isInitialized();

  boolean isValidated();

  void reset();

  byte getTriesRemaining();

  byte getBioType();

  short getVersion(byte[] var1, short var2);

  short getPublicTemplateData(short var1, byte[] var2, short var3, short var4) throws BioException;

  short initMatch(byte[] var1, short var2, short var3) throws BioException;

  short match(byte[] var1, short var2, short var3) throws BioException;
}
