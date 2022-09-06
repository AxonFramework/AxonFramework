package org.axonframework.eventhandling;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests serialization capabilities of {@link GapAwareTrackingToken}.
 *
 * @author JohT
 */
class GapAwareTrackingTokenSerializationTest {

    public static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void tokenShouldBeSerializable(TestSerializer serializer) {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(Long.MAX_VALUE, asList(0L, 1L));
        assertEquals(subject, serializer.serializeDeserialize(subject));
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void tokenWithoutGapsShouldBeSerializable(TestSerializer serializer) {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(0, emptyList());
        assertEquals(subject, serializer.serializeDeserialize(subject));
    }
}