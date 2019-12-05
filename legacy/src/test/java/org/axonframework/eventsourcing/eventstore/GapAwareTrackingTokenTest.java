package org.axonframework.eventsourcing.eventstore;

import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.SimpleSerializedObject;
import org.axonframework.serialization.json.JacksonSerializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.SortedSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test whether the serialized form of the {@link org.axonframework.eventsourcing.eventstore.GapAwareTrackingToken} can
 * be deserialized into the {@link GapAwareTrackingToken}, using the {@link XStreamSerializer} and
 * {@link JacksonSerializer}.
 *
 * @author Steven van Beelen
 */
class GapAwareTrackingTokenTest {

    private static final String LEGACY_GAP_AWARE_TRACKING_TOKEN_CLASS_NAME =
            "org.axonframework.eventsourcing.eventstore.GapAwareTrackingToken";
    private static final int TOKEN_INDEX = 75;
    private static final List<Long> TOKEN_GAPS = Stream.of(0L, 25L, 58L).collect(Collectors.toList());

    @Test
    void testXStreamSerializationOfOldGapAwareTrackingToken() {
        XStreamSerializer serializer = XStreamSerializer.defaultSerializer();

        String xmlSerializedGapAwareTrackingToken =
                "<org.axonframework.eventsourcing.eventstore.GapAwareTrackingToken>"
                        + "<index>" + TOKEN_INDEX + "</index>"
                        + "<gaps class=\"java.util.concurrent.ConcurrentSkipListSet\">"
                        + "<m class=\"java.util.concurrent.ConcurrentSkipListMap\" serialization=\"custom\">"
                        + "<unserializable-parents/>"
                        + "<java.util.concurrent.ConcurrentSkipListMap>"
                        + "<default/>"
                        + "<long>" + TOKEN_GAPS.get(0) + "</long><boolean>true</boolean>"
                        + "<long>" + TOKEN_GAPS.get(1) + "</long><boolean>true</boolean>"
                        + "<long>" + TOKEN_GAPS.get(2) + "</long><boolean>true</boolean>"
                        + "<null/>"
                        + "</java.util.concurrent.ConcurrentSkipListMap>"
                        + "</m><"
                        + "/gaps>"
                        + "</org.axonframework.eventsourcing.eventstore.GapAwareTrackingToken>";
        SerializedObject<String> serializedGapAwareTrackingToken = new SimpleSerializedObject<>(
                xmlSerializedGapAwareTrackingToken, String.class, LEGACY_GAP_AWARE_TRACKING_TOKEN_CLASS_NAME, null
        );

        GapAwareTrackingToken result = serializer.deserialize(serializedGapAwareTrackingToken);
        assertEquals(TOKEN_INDEX, result.getIndex());
        SortedSet<Long> resultGaps = result.getGaps();
        assertFalse(resultGaps.isEmpty());
        for (Long tokenGap : TOKEN_GAPS) {
            assertTrue(resultGaps.contains(tokenGap));
        }
    }

    @Test
    void testJacksonSerializationOfOldGapAwareTrackingToken() {
        JacksonSerializer serializer = JacksonSerializer.defaultSerializer();

        String jacksonSerializedGapAwareTrackingToken =
                "{\"index\": " + TOKEN_INDEX + ", \"gaps\": "
                        + "[" + TOKEN_GAPS.get(0) + ", " + TOKEN_GAPS.get(1) + ", " + TOKEN_GAPS.get(2) + "]}";
        SerializedObject<String> serializedGapAwareTrackingToken = new SimpleSerializedObject<>(
                jacksonSerializedGapAwareTrackingToken, String.class, LEGACY_GAP_AWARE_TRACKING_TOKEN_CLASS_NAME, null
        );

        GapAwareTrackingToken result = serializer.deserialize(serializedGapAwareTrackingToken);
        assertEquals(TOKEN_INDEX, result.getIndex());
        SortedSet<Long> resultGaps = result.getGaps();
        assertFalse(resultGaps.isEmpty());
        for (Long tokenGap : TOKEN_GAPS) {
            assertTrue(resultGaps.contains(tokenGap));
        }
    }
}