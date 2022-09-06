package org.axonframework.eventhandling.scheduling.java;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests serialization capabilities of {@link SimpleScheduleToken}.
 * 
 * @author JohT
 */
class SimpleScheduleTokenSerializationTest {

    static Collection<TestSerializer> serializers() {
       return TestSerializer.all();
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void tokenShouldBeSerializable(TestSerializer serializer) {
        SimpleScheduleToken tokenToTest = new SimpleScheduleToken("28bda08d-2dd5-4420-98cb-75ca073446b4");
        assertEquals(tokenToTest, serializer.serializeDeserialize(tokenToTest));
    }
}