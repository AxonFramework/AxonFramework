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

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ByteArrayToInputStreamConverter}.
 *
 * @author Steven van Beelen
 */
class ByteArrayToInputStreamConverterTest {

    private ByteArrayToInputStreamConverter testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new ByteArrayToInputStreamConverter();
    }

    @Test
    void validateSourceAndTargetType() {
        assertEquals(byte[].class, testSubject.expectedSourceType());
        assertEquals(InputStream.class, testSubject.targetType());
    }

    @Test
    void convert() throws IOException {
        byte[] testObject = "Hello, world!".getBytes();

        InputStream result = testSubject.convert(testObject);

        assertNotNull(result);
        assertArrayEquals(testObject, result.readAllBytes());
    }

    @Test
    void convertIsNullSafe() {
        //noinspection resource
        assertDoesNotThrow(() -> testSubject.convert(null));
        assertNull(testSubject.convert(null));
    }
}