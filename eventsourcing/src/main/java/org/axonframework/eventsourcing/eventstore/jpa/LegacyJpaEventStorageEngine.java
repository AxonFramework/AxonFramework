package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.persistence.TypedQuery;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GapAwareTrackingToken;
import org.axonframework.eventhandling.GenericDomainEventEntry;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackedDomainEventData;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AggregateBasedConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.eventsourcing.eventstore.LegacyResources;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcSQLErrorCodesResolver;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.serialization.Serializer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static java.lang.String.format;


/**
 * EventStorageEngine implementation that uses JPA to store and fetch events.
 * <p>
 * By default, the payload of events is stored as a serialized blob of bytes. Other columns are used to store meta-data
 * that allow quick finding of DomainEvents for a specific aggregate in the correct order.
 *
 * @author Mateusz Nowak
 * @since 5.0.0
 */
public class LegacyJpaEventStorageEngine implements AsyncEventStorageEngine {

    private final EntityManagerProvider entityManagerProvider;
    private final TransactionManager transactionManager;
    private final Serializer eventSerializer;
    private final Serializer snapshotSerializer;
    private final PersistenceExceptionResolver persistenceExceptionResolver;

    private boolean explicitFlush = false;
    private Executor executor;
    private final long lowestGlobalSequence = 1L;
    private final int maxGapOffset = 10000;
    private final int gapTimeout = 60000;


    public LegacyJpaEventStorageEngine(
            @javax.annotation.Nonnull EntityManagerProvider entityManagerProvider,
            @javax.annotation.Nonnull TransactionManager transactionManager,
            @javax.annotation.Nonnull Serializer eventSerializer,
            @javax.annotation.Nonnull Serializer snapshotSerializer
    ) {
        this.entityManagerProvider = entityManagerProvider;
        this.transactionManager = transactionManager;
        this.eventSerializer = eventSerializer;
        this.snapshotSerializer = snapshotSerializer;
        // optional config - default will be provided here:
        this.persistenceExceptionResolver = new JdbcSQLErrorCodesResolver();
        this.executor = Executors.newFixedThreadPool(10); // todo: configurable, AxonThreadFactory etc.
    }

    @Override
    public CompletableFuture<AppendTransaction> appendEvents(@Nonnull AppendCondition condition,
                                                             @Nonnull List<TaggedEventMessage<?>> events) {
        return CompletableFuture.supplyAsync(
                () -> events.isEmpty()
                        ? new NoOpAppendTransaction(condition)
                        : appendTransaction(condition, events, this.eventSerializer),
                executor
        );
    }

    private AppendTransaction appendTransaction(AppendCondition condition,
                                                List<TaggedEventMessage<?>> events,
                                                Serializer eventSerializer) {
        var tx = transactionManager.startTransaction();
        return new AppendTransaction() {
            @Override
            public CompletableFuture<ConsistencyMarker> commit() {
                try {
                    var entityManager = entityManagerProvider.getEntityManager();
                    events.stream().map(event -> createEventEntity(event, eventSerializer))
                          .forEach(entityManager::persist);
                    if (explicitFlush) {
                        entityManager.flush();
                    }
                    tx.commit();
                    return CompletableFuture.completedFuture(AggregateBasedConsistencyMarker.from(condition));
                } catch (Exception e) {
                    tx.rollback();
                    return CompletableFuture.failedFuture(mapPersistenceException(e, events.getFirst().event()));
                }
            }

            @Override
            public void rollback() {
                tx.rollback();
            }
        };
    }

    @Nullable
    private static String resolveAggregateIdentifier(Set<Tag> tags) {
        if (tags.isEmpty()) {
            return null;
        } else if (tags.size() > 1) {
            throw new IllegalArgumentException("Condition must provide exactly one tag");
        } else {
            return tags.iterator().next().value();
        }
    }

