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

package org.axonframework.common.stream;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Interface for a stream that can be polled for information using (optionally blocking) pull operations.
 *
 * @param <M> the type of {@link org.axonframework.messaging.Message} contained in this stream
 * @author Rene de Waele
 * @author Allard Buijze
 * @since 3.0
 */
public interface BlockingStream<M> extends AutoCloseable {

    /**
     * Checks whether or not the next message in the stream is available. If so this method returns {@code true}
     * immediately. If not it returns {@code false} immediately.
     * <p>
     * Note that if {@code this} {@link BlockingStream} has only recently been constructed, the chance is high there are
     * <b>no</b> events present yet. Hence, it is recommended to use {@link #hasNextAvailable(int, TimeUnit)} with a
     * reasonable time window or {@link #setOnAvailableCallback(Runnable)} instead.
     *
     * @return true if a message is available or becomes available before the given timeout, false otherwise
     */
    default boolean hasNextAvailable() {
        try {
            return hasNextAvailable(0, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Checks whether or not the next message in the stream is immediately available. If so, an Optional with the next
     * message is returned (without moving the stream pointer), otherwise an empty Optional is returned.
     *
     * @return the next event if immediately available
     */
    Optional<M> peek();

    /**
     * Checks whether or not the next message in the stream is available. If a message is available when this method is
     * invoked this method returns immediately. If not, this method will block until a message becomes available,
     * returning {@code true} or until the given {@code timeout} expires, returning {@code false}.
     * <p>
     * To check if the stream has messages available now, pass a zero {@code timeout}.
     *
     * @param timeout the maximum number of time units to wait for messages to become available
     * @param unit    the time unit for the timeout
     * @return true if a message is available or becomes available before the given timeout, false otherwise
     * @throws InterruptedException when the thread is interrupted before the indicated time is up
     */
    boolean hasNextAvailable(int timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Returns the next available message in the stream. Note that this method blocks for as long as there are no
     * available messages in the stream. In case this blocking behavior is not desired use {@link #hasNextAvailable}
     * with or without a timeout to check if the stream has available messages before calling this method.
     *
     * @return the next available message
     * @throws InterruptedException when the thread is interrupted before the next message is returned
     */
    M nextAvailable() throws InterruptedException;

    @Override
    void close();

    /**
     * Returns this MessageStream as a {@link Stream} of Messages. Note that the returned Stream will start at the
     * current position of this instance.
     * <p>
     * Note that iterating over the returned Stream may affect this MessageStream and vice versa. It is therefore not
     * recommended to use this MessageStream after invoking this method.
     *
     * @return This MessageStream as a Stream of Messages
     */
    default Stream<M> asStream() {
        return StreamUtils.asStream(this);
    }

    /**
     * Report the stream that a specific message was ignored by the consumer. Stream implementation can use this
     * information for instance to filter messages with the same type of payload.
     *
     * @param ignoredMessage the message containing the payload to exclude from the stream
     */
    default void skipMessagesWithPayloadTypeOf(M ignoredMessage) {
    }

    /**
     * Set a {@code callback} to be invoked once new messages are available on this stream. Returns {@code true} if this
     * functionality is supported and {@code false} otherwise. When {@code true} is returned, the callee can expect the
     * {@code callback} to be invoked immediately.
     * <p>
     * Note that returning {@code false} does not define the given {@code callback} is never invoked. If the callee
     * needs to be certain the {@code callback} is never invoked in case of {@code false}, a no-op {@link Runnable}
     * should be provided.
     *
     * @param callback a {@link Runnable}
     * @return {@code true} if on available callback is supported and can thus be waited on, {@code false otherwise}
     */
    default boolean setOnAvailableCallback(Runnable callback) {
        return false;
    }
}
