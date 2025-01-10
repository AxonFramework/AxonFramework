package org.axonframework.eventsourcing.eventstore.jpa;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.common.jpa.EntityManagerProvider;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AggregateBasedConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.eventsourcing.eventstore.jdbc.JdbcSQLErrorCodesResolver;
import org.axonframework.messaging.MessageStream;
import org.axonframework.modelling.command.AggregateStreamCreationException;
import org.axonframework.modelling.command.ConcurrencyException;
import org.axonframework.serialization.Serializer;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

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
    }

    @Override
    public CompletableFuture<AppendTransaction> appendEvents(@Nonnull AppendCondition condition,
                                                             @Nonnull List<TaggedEventMessage<?>> events) {
        if (events.isEmpty()) {
            return CompletableFuture.completedFuture(new NoOpAppendTransaction(condition));
        }

        transactionManager.executeInTransaction(() -> {
            try {
//                events.stream().map(event -> createEventEntity(event, serializer)).forEach(entityManager()::persist);
//                if (explicitFlush) {
//                    entityManager().flush();
//                }
            } catch (Exception e) {
                handlePersistenceException(e, events.get(0));
            }
        });

        return CompletableFuture.failedFuture(new UnsupportedOperationException("Not implemented yet"));
    }

    private Exception handlePersistenceException(Exception exception, TaggedEventMessage<?> failedEvent) {
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
    private String buildExceptionMessage(TaggedEventMessage<?> failedEvent) {
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
    private boolean isFirstDomainEvent(TaggedEventMessage<?> failedEvent) {
        if (failedEvent.event() instanceof DomainEventMessage<?>) {
            return ((DomainEventMessage<?>) failedEvent).getSequenceNumber() == 0L; // todo: what to do with that?
        }
        return false;
    }



    @Override
    public MessageStream<EventMessage<?>> source(@Nonnull SourcingCondition condition) {
        return null;
    }

    @Override
    public MessageStream<EventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        return null;
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
