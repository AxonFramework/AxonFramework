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

import io.axoniq.axonserver.grpc.query.QueryResponse;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryResponseMessage;
import org.axonframework.queryhandling.QueryResponseMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;
import reactor.util.context.Context;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

class StreamingQueryResponseProcessingTask<T> implements PrioritizedRunnable {


    public static final String MESSAGE_IDENTIFIER = "__message_identifier";
    public static final String MESSAGE_METADATA = "__message_metadata";
    private final Flux<QueryResponse> responseFlux;
    private final CompletableFuture<QueryResponseMessage<T>> queryTransaction;
    private final QuerySerializer serializer;
    private final ResponseType<T> responseType;
    private final long priority;

    public StreamingQueryResponseProcessingTask(Flux<QueryResponse> queryResult,
                                                CompletableFuture<QueryResponseMessage<T>> queryTransaction,
                                                QuerySerializer serializer,
                                                ResponseType<T> responseType,
                                                long priority) {
        this.responseFlux = queryResult;
        this.responseType = responseType;
        this.queryTransaction = queryTransaction;
        this.serializer = serializer;
        this.priority = priority;
    }

    @Override
    public void run() {
        //noinspection unchecked
        T payloadFlux = (T) responseFlux.map(this::deserialize)
                                        .switchOnFirst(this::onFirst)
                                        .map(Message::getPayload)
                                        .contextWrite(this::addEmptyContextualInfo);
        queryTransaction.complete(new GenericQueryResponseMessage<>(payloadFlux));
    }

    private Flux<QueryResponseMessage<T>> onFirst(Signal<? extends QueryResponseMessage<T>> firstSignal,
                                                  Flux<QueryResponseMessage<T>> flux) {
        if (firstSignal.isOnNext()) {
            QueryResponseMessage<T> firstResponse = firstSignal.get();

            AtomicReference<String> messageId = firstSignal.getContextView()
                                                           .get(MESSAGE_IDENTIFIER);
            messageId.set(firstResponse.getIdentifier());

            AtomicReference<MetaData> metaData = firstSignal.getContextView()
                                                            .get(MESSAGE_METADATA);
            metaData.set(firstResponse.getMetaData());
        }
        return flux;
    }

    private Context addEmptyContextualInfo(Context ctx) {
        return ctx.put(MESSAGE_IDENTIFIER, new AtomicReference<String>())
                  .put(MESSAGE_METADATA, new AtomicReference<MetaData>());
    }

    private QueryResponseMessage<T> deserialize(QueryResponse queryResponse) {
        //noinspection unchecked
        return serializer.deserializeResponse(queryResponse,
                                              ResponseTypes.instanceOf((Class<T>) responseType.getExpectedResponseType()));
    }

    @Override
    public long priority() {
        return priority;
    }
}
