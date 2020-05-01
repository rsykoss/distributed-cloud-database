package de.tum.i13.shared;

import java.io.IOException;
import java.io.Reader;

public class ReusableBufferedReader extends Reader {

    private char[] buffer = null;
    private int writeIndex = 0;
    private int readIndex = 0;
    private boolean endOfReaderReached = false;

    private Reader source = null;

    public ReusableBufferedReader(char[] buffer) {
        this.buffer = buffer;
    }

    public ReusableBufferedReader setSource(Reader source) {
        this.source = source;
        this.writeIndex = 0;
        this.readIndex = 0;
        this.endOfReaderReached = false;
        return this;
    }

    @Override
    public int read() throws IOException {
        if (endOfReaderReached) {
            return -1;
        }

        if (readIndex == writeIndex) {
            if (writeIndex == buffer.length) {
                this.writeIndex = 0;
                this.readIndex = 0;
            }
            // data should be read into buffer.
            int bytesRead = readCharsIntoBuffer();
            while (bytesRead == 0) {
                // continue until you actually get some bytes !
                bytesRead = readCharsIntoBuffer();
            }

            // if no more data could be read in, return -1;
            if (bytesRead == -1) {
                return -1;
            }
        }

        return 65535 & this.buffer[readIndex++];
    }

    @Override
    public int read(char[] dest, int offset, int length) throws IOException {
        int charsRead = 0;
        int data = 0;
        while (data != -1 && charsRead < length) {
            data = read();
            if (data == -1) {
                endOfReaderReached = true;
                if (charsRead == 0) {
                    return -1;
                }
                return charsRead;
            }
            dest[offset + charsRead] = (char) (65535 & data);
            charsRead++;
        }
        return charsRead;
    }

    private int readCharsIntoBuffer() throws IOException {
        int charsRead = this.source.read(this.buffer, this.writeIndex, this.buffer.length - this.writeIndex);
        writeIndex += charsRead;
        return charsRead;
    }

    @Override
    public void close() throws IOException {
        this.source.close();
    }
}