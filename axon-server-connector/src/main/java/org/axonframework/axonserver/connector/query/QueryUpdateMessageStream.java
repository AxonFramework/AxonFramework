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
import io.axoniq.axonserver.grpc.query.QueryUpdate;
import jakarta.annotation.Nonnull;
import org.axonframework.common.AxonException;
import org.axonframework.messaging.queryhandling.QueryResponseMessage;

import static org.axonframework.axonserver.connector.query.QueryConverter.convertQueryUpdate;
import static org.axonframework.axonserver.connector.util.ExceptionConverter.convertToAxonException;

/**
 * A specialized implementation of {@link AbstractQueryResponseMessageStream} that processes a stream of
 * {@link QueryUpdate} objects and transforms them into {@link QueryResponseMessage} instances. This class is used to
 * handle query update, including error handling and response message creation.
 * <p/>
 * This class relies on its abstract superclass to manage the underlying {@link ResultStream}, implementing
 * functionality specific to {@link QueryUpdate} to determine whether a message represents an error and to transform
 * such update into structured responses or exceptions.
 */
public class QueryUpdateMessageStream extends AbstractQueryResponseMessageStream<QueryUpdate> {

    /**
     * Initializes a new instance of the {@code QueryResponseMessageStream} which wraps a {@link ResultStream} of
     * {@link QueryUpdate} objects.
     *
     * @param stream the {@link ResultStream} of {@link QueryUpdate} instances to be wrapped; must not be null. If
     *               {@code null}, a {@link NullPointerException} will be thrown.
     */
    public QueryUpdateMessageStream(@Nonnull ResultStream<QueryUpdate> stream) {
        super(stream);
    }

    @Nonnull
    @Override
    protected QueryResponseMessage buildResponseMessage(@Nonnull QueryUpdate queryUpdate) {
        return convertQueryUpdate(queryUpdate);
    }


    @Nonnull
    @Override
    protected AxonException createAxonException(@Nonnull QueryUpdate queryUpdate) {
        return convertToAxonException(queryUpdate.getErrorCode(),
                                      queryUpdate.getErrorMessage(),
                                      queryUpdate.getPayload());
    }

    @Override
    protected boolean isError(@Nonnull QueryUpdate queryUpdate) {
        return queryUpdate.hasErrorMessage();
    }
}
