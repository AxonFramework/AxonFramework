package org.axonframework.eventsourcing.snapshotting;

import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.eventstore.jpa.SnapshotEventEntry;
import org.axonframework.eventsourcing.utils.TestSerializer;
import org.axonframework.serialization.Revision;
import org.axonframework.serialization.Serializer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link RevisionSnapshotFilter}.
 *
 * @author Steven van Beelen
 */
class RevisionSnapshotFilterTest {

    private static final String ALLOWED_REVISION = "LET ME IN";

    private final Serializer serializer = TestSerializer.secureXStreamSerializer();
    private final RevisionSnapshotFilter testSubject = new RevisionSnapshotFilter(ALLOWED_REVISION);

    @Test
    void testAllowsDomainEventDataContainingTheAllowedRevision() {
        DomainEventMessage<RightAggregateVersion> snapshotEvent = new GenericDomainEventMessage<>(
                RightAggregateVersion.class.getName(), "some-aggregate-id", 0, new RightAggregateVersion("some-state")
        );
        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, serializer);

        assertTrue(testSubject.allow(testDomainEventData));
    }

    @Test
    void testDisallowsDomainEventDataContainingTheAllowedRevision() {
        DomainEventMessage<WrongAggregateVersion> snapshotEvent = new GenericDomainEventMessage<>(
                WrongAggregateVersion.class.getName(), "some-aggregate-id", 0, new WrongAggregateVersion("some-state")
        );
        DomainEventData<byte[]> testDomainEventData = new SnapshotEventEntry(snapshotEvent, serializer);

        assertFalse(testSubject.allow(testDomainEventData));
    }

    @Revision(ALLOWED_REVISION)
    private static class RightAggregateVersion {

        private final String state;

        private RightAggregateVersion(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }
    }

    @Revision("some-other-revision")
    private static class WrongAggregateVersion {

        private final String state;

        private WrongAggregateVersion(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }
    }
}