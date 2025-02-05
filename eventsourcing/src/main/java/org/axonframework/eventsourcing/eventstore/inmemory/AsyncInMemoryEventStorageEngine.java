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

package org.axonframework.eventsourcing.eventstore.inmemory;

import jakarta.annotation.Nonnull;
import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.AsyncEventStorageEngine;
import org.axonframework.eventsourcing.eventstore.ConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.EventsCondition;
import org.axonframework.eventsourcing.eventstore.GlobalIndexConsistencyMarker;
import org.axonframework.eventsourcing.eventstore.SourcingCondition;
import org.axonframework.eventsourcing.eventstore.StreamingCondition;
import org.axonframework.eventsourcing.eventstore.Tag;
import org.axonframework.eventsourcing.eventstore.TaggedEventMessage;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.SimpleEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.axonframework.eventsourcing.eventstore.AppendEventsTransactionRejectedException.conflictingEventsDetected;

/**
 * Thread-safe {@link AsyncEventStorageEngine} implementation storing events in memory.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Milan Savić
 * @author Steven van Beelen
 * @since 3.0.0
 */ // TODO Rename to InMemoryEventStorageEngine once fully integrated
public class AsyncInMemoryEventStorageEngine implements AsyncEventStorageEngine {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final NavigableMap<Long, TaggedEventMessage<? extends EventMessage<?>>> eventStorage = new ConcurrentSkipListMap<>();
    private final ReentrantLock appendLock = new ReentrantLock();

    private final Set<MapBackedMessageStream> openStreams = new CopyOnWriteArraySet<>();

    private final long offset;

    /**
     * Initializes an in-memory {@link AsyncEventStorageEngine}.
     * <p>
     * The engine will be empty, and there is no offset for the first token.
     */
    public AsyncInMemoryEventStorageEngine() {
        this(0L);
    }

    /**
     * Initializes an in-memory {@link AsyncEventStorageEngine} using given {@code offset} to initialize the tokens.
     *
     * @param offset The value to use for the token of the first event appended.
     */
    public AsyncInMemoryEventStorageEngine(long offset) {
        this.offset = offset;
    }

    @Override
    public CompletableFuture<AppendTransaction> appendEvents(@Nonnull AppendCondition condition,
                                                             @Nonnull List<TaggedEventMessage<?>> events) {
        int tagCount = condition.criteria().stream().map(c -> c.tags().size()).reduce(0, Integer::max);
        if (tagCount > 1) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Conditions with more than one tag are not yet supported"));
        }
        if (containsConflicts(condition)) {
            // early failure, since we know conflicts already exist at insert-time
            return CompletableFuture.failedFuture(conflictingEventsDetected(condition.consistencyMarker()));
        }

