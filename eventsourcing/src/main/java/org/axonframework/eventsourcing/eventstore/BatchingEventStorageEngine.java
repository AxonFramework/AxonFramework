/*
 * Copyright (c) 2010-2018. Axon Framework
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
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.axonframework.common.BuilderUtils.assertThat;

/**
 * {@link AbstractEventStorageEngine} implementation that fetches events in batches from the backing database.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public abstract class BatchingEventStorageEngine extends AbstractEventStorageEngine {

    private static final int DEFAULT_BATCH_SIZE = 100;

    private final int batchSize;

    /**
     * Instantiate a {@link BatchingEventStorageEngine} based on the fields contained in the {@link Builder}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link BatchingEventStorageEngine} instance
     */
    protected BatchingEventStorageEngine(Builder builder) {
        super(builder);
        this.batchSize = builder.batchSize;
    }

    /**
     * Returns a batch of serialized event data entries in the event storage that have a {@link TrackingToken} greater
     * than the given {@code lastToken}. Event entries in the stream should be ordered by tracking token. If the {@code
     * lastToken} is {@code null} a stream containing all events should be returned.
     * <p>
     * Only if the returned List is empty the event storage assumes that the backing database holds no further
     * applicable entries.
     *
     * @param lastToken Object describing the global index of the last processed event or {@code null} to create a
     *                  stream of all events in the store
     * @param batchSize The maximum number of events that should be returned
     * @return A batch of tracked event messages stored since the given tracking token
     */
    protected abstract List<? extends TrackedEventData<?>> fetchTrackedEvents(TrackingToken lastToken, int batchSize);

    /**
     * Returns a batch of events published by an aggregate with given {@code aggregateIdentifier}.
     * <p/>
     * The sequence numbers in the returned batch should be ordered by sequence number. The first event in the batch
     * should have a sequence number equal to or larger than given {@code firstSequenceNumber}. Implementations should
     * make sure the returned batch does not contain gaps between events due to uncommitted storage transactions.
     * <p/>
     * If the returned number of entries is smaller than the given {@code batchSize} it is assumed that the storage
     * holds no further applicable entries.
     *
     * @param aggregateIdentifier The identifier of the aggregate to open a stream for
     * @param firstSequenceNumber The sequence number of the first excepted event entry
     * @param batchSize           The maximum number of events that should be returned
     * @return a batch of serialized event entries for the given aggregate
     */
    protected abstract List<? extends DomainEventData<?>> fetchDomainEvents(String aggregateIdentifier,
                                                                            long firstSequenceNumber, int batchSize);

    @Override
    protected Stream<? extends DomainEventData<?>> readEventData(String identifier, long firstSequenceNumber) {
        EventStreamSpliterator<? extends DomainEventData<?>> spliterator = new EventStreamSpliterator<>(
                lastItem -> fetchDomainEvents(identifier,
                                              lastItem == null ? firstSequenceNumber : lastItem.getSequenceNumber() + 1,
                                              batchSize), batchSize, false);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation produces non-blocking event streams.
     */
    @Override
    protected Stream<? extends TrackedEventData<?>> readEventData(TrackingToken trackingToken, boolean mayBlock) {
        EventStreamSpliterator<? extends TrackedEventData<?>> spliterator = new EventStreamSpliterator<>(
                lastItem -> fetchTrackedEvents(lastItem == null ? trackingToken : lastItem.trackingToken(), batchSize),
                batchSize, true);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Returns the maximum number of event entries to be fetched per batch.
     *
     * @return the fetch batch size
     */
    public int batchSize() {
        return batchSize;
    }

    /**
     * Abstract Builder class to instantiate a {@link BatchingEventStorageEngine}.
     * <p>
     * This implementation inherits the following defaults: The {@link Serializer} used for snapshots is defaulted to a
     * {@link org.axonframework.serialization.xml.XStreamSerializer}, the {@link EventUpcaster} defaults to a
     * {@link org.axonframework.serialization.upcasting.event.NoOpEventUpcaster}, the Serializer used for events is
     * also defaulted to a XStreamSerializer and the {@code snapshotFilter} defaults to a {@link Predicate} which
     * returns {@code true} regardless.
     * The {@code batchSize} in this Builder implementation is defaulted to an integer of size {@code 100}.
     */
    public abstract static class Builder extends AbstractEventStorageEngine.Builder {

        private int batchSize = DEFAULT_BATCH_SIZE;

        @Override
        public Builder snapshotSerializer(Serializer snapshotSerializer) {
            super.snapshotSerializer(snapshotSerializer);
            return this;
        }

        @Override
        public Builder upcasterChain(EventUpcaster upcasterChain) {
            super.upcasterChain(upcasterChain);
            return this;
        }

        @Override
        public Builder persistenceExceptionResolver(PersistenceExceptionResolver persistenceExceptionResolver) {
            super.persistenceExceptionResolver(persistenceExceptionResolver);
            return this;
        }

        @Override
        public Builder eventSerializer(Serializer eventSerializer) {
            super.eventSerializer(eventSerializer);
            return this;
        }

        @Override
        public Builder snapshotFilter(Predicate<? super DomainEventData<?>> snapshotFilter) {
            super.snapshotFilter(snapshotFilter);
            return this;
        }

        /**
         * Sets the {@code batchSize} specifying the number of events that should be read at each database access. When
         * more than this number of events must be read to rebuild an aggregate's state, the events are read in batches
         * of this size. Defaults to an integer of {@code 100}.
         * <p>
         * Tip: if you use a snapshotter, make sure to choose snapshot trigger and batch size such that a single batch
         * will generally retrieve all events required to rebuild an aggregate's state.
         *
         * @param batchSize an {@code int} specifying the number of events that should be read at each database access
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder batchSize(int batchSize) {
            assertThat(batchSize, size -> size > 0, "The batchSize must be a positive number");
            this.batchSize = batchSize;
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
        }
    }

    private static class EventStreamSpliterator<T> extends Spliterators.AbstractSpliterator<T> {

        private final Function<T, List<? extends T>> fetchFunction;
        private final int batchSize;
        private final boolean fetchUntilEmpty;
        private Iterator<? extends T> iterator;
        private T lastItem;
        private int sizeOfLastBatch;

        private EventStreamSpliterator(Function<T, List<? extends T>> fetchFunction, int batchSize,
                                       boolean fetchUntilEmpty) {
            super(Long.MAX_VALUE, NONNULL | ORDERED | DISTINCT | CONCURRENT);
            this.fetchFunction = fetchFunction;
            this.batchSize = batchSize;
            this.fetchUntilEmpty = fetchUntilEmpty;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action);
            if (iterator == null || !iterator.hasNext()) {
                if (iterator != null && batchSize > sizeOfLastBatch && !fetchUntilEmpty) {
                    return false;
                }
                List<? extends T> items = fetchFunction.apply(lastItem);
                iterator = items.iterator();
                if ((sizeOfLastBatch = items.size()) == 0) {
                    return false;
                }
            }
            action.accept(lastItem = iterator.next());
            return true;
        }
    }
}
