package org.axonframework.messaging.responsetypes;

import org.axonframework.serialization.TestSerializer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests serialization capabilities of {@link InstanceResponseType}.
 * 
 * @author JohT
 */
class InstanceResponseTypeSerializationTest extends AbstractResponseTypeTest<AbstractResponseTypeTest.QueryResponse> {

    InstanceResponseTypeSerializationTest() {
        super(new InstanceResponseType<>(QueryResponse.class));
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