    /**
     * Returns a Jpa event entity for given {@code eventMessage}. Use the given {@code serializer} to serialize the
     * payload and metadata of the event.
     *
     * @param eventMessage the event message to store
     * @param serializer   the serializer to serialize the payload and metadata
     * @return the Jpa entity to be inserted
     */
    protected Object createEventEntity(TaggedEventMessage<?> eventMessage,
                                       Serializer serializer) {
        var domainEventMessage = asDomainEventMessage(eventMessage);
        return new DomainEventEntry(domainEventMessage, serializer);
    }

    // todo: understand why sequenceNumber 0
    private static DomainEventMessage<?> asDomainEventMessage(TaggedEventMessage<?> taggedEvent) {
        EventMessage<?> event = taggedEvent.event();
        if (event instanceof DomainEventMessage<?>) {
            return (DomainEventMessage<?>) event;
        }
        String aggregateIdentifier = resolveAggregateIdentifier(taggedEvent.tags());
        String aggregateType = resolveAggregateType(taggedEvent.tags());

        return new GenericDomainEventMessage<>(aggregateType,
                                               aggregateIdentifier,
                                               0L,
                                               event.getIdentifier(),
                                               new QualifiedName("test", "event", "0.0.1"), // todo: change
                                               event.getPayload(),
                                               event.getMetaData(),
                                               event.getTimestamp());
    }


    @Nullable
    private static String resolveAggregateType(Set<Tag> tags) {
        if (tags.isEmpty()) {
            return null;
        } else if (tags.size() > 1) {
            throw new IllegalArgumentException("Condition must provide exactly one tag");
        } else {
            return tags.iterator().next().key();
        }
    }

    private RuntimeException mapPersistenceException(Exception exception, EventMessage<?> failedEvent) {
        String eventDescription = buildExceptionMessage(failedEvent);
        if (persistenceExceptionResolver != null && persistenceExceptionResolver.isDuplicateKeyViolation(exception)) {
            if (isFirstDomainEvent(failedEvent)) {
                return new AggregateStreamCreationException(eventDescription, exception);
            }
            return new ConcurrencyException(eventDescription, exception);
        } else {
            return new EventStoreException(eventDescription, exception);
        }
    }

    /**
     * Build an exception message based on an EventMessage.
     * todo: what to do!?!?!
     *
     * @param failedEvent the event to be used for the exception message
     * @return the created exception message
     */
    private String buildExceptionMessage(EventMessage<?> failedEvent) {
        String eventDescription = format("An event with identifier [%s] could not be persisted",
                                         failedEvent.getIdentifier());
        if (isFirstDomainEvent(failedEvent)) {
            DomainEventMessage<?> failedDomainEvent = (DomainEventMessage<?>) failedEvent;
            eventDescription = format(
                    "Cannot reuse aggregate identifier [%s] to create aggregate [%s] since identifiers need to be unique.",
                    failedDomainEvent.getAggregateIdentifier(),
                    failedDomainEvent.getType());
        } else if (failedEvent instanceof DomainEventMessage<?>) {
            DomainEventMessage<?> failedDomainEvent = (DomainEventMessage<?>) failedEvent;
            eventDescription = format("An event for aggregate [%s] at sequence [%d] was already inserted",
                                      failedDomainEvent.getAggregateIdentifier(),
                                      failedDomainEvent.getSequenceNumber());
        }
        return eventDescription;
    }

    /**
     * Check whether or not this is the first event, which means we tried to create an aggregate through the given
     * {@code failedEvent}.
     *
     * @param failedEvent the event to be checked
     * @return true in case of first event, false otherwise
     */
    private boolean isFirstDomainEvent(EventMessage<?> failedEvent) {
        if (failedEvent instanceof DomainEventMessage<?>) {
            return ((DomainEventMessage<?>) failedEvent).getSequenceNumber() == 0L; // todo: what to do with that?
        }
        return false;
    }

