package org.axonframework.eventhandling;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests serialization capabilities of {@link GlobalSequenceTrackingToken}.
 * 
 * @author JohT
 */
class GlobalSequenceTrackingTokenSerializationTest {

    static Collection<TestSerializer> serializers() {
       return TestSerializer.all();
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void tokenShouldBeSerializable(TestSerializer serializer) {
        GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(Long.MAX_VALUE);
        assertEquals(token, serializer.serializeDeserialize(token));
    }
}