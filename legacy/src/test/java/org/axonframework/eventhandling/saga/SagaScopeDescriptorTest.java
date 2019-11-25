package org.axonframework.eventhandling.saga;

import org.axonframework.modelling.saga.SagaScopeDescriptor;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test whether the serialized form of the {@link org.axonframework.eventhandling.saga.SagaScopeDescriptor} can be
 * deserialized into the {@link SagaScopeDescriptor}, using the {@link XStreamSerializer} and {@link JacksonSerializer}.
 *
 * @author Steven van Beelen
 */
class SagaScopeDescriptorTest {

    private static final String LEGACY_SCOPE_DESCRIPTOR_CLASS_NAME =
            "org.axonframework.eventhandling.saga.SagaScopeDescriptor";
    private static final String SAGA_TYPE = "saga-type";
    private static final String SAGA_ID = "saga-id";

    @Test
    void testXStreamSerializationOfOldSagaScopeDescriptor() {
        XStreamSerializer serializer = XStreamSerializer.defaultSerializer();

        String xmlSerializedScopeDescriptor =
                "<org.axonframework.eventhandling.saga.SagaScopeDescriptor>"
                        + "<type>" + SAGA_TYPE + "</type>"
                        + "<identifier class=\"string\">" + SAGA_ID + "</identifier>"
                        + "</org.axonframework.eventhandling.saga.SagaScopeDescriptor>";
        SerializedObject<String> serializedScopeDescriptor = new SimpleSerializedObject<>(
                xmlSerializedScopeDescriptor, String.class, LEGACY_SCOPE_DESCRIPTOR_CLASS_NAME, null
        );

        SagaScopeDescriptor result = serializer.deserialize(serializedScopeDescriptor);
        assertEquals(SAGA_TYPE, result.getType());
        assertEquals(SAGA_ID, result.getIdentifier());
    }

    @Test
    void testJacksonSerializationOfOldSagaScopeDescriptor() {
        JacksonSerializer serializer = JacksonSerializer.defaultSerializer();

        String jacksonSerializedScopeDescriptor =
                "{\"type\":\"" + SAGA_TYPE + "\",\"identifier\":\"" + SAGA_ID + "\"}";
        SerializedObject<String> serializedScopeDescriptor = new SimpleSerializedObject<>(
                jacksonSerializedScopeDescriptor, String.class, LEGACY_SCOPE_DESCRIPTOR_CLASS_NAME, null
        );

        SagaScopeDescriptor result = serializer.deserialize(serializedScopeDescriptor);
        assertEquals(SAGA_TYPE, result.getType());
        assertEquals(SAGA_ID, result.getIdentifier());
    }
}