package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericDomainEventEntry;
import org.axonframework.eventhandling.TrackedDomainEventData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import javax.annotation.Nonnull;

import static org.axonframework.common.DateTimeUtils.formatInstant;

/**
 * Contains operations that are used to interact with the legacy JPA event storage database structure.
 *
 * @param transactionManager
 * @param entityManager
 * @param domainEventEntryEntityName
 * @param snapshotEventEntryEntityName
 * @author Mateusz Nowak
 * @since 5.0.0
 */
record LegacyJpaOperations(
        TransactionManager transactionManager,
        EntityManager entityManager,
        String domainEventEntryEntityName,
        String snapshotEventEntryEntityName
) {

    /**
     * Returns a batch of event data as object entries in the event storage with a greater than the given
     * {@code token}.
     *
     * @param token     Object describing the global index of the last processed event.
     * @param batchSize Size of event list is decided by that.
     * @return A batch of event messages as object stored since the given tracking token.
     */
    List<Object[]> fetchEvents(GapAwareTrackingToken token, int batchSize) {
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

    List<? extends DomainEventData<?>> fetchDomainEvents(
            String aggregateIdentifier,
            long firstSequenceNumber,
            long lastSequenceNumber,
            int batchSize
    ) {
        return entityManager
                .createQuery(
                        "SELECT new org.axonframework.eventhandling.GenericDomainEventEntry(" +
                                "e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, e.timeStamp, "
                                + "e.payloadType, e.payloadRevision, e.payload, e.metaData) FROM "
                                + domainEventEntryEntityName() + " e WHERE e.aggregateIdentifier = :id "
                                + "AND e.sequenceNumber >= :min_seq AND e.sequenceNumber <= :max_seq ORDER BY e.sequenceNumber ASC"
                )
                .setParameter("id", aggregateIdentifier)
                .setParameter("min_seq", firstSequenceNumber)
                .setParameter("max_seq", lastSequenceNumber)
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

    List<Object[]> indexToTimestamp(GapAwareTrackingToken lastToken) {
        return entityManager
                .createQuery(
                        "SELECT e.globalIndex, e.timeStamp FROM " + domainEventEntryEntityName() + " e "
                                + "WHERE e.globalIndex >= :firstGapOffset "
                                + "AND e.globalIndex <= :maxGlobalIndex",
                        Object[].class
                )
                .setParameter("firstGapOffset", lastToken.getGaps().first())
                .setParameter("maxGlobalIndex", lastToken.getGaps().last() + 1L)
                .getResultList();
    }

    //todo: check return type!
    @SuppressWarnings("unchecked")
    List<? extends DomainEventData<?>> readSnapshotData(String aggregateIdentifier) {
        return entityManager
                .createQuery(
                        "SELECT new org.axonframework.eventhandling.GenericDomainEventEntry("
                                + "e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, "
                                + "e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData) FROM "
                                + snapshotEventEntryEntityName() + " e " + "WHERE e.aggregateIdentifier = :id "
                                + "ORDER BY e.sequenceNumber DESC"
                )
                .setParameter("id", aggregateIdentifier)
                .setMaxResults(1)
                .getResultList();
    }

    Optional<Long> maxGlobalIndex() {
        var results = entityManager
                .createQuery("SELECT MAX(e.globalIndex) FROM " + domainEventEntryEntityName() + " e", Long.class)
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    Optional<Long> globalIndexAt(Instant dateTime) {
        var results = entityManager
                .createQuery(
                        "SELECT MIN(e.globalIndex) - 1 FROM " + domainEventEntryEntityName() + " e "
                                + "WHERE e.timeStamp >= :dateTime", Long.class
                )
                .setParameter("dateTime", formatInstant(dateTime))
                .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    Optional<Long> minGlobalIndex() {
        var results = entityManager.createQuery(
                "SELECT MIN(e.globalIndex) - 1 FROM " + domainEventEntryEntityName() + " e", Long.class
        ).getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    void deleteSnapshots(String aggregateIdentifier, long sequenceNumber) {
        entityManager
                .createQuery(
                        "DELETE FROM " + snapshotEventEntryEntityName() + " e "
                                + "WHERE e.aggregateIdentifier = :aggregateIdentifier "
                                + "AND e.sequenceNumber < :sequenceNumber"
                )
                .setParameter("aggregateIdentifier", aggregateIdentifier)
                .setParameter("sequenceNumber", sequenceNumber)
                .executeUpdate();
    }

    Optional<Long> lastSequenceNumberFor(@Nonnull String aggregateIdentifier) {
        List<Long> results = entityManager.createQuery(
                                                  "SELECT MAX(e.sequenceNumber) FROM " + domainEventEntryEntityName()
                                                          + " e WHERE e.aggregateIdentifier = :aggregateId", Long.class)
                                          .setParameter("aggregateId", aggregateIdentifier)
                                          .getResultList();
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(results.get(0));
    }
}
