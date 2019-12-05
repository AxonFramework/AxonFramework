package org.axonframework.commandhandling.model;

import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test whether the serialized form of the {@link org.axonframework.commandhandling.model.AggregateScopeDescriptor} can
 * be deserialized into the {@link AggregateScopeDescriptor}, using the {@link XStreamSerializer} and
 * {@link JacksonSerializer}.
 *
 * @author Steven van Beelen
 */
class AggregateScopeDescriptorTest {

    private static final String LEGACY_SCOPE_DESCRIPTOR_CLASS_NAME =
            "org.axonframework.commandhandling.model.AggregateScopeDescriptor";
    private static final String AGGREGATE_TYPE = "aggregate-type";
    private static final String AGGREGATE_ID = "aggregate-id";

    @Test
    void testXStreamSerializationOfOldAggregateScopeDescriptor() {
        XStreamSerializer serializer = XStreamSerializer.defaultSerializer();

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
    void testJacksonSerializationOfOldAggregateScopeDescriptor() {
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