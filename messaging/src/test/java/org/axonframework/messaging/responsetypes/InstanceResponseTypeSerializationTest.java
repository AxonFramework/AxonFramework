package org.axonframework.messaging.responsetypes;

import static junit.framework.TestCase.assertEquals;

import org.axonframework.serialization.TestSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

/**
 * Tests serialization capabilities of {@link InstanceResponseType}.
 * 
 * @author JohT
 */
@RunWith(Parameterized.class)
public class InstanceResponseTypeSerializationTest extends AbstractResponseTypeTest<AbstractResponseTypeTest.QueryResponse> {

    private final TestSerializer serializer;

    public InstanceResponseTypeSerializationTest(TestSerializer serializer) {
        super(new InstanceResponseType<>(QueryResponse.class));
        this.serializer = serializer;
    }
    
    @Parameterized.Parameters(name = "{index} {0}")
    public static Collection<TestSerializer> serializers() {
       return TestSerializer.all();
    }
       
    @Test
    public void testResponseTypeShouldBeSerializable() {
        assertEquals(testSubject.getExpectedResponseType(), serializer.serializeDeserialize(testSubject).getExpectedResponseType());
    }
}