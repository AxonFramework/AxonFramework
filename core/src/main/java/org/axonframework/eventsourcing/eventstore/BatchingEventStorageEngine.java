/*
 * Copyright (c) 2010-2016. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.transaction.Transaction;
import org.axonframework.common.transaction.TransactionIsolationLevel;
import org.axonframework.common.transaction.TransactionManager;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

/**
 * @author Rene de Waele
 */
public abstract class BatchingEventStorageEngine extends AbstractEventStorageEngine {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private final TransactionManager transactionManager;
    private int batchSize = DEFAULT_BATCH_SIZE;

    protected BatchingEventStorageEngine(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    protected abstract List<? extends TrackedEventData<?>> fetchTrackedEvents(TrackingToken lastToken, int batchSize);

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
        EventStreamSpliterator<? extends TrackedEventData<?>> spliterator = new EventStreamSpliterator<>(lastItem -> {
            List<? extends TrackedEventData<?>> result =
                    fetchTrackedEvents(lastItem == null ? trackingToken : lastItem.trackingToken(), batchSize);
            if (containsGaps(trackingToken, result)) {
                result = ensureNoGaps(trackingToken, result);
            }
            return result;
        }, batchSize, true);
        return StreamSupport.stream(spliterator, false);
    }

    protected boolean containsGaps(TrackingToken lastToken, List<? extends TrackedEventData<?>> entries) {
        if (entries.isEmpty()) {
            return false;
        }
        Iterator<? extends TrackedEventData<?>> iterator = entries.iterator();
        TrackingToken previousToken = lastToken;
        if (previousToken == null) {
            previousToken = iterator.next().trackingToken();
        }
        while (iterator.hasNext()) {
            TrackingToken nextToken = iterator.next().trackingToken();
            if (!previousToken.isGuaranteedNext(nextToken)) {
                return true;
            }
            previousToken = nextToken;
        }
        return false;
    }

    protected List<? extends TrackedEventData<?>> ensureNoGaps(TrackingToken lastToken,
                                                               List<? extends TrackedEventData<?>> batch) {
        if (batch.isEmpty()) {
            return batch;
        }
        Transaction transaction = transactionManager.startTransaction(TransactionIsolationLevel.READ_UNCOMMITTED);
        try {
            List<TrackingToken> existingTokens =
                    fetchTokenRange(getTokenForGapDetection(lastToken), batch.get(batch.size() - 1).trackingToken());
            List<? extends TrackedEventData<?>> result = batch;
            Iterator<TrackingToken> existingTokenIterator = existingTokens.iterator();
            for (int i = 0; i < batch.size(); i++) {
                if (!existingTokenIterator.hasNext()
                        || !batch.get(i).trackingToken().equals(existingTokenIterator.next())) {
                    result = batch.subList(0, i);
                    break;
                }
            }
            transaction.commit();
            return result;
        } catch (Throwable e) {
            transaction.rollback();
            throw e;
        }
    }

    protected abstract TrackingToken getTokenForGapDetection(TrackingToken token);

    protected List<TrackingToken> fetchTokenRange(TrackingToken lastToken, TrackingToken end) {
        List<TrackingToken> result = new ArrayList<>();
        while (end.isAfter(lastToken)) {
            result.addAll(
                    fetchTrackedEvents(lastToken, (int) (batchSize * 1.1)).stream().map(TrackedEventData::trackingToken)
                            .filter(token -> !token.isAfter(end)).collect(toList()));
            if (!result.isEmpty()) {
                lastToken = result.get(result.size() - 1);
            }
        }
        return result;
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

    public int batchSize() {
        return batchSize;
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
