/*
 * Copyright (c) 2010-2021. Axon Framework
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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link ObjectNodeToJsonNodeConverter}.
 *
 * @author Steven van Beelen
 */
class ObjectNodeToJsonNodeConverterTest {

    private final ObjectNodeToJsonNodeConverter testSubject = new ObjectNodeToJsonNodeConverter();

    @Test
    void testExpectedSourceType() {
        assertEquals(ObjectNode.class, testSubject.expectedSourceType());
    }

    @Test
    void testTargetType() {
        assertEquals(JsonNode.class, testSubject.targetType());
    }

    @Test
    void testConvert() {
        ObjectNode expectedJsonNode = new ObjectNode(JsonNodeFactory.instance);

        JsonNode result = testSubject.convert(expectedJsonNode);

        assertEquals(expectedJsonNode, result);
    }
}