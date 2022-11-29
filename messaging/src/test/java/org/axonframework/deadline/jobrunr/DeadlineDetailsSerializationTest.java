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

import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.TestScopeDescriptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DeadlineDetailsSerializationTest {

    private static final String TEST_DEADLINE_NAME = "deadline-name";
    private static final String TEST_DEADLINE_PAYLOAD = "deadline-payload";
    private static MetaData metaData;
    private static UUID scheduleId;
    @SuppressWarnings("rawtypes")
    private static DeadlineMessage message;

    @BeforeAll
    static void setUp() {
        Map<String, Object> map = new HashMap<>();
        map.put("someStringValue", "foo");
        map.put("someIntValue", 2);
        metaData = new MetaData(map);
        scheduleId = UUID.randomUUID();
        message = GenericDeadlineMessage.asDeadlineMessage(TEST_DEADLINE_NAME, TEST_DEADLINE_PAYLOAD, Instant.now())
                                        .withMetaData(metaData);
    }

    @Test
    void whenSerializedAndDeserializedAllPropertiesShouldBeTheSameUsingXStream() {
        Serializer serializer = TestSerializer.XSTREAM.getSerializer();
        testSerialisationWithSpecicSerializer(serializer);
    }

    @Test
    void whenSerializedAndDeserializedAllPropertiesShouldBeTheSameUsingJackson() {
        Serializer serializer = TestSerializer.JACKSON.getSerializer();
        testSerialisationWithSpecicSerializer(serializer);
    }

    @SuppressWarnings("rawtypes")
    private void testSerialisationWithSpecicSerializer(Serializer serializer) {
        String expectedType = "aggregateType";
        String expectedIdentifier = "identifier";
        ScopeDescriptor descriptor = new TestScopeDescriptor(expectedType, expectedIdentifier);
        byte[] serializedDeadlineDetails = DeadlineDetails.serialized(
                TEST_DEADLINE_NAME, scheduleId, descriptor, message, serializer);
        SimpleSerializedObject<byte[]> serializedDeadlineMetaData = new SimpleSerializedObject<>(
                serializedDeadlineDetails, byte[].class, DeadlineDetails.class.getName(), null
        );
        DeadlineDetails result = serializer.deserialize(serializedDeadlineMetaData);

        assertEquals(TEST_DEADLINE_NAME, result.getDeadlineName());
        assertEquals(scheduleId, result.getDeadlineId());
        assertEquals(descriptor, result.getDeserializedScopeDescriptor(serializer));
        DeadlineMessage resultMessage = result.asDeadLineMessage(serializer);

        assertNotNull(resultMessage);
        assertEquals(TEST_DEADLINE_PAYLOAD, resultMessage.getPayload());
        assertEquals(metaData, resultMessage.getMetaData());
        assertEquals(message.getTimestamp(), resultMessage.getTimestamp());
    }
}