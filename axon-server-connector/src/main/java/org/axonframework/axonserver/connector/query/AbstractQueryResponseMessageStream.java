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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.connector.ResultStream;
import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonException;
import org.axonframework.common.annotations.Internal;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.SimpleEntry;
import org.axonframework.queryhandling.QueryResponseMessage;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;

/**
 * An abstract implementation of the {@link MessageStream} interface that wraps a {@link ResultStream}. This class
 * provides functionality for transforming the data in the {@link ResultStream} into {@link QueryResponseMessage}s,
 * handling any encountered errors, and managing stream lifecycle events.
 *
 * @param <T> The type of the objects in the underlying {@link ResultStream} to be transformed into
 *            {@link QueryResponseMessage}s.
 */
@Internal
public abstract class AbstractQueryResponseMessageStream<T> implements MessageStream<QueryResponseMessage> {

    private final ResultStream<T> stream;
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicReference<Runnable> callback = new AtomicReference<>(() -> {
    });

    /**
     * Constructs an instance of the AbstractQueryResponseMessageStream class with the provided result stream.
     *
     * @param stream The {@link ResultStream} instance from which query response data will be fetched. Must not be
     *               null.
     */
    public AbstractQueryResponseMessageStream(@Nonnull ResultStream<T> stream) {
        this.stream = requireNonNull(stream, "The query result stream cannot be null.");
    }

    @Override
    public Optional<MessageStream.Entry<QueryResponseMessage>> next() {
        return Optional.ofNullable(stream.nextIfAvailable()).flatMap(this::toEntry);
    }

    @Override
    public Optional<Entry<QueryResponseMessage>> peek() {
        return Optional.ofNullable(stream.peek()).flatMap(this::toEntry);
    }

    @Override
    public void onAvailable(@Nonnull Runnable callback) {
        this.callback.set(callback);
        stream.onAvailable(callback);
    }

    @Nonnull
    @Override
    public Optional<Throwable> error() {
        // Check if we've already processed and stored an error
        if (error.get() != null) {
            return Optional.of(error.get());
        }
        // Check if the underlying stream has an error
        Optional<Throwable> streamError = stream.getError();
        if (streamError.isPresent()) {
            return streamError;
        }
        // Check if the first peeked message is an error
        T peeked = stream.peek();
        if (peeked != null && isError(peeked)) {
            return Optional.of(createAxonException(peeked));
        }
        return Optional.empty();
    }

    @Override
    public boolean isCompleted() {
        return hasError() || stream.isClosed();
    }

    @Override
    public boolean hasNextAvailable() {
        return !hasError() && stream.peek() != null;
    }

    /**
     * Checks if there is an error in the stream by examining three sources:
     * <ol>
     *     <li>The error that has already been processed and stored</li>
     *     <li>The underlying stream's error state</li>
     *     <li>The next peeked message in the stream (if it's an error message)</li>
     * </ol>
     * A stream which contains an error should be considered completed.
     *
     * @return {@code true} if an error is detected from any source, {@code false} otherwise.
     */
    private boolean hasError() {
        // Check if we've already processed an error
        if (error.get() != null) {
            return true;
        }
        // Check if the underlying stream has an error
        if (stream.getError().isPresent()) {
            return true;
        }
        // Check if the next peeked message is an error
        T peeked = stream.peek();
        return peeked != null && isError(peeked);
    }

    @Override
    public void close() {
        if (!stream.isClosed()) {
            stream.close();
        }
    }

    @Nonnull
    private Optional<MessageStream.Entry<QueryResponseMessage>> toEntry(@Nonnull T t) {
        if (isError(t)) {
            error.set(createAxonException(t));
            close();
            callback.get().run();
            return Optional.empty();
        }

        return Optional.of(new SimpleEntry<>(
                buildResponseMessage(t),
                Context.empty()
        ));
    }

    @Nonnull
    protected abstract QueryResponseMessage buildResponseMessage(@Nonnull T t);

    @Nonnull
    protected abstract AxonException createAxonException(@Nonnull T t);

    protected abstract boolean isError(@Nonnull T t);
}
