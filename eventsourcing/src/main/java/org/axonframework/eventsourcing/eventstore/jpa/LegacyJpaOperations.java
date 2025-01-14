package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericDomainEventEntry;
import org.axonframework.eventhandling.TrackedDomainEventData;
import org.axonframework.eventhandling.TrackedEventData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

record LegacyJpaOperations(
        TransactionManager transactionManager,
        EntityManager entityManager,
        String domainEventEntryEntityName
) {

    /**
     * Returns a batch of event data as object entries in the event storage with a greater than the given
     * {@code token}.
     *
     * @param token     Object describing the global index of the last processed event.
     * @param batchSize Size of event list is decided by that.
     * @return A batch of event messages as object stored since the given tracking token.
     */
    protected List<Object[]> fetchEvents(GapAwareTrackingToken token, int batchSize) {
        TypedQuery<Object[]> query;
        if (token == null || token.getGaps().isEmpty()) {
            query = entityManager().createQuery(
                    "SELECT e.globalIndex, e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, "
                            + "e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData " +
                            "FROM " + domainEventEntryEntityName() + " e " +
                            "WHERE e.globalIndex > :token ORDER BY e.globalIndex ASC", Object[].class);
        } else {
            query = entityManager().createQuery(
                    "SELECT e.globalIndex, e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, "
                            + "e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData " +
                            "FROM " + domainEventEntryEntityName() + " e " +
                            "WHERE e.globalIndex > :token OR e.globalIndex IN :gaps ORDER BY e.globalIndex ASC",
                    Object[].class
            ).setParameter("gaps", token.getGaps());
        }
        return query.setParameter("token", token == null ? -1L : token.getIndex())
                    .setMaxResults(batchSize)
                    .getResultList();
    }

    List<? extends DomainEventData<?>> fetchDomainEvents(
            String aggregateIdentifier,
            long firstSequenceNumber,
            int batchSize
    ) {
        return entityManager
                .createQuery(
                        "SELECT new org.axonframework.eventhandling.GenericDomainEventEntry(" +
                                "e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, e.timeStamp, "
                                + "e.payloadType, e.payloadRevision, e.payload, e.metaData) FROM "
                                + domainEventEntryEntityName() + " e WHERE e.aggregateIdentifier = :id "
                                + "AND e.sequenceNumber >= :seq ORDER BY e.sequenceNumber ASC"
                )
                .setParameter("id", aggregateIdentifier)
                .setParameter("seq", firstSequenceNumber)
                .setMaxResults(batchSize)
                .getResultList();
    }

    // todo: check that return type changed!
    List<TrackedDomainEventData<?>> entriesToEvents(
            GapAwareTrackingToken previousToken,
            List<Object[]> entries,
            Instant gapTimeoutThreshold,
            long lowestGlobalSequence,
            int maxGapOffset
    ) {
        List<TrackedDomainEventData<?>> result = new ArrayList<>();
        GapAwareTrackingToken token = previousToken;
        for (Object[] entry : entries) {
            long globalSequence = (Long) entry[0];
            String aggregateIdentifier = (String) entry[2];
            String eventIdentifier = (String) entry[4];
            GenericDomainEventEntry<?> domainEvent = new GenericDomainEventEntry<>(
                    (String) entry[1], eventIdentifier.equals(aggregateIdentifier) ? null : aggregateIdentifier,
                    (long) entry[3], eventIdentifier, entry[5],
                    (String) entry[6], (String) entry[7], entry[8], entry[9]
            );

            // Now that we have the event itself, we can calculate the token
            boolean allowGaps = domainEvent.getTimestamp().isAfter(gapTimeoutThreshold);
            if (token == null) {
                token = GapAwareTrackingToken.newInstance(
                        globalSequence,
                        allowGaps
                                ? LongStream.range(Math.min(lowestGlobalSequence, globalSequence), globalSequence)
                                            .boxed()
                                            .collect(Collectors.toCollection(TreeSet::new))
                                : Collections.emptySortedSet()
                );
            } else {
                token = token.advanceTo(globalSequence, allowGaps ? maxGapOffset : 0);
            }
            result.add(new TrackedDomainEventData<>(token, domainEvent));
        }
        return result;
    }
}
