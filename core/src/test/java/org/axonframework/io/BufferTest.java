/*
 * Copyright (c) 2010-2012. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.io;

import org.junit.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.hibernate.internal.util.StringHelper.repeat;
import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class BufferTest {

    @Test
    public void testBufferCompact() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(128);
        final byte[] src = "hello, world".getBytes();
        buffer.put(src);

        buffer.flip();
        // now we can read from it
        final int incomplete_bytes = 2;
        final byte[] dst = new byte[src.length - incomplete_bytes];
        buffer.get(dst);
        assertArrayEquals(Arrays.copyOf(src, src.length - incomplete_bytes), dst);
        assertEquals(incomplete_bytes, buffer.remaining());

        System.out.println("[" + new String(buffer.array()) + "]");
        System.out.println(" " + repeat(" ", buffer.limit()) + "^ Limit");
        System.out.println(" " + repeat(" ", buffer.position()) + "^ Position");
        System.out.println();

        buffer.compact();
        System.out.println("[" + new String(buffer.array()) + "]");
        System.out.println(" " + repeat(" ", buffer.limit()) + "^ Limit");
        System.out.println(" " + repeat(" ", buffer.position()) + "^ Position");

        assertEquals(128 - incomplete_bytes, buffer.remaining());
    }

    @Test
    public void testWriteUtfLengthPrefix() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream(1024);
        DataOutputStream dataOut = new DataOutputStream(bout);
        dataOut.writeUTF(repeat("X", 500));

        byte[] bytes = bout.toByteArray();
        assertEquals(500 + 2, bytes.length);
        final ByteBuffer buffer = ByteBuffer.wrap(bytes, 0, 2);
        assertEquals("There aren't enough bytes to read a short", Short.SIZE >> 3, buffer.remaining());
        assertEquals(500, buffer.getShort());
        assertEquals(500, (( bytes[0] & 0xff) << 8) + (bytes[1] & 0xff));
    }
}
