package org.axonframework.eventsourcing.eventstore;

import org.axonframework.serialization.JavaSerializer;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.*;

import java.lang.reflect.Array;
import java.util.Collections;

import static org.junit.Assert.*;

public class TrackingTokenSerializationTest {

    private Serializer[] serializers;

    @Before
    public void setUp() {
        serializers = new Serializer[]{
                XStreamSerializer.builder().build(),
                JacksonSerializer.builder().build(),
                JavaSerializer.builder().build()
        };
    }

    @Test
    public void testSerializeGapAwareTokenWithoutGaps() {
        GapAwareTrackingToken token = GapAwareTrackingToken.newInstance(10, Collections.emptySet());
        GapAwareTrackingToken[] results = serializeToken(token);
        for (int i = 0; i < results.length; i++) {
            assertNotNull("Serializer " + serializers[i].getClass().getName() + " produced null result", results[i]);
        }
    }

    @Test
    public void testSerializeGapAwareTokenWithGaps() {
        GapAwareTrackingToken token = GapAwareTrackingToken.newInstance(10, Collections.emptySet());
        GapAwareTrackingToken[] results = serializeToken(token);
        for (int i = 0; i < results.length; i++) {
            assertNotNull("Serializer " + serializers[i].getClass().getName() + " produced null result", results[i]);
            assertEquals("Serializer " + serializers[i].getClass().getName() + " produced unequal result",
                         token, results[i]);
        }
    }

    @Test
    public void testSerializeGlobalSequenceTrackingToken() {
        GlobalSequenceTrackingToken token = new GlobalSequenceTrackingToken(35);
        GlobalSequenceTrackingToken[] results = serializeToken(token);
        for (int i = 0; i < results.length; i++) {
            assertNotNull("Serializer " + serializers[i].getClass().getName() + " produced null result", results[i]);
            assertEquals("Serializer " + serializers[i].getClass().getName() + " produced unequal result",
                         token, results[i]);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends TrackingToken> T[] serializeToken(T token) {
        T[] results = (T[]) Array.newInstance(token.getClass(), serializers.length);
        for (int i = 0; i < serializers.length; i++) {
            Serializer serializer = serializers[i];
            results[i] = serializer.deserialize(serializer.serialize(token, byte[].class));
        }
        return results;
    }
}
