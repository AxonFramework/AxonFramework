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

package org.axonframework.serialization.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.common.io.IOUtils;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link JsonNodeToByteArrayConverter}.
 *
 * @author Allard Buijze
 */
class JsonNodeToByteArrayConverterTest {

    private ObjectMapper objectMapper;

    private JsonNodeToByteArrayConverter testSubject;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        testSubject = new JsonNodeToByteArrayConverter(objectMapper);
    }

    @Test
    void throwsNullPointerExceptionWhenConstructingWithNullObjectMapper() {
        //noinspection DataFlowIssue
        assertThrows(NullPointerException.class, () -> new JsonNodeToByteArrayConverter(null));
    }

    @Test
    void validateSourceAndTargetType() {
        assertEquals(JsonNode.class, testSubject.expectedSourceType());
        assertEquals(byte[].class, testSubject.targetType());
    }

    @Test
    void convertNodeToBytes() throws Exception {
        final String content = "{\"someKey\":\"someValue\",\"someOther\":true}";
        JsonNode node = objectMapper.readTree(content);
        assertArrayEquals(content.getBytes(IOUtils.UTF8), testSubject.convert(node));
    }

    @Test
    void convertIsNullSafe() {
        assertDoesNotThrow(() -> testSubject.convert(null));
        assertNotNull(testSubject.convert(null));
    }
}
