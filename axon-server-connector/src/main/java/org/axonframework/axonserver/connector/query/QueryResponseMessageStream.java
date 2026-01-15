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

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.connector.ResultStream;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonException;
import org.axonframework.common.annotation.Internal;
import org.axonframework.messaging.core.MessageStream;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;

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
public class QueryResponseMessageStream extends AbstractQueryResponseMessageStream<QueryResponse> {

    /**
     * Initializes a new instance of the {@code QueryResponseMessageStream} which wraps a {@link ResultStream} of
     * {@link QueryResponse} objects.
     *
     * @param stream the {@link ResultStream} of {@link QueryResponse} instances to be wrapped; must not be null. If
     *               {@code null}, a {@link NullPointerException} will be thrown.
     */
    public QueryResponseMessageStream(@Nonnull ResultStream<QueryResponse> stream) {
        super(stream);
    }

    @Nonnull
    @Override
    protected QueryResponseMessage buildResponseMessage(@Nonnull QueryResponse queryResponse) {
        return convertQueryResponse(queryResponse);
    }

    @Nonnull
    @Override
    protected AxonException createAxonException(@Nonnull QueryResponse queryResponse) {
        return convertToAxonException(queryResponse.getErrorCode(),
                                      queryResponse.getErrorMessage(),
                                      queryResponse.getPayload());
    }

    @Override
    protected boolean isError(@Nonnull QueryResponse queryResponse) {
        return queryResponse.hasErrorMessage();
    }
}
