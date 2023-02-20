package org.axonframework.messaging;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests serialization capabilities of {@link RemoteExceptionDescription}.
 *
 * @author JohT
 */
class RemoteExceptionDescriptionSerializationTest {

    static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void tokenShouldBeSerializableWithJackson(TestSerializer serializer) {
        Throwable cause = new IllegalArgumentException("This is a test");
        Throwable exception = new IllegalStateException("Test with cause", cause);
        RemoteExceptionDescription descriptionToTest = RemoteExceptionDescription.describing(exception);
        assertEquals(descriptionToTest, serializer.serializeDeserialize(descriptionToTest));
    }
}