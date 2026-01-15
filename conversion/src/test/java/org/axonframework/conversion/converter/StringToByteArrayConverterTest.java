/*
 * Copyright (c) 2010-2026. Axon Framework
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

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link StringToByteArrayConverter}.
 *
 * @author Allard Buijze
 */
class StringToByteArrayConverterTest {

    private StringToByteArrayConverter testSubject;

    @BeforeEach
    void setUp() {
        testSubject = new StringToByteArrayConverter();
    }


    @Test
    void validateSourceAndTargetType() {
        assertEquals(String.class, testSubject.expectedSourceType());
        assertEquals(byte[].class, testSubject.targetType());
    }

    @Test
    void convert() {
        assertEquals(String.class, testSubject.expectedSourceType());
        assertEquals(byte[].class, testSubject.targetType());
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), testSubject.convert("hello"));
    }

    @Test
    void convertIsNullSafe() {
        assertDoesNotThrow(() -> testSubject.convert(null));
        assertNull(testSubject.convert(null));
    }
}
