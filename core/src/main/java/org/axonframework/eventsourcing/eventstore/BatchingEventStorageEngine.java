/*
 * Copyright (c) 2010-2016. Axon Framework
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * @author Rene de Waele
 */
public abstract class BatchingEventStorageEngine extends AbstractEventStorageEngine {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private int batchSize = DEFAULT_BATCH_SIZE;

    /**
     * The returned iterator will be closed if the resulting stream is closed.
     */
    protected abstract List<? extends TrackedEventData<?>> fetchBatch(TrackingToken lastToken, int batchSize);

    /**
     * Fetch a batch of events published by an aggregate with given {@code aggregateIdentifier}.
     * <p/>
     * The sequence numbers in the returned batch should be ordered by sequence number. The first event in the batch
     * should have a sequence number equal to or larger than given {@code firstSequenceNumber}. Implementations should
     * make sure the returned batch does not contain gaps between events due to uncommitted storage transactions.
     * <p/>
     * If the returned number of entries is smaller than the given {@code batchSize} it is assumed that the storage
     * holds no further applicable entries.
     */
    protected abstract List<? extends DomainEventData<?>> fetchBatch(String aggregateIdentifier,
                                                                     long firstSequenceNumber, int batchSize);

    @Override
    protected Stream<? extends DomainEventData<?>> readEventData(String identifier, long firstSequenceNumber) {
        EventStreamSpliterator<? extends DomainEventData<?>> spliterator = new EventStreamSpliterator<>(
                lastItem -> fetchBatch(identifier,
                                       lastItem == null ? firstSequenceNumber : lastItem.getSequenceNumber() + 1,
                                       batchSize), batchSize);
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
                lastItem -> fetchBatch(lastItem == null ? trackingToken : lastItem.trackingToken(), batchSize),
                batchSize);
        return StreamSupport.stream(spliterator, false);
    }

    /**
     * Sets the number of events that should be read at each database access. When more than this number of events must
     * be read to rebuild an aggregate's state, the events are read in batches of this size. Defaults to 100.
     * <p/>
     * Tip: if you use a snapshotter, make sure to choose snapshot trigger and batch size such that a single batch will
     * generally retrieve all events required to rebuild an aggregate's state.
     *
     * @param batchSize the number of events to read on each database access. Defaults to 100.
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    private static class EventStreamSpliterator<T> extends Spliterators.AbstractSpliterator<T> {

        private final Function<T, List<? extends T>> fetchFunction;
        private final int batchSize;
        private Iterator<? extends T> iterator;
        private T lastItem;
        private int lastBatchSize;

        private EventStreamSpliterator(Function<T, List<? extends T>> fetchFunction, int batchSize) {
            super(Long.MAX_VALUE, NONNULL | ORDERED | DISTINCT | CONCURRENT);
            this.fetchFunction = fetchFunction;
            this.batchSize = batchSize;
        }

        @Override
        public boolean tryAdvance(Consumer<? super T> action) {
            Objects.requireNonNull(action);
            if (iterator == null || !iterator.hasNext()) {
                if (iterator != null && batchSize > lastBatchSize) {
                    return false;
                }
                List<? extends T> items = fetchFunction.apply(lastItem);
                iterator = items.iterator();
                if ((lastBatchSize = items.size()) == 0) {
                    return false;
                }
            }
            action.accept(lastItem = iterator.next());
            return true;
        }
    }
}
