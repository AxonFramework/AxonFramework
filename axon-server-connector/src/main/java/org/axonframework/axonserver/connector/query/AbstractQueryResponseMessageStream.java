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

@Internal
public abstract class AbstractQueryResponseMessageStream<T> implements MessageStream<QueryResponseMessage> {

    private final ResultStream<T> stream;
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private final AtomicReference<Runnable> callback = new AtomicReference<>(() -> {
    });

    public AbstractQueryResponseMessageStream(ResultStream<T> stream) {
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

    private Optional<MessageStream.Entry<QueryResponseMessage>> toEntry(T queryResponse) {
        if (isError(queryResponse)) {
            error.set(createAxonException(queryResponse));
            close();
            callback.get().run();
            return Optional.empty();
        }

        return Optional.of(new SimpleEntry<>(
                buildResponseMessage(queryResponse),
                Context.empty()
        ));
    }

    protected abstract QueryResponseMessage buildResponseMessage(T queryResponse);

    protected abstract AxonException createAxonException(T queryResponse);

    protected abstract boolean isError(T queryResponse);

}
