/*
 * Copyright (c) 2011. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.common.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * Wrapper around an input stream that can be used to read simple values, written using the {@link
 * BinaryEntryOutputStream}. This input stream reads whitespace-separated entries from an input stream. That means that
 * the entries (except for binary entries) may not contain whitespace themselves.
 * <p/>
 * The BinaryEntryInputStream does not read ahead. It will read up to and inclusive the next whitespace each time is
 * read. When byte arrays are read, it will first read an entry containing its size, and then the exact number of bytes
 * as indicated in the size entry. Any whitespace character followed by the byte array is not read.
 * <p/>
 * This class is meant for internal use, and should be used with care.
 *
 * @author Allard Buijze
 * @since 1.0
 */
public class BinaryEntryInputStream {

    private final InputStream in;

    /**
     * Initialize a BinaryEntryInputStream reading its data from the given <code>inputStream</code>.
     *
     * @param inputStream The input stream providing the data
     */
    public BinaryEntryInputStream(InputStream inputStream) {
        this.in = inputStream;
    }

    /**
     * Reads the next entry as a number (long).
     *
     * @return the numeric value of the next entry
     *
     * @throws IOException           if an error occurs reading from the backing stream
     * @throws NumberFormatException if the entry read does not represent a Long
     */
    public long readNumber() throws IOException {
        String data = readString();
        if (data == null) {
            return -1;
        }

        return Long.parseLong(data);
    }

    /**
     * Reads the next entry as a String value.
     *
     * @return The String value of the next entry
     *
     * @throws IOException if an error occurs reading from the backing stream
     */
    public String readString() throws IOException {
        int codePoint = readFistNonWhitespaceCharacter();
        if (codePoint < 0) {
            return null; //NOSONAR
        }
        StringBuilder sb = new StringBuilder();
        while (!Character.isWhitespace(codePoint) && codePoint >= 0) {
            char nextChar = (char) codePoint;
            sb.append(nextChar);
            codePoint = in.read();
        }
        return sb.toString();
    }

    /**
     * Reads the next entry as a byte array.
     *
     * @return A byte[] of size <code>numberOfBytes</code> containing the entry read, or <code>null</code> of the input
     *         stream did not contain all the bytes for this entry.
     *
     * @throws IOException if an error occurs reading from the backing stream
     */
    public byte[] readBytes() throws IOException {
        int numberOfBytes = (int) readNumber();
        if (numberOfBytes < 0) {
            return null; //NOSONAR
        }
        byte[] bytesToRead = new byte[numberOfBytes];
        int bytesRead = in.read(bytesToRead);
        return bytesRead == bytesToRead.length ? bytesToRead : null;
    }

    private int readFistNonWhitespaceCharacter() throws IOException {
        int codePoint = in.read();
        while (Character.isWhitespace(codePoint)) {
            codePoint = in.read();
        }
        return codePoint;
    }
}
