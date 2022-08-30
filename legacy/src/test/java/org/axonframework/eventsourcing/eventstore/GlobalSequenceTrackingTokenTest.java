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

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.utils.TestSerializer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test whether the serialized form of the {@link org.axonframework.eventsourcing.eventstore.GlobalSequenceTrackingToken}
 * can be deserialized into the {@link GlobalSequenceTrackingToken}, using the {@link XStreamSerializer} and {@link
 * JacksonSerializer}.
 *
 * @author Steven van Beelen
 */
class GlobalSequenceTrackingTokenTest {

    private static final String LEGACY_GLOBAL_SEQUENCE_TRACKING_TOKEN_CLASS_NAME =
            "org.axonframework.eventsourcing.eventstore.GlobalSequenceTrackingToken";
    private static final int GLOBAL_INDEX = 10;

    @Test
    void xStreamSerializationOfOldGlobalSequenceTrackingToken() {
        XStreamSerializer serializer = TestSerializer.xStreamSerializer();

        String xmlSerializedGlobalSequenceTrackingToken =
                "<org.axonframework.eventsourcing.eventstore.GlobalSequenceTrackingToken>"
                        + "<globalIndex>" + GLOBAL_INDEX + "</globalIndex>"
                        + "</org.axonframework.eventsourcing.eventstore.GlobalSequenceTrackingToken>";
        SerializedObject<String> serializedGlobalSequenceTrackingToken = new SimpleSerializedObject<>(
                xmlSerializedGlobalSequenceTrackingToken, String.class,
                LEGACY_GLOBAL_SEQUENCE_TRACKING_TOKEN_CLASS_NAME, null
        );

        GlobalSequenceTrackingToken result = serializer.deserialize(serializedGlobalSequenceTrackingToken);
        assertEquals(GLOBAL_INDEX, result.getGlobalIndex());
    }

    @Test
    void jacksonSerializationOfOldGlobalSequenceTrackingToken() {
        JacksonSerializer serializer = JacksonSerializer.defaultSerializer();

        String jacksonSerializedGlobalSequenceTrackingToken =
                "{\"globalIndex\": " + GLOBAL_INDEX + "}";
        SerializedObject<String> serializedGlobalSequenceTrackingToken = new SimpleSerializedObject<>(
                jacksonSerializedGlobalSequenceTrackingToken, String.class,
                LEGACY_GLOBAL_SEQUENCE_TRACKING_TOKEN_CLASS_NAME, null
        );

        GlobalSequenceTrackingToken result = serializer.deserialize(serializedGlobalSequenceTrackingToken);
        assertEquals(GLOBAL_INDEX, result.getGlobalIndex());
    }
}