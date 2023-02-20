package org.axonframework.messaging.responsetypes;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests serialization capabilities of {@link MultipleInstancesResponseType}.
 *
 * @author JohT
 */
class MultipleInstancesResponseTypeSerializationTest extends AbstractResponseTypeTest<List<AbstractResponseTypeTest.QueryResponse>> {

    MultipleInstancesResponseTypeSerializationTest() {
        super(new MultipleInstancesResponseType<>(QueryResponse.class));
    }

    static Collection<TestSerializer> serializers() {
        return TestSerializer.all();
    }

    @MethodSource("serializers")
    @ParameterizedTest
    void responseTypeShouldBeSerializable(TestSerializer serializer) {
        assertEquals(testSubject.getExpectedResponseType(), serializer.serializeDeserialize(testSubject).getExpectedResponseType());
    }
}
