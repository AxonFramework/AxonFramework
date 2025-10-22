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
import io.axoniq.axonserver.grpc.query.QueryResponse;
import jakarta.annotation.Nonnull;
import org.axonframework.common.annotations.Internal;
import org.axonframework.messaging.Context;
import org.axonframework.messaging.MessageStream;
import org.axonframework.messaging.SimpleEntry;
import org.axonframework.queryhandling.QueryResponseMessage;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Objects.requireNonNull;
import static org.axonframework.axonserver.connector.query.QueryConverter.convertQueryResponse;
import static org.axonframework.axonserver.connector.util.ExceptionConverter.convertToAxonException;

/**
 * A {@link MessageStream} implementation that wraps an {@link ResultStream} of {@link QueryResponse}s, using
 * {@link QueryConverter}.
 *
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class QueryResponseMessageStream implements MessageStream<QueryResponseMessage> {

    private final ResultStream<QueryResponse> stream;
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicReference<Runnable> callback = new AtomicReference<>(() ->{});

    /**
     * Initializes a new instance of the {@code QueryResponseMessageStream} which wraps a {@link ResultStream} of
     * {@link QueryResponse} objects.
     *
     * @param stream the {@link ResultStream} of {@link QueryResponse} instances to be wrapped; must not be null. If
     *               {@code null}, a {@link NullPointerException} will be thrown.
     */
    public QueryResponseMessageStream(@Nonnull ResultStream<QueryResponse> stream) {
        this.stream = requireNonNull(stream, "The query result stream cannot be null.");
    }

    @Override
    public Optional<Entry<QueryResponseMessage>> next() {
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
        stream.close();
    }

    // TODO: clever name
    private Optional<MessageStream.Entry<QueryResponseMessage>> toEntry(QueryResponse queryResponse) {
        if (queryResponse.hasErrorMessage()) {
            error.set(convertToAxonException(queryResponse.getErrorCode(),
                                             queryResponse.getErrorMessage(),
                                             queryResponse.getPayload()));
            close();
            callback.get().run();
            return Optional.empty();
        }

        return Optional.of(new SimpleEntry<>(
                convertQueryResponse(queryResponse),
                Context.empty()
        ));
    }
}
