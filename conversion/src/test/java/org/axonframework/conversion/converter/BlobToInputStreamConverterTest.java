/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.conversion.converter;

import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link BlobToInputStreamConverter}.
 *
 * @author Steven van Beelen
 */
class BlobToInputStreamConverterTest {

    private static final byte[] TEST_BYTES = "some-text".getBytes();

    private BlobToInputStreamConverter testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new BlobToInputStreamConverter();
    }

    @Test
    void validateSourceAndTargetType() {
        assertEquals(Blob.class, testSubject.expectedSourceType());
        assertEquals(InputStream.class, testSubject.targetType());
    }

    @Test
    void convert() throws IOException {
        InputStream result = testSubject.convert(new TestBlob());

        assertNotNull(result);
        assertArrayEquals(TEST_BYTES, result.readAllBytes());
    }

    @Test
    void convertIsNullSafe() {
        //noinspection resource
        assertDoesNotThrow(() -> testSubject.convert(null));
        assertNull(testSubject.convert(null));
    }

    static class TestBlob implements Blob {

        @Override
        public long length() throws SQLException {
            return 0;
        }

        @Override
        public byte[] getBytes(long l, int i) throws SQLException {
            return new byte[0];
        }

        @Override
        public InputStream getBinaryStream() throws SQLException {
            return new ByteArrayInputStream(TEST_BYTES);
        }

        @Override
        public long position(byte[] bytes, long l) throws SQLException {
            return 0;
        }

        @Override
        public long position(Blob blob, long l) throws SQLException {
            return 0;
        }

        @Override
        public int setBytes(long l, byte[] bytes) throws SQLException {
            return 0;
        }

        @Override
        public int setBytes(long l, byte[] bytes, int i, int i1) throws SQLException {
            return 0;
        }

        @Override
        public OutputStream setBinaryStream(long l) throws SQLException {
            return null;
        }

        @Override
        public void truncate(long l) throws SQLException {

        }

        @Override
        public void free() throws SQLException {

        }

        @Override
        public InputStream getBinaryStream(long l, long l1) throws SQLException {
            return null;
        }
    }
}