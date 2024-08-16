package org.axonframework.eventsourcing.eventstore;

import org.axonframework.common.infra.ComponentDescriptor;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GlobalSequenceTrackingToken;
import org.axonframework.eventhandling.TrackedEventMessage;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.messaging.MessageStream;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

import static org.axonframework.eventhandling.EventUtils.asTrackedEventMessage;

/**
 * Thread-safe {@link AsyncEventStorageEngine} implementation storing events in memory.
 *
 * @author Allard Buijze
 * @author Rene de Waele
 * @author Milan Savic
 * @author Steven van Beelen
 * @since 3.0.0
 */
public class AsyncInMemoryEventStorageEngine implements AsyncEventStorageEngine {

    private final NavigableMap<Long, TrackedEventMessage<?>> events = new ConcurrentSkipListMap<>();

    private final long offset;

    /**
     * Initializes an in-memory {@link AsyncEventStorageEngine}. The engine will be empty, and there is no offset for
     * the first token.
     */
    public AsyncInMemoryEventStorageEngine() {
        this(0L);
    }

    /**
     * Initializes an in-memory {@link AsyncEventStorageEngine} using given {@code offset} to initialize the tokens
     * with.
     *
     * @param offset The value to use for the token of the first event appended.
     */
    public AsyncInMemoryEventStorageEngine(long offset) {
        this.offset = offset;
    }

    @Override
    public CompletableFuture<Long> appendEvents(@Nonnull AppendCondition condition,
                                                @Nonnull List<? extends EventMessage<?>> events) {
        synchronized (this.events) {
            long currentPosition = this.events.lastKey();
            if (condition.position() <= currentPosition) {
                return CompletableFuture.failedFuture(new AppendConditionAssertionException());
            }

            for (int index = 0; index < events.size(); index++) {
                EventMessage<?> event = events.get(index);
                currentPosition += index;
                TrackingToken token = new GlobalSequenceTrackingToken(currentPosition);
                this.events.put(currentPosition, asTrackedEventMessage(event, token));
            }

            return CompletableFuture.completedFuture(this.events.lastKey());
        }
    }

    @Override
    public MessageStream<TrackedEventMessage<?>> source(@Nonnull SourcingCondition condition) {
        return MessageStream.fromStream(eventsToStream(condition.start(),
                                                       condition.end().orElse(Long.MAX_VALUE),
                                                       condition.criteria()));
    }

    @Override
    public MessageStream<TrackedEventMessage<?>> stream(@Nonnull StreamingCondition condition) {
        return MessageStream.fromStream(eventsToStream(condition.position().position().orElse(-1),
                                                       Long.MAX_VALUE,
                                                       condition.criteria()));
    }

    private Stream<TrackedEventMessage<?>> eventsToStream(long start,
                                                          long end,
                                                          EventCriteria criteria) {
        return events.subMap(start, end)
                     .values()
                     .stream()
                     .filter(event -> check(event, criteria));
    }

    private static boolean check(TrackedEventMessage<?> event, EventCriteria criteria) {
        // TODO #3085 Remove usage of getPayloadType in favor of QualifiedName solution
        return isTypeApproved(event.getPayloadType().getName(), criteria.types())
                && isTagsApproved(event, criteria.tags());
    }

    private static boolean isTypeApproved(String eventName, Set<String> types) {
        return types.isEmpty() || types.contains(eventName);
    }

    private static boolean isTagsApproved(TrackedEventMessage<?> event, Set<EventCriteria.Tag> tags) {
        return tags.isEmpty() || tags.stream().reduce(
                true,
                (previousCheck, tag) -> {
                    // TODO this is where I would expect aggregate id validation...but, what else?
                    return previousCheck;
                },
                (previous, next) -> previous && next
        );
    }

    @Override
    public CompletableFuture<TrackingToken> tailToken() {
        return CompletableFuture.completedFuture(
                events.isEmpty()
                        ? new GlobalSequenceTrackingToken(offset - 1)
                        : new GlobalSequenceTrackingToken(events.firstKey() - 1)
        );
    }

    @Override
    public CompletableFuture<TrackingToken> headToken() {
        return CompletableFuture.completedFuture(
                events.isEmpty()
                        ? new GlobalSequenceTrackingToken(offset - 1)
                        : new GlobalSequenceTrackingToken(events.lastKey())
        );
    }

    @Override
    public CompletableFuture<TrackingToken> tokenAt(@Nonnull Instant at) {
        return events.entrySet()
                     .stream()
                     .filter(positionToEventEntry -> {
                         TrackedEventMessage<?> event = positionToEventEntry.getValue();
                         Instant eventTimestamp = event.getTimestamp();
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
        // TODO
    }
}
