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

import java.util.Objects;
import java.util.Optional;

/**
 * @author Steven van Beelen
 * @since 5.0.0
 */
@Internal
public class QueryResponseMessageStream implements MessageStream<QueryResponseMessage> {

    private final ResultStream<QueryResponse> stream;

    /**
     *
     * @param stream
     */
    public QueryResponseMessageStream(@Nonnull ResultStream<QueryResponse> stream) {
        this.stream = Objects.requireNonNull(stream, "The query result stream cannot be null.");
    }

    @Override
    public Optional<Entry<QueryResponseMessage>> next() {
        QueryResponse queryResponse = stream.nextIfAvailable();
        return queryResponse == null
                ? Optional.empty()
                : Optional.of(new SimpleEntry<>(QueryConverter.convertQueryResponse(queryResponse), Context.empty()));
    }

    @Override
    public Optional<Entry<QueryResponseMessage>> peek() {
        QueryResponse queryResponse = stream.peek();
        return queryResponse == null
                ? Optional.empty()
                : Optional.of(new SimpleEntry<>(QueryConverter.convertQueryResponse(queryResponse), Context.empty()));
    }

    @Override
    public void onAvailable(@Nonnull Runnable callback) {
        stream.onAvailable(callback);
    }

    @Override
    public Optional<Throwable> error() {
        return stream.getError();
    }

    @Override
    public boolean isCompleted() {
        return stream.isClosed();
    }

    @Override
    public boolean hasNextAvailable() {
        return stream.peek() != null;
    }

    @Override
    public void close() {
        stream.close();
    }
}
