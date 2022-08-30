/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Allard Buijze
 */
class ByteArrayToJsonNodeConverterTest {

    private ByteArrayToJsonNodeConverter testSubject;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testSubject = new ByteArrayToJsonNodeConverter(objectMapper);
    }

    @Test
    void convertNodeToBytes() throws Exception {
        final String content = "{\"someKey\":\"someValue\",\"someOther\":true}";
        JsonNode expected = objectMapper.readTree(content);
        assertEquals(expected, testSubject.convert(content.getBytes(IOUtils.UTF8)));
    }
}
