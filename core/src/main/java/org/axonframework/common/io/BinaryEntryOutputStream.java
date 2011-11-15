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

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Wrapper around an output stream that can be used to write simple values, that can be read back in by the {@link
 * BinaryEntryInputStream}. This output stream writes whitespace-separated entries to an output stream. That means that
 * the entries (except for binary entries) may not contain whitespace themselves.
 * <p/>
 * This class is meant for internal use, and should be used with care.
 *
 * @author Allard Buijze
 * @since 1.0
 */
public class BinaryEntryOutputStream {

    private static final String CHARSET_UTF8 = "UTF-8";
    private final OutputStream out;

    /**
     * Initialize a BinaryEntryOutputStream writing its entries to the given <code>outputStream</code>.
     *
     * @param outputStream the output stream where entries are to be written to
     */
    public BinaryEntryOutputStream(OutputStream outputStream) {
        this.out = outputStream;
    }

    /**
     * Writes a numeric entry.
     *
     * @param value The value to write
     * @throws IOException if an error occurs reading from the backing stream
     */
    public void writeNumber(long value) throws IOException {
        writeString(Long.toString(value));
    }

    /**
     * Writes a numeric entry.
     *
     * @param value The value to write
     * @throws IOException if an error occurs reading from the backing stream
     */
    public void writeNumber(int value) throws IOException {
        writeString(Integer.toString(value));
    }

    /**
     * Writes a String entry. The given <code>value</code> may not contain any whitespace characters. Use {@link
     * #writeBytes(byte[])} to write such entries instead.
     *
     * @param value The String to write. Should not contain any whitespace characters.
     * @throws IOException if an error occurs reading from the backing stream
     */
    public void writeString(String value) throws IOException {
        for (char ch : value.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                throw new IllegalArgumentException("Given value may not contains any whitespace characters");
            }
        }
        out.write(value.getBytes(CHARSET_UTF8));
        IOUtils.write(" ", out, CHARSET_UTF8);
    }

    /**
     * Writes the given <code>bytes</code> to the backing stream. The entry is terminated with a newline character.
     *
     * @param bytes The bytes to write
     * @throws IOException if an error occurs reading from the backing stream
     */
    public void writeBytes(byte[] bytes) throws IOException {
        writeNumber(bytes.length);
        out.write(bytes);
        IOUtils.write("\n", out, CHARSET_UTF8);
    }
}