    @Override
    public MessageStream<EventMessage<?>> source(@Nonnull SourcingCondition condition) {
//        var startToken = GapAwareTrackingToken.newInstance(lowestGlobalSequence, Collections.emptySet());
        var events = toEvents(null, fetchEvents(null)).stream();
//        events.stream().map(e -> )
        return MessageStream.fromStream(
                events,
                this::convertToMessage,
                event -> Context.with(LegacyResources.AGGREGATE_IDENTIFIER_KEY, event.getAggregateIdentifier())
                                .withResource(LegacyResources.AGGREGATE_TYPE_KEY, event.getType())
                                .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY,
                                              event.getSequenceNumber())
                                .withResource(ConsistencyMarker.RESOURCE_KEY,
                                              new AggregateBasedConsistencyMarker(event.getAggregateIdentifier(),
                                                                                  event.getSequenceNumber()))
        );
    }

    private EventMessage<?> convertToMessage(TrackedDomainEventData<?> event) {
        var payload = event.getPayload();
        var qualifiedName = payload.getType().getName().split("\\.(?=[^.]*$)");
        var namespace = qualifiedName[0];
        var localName = qualifiedName[1];
        var revision = payload.getType().getRevision();
        var name = new QualifiedName(namespace,
                                     localName,
                                     revision == null ? "0.0.1" : revision); // todo: resolve qualified name
        var identifier = event.getEventIdentifier();
        var data = payload.getData();
        var metadata = event.getMetaData();
        MetaData metaData = eventSerializer.convert(metadata.getData(), MetaData.class);
        try { // todo: how to serialize / deserliazie!?
            return new GenericEventMessage<>(
                    identifier,
                    name,
//                    eventSerializer.convert(data, Class.forName(payload.getType().getName())),
                    data,
                    metaData,
                    event.getTimestamp()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // todo: what to do with GAPS???

    /**
     * Returns a batch of event data as object entries in the event storage with a greater than the given {@code token}.
     * Size of event is decided by {@link #batchSize()}.
     *
     * @param token Object describing the global index of the last processed event.
     * @return A batch of event messages as object stored since the given tracking token.
     */
    private List<Object[]> fetchEvents(GapAwareTrackingToken token) {
        var entityManager = entityManagerProvider.getEntityManager();
        TypedQuery<Object[]> query;
        if (token == null || token.getGaps().isEmpty()) {
            query = entityManager.createQuery(
                    "SELECT e.globalIndex, e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, "
                            + "e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData " +
                            "FROM " + domainEventEntryEntityName() + " e " +
                            "WHERE e.globalIndex > :token ORDER BY e.globalIndex ASC", Object[].class);
        } else {
            query = entityManager.createQuery(
                    "SELECT e.globalIndex, e.type, e.aggregateIdentifier, e.sequenceNumber, e.eventIdentifier, "
                            + "e.timeStamp, e.payloadType, e.payloadRevision, e.payload, e.metaData " +
                            "FROM " + domainEventEntryEntityName() + " e " +
                            "WHERE e.globalIndex > :token OR e.globalIndex IN :gaps ORDER BY e.globalIndex ASC",
                    Object[].class
            ).setParameter("gaps", token.getGaps());
        }
        return query.setParameter("token", token == null ? -1L : token.getIndex())
                    .setMaxResults(2) // todo: configurable
                    .getResultList();
    }

    private List<TrackedDomainEventData<?>> toEvents(GapAwareTrackingToken previousToken, List<Object[]> entries) {
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
            boolean allowGaps = domainEvent.getTimestamp().isAfter(gapTimeoutThreshold());
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

    private Instant gapTimeoutThreshold() {
        return GenericEventMessage.clock.instant().minus(gapTimeout, ChronoUnit.MILLIS);
    }

    private String domainEventEntryEntityName() {
        return DomainEventEntry.class.getSimpleName(); // todo: configurable
    }

    @Override
    public MessageStream<EventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        return MessageStream.empty();
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        return null;
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        return null;
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        return null;
    }

    @Override
    public void describeTo(@javax.annotation.Nonnull ComponentDescriptor descriptor) {

    }

    // todo: better name?
    private record NoOpAppendTransaction(AppendCondition appendCondition) implements AppendTransaction {

        @Override
        public CompletableFuture<ConsistencyMarker> commit() { // todo: not sure about that from(appendCondition)
            return CompletableFuture.completedFuture(AggregateBasedConsistencyMarker.from(appendCondition));
        }

        @Override
        public void rollback() {

        }
    }
}
