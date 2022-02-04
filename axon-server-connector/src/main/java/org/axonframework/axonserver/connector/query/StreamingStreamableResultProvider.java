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
import org.axonframework.messaging.responsetypes.FluxResponseType;
import org.axonframework.messaging.responsetypes.MultipleInstancesResponseType;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.queryhandling.QueryResponseMessage;

class StreamingStreamableResultProvider implements StreamableResultProvider {

    private final boolean supportsStreaming;
    private final ResponseType<?> responseType;
    private final QueryResponseMessage<?> result;
    private final ReplyChannel<QueryResponse> responseHandler;
    private final QuerySerializer serializer;
    private final String clientId;
    private final String requestId;

    private final StreamableResultProvider delegate;

    StreamingStreamableResultProvider(boolean supportsStreaming,
                                      ResponseType<?> responseType,
                                      QueryResponseMessage<?> result,
                                      ReplyChannel<QueryResponse> responseHandler,
                                      QuerySerializer serializer,
                                      String clientId,
                                      String requestId,
                                      StreamableResultProvider delegate) {
        this.supportsStreaming = supportsStreaming;
        this.responseType = responseType;
        this.result = result;
        this.responseHandler = responseHandler;
        this.serializer = serializer;
        this.clientId = clientId;
        this.requestId = requestId;
        this.delegate = delegate;
    }

    @Override
    public StreamableResult provide() {
        if (supportsStreaming) {
            if (responseType instanceof FluxResponseType) {
                return new StreamableFluxResult(result, responseHandler, serializer, requestId, clientId);
            } else if (responseType instanceof MultipleInstancesResponseType) {
                return new StreamableMultiInstanceResult<>(result, responseHandler, serializer, requestId);
            } else {
                return new StreamableInstanceResult(result, responseHandler, serializer, requestId);
            }
        }
        return delegate.provide();
    }
}
