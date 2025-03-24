package com.android.javacard.mdl;

import javacard.framework.Util;

public class Buffer {
    private short curOff;
    private short internalOff = 0;
    private static final short MAX_LEN = 16;

    public Buffer() {
        internalOff = 0;
        curOff = -1;
    }

    private byte[] buffer = new byte[MAX_LEN];
    private byte[] internal = new byte[5120];


    private short copyLen(short bufLen) {
        short copyLen = (short) buffer.length;
        short len = (short) (curOff + 1);
        if (len < buffer.length) {
            copyLen = (short) (buffer.length - len);
        }
        if (bufLen < copyLen) {
            copyLen = bufLen;
        }
        return copyLen;
    }

    private boolean isBufferFull() {
        return (curOff >= (short) (buffer.length - 1));
    }

    public short bufferData(byte[] buf, short start, short bufLen) {
        /*
                          (Send for Encrypt)
        Buffered Data        Input data            Internal buffer
        len    0             0 (2)  len     (1)      0      len
        |------|    ---->    |------|      ---->    |------|
        |______|             |______|               |______|
           <--------------------------------------------|

        */
        if (bufLen == 0) return 0;
        // Give space in buf for a minimum of bufLen to maximum of 16.
        short copyLen = copyLen(bufLen);
        // Backup of the input buf for copyLen
        Util.arrayCopyNonAtomic(buf, (short) (start + (bufLen - copyLen)), internal, internalOff, copyLen);
        // Shift the buf right only if the buf length is more than 16
        if (isBufferFull() && bufLen > buffer.length) {
            Util.arrayCopyNonAtomic(buf, start, buf, (short) (start + copyLen), copyLen);
        }
        // copy from buffered data to input data
        if (isBufferFull()) {
            Util.arrayCopyNonAtomic(buffer, (short) 0, buf, start, copyLen);
        }
        //shift the buffered data
        if (isBufferFull() && copyLen < buffer.length) {
            Util.arrayCopyNonAtomic(buffer, copyLen, buffer, (short) 0, (short) (buffer.length - copyLen));
        }
        // copy the new data to buffered data
        short dataOff = (short) (curOff + 1);
        if (isBufferFull()) {
            dataOff = (short) (buffer.length - copyLen);
        }
        Util.arrayCopyNonAtomic(internal, internalOff, buffer, dataOff, copyLen);
        // Update the length
        if (!isBufferFull()) {
            curOff += copyLen;
            if (isBufferFull()) curOff = (short) (buffer.length - 1);
        }
        return (short) (isBufferFull() ? bufLen : (bufLen - copyLen));
    }

    public short clearBufferData(byte[] buf, short start, short len) {
        if (curOff == -1) {
            return len;
        }
        Util.arrayCopyNonAtomic(buf, start, internal, internalOff, len);
        Util.arrayCopyNonAtomic(buffer, (short) 0, buf, start, (short) buffer.length);
        Util.arrayCopyNonAtomic(internal, internalOff, buf, (short) (start + buffer.length), len);
        curOff = -1;
        return (short) (len + buffer.length);
    }
}
