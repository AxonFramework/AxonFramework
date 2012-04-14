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

import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class BinaryEntryInputStreamTest {

    private static final String ENTRY1 = "12 Text Value 12 twelve bytes";
    private static final String ENTRY2 = "-12 TextValue 12 twelve bytes";
    private static final String INCOMPLETE_ENTRY1 = "-12 TextValue 12 missing";
    private static final String INCOMPLETE_ENTRY2 = "-12 TextValue";

    @Test
    public void testReadEntries_SimpleCases() throws IOException {
        BinaryEntryInputStream in = new BinaryEntryInputStream(new ByteArrayInputStream(ENTRY1.getBytes("UTF-8")));
        assertEquals(12L, in.readNumber());
        assertEquals("Text", in.readString());
        assertEquals("Value", in.readString());
        assertEquals("twelve bytes", new String(in.readBytes(), "UTF-8"));
    }

    @Test
    public void testReadEntries_NegativeNumbers() throws IOException {
        BinaryEntryInputStream in = new BinaryEntryInputStream(new ByteArrayInputStream(ENTRY2.getBytes("UTF-8")));
        assertEquals(-12L, in.readNumber());
        assertEquals("TextValue", in.readString());
        assertEquals("twelve bytes", new String(in.readBytes(), "UTF-8"));
    }

    @Test
    public void testReadEntries_MissingBytes() throws IOException {
        BinaryEntryInputStream in = new BinaryEntryInputStream(
                new ByteArrayInputStream(INCOMPLETE_ENTRY1.getBytes("UTF-8")));
        assertEquals(-12L, in.readNumber());
        assertEquals("TextValue", in.readString());
        assertNull(in.readBytes());
    }

    @Test
    public void testReadEntries_NoBytesEntry() throws IOException {
        BinaryEntryInputStream in = new BinaryEntryInputStream(
                new ByteArrayInputStream(INCOMPLETE_ENTRY2.getBytes("UTF-8")));
        assertEquals(-12L, in.readNumber());
        assertEquals("TextValue", in.readString());
        assertNull(in.readBytes());
    }

    @Test(expected = NumberFormatException.class)
    public void testReadEntries_NotANumber() throws IOException {
        BinaryEntryInputStream in = new BinaryEntryInputStream(new ByteArrayInputStream("-12a".getBytes("UTF-8")));
        in.readNumber();
    }
}
