package org.axonframework.eventhandling;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests serialization capabilities of {@link ReplayToken}.
 *
 * @author JohT
 */
class ReplayTokenSerializationTest {

    static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void tokenShouldBeSerializable(TestSerializer serializer) {
        TrackingToken innerToken = GapAwareTrackingToken.newInstance(10, Collections.singleton(9L));
        ReplayToken token = new ReplayToken(innerToken);
        assertEquals(token, serializer.serializeDeserialize(token));
    }
}