/*
 * Copyright (c) 2010-2026. Axon Framework
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

import jakarta.annotation.Nonnull;
import org.axonframework.common.Registration;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.core.MessageStream;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A {@link MessageStream} implementation that continuously fetches event messages
 * from a configurable data source. This stream has no defined end and will
 * continue retrieving new batches of data as they become available.
 * <p>
 * The stream relies on externally provided functional strategies to control its
 * behavior:
 * <ul>
 *     <li>A {@code fetcher} to obtain the next batch of elements based on the last item fetched.</li>
 *     <li>A {@code converter} to transform fetched elements into {@link Entry} instances.</li>
 *     <li>A {@code callbackTracker} to manage callback registration for new data availability.</li>
 * </ul>
 * The stream supports lifecycle management through {@link #close()} and
 * callback registration via {@link #setCallback(Runnable)}.
 *
 * @param <E> the type of the raw elements returned by the fetcher before conversion to {@link EventMessage}s
 */
@Internal
public final class ContinuousMessageStream<E> implements MessageStream<EventMessage> {
    private final Function<E, List<E>> fetcher;
    private final BiFunction<ContinuousMessageStream<?>, Runnable, Registration> callbackTracker;
    private final Function<E, Entry<EventMessage>> converter;

    private E lastItem;
    private List<E> data = List.of();
    private Entry<EventMessage> nextEntry;
    private Throwable error;
    private int position;  // position within data
    private Registration callbackRegistration;
    private Runnable callback;
    private boolean closed;

    /**
     * Creates a new {@code ContinuousMessageStream} instance configured with the given strategies.
     *
     * @param fetcher          a function that, given the last fetched element (or {@code null} for the first call),
     *                         retrieves the next batch of elements; must not return {@code null}
     * @param converter        a function converting each fetched element into an {@link Entry} containing an {@link EventMessage}
     * @param callbackTracker  a function that, given this stream and a callback {@link Runnable}, registers
     *                         a listener and returns a {@link Registration} allowing it to be canceled
     */
    public ContinuousMessageStream(
        @Nonnull Function<E, List<E>> fetcher,
        @Nonnull Function<E, Entry<EventMessage>> converter,
        @Nonnull BiFunction<ContinuousMessageStream<?>, Runnable, Registration> callbackTracker
    ) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
        this.converter = Objects.requireNonNull(converter, "converter");
        this.callbackTracker = Objects.requireNonNull(callbackTracker, "callbackTracker");
    }

    @Override
    public synchronized void setCallback(Runnable callback) {
        if (!closed) {
            this.callback = callback;

            if (callback == null) {
                callbackRegistration.cancel();
                callbackRegistration = null;
            }
            else if (callbackRegistration == null) {
                this.callbackRegistration = callbackTracker.apply(this, this::invokeCallback);
            }

            invokeCallback();  // safe, it checks for null
        }
    }

    @Override
    public synchronized Optional<Entry<EventMessage>> next() {
        try {
            return peek();
        }
        finally {
            nextEntry = null;
        }
    }

    @Override
    public synchronized Optional<Entry<EventMessage>> peek() {
        if (closed) {
            return Optional.empty();
        }

        if (nextEntry == null) {
            if (position >= data.size()) {
                fetchMore();  // TODO #3854 - ContinuousMessageStream may block in its MessageStream::peek call (and methods that rely on it) which is not allowed

                if (closed || data.isEmpty()) {  // closed may happen here if fetch had an error
                    return Optional.empty();
                }
            }

            E element = data.get(position++);

            nextEntry = converter.apply(element);
        }

        return Optional.of(nextEntry);
    }

    @Override
    public synchronized Optional<Throwable> error() {
        return Optional.ofNullable(error);
    }

    @Override
    public synchronized boolean isCompleted() {
        return error != null;  // an infinite stream only completes when an error occurred
    }

    @Override
    public synchronized boolean hasNextAvailable() {
        return peek().isPresent();
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            data = null;

            if (callbackRegistration != null) {
                invokeCallback();

                callback = null;
                callbackRegistration.cancel();
            }
        }
    }

    private void invokeCallback() {
        try {
            if (callback != null) {
                callback.run();
            }
        }
        catch (Exception e) {
            error = e;
            close();
        }
    }

    private void fetchMore() {
        try {
            this.data = fetcher.apply(lastItem);
            this.position = 0;

            if (!data.isEmpty()) {
                this.lastItem = data.getLast();
            }
        }
        catch (Exception e) {
            error = e;
            close();
        }
    }
}