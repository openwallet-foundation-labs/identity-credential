package javacardx.biometry;

public interface OwnerBioTemplate extends BioTemplate {
  void init(byte[] var1, short var2, short var3) throws BioException;

  void update(byte[] var1, short var2, short var3) throws BioException;

  void doFinal() throws BioException;

  void resetUnblockAndSetTryLimit(byte var1) throws BioException;
}

