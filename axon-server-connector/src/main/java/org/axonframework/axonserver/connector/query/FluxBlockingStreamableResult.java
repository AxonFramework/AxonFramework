/*
 * Copyright (c) 2010-2022. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.axonserver.connector.query;

import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.responsetypes.FluxResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryResponseMessage;

class FluxBlockingStreamableResult implements StreamableResultProvider {

    private final ResponseType<?> originalResponseType;
    private final ResponseType<?> adaptedResponseType;
    private final QueryResponseMessage<?> result;
    private final ReplyChannel<QueryResponse> responseHandler;
    private final QuerySerializer serializer;
    private final String requestId;

    private final StreamableResultProvider delegate;

    FluxBlockingStreamableResult(ResponseType<?> originalResponseType,
                                 ResponseType<?> adaptedResponseType,
                                 QueryResponseMessage<?> result,
                                 ReplyChannel<QueryResponse> responseHandler,
                                 QuerySerializer serializer, String requestId,
                                 StreamableResultProvider delegate) {
        this.originalResponseType = originalResponseType;
        this.adaptedResponseType = adaptedResponseType;
        this.result = result;
        this.responseHandler = responseHandler;
        this.serializer = serializer;
        this.requestId = requestId;
        this.delegate = delegate;
    }

    @Override
    public StreamableResult provide() {
        if (adaptedResponseType instanceof FluxResponseType) {
            Object payload = originalResponseType.convert(result.getPayload());
            QueryResponseMessage<Object> singleInstancedResponse = new GenericQueryResponseMessage<>(
                    new GenericMessage<>(result.getIdentifier(), payload, result.getMetaData()));
            return new StreamableInstanceResult(singleInstancedResponse, responseHandler, serializer, requestId);
        }
        return delegate.provide();
    }
}
