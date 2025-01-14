package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AggregateBasedConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EventCriteria;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.eventsourcing.eventstore.LegacyResources;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.messaging.ClassBasedMessageNameResolver;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageNameResolver;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.QualifiedName;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static java.lang.String.format;
import static org.axonframework.common.BuilderUtils.*;


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
    private final EventUpcaster upcasterChain;
    private final SnapshotFilter snapshotFilter;
    private final int batchSize;

    private final boolean explicitFlush;
    private final long lowestGlobalSequence;
    private final int maxGapOffset;
    private final int gapTimeout;

    // AsyncEventStorageEngine specific
    private final PersistenceExceptionMapper persistenceExceptionMapper;
    private final MessageNameResolver messageNameResolver;
    private final LegacyJpaOperations legacyJpaOperations;
    private Executor writeExecutor;

    public LegacyJpaEventStorageEngine(
            @javax.annotation.Nonnull EntityManagerProvider entityManagerProvider,
            @javax.annotation.Nonnull TransactionManager transactionManager,
            @javax.annotation.Nonnull Serializer eventSerializer,
            @javax.annotation.Nonnull Serializer snapshotSerializer,
            @javax.annotation.Nonnull UnaryOperator<Config> configurationOverride
    ) {
        this.writeExecutor = Executors.newFixedThreadPool(10); // todo: configurable, AxonThreadFactory etc.
        // todo: is is useful? Maybe we'd like to utilize the same thread as invoker?

        this.entityManagerProvider = entityManagerProvider;
        this.transactionManager = transactionManager;
        this.eventSerializer = eventSerializer;
        this.snapshotSerializer = snapshotSerializer;

        var customization = configurationOverride.apply(Config.defaultConfig());
        this.upcasterChain = customization.upcasterChain();
        this.persistenceExceptionResolver = customization.persistenceExceptionResolver();
        this.snapshotFilter = customization.snapshotFilter();
        this.batchSize = customization.batchSize();
        this.messageNameResolver = customization.messageNameResolver();

        // BatchingEventStorageEngine
        // customization.batchSize();
        // customization.finalAggregateBatchPredicate();

        // JpaEventStorageEngine
        this.explicitFlush = customization.explicitFlush();
        this.lowestGlobalSequence = customization.lowestGlobalSequence();
        this.maxGapOffset = customization.maxGapOffset();
        this.gapTimeout = customization.gapTimeout();

        this.legacyJpaOperations = new LegacyJpaOperations(transactionManager,
                                                           entityManagerProvider.getEntityManager(),
                                                           domainEventEntryEntityName(),
                                                           SnapshotEventEntry.class.getSimpleName());
        this.persistenceExceptionMapper = new PersistenceExceptionMapper(persistenceExceptionResolver);
    }

    public record Config(
            EventUpcaster upcasterChain,
            PersistenceExceptionResolver persistenceExceptionResolver,
            SnapshotFilter snapshotFilter,
            int batchSize,
            Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate, // todo: handle!,
            MessageNameResolver messageNameResolver,
            boolean explicitFlush,
            int maxGapOffset,
            long lowestGlobalSequence,
            int gapTimeout,
            int gapCleaningThreshold
    ) {

        private static final int DEFAULT_MAX_GAP_OFFSET = 10000;
        private static final long DEFAULT_LOWEST_GLOBAL_SEQUENCE = 1;
        private static final int DEFAULT_GAP_TIMEOUT = 60000;
        private static final int DEFAULT_GAP_CLEANING_THRESHOLD = 250;

        public Config {
            assertNonNull(upcasterChain, "EventUpcaster may not be null");
            assertNonNull(snapshotFilter, "The snapshotFilter may not be null");
            assertThat(batchSize, size -> size > 0, "The batchSize must be a positive number");
            assertNonNull(messageNameResolver, "MessageNameResolver may not be null");
            assertPositive(maxGapOffset, "maxGapOffset");
            assertThat(lowestGlobalSequence,
                       number -> number > 0,
                       "The lowestGlobalSequence must be a positive number");
            assertPositive(gapTimeout, "gapTimeout");
            assertPositive(gapCleaningThreshold, "gapCleaningThreshold");
        }

        public static Config defaultConfig() {
            return new Config(
                    NoOpEventUpcaster.INSTANCE,
                    null,
                    SnapshotFilter.allowAll(),
                    100,
                    null,
                    new ClassBasedMessageNameResolver(),
                    true,
                    DEFAULT_MAX_GAP_OFFSET,
                    DEFAULT_LOWEST_GLOBAL_SEQUENCE,
                    DEFAULT_GAP_TIMEOUT,
                    DEFAULT_GAP_CLEANING_THRESHOLD
            );
        }

        public Config upcasterChain(EventUpcaster upcasterChain) {
            return new Config(upcasterChain,
                              persistenceExceptionResolver,
                              snapshotFilter,
                              batchSize,
                              finalAggregateBatchPredicate,
                              messageNameResolver,
                              explicitFlush,
                              maxGapOffset,
                              lowestGlobalSequence,
                              gapTimeout,
                              gapCleaningThreshold
            );
        }

        public Config persistenceExceptionResolver(PersistenceExceptionResolver persistenceExceptionResolver) {
            return new Config(upcasterChain,
                              persistenceExceptionResolver,
                              snapshotFilter,
                              batchSize,
                              finalAggregateBatchPredicate,
                              messageNameResolver,
                              explicitFlush,
                              maxGapOffset,
                              lowestGlobalSequence,
                              gapTimeout,
                              gapCleaningThreshold
            );
        }

        public Config snapshotFilter(SnapshotFilter snapshotFilter) {
            return new Config(upcasterChain,
                              persistenceExceptionResolver,
                              snapshotFilter,
                              batchSize,
                              finalAggregateBatchPredicate,
                              messageNameResolver,
                              explicitFlush,
                              maxGapOffset,
                              lowestGlobalSequence,
                              gapTimeout,
                              gapCleaningThreshold
            );
        }

        public Config batchSize(int batchSize) {
            return new Config(upcasterChain,
                              persistenceExceptionResolver,
                              snapshotFilter,
                              batchSize,
                              finalAggregateBatchPredicate,
                              messageNameResolver,
                              explicitFlush,
                              maxGapOffset,
                              lowestGlobalSequence,
                              gapTimeout,
                              gapCleaningThreshold
            );
        }

        public Config finalAggregateBatchPredicate(
                Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate) {
            return new Config(upcasterChain,
                              persistenceExceptionResolver,
                              snapshotFilter,
                              batchSize,
                              finalAggregateBatchPredicate,
                              messageNameResolver,
                              explicitFlush,
                              maxGapOffset,
                              lowestGlobalSequence,
                              gapTimeout,
                              gapCleaningThreshold
            );
        }

        public Config messageNameResolver(MessageNameResolver messageNameResolver) {
            return new Config(upcasterChain,
                              persistenceExceptionResolver,
                              snapshotFilter,
                              batchSize,
                              finalAggregateBatchPredicate,
                              messageNameResolver,
                              explicitFlush,
                              maxGapOffset,
                              lowestGlobalSequence,
                              gapTimeout,
                              gapCleaningThreshold
            );
        }

        // todo: other functions!
    }

    @Override
    public CompletableFuture<AppendTransaction> appendEvents(@Nonnull AppendCondition condition,
                                                             @Nonnull List<TaggedEventMessage<?>> events) {
        try {
            assertValidTags(events);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(new EmptyAppendTransaction(condition));
        }
        return CompletableFuture.supplyAsync(
                () -> appendTransaction(condition, events),
                writeExecutor
        );
    }

    // todo: move it for some base class
    private void assertValidTags(List<TaggedEventMessage<?>> events) {
        for (TaggedEventMessage<?> taggedEvent : events) {
            if (taggedEvent.tags().size() > 1) {
                throw new IllegalArgumentException(
                        "An Event Storage engine in Aggregate mode does not support multiple tags per event");
            }
        }
    }

    private AppendTransaction appendTransaction(AppendCondition condition, List<TaggedEventMessage<?>> events) {
        var tx = transactionManager.startTransaction();
        return new AppendTransaction() {
            @Override
            public CompletableFuture<ConsistencyMarker> commit() {
                var consistencyMarker = AggregateBasedConsistencyMarker.from(condition);
                var aggregateSequences = new HashMap<String, AtomicLong>();
                try {
                    var entityManager = entityManagerProvider.getEntityManager();
                    events.forEach(taggedEvent -> {
                        var aggregateIdentifier = resolveAggregateIdentifier(taggedEvent.tags());
                        var aggregateType = resolveAggregateType(taggedEvent.tags());
                        var event = taggedEvent.event();
                        if (aggregateIdentifier != null && aggregateType != null && !taggedEvent.tags().isEmpty()) {
                            // todo: what is GenericDomainEventMessage instance
                            var nextSequence = resolveSequencer(
                                    aggregateSequences,
                                    aggregateIdentifier,
                                    consistencyMarker
                            ).incrementAndGet();
                            var domainEvent = new GenericDomainEventMessage<>(
                                    aggregateType,
                                    aggregateIdentifier,
                                    nextSequence,
                                    event.getIdentifier(),
                                    LegacyJpaEventStorageEngine.this.messageNameResolver.resolve(event.getPayload()),
                                    event.getPayload(),
                                    event.getMetaData(),
                                    event.getTimestamp()
                            );
                            Object eventEntity = new DomainEventEntry(domainEvent, eventSerializer);
                            entityManager.persist(eventEntity);
                        } else {
                            var domainEvent = new GenericDomainEventMessage<>(
                                    null,
                                    null,
                                    0L,
                                    event.getIdentifier(),
                                    LegacyJpaEventStorageEngine.this.messageNameResolver.resolve(event.getPayload()),
                                    event.getPayload(),
                                    event.getMetaData(),
                                    event.getTimestamp()
                            );
                            Object eventEntity = new DomainEventEntry(domainEvent, eventSerializer);
                            entityManager.persist(eventEntity);
                        }
                    });
                    if (explicitFlush) {
                        entityManager.flush();
                    }
                    var newConsistencyMarker = consistencyMarker;
                    for (var aggSeq : aggregateSequences.entrySet()) {
                        newConsistencyMarker = newConsistencyMarker.forwarded(aggSeq.getKey(), aggSeq.getValue().get());
                    }
                    var finalConsistencyMarker = newConsistencyMarker;
                    tx.commit();
                    return CompletableFuture.completedFuture(finalConsistencyMarker);
                } catch (Exception e) {
                    tx.rollback();
                    return CompletableFuture.failedFuture(persistenceExceptionMapper.mapPersistenceException(e,
                                                                                                             events.getFirst()
                                                                                                                   .event()));
                }
            }

            @Override
            public void rollback() {
                tx.rollback();
            }
        };
    }

    // todo: how do I know (even if its only tag), that it's an aggregate id?
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

    private static AtomicLong resolveSequencer(Map<String, AtomicLong> aggregateSequences, String aggregateIdentifier,
                                               AggregateBasedConsistencyMarker consistencyMarker) {
        return aggregateSequences.computeIfAbsent(aggregateIdentifier,
                                                  i -> new AtomicLong(consistencyMarker.positionOf(i)));
    }

    // todo: don't mind gaps
    // now support just one aggregate per source!
    // todo: support batches!!!
    // same as old:     public DomainEventStream readEvents(@Nonnull String aggregateIdentifier, long firstSequenceNumber) {
    @Override
    public MessageStream<EventMessage<?>> source(@Nonnull SourcingCondition condition) {
        MessageStream<EventMessage<?>> resultingStream = MessageStream.empty();
        for (EventCriteria criterion : condition.criteria()) {
            var aggregateIdentifier = resolveAggregateIdentifier(criterion.tags());
            // todo: support condition.end()
            // todo: fetch in single transaction or multiple???
            var events =
                    transactionManager.fetchInTransaction(() -> legacyJpaOperations.fetchDomainEvents(
                            aggregateIdentifier,
                            condition.start(),
                            batchSize));
            // todo: upcasting etc!!
            MessageStream<EventMessage<?>> aggregateEvents = MessageStream.fromStream(
                    events.stream(),
                    this::convertToMessage,
                    event -> Context.with(LegacyResources.AGGREGATE_IDENTIFIER_KEY,
                                          event.getAggregateIdentifier())
                                    .withResource(LegacyResources.AGGREGATE_TYPE_KEY, event.getType())
                                    .withResource(LegacyResources.AGGREGATE_SEQUENCE_NUMBER_KEY,
                                                  event.getSequenceNumber())
                                    .withResource(ConsistencyMarker.RESOURCE_KEY,
                                                  new AggregateBasedConsistencyMarker(event.getAggregateIdentifier(),
                                                                                      event.getSequenceNumber()))
            );
            resultingStream = resultingStream.concatWith(aggregateEvents);
        }
        AtomicReference<ConsistencyMarker> consistencyMarker = new AtomicReference<>();
        return resultingStream.map(e -> {
            ConsistencyMarker newMarker = consistencyMarker.accumulateAndGet(e.getResource(ConsistencyMarker.RESOURCE_KEY),
                                                                             (m1, m2) -> m1
                                                                                     == null ? m2 : m1.upperBound(m2));
            return e.withResource(ConsistencyMarker.RESOURCE_KEY, newMarker);
        });
    }

    private EventMessage<?> convertToMessage(DomainEventData<?> event) {
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
        try { // todo: how to serialize / deserliazie the payload and metadata!?
            return new GenericEventMessage<>(
                    identifier,
                    name,
                    data,
                    metaData,
                    event.getTimestamp()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Instant gapTimeoutThreshold() {
        return GenericEventMessage.clock.instant().minus(gapTimeout, ChronoUnit.MILLIS);
    }

    private String domainEventEntryEntityName() {
        return DomainEventEntry.class.getSimpleName(); // todo: configurable
    }

    @Override
    public MessageStream<EventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        // todo: find tracking token by condition (position)
        //        var startToken = GapAwareTrackingToken.newInstance(lowestGlobalSequence, Collections.emptySet());
        // todo: there fetchDomainEvents
        var events = legacyJpaOperations.entriesToEvents(
                null,
                legacyJpaOperations.fetchEvents(null, batchSize),
                gapTimeoutThreshold(),
                lowestGlobalSequence,
                maxGapOffset);
//        var processed = upcastAndDeserializeTrackedEvents(events, eventSerializer, upcasterChain)
//                .filter(e -> e instanceof TrackedDomainEventData<?>)
//                .map(e -> (TrackedDomainEventData<?>) e);
        return MessageStream.fromStream(
                events.stream(),
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
        // todo: how to describe? what's the idea behind it?
    }

    private record EmptyAppendTransaction(AppendCondition appendCondition) implements AppendTransaction {

        @Override
        public CompletableFuture<ConsistencyMarker> commit() {
            return CompletableFuture.completedFuture(AggregateBasedConsistencyMarker.from(appendCondition));
        }

        @Override
        public void rollback() {

        }
    }

    private record PersistenceExceptionMapper(PersistenceExceptionResolver persistenceExceptionResolver) {

        RuntimeException mapPersistenceException(Exception exception, EventMessage<?> failedEvent) {
            String eventDescription = buildExceptionMessage(failedEvent);
            if (persistenceExceptionResolver != null
                    && persistenceExceptionResolver.isDuplicateKeyViolation(exception)) {
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
            } else if (failedEvent instanceof DomainEventMessage<?> failedDomainEvent) {
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
            return failedEvent instanceof DomainEventMessage<?> domainEvent
                    && domainEvent.getSequenceNumber() == 0L;
        }
    }
}
