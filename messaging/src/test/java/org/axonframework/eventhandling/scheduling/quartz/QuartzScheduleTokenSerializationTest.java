package org.axonframework.eventhandling.scheduling.quartz;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests serialization capabilities of {@link QuartzScheduleToken}.
 * 
 * @author JohT
 */
class QuartzScheduleTokenSerializationTest {

    public static Collection<TestSerializer> serializers() {
       return TestSerializer.all();
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void tokenShouldBeSerializable(TestSerializer serializer) {
        QuartzScheduleToken tokenToTest = new QuartzScheduleToken("jobIdentifier", "groupIdentifier");
        assertEquals(tokenToTest, serializer.serializeDeserialize(tokenToTest));
    }
}