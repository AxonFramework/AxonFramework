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
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryResponseMessage;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

class StreamableMultiInstanceResult<T> implements StreamableResult {

    private final QueryResponseMessage<T> resultMessage;
    private final Iterator<T> result;
    private final ReplyChannel<QueryResponse> responseHandler;
    private final QuerySerializer serializer;
    private final String requestId;

    private final AtomicLong requestedRef = new AtomicLong();
    private final AtomicBoolean flowGate = new AtomicBoolean();
    private volatile boolean cancelled = false;

    public StreamableMultiInstanceResult(QueryResponseMessage<T> resultMessage,
                                         ReplyChannel<QueryResponse> responseHandler,
                                         QuerySerializer serializer,
                                         String requestId) {
        this.resultMessage = resultMessage;
        this.responseHandler = responseHandler;
        this.serializer = serializer;
        this.requestId = requestId;
        // TODO: 2/2/22 move to the first request ???
        this.result = resultMessage.getPayload() != null ?
                ((List<T>) resultMessage.getPayload()).iterator() : Collections.emptyIterator();
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
        GenericMessage<?> payload = new GenericMessage<>(resultMessage.getIdentifier(),
                                                         resultMessage.getPayloadType(),
                                                         result.next(),
                                                         resultMessage.getMetaData());
        GenericQueryResponseMessage<?> message = new GenericQueryResponseMessage<>(payload);
        responseHandler.send(serializer.serializeResponse(message, requestId)
                                       .toBuilder()
                                       .setStreamed(true)
                                       .build());
    }
}
