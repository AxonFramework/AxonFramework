package org.axonframework.eventhandling;

import static junit.framework.TestCase.assertEquals;

import org.axonframework.serialization.TestSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

/**
 * Tests serialization capabilities of {@link GlobalSequenceTrackingToken}.
 * 
 * @author JohT
 */
@RunWith(Parameterized.class)
public class GlobalSequenceTrackingTokenSerializationTest {

    private final TestSerializer serializer;

    public GlobalSequenceTrackingTokenSerializationTest(TestSerializer serializer) {
        this.serializer = serializer;
    }
    
    @Parameterized.Parameters(name = "{index} {0}")
    public static Collection<TestSerializer> serializers() {
       return TestSerializer.all();
    }
       
    @Test
    public void testTokenShouldBeSerializable() {
        GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(Long.MAX_VALUE);
        assertEquals(token, serializer.serializeDeserialize(token));
    }
}