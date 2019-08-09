package org.axonframework.eventhandling;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;

import org.axonframework.serialization.TestSerializer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

/**
 * Tests serialization capabilities of {@link GapAwareTrackingToken}.
 * 
 * @author JohT
 */
@RunWith(Parameterized.class)
public class GapAwareTrackingTokenSerializationTest {

    private final TestSerializer serializer;

    public GapAwareTrackingTokenSerializationTest(TestSerializer serializer) {
        this.serializer = serializer;
    }
    
    @Parameterized.Parameters(name = "{index} {0}")
    public static Collection<TestSerializer> serializers() {
       return TestSerializer.all();
    }
       
    @Test
    public void testTokenShouldBeSerializable() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(Long.MAX_VALUE, asList(0L, 1L));
        assertEquals(subject, serializer.serializeDeserialize(subject));
    }

    @Test
    public void testTokenWithoutGapsShouldBeSerializable() {
        GapAwareTrackingToken subject = GapAwareTrackingToken.newInstance(0, emptyList());
        assertEquals(subject, serializer.serializeDeserialize(subject));
    }
}