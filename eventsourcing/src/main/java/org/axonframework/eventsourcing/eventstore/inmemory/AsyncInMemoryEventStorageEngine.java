/*
 * Copyright (c) 2010-2024. Axon Framework
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
import org.axonframework.eventhandling.GenericTrackedEventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.eventstore.*;
import org.axonframework.messaging.MessageStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.axonframework.eventsourcing.eventstore.AppendConditionAssertionException.consistencyMarkerSurpassed;
import static org.axonframework.eventsourcing.eventstore.AppendConditionAssertionException.tooManyIndices;
import static org.axonframework.eventsourcing.eventstore.IndexedEventMessage.asIndexedEvent;

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

    private final NavigableMap<Long, TrackedEventMessage<?>> events = new ConcurrentSkipListMap<>();

    private final Clock clock;
    private final long offset;

    /**
     * Initializes an in-memory {@link AsyncEventStorageEngine} using the given {@code clock} for time-based
     * operations.
     * <p>
     * The engine will be empty, and there is no offset for the first token.
     *
     * @param clock The {@link Clock} used for time-based operations, like {@link #tokenSince(Duration)}.
     */
    public AsyncInMemoryEventStorageEngine(@Nonnull Clock clock) {
        this(clock, 0L);
    }

    /**
     * Initializes an in-memory {@link AsyncEventStorageEngine} using given {@code offset} to initialize the tokens with
     * and the given {@code clock} for time-based operations.
     *
     * @param clock  The {@link Clock} used for time-based operations, like {@link #tokenSince(Duration)}.
     * @param offset The value to use for the token of the first event appended.
     */
    public AsyncInMemoryEventStorageEngine(@Nonnull Clock clock,
                                           long offset) {
        this.clock = clock;
        this.offset = offset;
    }

    @Override
    public CompletableFuture<Long> appendEvents(@Nonnull AppendCondition condition,
                                                @Nonnull List<? extends EventMessage<?>> events) {
        int indexCount = condition.criteria().indices().size();
        if (indexCount > 1) {
            return CompletableFuture.failedFuture(tooManyIndices(indexCount, 1));
        }

        synchronized (this.events) {
            long head = this.events.isEmpty() ? -1 : this.events.lastKey();
            List<? extends EventMessage<?>> eventsToAppend;

            if (indexCount != 0) {
                if (this.events.tailMap(condition.consistencyMarker() + 1)
                               .values()
                               .stream()
                               .filter(trackedEvent -> trackedEvent instanceof IndexedEventMessage<?>)
                               .map(trackedEvent -> (IndexedEventMessage<?>) trackedEvent)
                               .anyMatch(taggedEvent -> condition.criteria().matchingIndices(taggedEvent.indices()))) {
                    return CompletableFuture.failedFuture(consistencyMarkerSurpassed(condition.consistencyMarker()));
                }
                eventsToAppend = events.stream()
                                       .map(event -> asIndexedEvent(event, condition.criteria().indices()))
                                       .toList();
            } else {
                eventsToAppend = new ArrayList<>(events);
            }

            for (EventMessage<?> event : eventsToAppend) {
                head++;
                TrackingToken token = new GlobalSequenceTrackingToken(head);
                TrackedEventMessage<?> trackedEvent = asTrackedEventMessage(event, token);
                this.events.put(head, trackedEvent);

                if (logger.isDebugEnabled()) {
                    logger.debug("Appended tracked event [{}] with position [{}] and timestamp [{}].",
                                 trackedEvent.getIdentifier(), head, trackedEvent.getTimestamp());
                }
            }
            return CompletableFuture.completedFuture(head);
        }
    }

    // TODO #3129 - MessageStream allows Pair<TrackingToken, EventMessage> type - Remove this method.
    private static <P> TrackedEventMessage<P> asTrackedEventMessage(EventMessage<P> event, TrackingToken token) {
        return event instanceof IndexedEventMessage<P> taggedEvent
                ? new GenericTrackedAndIndexedEventMessage<>(event, token, taggedEvent.indices())
                : new GenericTrackedEventMessage<>(token, event);
    }

    @Override
    public MessageStream<EventMessage<?>> source(@Nonnull SourcingCondition condition) {
        if (logger.isDebugEnabled()) {
            logger.debug("Start sourcing events with condition [{}].", condition);
        }

        return MessageStream.fromStream(
                eventsToStream(
                        condition.start(),
                        condition.end(),
                        condition.criteria()
                ).map(Function.identity())
        );
    }

    @Override
    public MessageStream<TrackedEventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        if (logger.isDebugEnabled()) {
            logger.debug("Start streaming events with condition [{}].", condition);
        }

        return MessageStream.fromStream(
                eventsToStream(condition.position().position().orElse(-1),
                               Long.MAX_VALUE,
                               condition.criteria())
        );
    }

    private Stream<TrackedEventMessage<?>> eventsToStream(long start,
                                                          long end,
                                                          EventCriteria criteria) {
        return events.subMap(start, end)
                     .values()
                     .stream()
                     .filter(event -> match(event, criteria));
    }

    private static boolean match(TrackedEventMessage<?> event, EventCriteria criteria) {
        // TODO #3085 Remove usage of getPayloadType in favor of QualifiedName solution
        return matchingType(event.getPayloadType().getName(), criteria.types())
                && matchingIndices(event, criteria);
    }

    private static boolean matchingType(String eventName, Set<String> types) {
        return types.isEmpty() || types.contains(eventName);
    }

    private static boolean matchingIndices(TrackedEventMessage<?> trackedEvent, EventCriteria criteria) {
        if (criteria.indices().isEmpty()) {
            // No criteria are present, so we match successfully.
            return true;
        }
        if (trackedEvent instanceof IndexedEventMessage<?> taggedEvent) {
            return criteria.matchingIndices(taggedEvent.indices());
        }
        return false;
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        if (logger.isDebugEnabled()) {
            logger.debug("Operation tailToken() is invoked.");
        }

        return CompletableFuture.completedFuture(
                events.isEmpty()
                        ? new GlobalSequenceTrackingToken(offset - 1)
                        : new GlobalSequenceTrackingToken(events.firstKey() - 1)
        );
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        if (logger.isDebugEnabled()) {
            logger.debug("Operation headToken() is invoked.");
        }

        return CompletableFuture.completedFuture(
                events.isEmpty()
                        ? new GlobalSequenceTrackingToken(offset - 1)
                        : new GlobalSequenceTrackingToken(events.lastKey())
        );
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        if (logger.isDebugEnabled()) {
            logger.debug("Operation tokenAt() is invoked with Instant [{}].", at);
        }

        return events.entrySet()
                     .stream()
                     .filter(positionToEventEntry -> {
                         TrackedEventMessage<?> event = positionToEventEntry.getValue();
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
    public CompletableFuture<TrackingToken> tokenSince(@Nonnull Duration since) {
        if (logger.isDebugEnabled()) {
            logger.debug("Operation tokenSince() is invoked with Duration [{}].", since);
        }

        return tokenAt(clock.instant().minus(since));
    }

    @Override
    public void describeTo(@Nonnull ComponentDescriptor descriptor) {
        descriptor.describeProperty("clock", clock);
        descriptor.describeProperty("offset", offset);
    }
}
