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

import org.axonframework.eventhandling.TrackedEventMessage;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Describes a stream of {@link TrackedEventMessage TrackedEventMessages}.
 *
 * @author Rene de Waele
 */
public interface TrackingEventStream extends AutoCloseable {

    /**
     * Checks whether or not the next event message in the stream is available. If so this method returns
     * {@code true} immediately. If not it returns {@code false} immediately.
     *
     * @return true if an event is available or becomes available before the given timeout, false otherwise
     */
    default boolean hasNextAvailable() {
        try {
            return hasNextAvailable(0, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Checks whether or not the next event message in the stream is immediately available. If so, an Optional with
     * the next event is returned (without moving the stream pointer), otherwise an empty Optional is returned.
     *
     * @return the next event if immediately available
     */
    Optional<TrackedEventMessage<?>> peek();

    /**
     * Checks whether or not the next event message in the stream is available. If an event is available when this
     * method is invoked this method returns immediately. If not, this method will block until an event becomes
     * available, returning {@code true} or until the given {@code timeout} expires, returning
     * {@code false}.
     * <p>
     * To check if the stream has events available now, pass a zero {@code timeout}.
     *
     * @param timeout the maximum number of time units to wait for events to become available
     * @param unit    the time unit for the timeout
     * @return true if an event is available or becomes available before the given timeout, false otherwise
     * @throws InterruptedException when the thread is interrupted before the indicated time is up
     */
    boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Returns the next available event message in the stream. Note that this method blocks for as long as there are no
     * available events in the stream. In case this blocking behavior is not desired use {@link #hasNextAvailable} with
     * or without a timeout to check if the stream has available events before calling this method.
     *
     * @return the next available event message
     * @throws InterruptedException when the thread is interrupted before the next event is returned
     */
    TrackedEventMessage<?> nextAvailable() throws InterruptedException;

    @Override
    void close();

    /**
     * Returns this TrackingEventStream as a {@link Stream} of TrackedEventMessages. Note that the returned Stream will
     * start at the current position of the TrackingEventStream.
     * <p>
     * Note that iterating over the returned Stream may affect this TrackingEventStream and vice versa. It is therefore
     * not recommended to use this TrackingEventStream after invoking this method.
     *
     * @return This TrackingEventStream as a Stream of event messages
     */
    default Stream<? extends TrackedEventMessage> asStream() {
        return EventUtils.asStream(this);
    }
}
