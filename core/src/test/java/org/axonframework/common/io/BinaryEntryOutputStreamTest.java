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

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class BinaryEntryOutputStreamTest {

    private BinaryEntryOutputStream out;
    private ByteArrayOutputStream baos;

    @Before
    public void setUp() throws Exception {
        baos = new ByteArrayOutputStream();
        out = new BinaryEntryOutputStream(baos);
    }

    @Test
    public void testWriteNumber_Integer() throws Exception {
        out.writeNumber(12);
        out.writeNumber(-12);
        out.writeNumber(Integer.MAX_VALUE);
        out.writeNumber(Integer.MIN_VALUE);

        assertEquals("12 -12 2147483647 -2147483648 ", new String(baos.toByteArray(), "UTF-8"));
    }

    @Test
    public void testWriteNumber_Long() throws Exception {
        out.writeNumber(12L);
        out.writeNumber(-12L);
        out.writeNumber(Long.MAX_VALUE);
        out.writeNumber(Long.MIN_VALUE);

        assertEquals("12 -12 9223372036854775807 -9223372036854775808 ", new String(baos.toByteArray(), "UTF-8"));
    }

    @Test
    public void testWriteString() throws Exception {
        out.writeString("Hello_world!");
        out.writeString("Hi");
        assertEquals("Hello_world! Hi ", new String(baos.toByteArray(), "UTF-8"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteString_ContainsSpace() throws Exception {
        out.writeString("Hello world!");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWriteString_ContainsNewLine() throws Exception {
        out.writeString("Hello\nworld!");
    }

    @Test
    public void testWriteBytes() throws Exception {
        out.writeBytes("Hello world! This is content".getBytes("UTF-8"));

        assertEquals("28 Hello world! This is content\n", new String(baos.toByteArray(), "UTF-8"));
    }
}
