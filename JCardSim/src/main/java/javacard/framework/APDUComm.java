package javacard.framework;

  public interface APDUComm {
    byte getLogicalChannel();

    boolean getNoChainingFlag();

    void resetAPDU();

    void complete(short var1) throws APDUException;

    void undoIncomingAndReceive();

    byte getCLAChannel();

    void waitExtension() throws APDUException;

    byte[] getBuffer();

    short getInBlockSize();

    short getOutBlockSize();

    byte getProtocol();

    byte getNAD();

    short setOutgoing() throws APDUException;

    short setOutgoingNoChaining() throws APDUException;

    void setOutgoingLength(short var1) throws APDUException;

    short receiveBytes(short var1) throws APDUException;

    short setIncomingAndReceive() throws APDUException;

    void sendBytes(short var1, short var2) throws APDUException;

    void sendBytesLong(byte[] var1, short var2, short var3) throws APDUException, SecurityException;

    void setOutgoingAndSend(short var1, short var2) throws APDUException;

    byte getCurrentState();

    byte[] getCurrentAPDUBuffer() throws SecurityException;

    void markExtendedSupport(boolean var1) throws APDUException;

    short getIncomingLength();

    short getOffsetCdata();

    boolean isValidCLA();

    boolean isCommandChainingCLA();

    boolean isSecureMessagingCLA();

    boolean isISOInterindustryCLA();

    void verifyLe();

}
