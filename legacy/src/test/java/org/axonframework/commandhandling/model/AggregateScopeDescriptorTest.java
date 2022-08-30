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

package org.axonframework.commandhandling.model;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.axonframework.utils.TestSerializer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test whether the serialized form of the {@link org.axonframework.commandhandling.model.AggregateScopeDescriptor} can
 * be deserialized into the {@link AggregateScopeDescriptor}, using the {@link XStreamSerializer} and {@link
 * JacksonSerializer}.
 *
 * @author Steven van Beelen
 */
class AggregateScopeDescriptorTest {

    private static final String LEGACY_SCOPE_DESCRIPTOR_CLASS_NAME =
            "org.axonframework.commandhandling.model.AggregateScopeDescriptor";
    private static final String AGGREGATE_TYPE = "aggregate-type";
    private static final String AGGREGATE_ID = "aggregate-id";

    @Test
    void xStreamSerializationOfOldAggregateScopeDescriptor() {
        XStreamSerializer serializer = TestSerializer.xStreamSerializer();

        String xmlSerializedScopeDescriptor =
                "<org.axonframework.commandhandling.model.AggregateScopeDescriptor serialization=\"custom\">"
                        + "<org.axonframework.commandhandling.model.AggregateScopeDescriptor>"
                        + "<default>"
                        + "<identifier class=\"string\">" + AGGREGATE_ID + "</identifier>"
                        + "<type>" + AGGREGATE_TYPE + "</type>"
                        + "</default>"
                        + "</org.axonframework.commandhandling.model.AggregateScopeDescriptor>"
                        + "</org.axonframework.commandhandling.model.AggregateScopeDescriptor>";
        SerializedObject<String> serializedScopeDescriptor = new SimpleSerializedObject<>(
                xmlSerializedScopeDescriptor, String.class, LEGACY_SCOPE_DESCRIPTOR_CLASS_NAME, null
        );

        org.axonframework.modelling.command.AggregateScopeDescriptor result =
                serializer.deserialize(serializedScopeDescriptor);
        assertEquals(AGGREGATE_TYPE, result.getType());
        assertEquals(AGGREGATE_ID, result.getIdentifier());
    }

    @Test
    void jacksonSerializationOfOldAggregateScopeDescriptor() {
        JacksonSerializer serializer = JacksonSerializer.defaultSerializer();

        String jacksonSerializedScopeDescriptor =
                "{\"type\":\"" + AGGREGATE_TYPE + "\",\"identifier\":\"" + AGGREGATE_ID + "\"}";
        SerializedObject<String> serializedScopeDescriptor = new SimpleSerializedObject<>(
                jacksonSerializedScopeDescriptor, String.class, LEGACY_SCOPE_DESCRIPTOR_CLASS_NAME, null
        );

        org.axonframework.modelling.command.AggregateScopeDescriptor result =
                serializer.deserialize(serializedScopeDescriptor);
        assertEquals(AGGREGATE_TYPE, result.getType());
        assertEquals(AGGREGATE_ID, result.getIdentifier());
    }
}