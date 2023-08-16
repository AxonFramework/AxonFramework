/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.common.BuilderUtils;
import org.axonframework.common.jdbc.PersistenceExceptionResolver;
import org.axonframework.eventhandling.DomainEventData;
import org.axonframework.eventhandling.TrackedEventData;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.snapshotting.SnapshotFilter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.upcasting.event.EventUpcaster;
import org.axonframework.serialization.upcasting.event.NoOpEventUpcaster;

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
import static org.axonframework.common.ObjectUtils.getOrDefault;

/**
 * {@link AbstractEventStorageEngine} implementation that fetches events in batches from the backing database.
 *
 * @author Rene de Waele
 * @since 3.0
 */
public abstract class BatchingEventStorageEngine extends AbstractEventStorageEngine {

    private static final int DEFAULT_BATCH_SIZE = 100;
    /**
     * The batch optimization is intended to *not* retrieve a second batch of events to cover for potential gaps in the
     * first batch. This optimization is desirable for aggregate event streams, as these close once the end is reached.
     * For token-based event reading the stream does not necessarily close once reaching the end, thus the optimization
     * will block further event retrieval.
     */
    private static final boolean BATCH_OPTIMIZATION_DISABLED = false;

    private final int batchSize;
    private final Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate;

    /**
     * Instantiate a {@link BatchingEventStorageEngine} based on the fields contained in the {@link Builder}.
     * <p>
     * Will assert that the event and snapshot {@link Serializer} are not {@code null}, and will throw an {@link
     * AxonConfigurationException} if any of them is {@code null}.
     *
     * @param builder the {@link Builder} used to instantiate a {@link BatchingEventStorageEngine} instance
     */
    protected BatchingEventStorageEngine(Builder builder) {
        super(builder);
        this.batchSize = builder.batchSize;
        this.finalAggregateBatchPredicate = getOrDefault(builder.finalAggregateBatchPredicate, this::defaultFinalAggregateBatchPredicate);
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
     *
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
     * holds no further applicable entries. Implementations for which this is not always the case should override
     * {@link #fetchForAggregateUntilEmpty()} to return {@code true} and preferably configure a better
     * {@link Builder#finalAggregateBatchPredicate(Predicate)} to provide a better heuristic for detecting the last
     * batch in a stream.
     *
     * @param aggregateIdentifier The identifier of the aggregate to open a stream for
     * @param firstSequenceNumber The sequence number of the first excepted event entry
     * @param batchSize           The maximum number of events that should be returned
     *
     * @return a batch of serialized event entries for the given aggregate
     */
    protected abstract List<? extends DomainEventData<?>> fetchDomainEvents(String aggregateIdentifier,
                                                                            long firstSequenceNumber, int batchSize);

    /**
     * Specifies whether the {@link #readEventData(String, long)} should proceed fetching events for an aggregate until
     * an empty batch is returned. Defaults to {@code false}, as Aggregate event batches typically do not have gaps in
     * them.
     *
     * @return a {@code boolean} specifying whether {@link #readEventData(String, long)} should proceed fetching events
     * for an aggregate until an empty batch is returned
     */
    protected boolean fetchForAggregateUntilEmpty() {
        return false;
    }

    private boolean defaultFinalAggregateBatchPredicate(List<? extends DomainEventData<?>> recentBatch) {
        return fetchForAggregateUntilEmpty() ? recentBatch.isEmpty() : recentBatch.size() < batchSize;
    }

    @Override
    protected Stream<? extends DomainEventData<?>> readEventData(String identifier, long firstSequenceNumber) {
        EventStreamSpliterator<? extends DomainEventData<?>> spliterator = new EventStreamSpliterator<>(
                lastItem -> fetchDomainEvents(identifier,
                                              lastItem == null ? firstSequenceNumber : lastItem.getSequenceNumber() + 1,
                                              batchSize), finalAggregateBatchPredicate);
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
                batch -> BATCH_OPTIMIZATION_DISABLED
        );
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
     * The {@link EventUpcaster} defaults to a {@link NoOpEventUpcaster}, the {@code snapshotFilter} defaults to a
     * {@link SnapshotFilter#allowAll()} instance and the {@code batchSize} is defaulted to an integer of size {@code
     * 100}.
     * <p>
     * The event and snapshot {@link Serializer} are <b>hard requirements</b> and as such should be provided.
     */
    public abstract static class Builder extends AbstractEventStorageEngine.Builder {

        private int batchSize = DEFAULT_BATCH_SIZE;
        private Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate;

        @Override
        public BatchingEventStorageEngine.Builder snapshotSerializer(Serializer snapshotSerializer) {
            super.snapshotSerializer(snapshotSerializer);
            return this;
        }

        @Override
        public BatchingEventStorageEngine.Builder upcasterChain(EventUpcaster upcasterChain) {
            super.upcasterChain(upcasterChain);
            return this;
        }

        @Override
        public BatchingEventStorageEngine.Builder persistenceExceptionResolver(
                PersistenceExceptionResolver persistenceExceptionResolver
        ) {
            super.persistenceExceptionResolver(persistenceExceptionResolver);
            return this;
        }

        @Override
        public BatchingEventStorageEngine.Builder eventSerializer(Serializer eventSerializer) {
            super.eventSerializer(eventSerializer);
            return this;
        }

        /**
         * Defines the predicate to use to recognize the terminal batch when reading an event stream for an aggregate.
         * The default behavior is implementation-specific.
         *
         * @param finalAggregateBatchPredicate The predicate that indicates whether a given batch is to be considered
         *                                     the final batch of an event stream.
         *
         * @return the current Builder instance, for fluent interfacing
         */
        public BatchingEventStorageEngine.Builder finalAggregateBatchPredicate(Predicate<List<? extends DomainEventData<?>>> finalAggregateBatchPredicate) {
            BuilderUtils.assertNonNull(finalAggregateBatchPredicate, "The finalAggregateBatchPredicate must not be null");
            this.finalAggregateBatchPredicate = finalAggregateBatchPredicate;
            return this;
        }

        /**
         * {@inheritDoc}
         *
         * @deprecated in favor of {@link #snapshotFilter(SnapshotFilter)}
         */
        @Override
        @Deprecated
        public BatchingEventStorageEngine.Builder snapshotFilter(Predicate<? super DomainEventData<?>> snapshotFilter) {
            super.snapshotFilter(snapshotFilter);
            return this;
        }

        @Override
        public BatchingEventStorageEngine.Builder snapshotFilter(SnapshotFilter snapshotFilter) {
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
         *
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
        private final Predicate<List<? extends T>> finalBatchPredicate;

        private Iterator<? extends T> iterator;
        private T lastItem;
        private boolean lastBatchFound;

        private EventStreamSpliterator(Function<T, List<? extends T>> fetchFunction,
                                       Predicate<List<? extends T>> finalBatchPredicate) {
            super(Long.MAX_VALUE, NONNULL | ORDERED | DISTINCT | CONCURRENT);
            this.fetchFunction = fetchFunction;
            this.finalBatchPredicate = finalBatchPredicate;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action);
            if (iterator == null || !iterator.hasNext()) {
                if (lastBatchFound) {
                    return false;
                }
                List<? extends T> items = fetchFunction.apply(lastItem);
                lastBatchFound = finalBatchPredicate.test(items);
                iterator = items.iterator();
            }
            if (!iterator.hasNext()) {
                return false;
            }

            action.accept(lastItem = iterator.next());
            return true;
        }
    }
}
