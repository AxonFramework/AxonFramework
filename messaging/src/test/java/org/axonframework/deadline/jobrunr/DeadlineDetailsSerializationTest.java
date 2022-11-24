/*
 * Copyright (c) 2010-2022. Axon Framework
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

package org.axonframework.deadline.jobrunr;

import org.axonframework.deadline.TestScopeDescriptor;
import org.axonframework.messaging.ScopeDescriptor;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.junit.jupiter.api.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeadlineDetailsSerializationTest {

    private static JacksonJsonMapper jacksonJsonMapper;
    private static Map<String, Object> metaData;

    @BeforeAll
    static void setUp() {
        jacksonJsonMapper = new JacksonJsonMapper();
        metaData = new HashMap<>();
        metaData.put("someStringValue", "foo");
        metaData.put("someIntValue", 2);
    }

    @Test
    void whenSerializedAndDeserializedAllPropertiesShouldBeTheSame() {
        String expectedType = "aggregateType";
        String expectedIdentifier = "identifier";
        ScopeDescriptor scopeDescription = new TestScopeDescriptor(expectedType, expectedIdentifier);
        UUID id = UUID.randomUUID();
        DeadlineDetails testSubject = new DeadlineDetails("deadlineName", id, scopeDescription, "test", metaData);

        String serializedObject = jacksonJsonMapper.serialize(testSubject);
        DeadlineDetails result = jacksonJsonMapper.deserialize(serializedObject, DeadlineDetails.class);

        assertEquals(scopeDescription, result.getScopeDescription());
        assertEquals("deadlineName", result.getDeadlineName());
        assertEquals(id, result.getDeadlineId());
        assertEquals("test", result.getPayload());
        assertEquals(metaData, result.getMetaData());
    }
}