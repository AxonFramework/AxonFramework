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

package org.axonframework.deadline.jobrunr;

import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.TestScopeDescriptor;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MessageType;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeadlineDetailsSerializationTest {

    private static final String TEST_DEADLINE_NAME = "deadline-name";
    private static final String TEST_DEADLINE_PAYLOAD = "deadline-payload";
    private static MetaData metaData;
    private static DeadlineMessage message;

    @BeforeAll
    static void setUp() {
        Map<String, String> map = new HashMap<>();
        map.put("someStringValue", "foo");
        map.put("someIntValue", "2");
        metaData = new MetaData(map);
        message = new GenericDeadlineMessage(
                TEST_DEADLINE_NAME,
                new GenericMessage(new MessageType(TEST_DEADLINE_PAYLOAD.getClass()), TEST_DEADLINE_PAYLOAD),
                Instant::now
        ).withMetaData(metaData);
    }

    @Test
    void whenSerializedAndDeserializedAllPropertiesShouldBeTheSameUsingJackson() {
        Serializer serializer = TestSerializer.JACKSON.getSerializer();
        testSerialisationWithSpecificSerializer(serializer);
    }

    private void testSerialisationWithSpecificSerializer(Serializer serializer) {
        String expectedType = "aggregateType";
        String expectedIdentifier = "identifier";
        ScopeDescriptor descriptor = new TestScopeDescriptor(expectedType, expectedIdentifier);
        String serializedDeadlineDetails = DeadlineDetails.serialized(
                TEST_DEADLINE_NAME, descriptor, message, serializer);
        SimpleSerializedObject<String> serializedDeadlineMetaData = new SimpleSerializedObject<>(
                serializedDeadlineDetails, String.class, DeadlineDetails.class.getName(), null
        );
        DeadlineDetails result = serializer.deserialize(serializedDeadlineMetaData);

        assertEquals(TEST_DEADLINE_NAME, result.getDeadlineName());
        assertEquals(descriptor, result.getDeserializedScopeDescriptor(serializer));
        DeadlineMessage resultMessage = result.asDeadLineMessage(serializer);

        assertNotNull(resultMessage);
        assertEquals(TEST_DEADLINE_PAYLOAD, resultMessage.payload());
        assertEquals(metaData, resultMessage.metaData());
    }
}