        return CompletableFuture.completedFuture(new AppendTransaction() {

            private final AtomicBoolean finished = new AtomicBoolean(false);

            @Override
            public CompletableFuture<ConsistencyMarker> commit() {
                if (finished.getAndSet(true)) {
                    return CompletableFuture.failedFuture(new IllegalStateException("Already committed or rolled back"));
                }
                appendLock.lock();
                try {
                    if (containsConflicts(condition)) {
                        return CompletableFuture.failedFuture(conflictingEventsDetected(condition.consistencyMarker()));
                    }
                    Optional<ConsistencyMarker> newHead =
                            events.stream()
                                  .map(event -> {
                                      long next = nextIndex();
                                      eventStorage.put(next, event);

                                      if (logger.isDebugEnabled()) {
                                          logger.debug("Appended event [{}] with position [{}] and timestamp [{}].",
                                                       event.event().getIdentifier(),
                                                       next,
                                                       event.event().getTimestamp());
                                      }
                                      return (ConsistencyMarker) new GlobalIndexConsistencyMarker(next);
                                  })
                                  .reduce(ConsistencyMarker::upperBound);

                    openStreams.forEach(m -> m.callback.get().run());
                    return CompletableFuture.completedFuture(newHead.orElse(ConsistencyMarker.ORIGIN));
                } finally {
                    appendLock.unlock();
                }
            }

            @Override
            public void rollback() {
                finished.set(true);
            }
        });
    }

    private long nextIndex() {
        return eventStorage.isEmpty() ? 0 : eventStorage.lastKey() + 1;
    }

    private boolean containsConflicts(AppendCondition condition) {
        if (Objects.equals(condition.consistencyMarker(), ConsistencyMarker.INFINITY)) {
            return false;
        }

        return this.eventStorage.tailMap(GlobalIndexConsistencyMarker.position(condition.consistencyMarker()) + 1)
                                .values()
                                .stream()
                                .map(event -> (TaggedEventMessage<?>) event)
                                .anyMatch(taggedEvent -> condition.matches(taggedEvent.event().type().name(), taggedEvent.tags()));
    }

    @Override
    public MessageStream<EventMessage<?>> source(@Nonnull SourcingCondition condition) {
        if (logger.isDebugEnabled()) {
            logger.debug("Start sourcing events with condition [{}].", condition);
        }

        return eventsToMessageStream(condition.start(),
                                     eventStorage.isEmpty() ? -1 : Math.min(condition.end(), eventStorage.lastKey()),
                                     condition);
    }

    @Override
    public MessageStream<EventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        if (logger.isDebugEnabled()) {
            logger.debug("Start streaming events with condition [{}].", condition);
        }

        return eventsToMessageStream(condition.position().position().orElse(-1) + 1, Long.MAX_VALUE, condition);
    }

    private MessageStream<EventMessage<?>> eventsToMessageStream(long start, long end, EventsCondition condition) {
        MapBackedMessageStream mapBackedMessageStream = new MapBackedMessageStream(start, end, condition);
        openStreams.add(mapBackedMessageStream);
        return mapBackedMessageStream;
    }

    private static boolean match(TaggedEventMessage<?> taggedEvent, EventsCondition condition) {
        String qualifiedName = taggedEvent.event().type().name();
        return condition.matches(qualifiedName, taggedEvent.tags());
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        if (logger.isDebugEnabled()) {
            logger.debug("Operation tailToken() is invoked.");
        }

        return CompletableFuture.completedFuture(
                eventStorage.isEmpty()
                        ? new GlobalSequenceTrackingToken(offset - 1)
                        : new GlobalSequenceTrackingToken(eventStorage.firstKey() - 1)
        );
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        if (logger.isDebugEnabled()) {
            logger.debug("Operation headToken() is invoked.");
        }

        return CompletableFuture.completedFuture(
                eventStorage.isEmpty()
                        ? new GlobalSequenceTrackingToken(offset - 1)
                        : new GlobalSequenceTrackingToken(eventStorage.lastKey())
        );
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        if (logger.isDebugEnabled()) {
            logger.debug("Operation tokenAt() is invoked with Instant [{}].", at);
        }

        return eventStorage.entrySet()
                           .stream()
                           .filter(positionToEventEntry -> {
                               EventMessage<?> event = positionToEventEntry.getValue().event();
                               Instant eventTimestamp = event.getTimestamp();
                               logger.debug("instant [{}]", eventTimestamp);
                               return eventTimestamp.equals(at) || eventTimestamp.isAfter(at);
                           })
                           .map(Map.Entry::getKey)
                           .min(Comparator.comparingLong(Long::longValue))
                           .map(position -> position - 1)
                           .map(GlobalSequenceTrackingToken::new)
                           .map(tt -> (TrackingToken) tt)
                           .map(CompletableFuture::completedFuture)
                           .orElseGet(this::headToken);
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("offset", offset);
    }

    private class MapBackedMessageStream implements MessageStream<EventMessage<?>> {

        private final AtomicLong position;
        private final AtomicReference<Runnable> callback;
        private final long end;
        private final EventsCondition condition;

        public MapBackedMessageStream(long start, long end, EventsCondition condition) {
            this.end = end;
            this.condition = condition;
            position = new AtomicLong(start);
            callback = new AtomicReference<>(() -> {
            });
        }

        @Override
        public Optional<Entry<EventMessage<?>>> next() {
            long currentPosition = position.get();
            while (currentPosition <= end
                    && eventStorage.containsKey(currentPosition)
                    && position.compareAndSet(currentPosition,
                                              currentPosition + 1)) {
                TaggedEventMessage<?> nextEvent = eventStorage.get(currentPosition);
                if (match(nextEvent, condition)) {
                    Context context = Context.empty();
                    context = TrackingToken.addToContext(context, new GlobalSequenceTrackingToken(currentPosition));
                    context = Tag.addToContext(context, nextEvent.tags());
                    return Optional.of(new SimpleEntry<>(nextEvent.event(), context));
                }
                currentPosition = position.get();
            }
            return Optional.empty();
        }

        @Override
        public void onAvailable(@Nonnull Runnable callback) {
            this.callback.set(callback);
            if (eventStorage.containsKey(position.get())) {
                callback.run();
            }
        }

        @Override
        public Optional<Throwable> error() {
            return Optional.empty();
        }

        @Override
        public boolean isCompleted() {
            long currentPosition = position.get();
            return currentPosition > end;
        }

        @Override
        public boolean hasNextAvailable() {
            long currentPosition = position.get();
            return currentPosition <= end && eventStorage.containsKey(currentPosition);
        }

        @Override
        public void close() {
            position.set(end + 1);
        }
    }
}
