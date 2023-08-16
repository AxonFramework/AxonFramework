/*
 * Copyright (c) 2010-2023. Axon Framework
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
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.MetaData;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryResponseMessage;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Collections.emptyIterator;

/**
 * An implementation of the {@link StreamableResponse} that can stream a {@link List} of results one by one.
 *
 * @param <T> The type of result to be streamed
 * @author Milan Savic
 * @author Stefan Dragisic
 * @since 4.6.0
 */
class StreamableMultiInstanceResponse<T> implements StreamableResponse {

    private final QueryResponseMessage<List<T>> resultMessage;
    private final Class<T> responseType;
    private final Iterator<T> result;
    private final ReplyChannel<QueryResponse> responseHandler;
    private final QuerySerializer serializer;
    private final String requestId;

    private final AtomicLong requestedRef = new AtomicLong();
    private final AtomicBoolean flowGate = new AtomicBoolean();
    private final AtomicBoolean firstResponseToBeSent = new AtomicBoolean(true);
    private volatile boolean cancelled = false;

    /**
     * Initializing the streamable multi instance result.
     *
     * @param resultMessage   the result message which payload should be streamed
     * @param responseType    the type of single item that needs to be streamed
     * @param responseHandler the {@link ReplyChannel} used for sending items to the Axon Server
     * @param serializer      the serializer used to serialize items
     * @param requestId       the identifier of the request these responses refer to
     */
    public StreamableMultiInstanceResponse(QueryResponseMessage<List<T>> resultMessage,
                                           Class<T> responseType,
                                           ReplyChannel<QueryResponse> responseHandler,
                                           QuerySerializer serializer,
                                           String requestId) {
        this.resultMessage = resultMessage;
        this.responseType = responseType;
        this.responseHandler = responseHandler;
        this.serializer = serializer;
        this.requestId = requestId;
        List<T> payload = resultMessage.getPayload();
        this.result = payload != null ? payload.iterator() : emptyIterator();
    }

    @Override
    public void request(long requested) {
        requestedRef.getAndUpdate(current -> {
            try {
                return Math.addExact(requested, current);
            } catch (ArithmeticException e) {
                return Long.MAX_VALUE;
            }
        });
        stream();
    }

    @Override
    public void cancel() {
        responseHandler.complete();
        cancelled = true;
    }

    private void stream() {
        do {
            if (!flowGate.compareAndSet(false, true)) {
                return;
            }
            try {
                while (requestedRef.get() > 0
                        && result.hasNext()
                        && !cancelled) {
                    send();
                    requestedRef.decrementAndGet();
                }
                if (!result.hasNext()) {
                    responseHandler.complete();
                }
            } finally {
                flowGate.set(false);
            }
        } while (requestedRef.get() > 0 && result.hasNext() && !cancelled);
    }

    private void send() {
        GenericMessage<?> delegate;
        if (firstResponseToBeSent.compareAndSet(true, false)) {
            delegate = new GenericMessage<>(resultMessage.getIdentifier(),
                                            responseType,
                                            result.next(),
                                            resultMessage.getMetaData());
        } else {
            delegate = new GenericMessage<>(responseType,
                                            result.next(),
                                            MetaData.emptyInstance());
        }
        GenericQueryResponseMessage<?> message = new GenericQueryResponseMessage<>(delegate);
        responseHandler.send(serializer.serializeResponse(message, requestId));
    }
}
