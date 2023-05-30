/*
 * Copyright (c) 2010-2023. Axon Framework
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

package org.axonframework.deadline.dbscheduler;

import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.TestScopeDescriptor;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DbSchedulerHumanReadableDeadlineDetailsSerializationTest {

    private static final String TEST_DEADLINE_NAME = "deadline-name";
    private static final String TEST_DEADLINE_PAYLOAD = "deadline-payload";
    private static MetaData metaData;
    @SuppressWarnings("rawtypes")
    private static DeadlineMessage message;

    public static Collection<TestSerializer> serializers() {
        List<TestSerializer> testSerializerList = new ArrayList<>();
        testSerializerList.add(TestSerializer.JAVA);
        testSerializerList.add(TestSerializer.JACKSON);
        testSerializerList.add(TestSerializer.XSTREAM);
        return testSerializerList;
    }

    @BeforeAll
    static void setUp() {
        Map<String, Object> map = new HashMap<>();
        map.put("someStringValue", "foo");
        map.put("someIntValue", 2);
        metaData = new MetaData(map);
        message = GenericDeadlineMessage.asDeadlineMessage(TEST_DEADLINE_NAME, TEST_DEADLINE_PAYLOAD, Instant.now())
                                        .withMetaData(metaData);
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void whenSerializedAndDeserializedAllPropertiesShouldBeTheSame() {
        Serializer serializer = TestSerializer.XSTREAM.getSerializer();
        String expectedType = "aggregateType";
        String expectedIdentifier = "identifier";
        ScopeDescriptor descriptor = new TestScopeDescriptor(expectedType, expectedIdentifier);
        DbSchedulerHumanReadableDeadlineDetails result = DbSchedulerHumanReadableDeadlineDetails.serialized(
                TEST_DEADLINE_NAME, descriptor, message, serializer);

        assertEquals(TEST_DEADLINE_NAME, result.getDeadlineName());
        assertEquals(descriptor, result.getDeserializedScopeDescriptor(serializer));
        DeadlineMessage<?> resultMessage = result.asDeadLineMessage(serializer);

        assertNotNull(resultMessage);
        assertEquals(TEST_DEADLINE_PAYLOAD, resultMessage.getPayload());
        assertEquals(metaData, resultMessage.getMetaData());
    }
}