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

package org.axonframework.messaging.responsetypes;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.axonframework.messaging.responsetypes.InstanceResponseType;
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.junit.*;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Tests for json serialization / deserialization of response types.
 *
 * @author Milan Savic
 */
public class ResponseTypesJacksonSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSerDeserOfInstanceResponseType() throws IOException {
        InstanceResponseType<String> stringResponseType = (InstanceResponseType<String>) ResponseTypes
                .instanceOf(String.class);

        String serialized = objectMapper.writeValueAsString(stringResponseType);
        InstanceResponseType<String> deserialized = objectMapper.readerFor(InstanceResponseType.class)
                                                                .readValue(serialized);

        assertEquals(stringResponseType.getExpectedResponseType(), deserialized.getExpectedResponseType());
    }

    @Test
    public void testSerDeserOfMultipleInstanceResponseType() throws IOException {
        MultipleInstancesResponseType<String> stringResponseType = (MultipleInstancesResponseType<String>) ResponseTypes
                .multipleInstancesOf(String.class);

        String serialized = objectMapper.writeValueAsString(stringResponseType);
        MultipleInstancesResponseType<String> deserialized = objectMapper.readerFor(MultipleInstancesResponseType.class)
                                                                         .readValue(serialized);

        assertEquals(stringResponseType.getExpectedResponseType(), deserialized.getExpectedResponseType());
    }
}
