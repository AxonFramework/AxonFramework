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

import io.axoniq.axonserver.connector.ReplyChannel;
import io.axoniq.axonserver.grpc.query.QueryResponse;
import jakarta.annotation.Nonnull;
import org.axonframework.common.annotations.Internal;
import org.axonframework.queryhandling.QueryResponseMessage;

/**
 * An implementation of the {@link StreamableResponse} that streams the whole result at once.
 *
 * @author Milan Savic
 * @author Stefan Dragisic
 * @since 4.6.0
 */
@Internal
class StreamableInstanceResponse implements StreamableResponse {

    private final QueryResponseMessage result;
    private final ReplyChannel<QueryResponse> responseHandler;
    // TODO #3488 - Use QueryConverter (that is styled after CommandConverter) to convert the QueryRequest to a QueryMessage
//    private final QuerySerializer serializer;
    private final String requestId;
    private volatile boolean cancelled = false;

    /**
     * Instantiates this streamable instance result.
     *
     * @param result          the result to be streamed
     * @param responseHandler the {@link ReplyChannel} used for sending the result to the Axon Server
     * @param requestId       the identifier of the request these responses refer to
     */
    public StreamableInstanceResponse(QueryResponseMessage result,
                                      ReplyChannel<QueryResponse> responseHandler,
                                      String requestId) {
        this.result = result;
        this.responseHandler = responseHandler;
        this.requestId = requestId;
    }

    @Override
    public void request(long requested) {
        if (requested <= 0 || cancelled) {
            return;
        }
        // TODO #3488 - Use QueryConverter (that is styled after CommandConverter) to convert the QueryRequest to a QueryMessage
//        responseHandler.sendLast(serializer.serializeResponse(result, requestId));
    }

    @Override
    public void cancel() {
        responseHandler.complete();
        cancelled = true;
    }
}
