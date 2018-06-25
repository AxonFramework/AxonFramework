package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.IdentifierFactory;
import org.axonframework.serialization.AnnotationRevisionResolver;
import org.axonframework.serialization.Revision;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RevisionBasedSnapshotJuryTest {

    private static final String PAYLOAD = "payload", AGGREGATE = "aggregate", TYPE = "type", METADATA = "metadata";
    private RevisionBasedSnapshotJury testSubject;

    @Before
    public void setUp() throws Exception {
        testSubject = new RevisionBasedSnapshotJury(new AnnotationRevisionResolver());
    }

    @Test
    public void testSameRevisionForAggregateAndPayload() {
        assertTrue(testSubject.decide(createEntry(WithAnnotationAggregate.class.getName(), "2.3-TEST")));
    }

    @Test
    public void testDifferentRevisionsForAggregateAndPayload() {
        assertFalse(testSubject.decide(createEntry(WithAnnotationAggregate.class.getName(), "2.3-TEST-DIFFERENT")));
    }

    @Test
    public void testNoRevisionForAggregateAndPayload() {
        assertTrue(testSubject.decide(createEntry(WithoutAnnotationAggregate.class.getName())));
    }

    @Test
    public void testNoRevisionForPayload() {
        assertFalse(testSubject.decide(createEntry(WithAnnotationAggregate.class.getName())));
    }

    @Test
    public void testNoRevisionForAggregate() {
        assertFalse(testSubject.decide(createEntry(WithoutAnnotationAggregate.class.getName(), "2.3-TEST")));
    }

    private static DomainEventData<?> createEntry(String payloadType) {
        return createEntry(payloadType, null);
    }

    private static DomainEventData<?> createEntry(String payloadType, String payloadRevision) {
        return new GenericDomainEventEntry<>(TYPE, AGGREGATE, 0,
                IdentifierFactory.getInstance().generateIdentifier(), Instant.now(),
                payloadType, payloadRevision, PAYLOAD, METADATA);
    }

    @Revision("2.3-TEST")
    private class WithAnnotationAggregate {

    }

    private class WithoutAnnotationAggregate {

    }
}