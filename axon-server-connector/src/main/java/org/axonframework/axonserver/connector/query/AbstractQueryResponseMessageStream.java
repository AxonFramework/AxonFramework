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
        return Optional.ofNullable(error.get()).or(stream::getError);
    }

    @Override
    public boolean isCompleted() {
        return error.get() != null || stream.isClosed();
    }

    @Override
    public boolean hasNextAvailable() {
        return error.get() == null && stream.peek() != null;
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
