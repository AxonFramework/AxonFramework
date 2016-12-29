/*
 * Copyright (c) 2010-2016. Axon Framework
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

package org.axonframework.mongo.eventsourcing.eventstore;

import org.axonframework.eventsourcing.eventstore.TrackingToken;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static java.util.Collections.unmodifiableSet;

/**
 * Tracking token implementation produced by the {@link MongoEventStorageEngine} to keep track of the position in an
 * event stream. Order is determined by comparing timestamp, sequence number and event id.
 * <p>
 * This tracking token implementation keeps track of all events retrieved in a period of time before the furthest
 * position in the stream. This makes it possible to detect event entries that have a lower timestamp than that
 * with the highest timestamp but are published at a later time (due to time differences between nodes).
 *
 * @author Rene de Waele
 */
public class MongoTrackingToken implements TrackingToken {

    private final long timestamp;
    private final Map<String, Long> trackedEvents;

    /**
     * Returns a new instance of a {@link MongoTrackingToken} with given {@code timestamp}, {@code eventIdentifier} and
     * {@code sequenceNumber} for the initial event in a stream.
     *
     * @param timestamp       the event's timestamp
     * @param eventIdentifier the event's identifier
     * @return initial Mongo tracking token instance
     */
    public static MongoTrackingToken of(Instant timestamp, String eventIdentifier) {
        return new MongoTrackingToken(timestamp.toEpochMilli(),
                                      Collections.singletonMap(eventIdentifier, timestamp.toEpochMilli()));
    }

    private MongoTrackingToken(long timestamp, Map<String, Long> trackedEvents) {
        this.timestamp = timestamp;
        this.trackedEvents = trackedEvents;
    }

    /**
     * Returns a new {@link MongoTrackingToken} instance based on this token but which has advanced to the event with
     * given {@code timestamp}, {@code eventIdentifier} and {@code sequenceNumber}. Prior events with a timestamp
     * smaller or equal than the latest event timestamp minus the given {@code lookBackTime} will not be included in the
     * new token.
     *
     * @param timestamp       the timestamp of the next event
     * @param eventIdentifier the maximum distance between a gap and the token's index
     * @param lookBackTime    the maximum time between the latest and oldest event stored in the new key
     * @return the new token that has advanced from the current token
     */
    public MongoTrackingToken advanceTo(Instant timestamp, String eventIdentifier, Duration lookBackTime) {
        if (trackedEvents.containsKey(eventIdentifier)) {
            throw new IllegalArgumentException(
                    String.format("The event to advance to [%s] should not be one of the token's known events",
                                  eventIdentifier));
        }
        long millis = timestamp.toEpochMilli();
        LinkedHashMap<String, Long> trackedEvents = new LinkedHashMap<>(this.trackedEvents);
        trackedEvents.put(eventIdentifier, millis);
        long newTimestamp = Math.max(millis, this.timestamp);
        return new MongoTrackingToken(newTimestamp, trim(trackedEvents, newTimestamp, lookBackTime));
    }

    private Map<String, Long> trim(LinkedHashMap<String, Long> priorEvents, long currentTime, Duration lookBackTime) {
        Long cutOffTimestamp = currentTime - lookBackTime.toMillis();
        Iterator<Long> iterator = priorEvents.values().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().compareTo(cutOffTimestamp) < 0) {
                iterator.remove();
            } else {
                return priorEvents;
            }
        }
        return priorEvents;
    }

    /**
     * Get the timestamp of the last event tracked by this token.
     *
     * @return the timestamp of the event with this token
     */
    public Instant getTimestamp() {
        return Instant.ofEpochMilli(timestamp);
    }

    /**
     * Returns an {@link Iterable} with all known identifiers of events tracked before and including this token. Note,
     * the token only stores ids of prior events if they are not too old, see
     * {@link #advanceTo(Instant, String, Duration)}.
     *
     * @return all known event identifiers
     */
    public Set<String> getKnownEventIds() {
        return unmodifiableSet(trackedEvents.keySet());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MongoTrackingToken that = (MongoTrackingToken) o;
        return timestamp == that.timestamp && Objects.equals(trackedEvents, that.trackedEvents);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, trackedEvents);
    }

    @Override
    public String toString() {
        return "MongoTrackingToken{" + "timestamp=" + timestamp + ", trackedEvents=" + trackedEvents + '}';
    }
}
