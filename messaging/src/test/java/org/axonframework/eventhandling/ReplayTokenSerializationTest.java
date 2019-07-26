package org.axonframework.eventhandling;

import static junit.framework.TestCase.assertEquals;

import org.axonframework.serialization.TestSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.Collections;

/**
 * Tests serialization capabilities of {@link ReplayToken}.
 * 
 * @author JohT
 */
@RunWith(Parameterized.class)
public class ReplayTokenSerializationTest {

    private final TestSerializer serializer;

    public ReplayTokenSerializationTest(TestSerializer serializer) {
        this.serializer = serializer;
    }
    
    @Parameterized.Parameters(name = "{index} {0}")
    public static Collection<TestSerializer> serializers() {
       return TestSerializer.all();
    }

    @Test
    public void testTokenShouldBeSerializable() {
        TrackingToken innerToken = GapAwareTrackingToken.newInstance(10, Collections.singleton(9L));
        ReplayToken token = new ReplayToken(innerToken);
        assertEquals(token, serializer.serializeDeserialize(token));
    }
}