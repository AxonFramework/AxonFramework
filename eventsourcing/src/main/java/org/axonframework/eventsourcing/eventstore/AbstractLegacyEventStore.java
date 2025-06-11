/*
 * Copyright (c) 2010-2025. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.AbstractEventBus;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.monitoring.NoOpMessageMonitor;
import org.axonframework.tracing.NoOpSpanFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import jakarta.annotation.Nonnull;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Abstract implementation of an {@link LegacyEventStore} that uses a {@link LegacyEventStorageEngine} to store and load events.
 *
 * @author Rene de Waele
 * @since 3.0
 * @deprecated This will be removed.
 */
@Deprecated(since = "5.0.0", forRemoval = true)
public abstract class AbstractLegacyEventStore extends AbstractEventBus implements LegacyEventStore {

    private static final Logger logger = LoggerFactory.getLogger(AbstractLegacyEventStore.class);

    private final LegacyEventStorageEngine storageEngine;

    /**
     * Instantiate an {@link AbstractLegacyEventStore} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the {@link LegacyEventStorageEngine} is not {@code null}, and will throw an
     * {@link AxonConfigurationException} if it is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link AbstractLegacyEventStore} instance
     */
    protected AbstractLegacyEventStore(Builder builder) {
        super(builder);
        this.storageEngine = builder.storageEngine;
    }

    @Override
    protected void prepareCommit(List<? extends EventMessage<?>> events) {
        storageEngine.appendEvents(events);
        super.prepareCommit(events);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation returns a {@link DomainEventStream} starting with the last stored snapshot of the aggregate
     * followed by subsequent domain events.
     */
    @Override
    public DomainEventStream readEvents(@Nonnull String aggregateIdentifier) {
        Optional<DomainEventMessage<?>> optionalSnapshot;
        try {
            optionalSnapshot = storageEngine.readSnapshot(aggregateIdentifier);
        } catch (Exception | LinkageError e) {
            optionalSnapshot = handleSnapshotReadingError(aggregateIdentifier, e);
        }
        DomainEventStream eventStream;
        if (optionalSnapshot.isPresent()) {
            DomainEventMessage<?> snapshot = optionalSnapshot.get();
            eventStream = DomainEventStream.concat(DomainEventStream.of(snapshot),
                                                   storageEngine.readEvents(aggregateIdentifier,
                                                                            snapshot.getSequenceNumber() + 1));
        } else {
            eventStream = storageEngine.readEvents(aggregateIdentifier);
        }

        Stream<? extends DomainEventMessage<?>> domainEventMessages = stagedDomainEventMessages(aggregateIdentifier);
        return DomainEventStream.concat(eventStream, DomainEventStream.of(domainEventMessages));
    }

    /**
     * Invoked when an error ({@link Exception} or {@link LinkageError}) occurs while attempting to read a snapshot
     * event. This method can be overridden to change the default behavior, which is to log the exception (warn level)
     * and ignore the snapshot.
     * <p>
     * Overriding implementations may choose to return normally, or raise an exception. Exceptions raised from this
     * method are propagated to the caller of the {@link #readEvents(String)} or {@link #readEvents(String, long)}
     * methods.
     * <p>
     * Returning an empty Optional will force the initialization of the aggregate to happen based on the entire event
     * stream of that aggregate.
     *
     * @param aggregateIdentifier The identifier of the aggregate for which an snapshot failed to load
     * @param e                   The exception or error that occurred while loading or deserializing the snapshot
     * @return An optional DomainEventMessage to use as the snapshot for this aggregate
     *
     * @throws RuntimeException any runtimeException to fail loading the
     */
    protected Optional<DomainEventMessage<?>> handleSnapshotReadingError(String aggregateIdentifier, Throwable e) {
        logger.warn("Error reading snapshot for aggregate [{}]. Reconstructing from entire event stream.",
                    aggregateIdentifier, e);
        return Optional.empty();
    }

    /**
     * Returns a Stream of all DomainEventMessages that have been staged for publication by an Aggregate with given
     * {@code aggregateIdentifier}.
     *
     * @param aggregateIdentifier The identifier of the aggregate to get staged events for
     * @return a Stream of DomainEventMessage of the identified aggregate
     */
    protected Stream<? extends DomainEventMessage<?>> stagedDomainEventMessages(String aggregateIdentifier) {
        return queuedMessages().stream()
                               .filter(m -> m instanceof DomainEventMessage)
                               .map(m -> (DomainEventMessage<?>) m)
                               .filter(m -> aggregateIdentifier.equals(m.getAggregateIdentifier()));
    }

    @Override
    public DomainEventStream readEvents(@Nonnull String aggregateIdentifier, long firstSequenceNumber) {
        return DomainEventStream.concat(storageEngine.readEvents(aggregateIdentifier, firstSequenceNumber),
                                        DomainEventStream.of(
                                                stagedDomainEventMessages(aggregateIdentifier)
                                                        .filter(m -> m.getSequenceNumber() >= firstSequenceNumber)));
    }

    @Override
    public void storeSnapshot(@Nonnull DomainEventMessage<?> snapshot) {
        storageEngine.storeSnapshot(snapshot);
    }

    /**
     * Returns the {@link LegacyEventStorageEngine} used by the event store.
     *
     * @return The event storage engine used by this event store
     */
    protected LegacyEventStorageEngine storageEngine() {
        return storageEngine;
    }

    @Override
    public Optional<Long> lastSequenceNumberFor(String aggregateIdentifier) {
        Optional<Long> highestStaged = stagedDomainEventMessages(aggregateIdentifier)
                .map(DomainEventMessage::getSequenceNumber)
                .max(Long::compareTo);
        if (highestStaged.isPresent()) {
            return highestStaged;
        }
        return storageEngine.lastSequenceNumberFor(aggregateIdentifier);
    }

    @Override
    public TrackingToken createTailToken() {
        return storageEngine.createTailToken();
    }

    @Override
    public TrackingToken createHeadToken() {
        return storageEngine.createHeadToken();
    }

    @Override
    public TrackingToken createTokenAt(Instant dateTime) {
        return storageEngine.createTokenAt(dateTime);
    }

    /**
     * Abstract Builder class to instantiate an {@link AbstractLegacyEventStore}.
     * <p>
     * The {@link MessageMonitor} is defaulted to an {@link NoOpMessageMonitor} and the
     * {@link org.axonframework.eventhandling.EventBusSpanFactory} defaults to a
     * {@link org.axonframework.eventhandling.DefaultEventBusSpanFactory} backed by a {@link NoOpSpanFactory}. The
     * {@link LegacyEventStorageEngine} is a
     * <b>hard requirement</b> and as such should be provided.
     */
    public abstract static class Builder extends AbstractEventBus.Builder {

        protected LegacyEventStorageEngine storageEngine;

        @Override
        public Builder messageMonitor(@Nonnull MessageMonitor<? super EventMessage<?>> messageMonitor) {
            super.messageMonitor(messageMonitor);
            return this;
        }

        /**
         * Sets the {@link LegacyEventStorageEngine} used to store and load events.
         *
         * @param storageEngine the {@link LegacyEventStorageEngine} used to store and load events
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder storageEngine(LegacyEventStorageEngine storageEngine) {
            assertNonNull(storageEngine, "EventStorageEngine may not be null");
            this.storageEngine = storageEngine;
            return this;
        }

        /**
         * Validates whether the fields contained in this Builder are set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        @Override
        protected void validate() throws AxonConfigurationException {
            super.validate();
            assertNonNull(storageEngine, "The EventStorageEngine is a hard requirement and should be provided");
        }
    }
}
